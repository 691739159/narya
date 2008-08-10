//
// $Id$
//
// Narya library - tools for developing networked games
// Copyright (C) 2002-2007 Three Rings Design, Inc., All Rights Reserved
// http://www.threerings.net/code/narya/
//
// This library is free software; you can redistribute it and/or modify it
// under the terms of the GNU Lesser General Public License as published
// by the Free Software Foundation; either version 2.1 of the License, or
// (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

package com.threerings.presents.peer.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import java.util.List;
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Injector;

import com.samskivert.jdbc.RepositoryUnit;
import com.samskivert.jdbc.WriteOnlyUnit;
import com.samskivert.util.ArrayIntSet;
import com.samskivert.util.ChainedResultListener;
import com.samskivert.util.Interval;
import com.samskivert.util.Invoker;
import com.samskivert.util.ObserverList;
import com.samskivert.util.ResultListener;
import com.samskivert.util.ResultListenerList;
import com.samskivert.util.Tuple;

import com.threerings.io.ObjectInputStream;
import com.threerings.io.ObjectOutputStream;
import com.threerings.io.Streamable;
import com.threerings.util.Name;

import com.threerings.presents.annotation.MainInvoker;
import com.threerings.presents.data.ClientObject;
import com.threerings.presents.dobj.DObject;
import com.threerings.presents.dobj.EntryAddedEvent;
import com.threerings.presents.dobj.EntryRemovedEvent;
import com.threerings.presents.dobj.EntryUpdatedEvent;
import com.threerings.presents.dobj.ObjectAccessException;
import com.threerings.presents.dobj.SetListener;
import com.threerings.presents.dobj.Subscriber;

import com.threerings.presents.client.Client;
import com.threerings.presents.server.ClientManager;
import com.threerings.presents.server.InvocationManager;
import com.threerings.presents.server.PresentsClient;
import com.threerings.presents.server.PresentsDObjectMgr;
import com.threerings.presents.server.ShutdownManager;
import com.threerings.presents.server.net.ConnectionManager;

import com.threerings.presents.peer.data.ClientInfo;
import com.threerings.presents.peer.data.NodeObject;
import com.threerings.presents.peer.net.PeerCreds;
import com.threerings.presents.peer.server.persist.NodeRecord;
import com.threerings.presents.peer.server.persist.NodeRepository;

import static com.threerings.presents.Log.log;

/**
 * Manages connections to the other nodes in a Presents server cluster. Each server maintains a
 * client connection to the other servers and subscribes to the {@link NodeObject} of all peer
 * servers and uses those objects to communicate cross-node information.
 */
public abstract class PeerManager
    implements PeerProvider, ClientManager.ClientObserver, ShutdownManager.Shutdowner
{
    /**
     * Used by entities that wish to know when cached data has become stale due to a change on
     * one of our peer servers.
     */
    public static interface StaleCacheObserver
    {
        /**
         * Called when some possibly cached data has changed on one of our peer servers.
         */
        void changedCacheData (Streamable data);
    }

    /**
     * Used by entities that wish to know when this peer has been forced into immediately releasing
     * a lock.
     */
    public static interface DroppedLockObserver
    {
        /**
         * Called when this node has been forced to drop a lock.
         */
        void droppedLock (NodeObject.Lock lock);
    }

    /**
     * Wraps an operation that needs a shared resource lock to be acquired before it can be
     * performed, and released after it completes. Used by {@link #performWithLock}.
     */
    public static interface LockedOperation
    {
        /**
         * Called when the resource lock was acquired successfully. The lock will be released
         * immediately after this function call finishes.
         */
        void run ();

        /**
         * Called when the resource lock was not acquired successfully, with the name of the peer
         * who is holding the lock (or null in case of a generic failure).
         */
        void fail (String peerName);
    }

    /**
     * Encapsulates code that is meant to be executed one or more servers.
     *
     * <p><b>Note well</b>: the action you provide is serialized and sent to the server to which
     * the member is currently connection. This means you MUST NOT instantiate a NodeAction
     * anonymously because that will maintain an implicit non-transient reference to its containing
     * class which will then also be serialized (assuming it is even serializable).
     */
    public static abstract class NodeAction implements Streamable
    {
        /** Returns true if this action should be executed on the specified node. This will be
         * called on the originating server to decide whether or not to deliver the action to the
         * server in question. */
        public abstract boolean isApplicable (NodeObject nodeobj);

        /** Invokes the action on the target server. */
        public void invoke () {
            try {
                execute();
            } catch (Throwable t) {
                log.warning(getClass().getName() + " failed.");
            }
        }

        protected abstract void execute ();
    }

    /**
     * Creates an uninitialized peer manager.
     */
    @Inject public PeerManager (ShutdownManager shutmgr)
    {
        shutmgr.registerShutdowner(this);
    }

    /**
     * Returns the distributed object that represents this node to its peers.
     */
    public NodeObject getNodeObject ()
    {
        return _nodeobj;
    }

    /**
     * Initializes this peer manager and initiates the process of connecting to its peer
     * nodes. This will also reconfigure the ConnectionManager and ClientManager with peer related
     * bits, so this should not be called until <em>after</em> the main server has set up its
     * client factory and authenticator.
     *
     * @param nodeName this node's unique name.
     * @param sharedSecret a shared secret used to allow the peers to authenticate with one
     * another.
     * @param hostName the DNS name of the server running this node.
     * @param publicHostName if non-null, a separate public DNS hostname by which the node is to be
     * known to normal clients (we may want inter-peer communication to take place over a different
     * network than the communication between real clients and the various peer servers).
     * @param port the port on which other nodes should connect to us.
     */
    public void init (Injector injector, String nodeName, String sharedSecret,
                      String hostName, String publicHostName, int port)
    {
        _injector = injector;
        _nodeName = nodeName;
        _sharedSecret = sharedSecret;

        // wire ourselves into the server
        _conmgr.addChainedAuthenticator(new PeerAuthenticator(this));
        _clmgr.setClientFactory(new PeerClientFactory(_clmgr.getClientFactory()));

        // create our node object
        _nodeobj = _omgr.registerObject(createNodeObject());
        _nodeobj.setNodeName(nodeName);

        // register ourselves with the node table
        _self = new NodeRecord(
            _nodeName, hostName, (publicHostName == null) ? hostName : publicHostName, port);
        _invoker.postUnit(new WriteOnlyUnit("registerNode(" + _self + ")") {
            @Override
            public void invokePersist () throws Exception {
                _noderepo.updateNode(_self);
            }
        });

        // set the invocation service
        _nodeobj.setPeerService(_invmgr.registerDispatcher(new PeerDispatcher(this)));

        // register ourselves as a client observer
        _clmgr.addClientObserver(this);

        // and start our peer refresh interval
        _peerRefresher.schedule(5000L, 60*1000L);

        // give derived classes an easy way to get in on the init action
        didInit();
    }

    /**
     * Returns true if the supplied peer credentials match our shared secret.
     */
    public boolean isAuthenticPeer (PeerCreds creds)
    {
        return PeerCreds.createPassword(creds.getNodeName(), _sharedSecret).equals(
            creds.getPassword());
    }

    /**
     * Locates the client with the specified name. Returns null if the client is not logged onto
     * any peer.
     */
    public ClientInfo locateClient (final Name key)
    {
        return lookupNodeDatum(new Function<NodeObject,ClientInfo>() {
            public ClientInfo apply (NodeObject nodeobj) {
                return nodeobj.clients.get(key);
            }
        });
    }

    /**
     * Locates a datum from among the set of peer {@link NodeObject}s. Objects are searched in
     * arbitrary order and the first non-null value returned by the supplied lookup operation is
     * returned to the caller. Null if all lookup operations returned null.
     */
    public <T> T lookupNodeDatum (Function<NodeObject,T> op)
    {
        T value = op.apply(_nodeobj);
        if (value != null) {
            return value;
        }
        for (PeerNode peer : _peers.values()) {
            if (peer.nodeobj == null) {
                continue;
            }
            value = op.apply(peer.nodeobj);
            if (value != null) {
                return value;
            }
        }
        return value;
    }

    /**
     * Applies the supplied operation to all {@link NodeObject}s. The operation should not modify
     * the objects unless you really know what you're doing. more likely it will summarize
     * information contained therein.
     */
    public void applyToNodes (Function<NodeObject, Void> op)
    {
        op.apply(_nodeobj);
        for (PeerNode peer : _peers.values()) {
            if (peer.nodeobj != null) {
                op.apply(peer.nodeobj);
            }
        }
    }

    /**
     * Invokes the supplied function on <em>all</em> node objects (except the local node). A caller
     * that needs to call an invocation service method on a remote node should use this mechanism
     * to locate the appropriate node (or nodes) and call the desired method.
     */
    public void invokeOnNodes (Function<Tuple<Client,NodeObject>,Void> func)
    {
        for (PeerNode peer : _peers.values()) {
            if (peer.nodeobj != null) {
                func.apply(Tuple.create(peer.getClient(), peer.nodeobj));
            }
        }
    }

    /**
     * Invokes the supplied action on this and any other server that it indicates is appropriate.
     * The action will be executed on the distributed object thread, but this method does not need
     * to be called from the distributed object thread.
     */
    public void invokeNodeAction (final NodeAction action)
    {
        invokeNodeAction(action, null);
    }

    /**
     * Invokes the supplied action on this and any other server that it indicates is appropriate.
     * The action will be executed on the distributed object thread, but this method does not need
     * to be called from the distributed object thread.
     *
     * @param onDropped a runnable to be executed if the action was not invoked on the local server
     * or any peer node due to failing to match any of the nodes. The runnable will be executed on
     * the dobj event thread and will be passed the node action that was not invoked.
     */
    public <T extends NodeAction> void invokeNodeAction (
        final T action, final Function<T,Void> onDropped)
    {
        // if we're not on the dobjmgr thread, get there
        if (!_omgr.isDispatchThread()) {
            _omgr.postRunnable(new Runnable() {
                public void run () {
                    invokeNodeAction(action, onDropped);
                }
            });
            return;
        }

        // first serialize the action to make sure we can
        byte[] actionBytes = flattenAction(action);

        // invoke the action on our local server if appropriate
        boolean invoked = false;
        if (action.isApplicable(_nodeobj)) {
            _injector.injectMembers(action);
            action.invoke();
            invoked = true;
        }

        // now send it to any remote node that is also appropriate
        for (PeerNode peer : _peers.values()) {
            if (peer.nodeobj != null && action.isApplicable(peer.nodeobj)) {
                peer.nodeobj.peerService.invokeAction(peer.getClient(), actionBytes);
                invoked = true;
            }
        }

        // if we did not invoke the action on any node, call the onDropped handler
        if (!invoked && onDropped != null) {
            onDropped.apply(action);
        }
    }

    /**
     * Invokes a node action on a specific node <em>without</em> executing {@link
     * NodeAction#isApplicable} to determine whether the action is applicable.
     */
    public void invokeNodeAction (String nodeName, NodeAction action)
    {
        PeerNode peer = _peers.get(nodeName);
        if (peer != null) {
            peer.nodeobj.peerService.invokeAction(peer.getClient(), flattenAction(action));
        }
    }

    /**
     * Initiates a proxy on an object that is managed by the specified peer. The object will be
     * proxied into this server's distributed object space and its local oid reported back to the
     * supplied result listener.
     *
     * <p> Note that proxy requests <em>do not</em> stack like subscription requests. Only one
     * entity must issue a request to proxy an object and that entity must be responsible for
     * releasing the proxy when it knows that there are no longer any local subscribers to the
     * object.
     */
    public <T extends DObject> void proxyRemoteObject (
        String nodeName, int remoteOid, final ResultListener<Integer> listener)
    {
        final Client peer = getPeerClient(nodeName);
        if (peer == null) {
            String errmsg = "Have no connection to peer [node=" + nodeName + "].";
            listener.requestFailed(new ObjectAccessException(errmsg));
            return;
        }

        final Tuple<String, Integer> key = Tuple.create(nodeName, remoteOid);
        if (_proxies.containsKey(key)) {
            String errmsg = "Cannot proxy already proxied object [key=" + key + "].";
            listener.requestFailed(new ObjectAccessException(errmsg));
            return;
        }

        // issue a request to subscribe to the remote object
        peer.getDObjectManager().subscribeToObject(remoteOid, new Subscriber<T>() {
            public void objectAvailable (T object) {
                // make a note of this proxy mapping
                _proxies.put(key, new Tuple<Subscriber<?>, DObject>(this, object));
                // map the object into our local oid space
                _omgr.registerProxyObject(object, peer.getDObjectManager());
                // then tell the caller about the (now remapped) oid
                listener.requestCompleted(object.getOid());
            }
            public void requestFailed (int oid, ObjectAccessException cause) {
                listener.requestFailed(cause);
            }
        });
    }

    /**
     * Unsubscribes from and clears a proxied object. The caller must be sure that there are no
     * remaining subscribers to the object on this local server.
     */
    public void unproxyRemoteObject (String nodeName, int remoteOid)
    {
        Tuple<String,Integer> key = Tuple.create(nodeName, remoteOid);
        Tuple<Subscriber<?>, DObject> bits = _proxies.remove(key);
        if (bits == null) {
            log.warning("Requested to clear unknown proxy [key=" + key + "].");
            return;
        }

        // clear out the local object manager's proxy mapping
        _omgr.clearProxyObject(remoteOid, bits.right);

        final Client peer = getPeerClient(nodeName);
        if (peer == null) {
            log.warning("Unable to unsubscribe from proxy, missing peer [key=" + key + "].");
            return;
        }

        // restore the object's omgr reference to our ClientDObjectMgr and its oid back to the
        // remote oid so that it can properly finish the unsubscription process
        bits.right.setOid(remoteOid);
        bits.right.setManager(peer.getDObjectManager());

        // finally unsubscribe from the object on our peer
        peer.getDObjectManager().unsubscribeFromObject(remoteOid, bits.left);
    }

    /**
     * Returns the client object representing the connection to the named peer, or
     * <code>null</code> if we are not currently connected to it.
     */
    public Client getPeerClient (String nodeName)
    {
        PeerNode peer = _peers.get(nodeName);
        return (peer == null) ? null : peer.getClient();
    }

    /**
     * Returns the public hostname to use when connecting to the specified peer or null if the peer
     * is not currently connected to this server.
     */
    public String getPeerPublicHostName (String nodeName)
    {
        if (_nodeName.equals(nodeName)) {
            return _self.publicHostName;
        }
        PeerNode peer = _peers.get(nodeName);
        return (peer == null) ? null : peer.getPublicHostName();
    }

    /**
     * Returns the port on which to connect to the specified peer or -1 if the peer is not
     * currently connected to this server.
     */
    public int getPeerPort (String nodeName)
    {
        if (_nodeName.equals(nodeName)) {
            return _self.port;
        }
        PeerNode peer = _peers.get(nodeName);
        return (peer == null) ? -1 : peer.getPort();
    }

    /**
     * Acquires a lock on a resource shared amongst this node's peers.  If the lock is successfully
     * acquired, the supplied listener will receive this node's name.  If another node acquires the
     * lock first, then the listener will receive the name of that node.
     */
    public void acquireLock (final NodeObject.Lock lock, final ResultListener<String> listener)
    {
        // wait until any pending resolution is complete
        queryLock(lock, new ChainedResultListener<String, String>(listener) {
            public void requestCompleted (String result) {
                if (result == null) {
                    if (_suboids.isEmpty()) {
                        _nodeobj.addToLocks(lock);
                        listener.requestCompleted(_nodeName);
                    } else {
                        _locks.put(lock, new LockHandler(lock, true, listener));
                    }
                } else {
                    listener.requestCompleted(result);
                }
            }
        });
    }

    /**
     * Releases a lock.  This can be cancelled using {@link #reacquireLock}, in which case the
     * passed listener will receive this node's name as opposed to <code>null</code>, which
     * signifies that the lock has been successfully released.
     */
    public void releaseLock (final NodeObject.Lock lock, final ResultListener<String> listener)
    {
        // wait until any pending resolution is complete
        queryLock(lock, new ChainedResultListener<String, String>(listener) {
            public void requestCompleted (String result) {
                if (_nodeName.equals(result)) {
                    if (_suboids.isEmpty()) {
                        _nodeobj.removeFromLocks(lock);
                        listener.requestCompleted(null);
                    } else {
                        _locks.put(lock, new LockHandler(lock, false, listener));
                    }
                } else {
                    if (result != null) {
                        log.warning("Tried to release lock held by another peer [lock=" + lock +
                                    ", owner=" + result + "].");
                    }
                    listener.requestCompleted(result);
                }
            }
        });
    }

    /**
     * Reacquires a lock after a call to {@link #releaseLock} but before the result listener
     * supplied to that method has been notified with the result of the action.  The result
     * listener will receive the name of this node to indicate that the lock is still held.  If a
     * node requests to release a lock, then receives a lock-related request from another peer, it
     * can use this method to cancel the release reliably, since the lock-related request will have
     * been sent before the peer's ratification of the release.
     */
    public void reacquireLock (NodeObject.Lock lock)
    {
        // make sure we're releasing it
        LockHandler handler = _locks.get(lock);
        if (handler == null || !handler.getNodeName().equals(_nodeName) || handler.isAcquiring()) {
            log.warning("Tried to reacquire lock not being released [lock=" + lock +
                        ", handler=" + handler + "].");
            return;
        }

        // perform an update to let other nodes know that we're reacquiring
        _nodeobj.updateLocks(lock);

        // cancel the handler and report to any listeners
        _locks.remove(lock);
        handler.cancel();
        handler.listeners.requestCompleted(_nodeName);
    }

    /**
     * Determines the owner of the specified lock, waiting for any resolution to complete before
     * notifying the supplied listener.
     */
    public void queryLock (NodeObject.Lock lock, ResultListener<String> listener)
    {
        // if it's being resolved, add the listener to the list
        LockHandler handler = _locks.get(lock);
        if (handler != null) {
            handler.listeners.add(listener);
            return;
        }

        // otherwise, return its present value
        listener.requestCompleted(queryLock(lock));
    }

    /**
     * Finds the owner of the specified lock (if any) among this node and its peers.  This answer
     * is not definitive, as the lock may be in the process of resolving.
     */
    public String queryLock (NodeObject.Lock lock)
    {
        // look for it in our own lock set
        if (_nodeobj.locks.contains(lock)) {
            return _nodeName;
        }

        // then in our peers
        for (PeerNode peer : _peers.values()) {
            if (peer.nodeobj != null && peer.nodeobj.locks.contains(lock)) {
                return peer.getNodeName();
            }
        }
        return null;
    }

    /**
     * Tries to acquire the resource lock and, if successful, performs the operation and releases
     * the lock; if unsuccessful, calls the operation's failure handler. Please note: the lock will
     * be released immediately after the operation.
     */
    public void performWithLock (final NodeObject.Lock lock, final LockedOperation operation)
    {
        acquireLock(lock, new ResultListener<String>() {
            public void requestCompleted (String nodeName) {
                if (getNodeObject().nodeName.equals(nodeName)) {
                    // lock acquired successfully - perform the operation, and release the lock.
                    try {
                        operation.run();
                    } finally {
                        releaseLock(lock, new ResultListener.NOOP<String>());
                    }
                } else {
                    // some other peer beat us to it
                    operation.fail(nodeName);
                    if (nodeName == null) {
                        log.warning("Lock acquired by null? [lock=" + lock + "].");
                    }
                }
            }
            public void requestFailed (Exception cause) {
                log.warning("Lock acquisition failed [lock=" + lock + "].", cause);
                operation.fail(null);
            }
        });
    }

    /**
     * Adds an observer to notify when this peer has been forced to drop a lock immediately.
     */
    public void addDroppedLockObserver (DroppedLockObserver observer)
    {
        _dropobs.add(observer);
    }

    /**
     * Removes a dropped lock observer from the list.
     */
    public void removeDroppedLockObserver (DroppedLockObserver observer)
    {
        _dropobs.remove(observer);
    }

    /**
     * Called by {@link PeerClient}s when clients subscribe to the {@link NodeObject}.
     */
    public void clientSubscribedToNode (int cloid)
    {
        _suboids.add(cloid);
    }

    /**
     * Called by {@link PeerClient}s when clients unsubscribe from the {@link NodeObject}.
     */
    public void clientUnsubscribedFromNode (int cloid)
    {
        _suboids.remove(cloid);
        for (LockHandler handler : _locks.values().toArray(new LockHandler[_locks.size()])) {
            if (handler.getNodeName().equals(_nodeName)) {
                handler.clientUnsubscribed(cloid);
            }
        }
    }

    /**
     * Registers a stale cache observer.
     */
    public void addStaleCacheObserver (String cache, StaleCacheObserver observer)
    {
        ObserverList<StaleCacheObserver> list = _cacheobs.get(cache);
        if (list == null) {
            list = new ObserverList<StaleCacheObserver>(ObserverList.FAST_UNSAFE_NOTIFY);
            _cacheobs.put(cache, list);
        }
        list.add(observer);
    }

    /**
     * Removes a stale cache observer registration.
     */
    public void removeStaleCacheObserver (String cache, StaleCacheObserver observer)
    {
        ObserverList<StaleCacheObserver> list = _cacheobs.get(cache);
        if (list == null) {
            return;
        }
        list.remove(observer);
        if (list.isEmpty()) {
            _cacheobs.remove(cache);
        }
    }

    /**
     * Called when cached data has changed on the local server and needs to inform our peers.
     */
    public void broadcastStaleCacheData (String cache, Streamable data)
    {
        _nodeobj.setCacheData(new NodeObject.CacheData(cache, data));
    }

    // from interface ShutdownManager.Shutdowner
    public void shutdown ()
    {
        // clear out our invocation service
        if (_nodeobj != null) {
            _invmgr.clearDispatcher(_nodeobj.peerService);
        }

        // stop our peer refresher interval
        _peerRefresher.cancel();

        // clear out our client observer registration
        _clmgr.removeClientObserver(this);

        // clear our record from the node table
        _invoker.postUnit(new WriteOnlyUnit("deleteNode(" + _nodeName + ")") {
            @Override
            public void invokePersist () throws Exception {
                _noderepo.deleteNode(_nodeName);
            }
        });

        // shut down the peers
        for (PeerNode peer : _peers.values()) {
            peer.shutdown();
        }
    }

    // from interface PeerProvider
    public void ratifyLockAction (ClientObject caller, NodeObject.Lock lock, boolean acquire)
    {
        LockHandler handler = _locks.get(lock);
        if (handler != null && handler.getNodeName().equals(_nodeName)) {
            handler.ratify(caller, acquire);
        } else {
            // this is not an error condition, as we may have cancelled the handler or
            // allowed another to take priority
        }
    }

    // from interface PeerProvider
    public void invokeAction (ClientObject caller, byte[] serializedAction)
    {
        NodeAction action = null;
        try {
            ObjectInputStream oin =
                new ObjectInputStream(new ByteArrayInputStream(serializedAction));
            action = (NodeAction)oin.readObject();
            _injector.injectMembers(action);
            action.invoke();
        } catch (Exception e) {
            log.warning("Failed to execute node action [from=" + caller.who() +
                    ", action=" + action + ", serializedSize=" + serializedAction.length + "].");
        }
    }

    // from interface ClientManager.ClientObserver
    public void clientSessionDidStart (PresentsClient client)
    {
        if (ignoreClient(client)) {
            return;
        }

        // create and publish a ClientInfo record for this client
        ClientInfo clinfo = createClientInfo();
        initClientInfo(client, clinfo);

        // sanity check
        if (_nodeobj.clients.contains(clinfo)) {
            log.warning("Received clientSessionDidStart() for already registered client!? " +
                        "[old=" + _nodeobj.clients.get(clinfo.getKey()) + ", new=" + clinfo + "].");
            // go ahead and update the record
            _nodeobj.updateClients(clinfo);
        } else {
            _nodeobj.addToClients(clinfo);
        }
    }

    // from interface ClientManager.ClientObserver
    public void clientSessionDidEnd (PresentsClient client)
    {
        if (ignoreClient(client)) {
            return;
        }

        // we scan through the list instead of relying on ClientInfo.getKey() because we want
        // derived classes to be able to override that for lookups that happen way more frequently
        // than logging off
        Name username = client.getCredentials().getUsername();
        for (ClientInfo clinfo : _nodeobj.clients) {
            if (clinfo.username.equals(username)) {
                clearClientInfo(client, clinfo);
                return;
            }
        }
        log.warning("Session ended for unregistered client [who=" + username + "].");
    }

    /**
     * Called after we have finished our initialization.
     */
    protected void didInit ()
    {
    }

    /**
     * Reloads the list of peer nodes from our table and refreshes each with a call to {@link
     * #refreshPeer}.
     */
    protected void refreshPeers ()
    {
        // load up information on our nodes
        _invoker.postUnit(new RepositoryUnit("refreshPeers") {
            @Override
            public void invokePersist () throws Exception {
                // let the world know that we're alive
                _noderepo.heartbeatNode(_nodeName);
                // then load up all the peer records
                _nodes = _noderepo.loadNodes();
            }
            @Override
            public void handleSuccess () {
                for (NodeRecord record : _nodes) {
                    if (record.nodeName.equals(_nodeName)) {
                        continue;
                    }
                    try {
                        refreshPeer(record);
                    } catch (Exception e) {
                        log.warning("Failure refreshing peer " + record + ".", e);
                    }
                }
            }
            @Override
            public long getLongThreshold () {
                return 700L;
            }
            protected List<NodeRecord> _nodes;
        });
    }

    /**
     * Ensures that we have a connection to the specified node if it has checked in since we last
     * failed to connect.
     */
    protected void refreshPeer (NodeRecord record)
    {
        PeerNode peer = _peers.get(record.nodeName);
        if (peer == null) {
            _peers.put(record.nodeName, peer = createPeerNode());
            peer.init(this, _omgr, record);
        }
        peer.refresh(record);
    }

    /**
     * Returns true if we should ignore the supplied client, false if we should let our other peers
     * know that this client is authenticated with this server. <em>Note:</em> this is called at
     * the beginning and end of the client session, so this method should return the same value
     * both times.
     */
    protected boolean ignoreClient (PresentsClient client)
    {
        // if this is another peer, don't publish their info
        return (client instanceof PeerClient);
    }

    /**
     * Creates the appropriate derived class of {@link NodeObject} which will be registered with
     * the distributed object system.
     */
    protected NodeObject createNodeObject ()
    {
        return new NodeObject();
    }

    /**
     * Creates a {@link ClientInfo} record which will subsequently be initialized by a call to
     * {@link #initClientInfo}.
     */
    protected ClientInfo createClientInfo ()
    {
        return new ClientInfo();
    }

    /**
     * Returns the lock handler for the specified lock.
     */
    protected LockHandler getLockHandler (NodeObject.Lock lock)
    {
        return _locks.get(lock);
    }

    protected LockHandler createLockHandler (PeerNode peer, NodeObject.Lock lock, boolean acquire)
    {
        LockHandler handler = new LockHandler(peer, lock, acquire);
        _locks.put(lock, handler);
        return handler;
    }

    /**
     * Called when possibly cached data has changed on one of our peer servers.
     */
    protected void changedCacheData (String cache, final Streamable data)
    {
        // see if we have any observers
        ObserverList<StaleCacheObserver> list = _cacheobs.get(cache);
        if (list == null) {
            return;
        }
        // if so, notify them
        list.apply(new ObserverList.ObserverOp<StaleCacheObserver>() {
            public boolean apply (StaleCacheObserver observer) {
                observer.changedCacheData(data);
                return true;
            }
        });
    }

    /**
     * Called when we have been forced to drop a lock.
     */
    protected void droppedLock (final NodeObject.Lock lock)
    {
        _nodeobj.removeFromLocks(lock);
        _dropobs.apply(new ObserverList.ObserverOp<DroppedLockObserver>() {
            public boolean apply (DroppedLockObserver observer) {
                observer.droppedLock(lock);
                return true;
            }
        });
    }

    /**
     * Initializes the supplied client info for the supplied client.
     */
    protected void initClientInfo (PresentsClient client, ClientInfo info)
    {
        info.username = client.getCredentials().getUsername();
    }

    /**
     * Called when a client ends their session to clear their information from our node object.
     */
    protected void clearClientInfo (PresentsClient client, ClientInfo info)
    {
        _nodeobj.removeFromClients(info.getKey());
    }

    /**
     * Creates a {@link PeerNode} to manage our connection to the specified peer.
     */
    protected PeerNode createPeerNode ()
    {
        return new PeerNode();
    }

    /**
     * Creates credentials that a {@link PeerNode} can use to authenticate with another node.
     */
    protected PeerCreds createCreds ()
    {
        return new PeerCreds(_nodeName, _sharedSecret);
    }

    /**
     * Called when a peer connects to this server.
     */
    protected void peerDidLogon (PeerNode peer)
    {
        // check for lock conflicts
        for (NodeObject.Lock lock : peer.nodeobj.locks) {
            PeerManager.LockHandler handler = _locks.get(lock);
            if (handler != null) {
                log.warning("Client hijacked lock in process of resolution [handler=" + handler +
                            ", node=" + peer.getNodeName() + "].");
                handler.clientHijackedLock(peer.getNodeName());

            } else if (_nodeobj.locks.contains(lock)) {
                log.warning("Client hijacked lock owned by this node [lock=" + lock +
                            ", node=" + peer.getNodeName() + "].");
                droppedLock(lock);
            }
        }
    }

    /**
     * Called when a peer disconnects from this server.
     */
    protected void peerDidLogoff (PeerNode peer)
    {
        // clear any locks held by that peer
        for (LockHandler handler : _locks.values().toArray(new LockHandler[_locks.size()])) {
            if (handler.getNodeName().equals(peer.getNodeName())) {
                handler.clientDidLogoff();
            }
        }
    }

    /**
     * Flattens the supplied node action into bytes.
     */
    protected byte[] flattenAction (NodeAction action)
    {
        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            ObjectOutputStream oout = new ObjectOutputStream(bout);
            oout.writeObject(action);
            return bout.toByteArray();
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Failed to serialize node action [action=" + action + "].", e);
        }
    }

    /**
     * Handles a lock in a state of resolution.
     */
    protected class LockHandler
        implements SetListener<NodeObject.Lock>
    {
        /** Listeners waiting for resolution. */
        public ResultListenerList<String> listeners = new ResultListenerList<String>();

        /**
         * Creates a handler to acquire or release a lock for this node.
         */
        public LockHandler (NodeObject.Lock lock, boolean acquire, ResultListener<String> listener)
        {
            _lock = lock;
            _acquire = acquire;
            listeners.add(listener);

            // signal our desire to acquire or release the lock
            if (acquire) {
                _nodeobj.setAcquiringLock(lock);
            } else {
                _nodeobj.setReleasingLock(lock);
            }

            // take a snapshot of the set of subscriber client oids; we will act when all of them
            // ratify
            _remoids = (ArrayIntSet)_suboids.clone();

            // schedule a timeout to act if something goes wrong
            (_timeout = new Interval(_omgr) {
                @Override
                public void expired () {
                    log.warning("Lock handler timed out, acting anyway [lock=" + _lock +
                                ", acquire=" + _acquire + "].");
                    activate();
                }
            }).schedule(LOCK_TIMEOUT);
        }

        /**
         * Creates a handle that tracks another node's acquisition or release of a lock.
         */
        public LockHandler (PeerNode peer, NodeObject.Lock lock, boolean acquire)
        {
            _peer = peer;
            _lock = lock;
            _acquire = acquire;

            // ratify the action
            peer.nodeobj.peerService.ratifyLockAction(peer.getClient(), lock, acquire);

            // listen for the act to take place
            peer.nodeobj.addListener(this);
        }

        /**
         * Returns the name of the node waiting to perform the action.
         */
        public String getNodeName ()
        {
            return (_peer == null) ? _nodeName : _peer.getNodeName();
        }

        /**
         * Checks whether we are acquiring as opposed to releasing a lock.
         */
        public boolean isAcquiring ()
        {
            return _acquire;
        }

        /**
         * Signals that one of the remote nodes has ratified the pending action.
         */
        public void ratify (ClientObject caller, boolean acquire)
        {
            if (acquire != _acquire) {
                return;
            }
            if (!_remoids.remove(caller.getOid())) {
                log.warning("Received unexpected ratification [handler=" + this +
                            ", who=" + caller.who() + "].");
            }
            maybeActivate();
        }

        /**
         * Called when a client has unsubscribed from this node (which is waiting for
         * ratification).
         */
        public void clientUnsubscribed (int cloid)
        {
            // unsubscription is implicit ratification
            if (_remoids.remove(cloid)) {
                maybeActivate();
            }
        }

        /**
         * Called when the connection to the controlling node has been broken.
         */
        public void clientDidLogoff ()
        {
            _locks.remove(_lock);
            listeners.requestCompleted(null);
        }

        /**
         * Called when a client hijacks the lock by having it in its node object when it connects.
         */
        public void clientHijackedLock (String nodeName)
        {
            cancel();
            _locks.remove(_lock);
            listeners.requestCompleted(nodeName);
        }

        /**
         * Cancels this handler, as another one will be taking its place.
         */
        public void cancel ()
        {
            if (_peer != null) {
                _peer.nodeobj.removeListener(this);
            } else {
                _timeout.cancel();
            }
        }

        // documentation inherited from interface SetListener
        public void entryAdded (EntryAddedEvent<NodeObject.Lock> event)
        {
            if (_acquire && event.getName().equals(NodeObject.LOCKS) &&
                event.getEntry().equals(_lock)) {
                wasActivated(_peer.getNodeName());
            }
        }

        // documentation inherited from interface SetListener
        public void entryRemoved (EntryRemovedEvent<NodeObject.Lock> event)
        {
            if (!_acquire && event.getName().equals(NodeObject.LOCKS) &&
                event.getOldEntry().equals(_lock)) {
                wasActivated(null);
            }
        }

        // documentation inherited from interface SetListener
        public void entryUpdated (EntryUpdatedEvent<NodeObject.Lock> event)
        {
            if (!_acquire && event.getName().equals(NodeObject.LOCKS) &&
                event.getEntry().equals(_lock)) {
                wasActivated(_peer.getNodeName());
            }
        }

        @Override // documentation inherited
        public String toString ()
        {
            return "[node=" + getNodeName() + ", lock=" + _lock + ", acquire=" + _acquire + "]";
        }

        /**
         * Performs the action if all remote nodes have ratified.
         */
        protected void maybeActivate ()
        {
            if (_remoids.isEmpty()) {
                _timeout.cancel();
                activate();
            }
        }

        /**
         * Performs the configured action.
         */
        protected void activate ()
        {
            _locks.remove(_lock);
            if (_acquire) {
                _nodeobj.addToLocks(_lock);
                listeners.requestCompleted(_nodeName);
            } else {
                _nodeobj.removeFromLocks(_lock);
                listeners.requestCompleted(null);
            }
        }

        /**
         * Called when the remote node has performed its action.
         */
        protected void wasActivated (String owner)
        {
            _peer.nodeobj.removeListener(this);
            _locks.remove(_lock);
            listeners.requestCompleted(owner);
        }

        protected PeerNode _peer;
        protected NodeObject.Lock _lock;
        protected boolean _acquire;
        protected ArrayIntSet _remoids;
        protected Interval _timeout;
    }

    // (this need not use a runqueue as all it will do is post an invoker unit)
    protected Interval _peerRefresher = new Interval() {
        @Override
        public void expired () {
            refreshPeers();
        }
    };

    protected String _nodeName, _sharedSecret;
    protected NodeRecord _self;
    protected NodeObject _nodeobj;
    protected Map<String,PeerNode> _peers = Maps.newHashMap();

    /** Used to resolve dependencies in unserialized {@link NodeAction} instances. */
    protected Injector _injector;

    /** The client oids of all peers subscribed to the node object. */
    protected ArrayIntSet _suboids = new ArrayIntSet();

    /** Contains a mapping of proxied objects to subscriber instances. */
    protected Map<Tuple<String,Integer>,Tuple<Subscriber<?>,DObject>> _proxies = Maps.newHashMap();

    /** Our stale cache observers. */
    protected Map<String, ObserverList<StaleCacheObserver>> _cacheobs = Maps.newHashMap();

    /** Listeners for dropped locks. */
    protected ObserverList<DroppedLockObserver> _dropobs = ObserverList.newFastUnsafe();

    /** Locks in the process of resolution. */
    protected Map<NodeObject.Lock, LockHandler> _locks = Maps.newHashMap();

    // our service dependencies
    @Inject protected ConnectionManager _conmgr;
    @Inject protected ClientManager _clmgr;
    @Inject protected PresentsDObjectMgr _omgr;
    @Inject protected InvocationManager _invmgr;
    @Inject protected @MainInvoker Invoker _invoker;
    @Inject protected NodeRepository _noderepo;

    /** We wait this long for peer ratification to complete before acquiring/releasing the lock. */
    protected static final long LOCK_TIMEOUT = 5000L;
}
