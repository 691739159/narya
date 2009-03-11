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

package com.threerings.crowd.peer.server;

import java.util.Map;

import com.google.common.collect.Maps;
import com.google.inject.Inject;

import com.threerings.util.Name;

import com.threerings.presents.data.ClientObject;
import com.threerings.presents.peer.data.ClientInfo;
import com.threerings.presents.peer.data.NodeObject;
import com.threerings.presents.peer.server.PeerManager;
import com.threerings.presents.peer.server.PeerNode;
import com.threerings.presents.server.InvocationException;
import com.threerings.presents.server.InvocationManager;
import com.threerings.presents.server.PresentsSession;
import com.threerings.presents.server.ShutdownManager;

import com.threerings.crowd.chat.client.ChatService;
import com.threerings.crowd.chat.data.UserMessage;
import com.threerings.crowd.chat.server.ChatProvider;
import com.threerings.crowd.data.BodyObject;
import com.threerings.crowd.peer.data.CrowdClientInfo;
import com.threerings.crowd.peer.data.CrowdNodeObject;

/**
 * Extends the standard peer manager and bridges certain Crowd services.
 */
public class CrowdPeerManager extends PeerManager
    implements CrowdPeerProvider, ChatProvider.ChatForwarder
{
    /**
     * Creates an uninitialized peer manager.
     */
    @Inject public CrowdPeerManager (ShutdownManager shutmgr)
    {
        super(shutmgr);
    }

    // from interface CrowdPeerProvider
    public void deliverTell (ClientObject caller, UserMessage message,
                             Name target, ChatService.TellListener listener)
        throws InvocationException
    {
        // we just forward the message as if it originated on this server
        _chatprov.deliverTell(message, target, listener);
    }

    // from interface CrowdPeerProvider
    public void deliverBroadcast (ClientObject caller, Name from, String bundle, String msg,
                                  boolean attention)
    {
        // deliver the broadcast locally on this server
        _chatprov.broadcast(from, bundle, msg, attention, false);
    }

    // from interface ChatProvider.ChatForwarder
    public boolean forwardTell (UserMessage message, Name target,
                                ChatService.TellListener listener)
    {
        // look up their auth username from their visible name
        Name username = _viztoauth.get(target);
        if (username == null) {
            return false; // sorry kid, don't know ya
        }

        // look through our peers to see if the target user is online on one of them
        for (PeerNode peer : _peers.values()) {
            CrowdNodeObject cnobj = (CrowdNodeObject)peer.nodeobj;
            if (cnobj == null) {
                continue;
            }
            // we have to use auth username to look up their ClientInfo
            CrowdClientInfo cinfo = (CrowdClientInfo)cnobj.clients.get(username);
            if (cinfo != null) {
                cnobj.crowdPeerService.deliverTell(peer.getClient(), message, target, listener);
                return true;
            }
        }
        return false;
    }

    // from interface ChatProvider.ChatForwarder
    public void forwardBroadcast (Name from, String bundle, String msg, boolean attention)
    {
        for (PeerNode peer : _peers.values()) {
            if (peer.nodeobj != null) {
                ((CrowdNodeObject)peer.nodeobj).crowdPeerService.deliverBroadcast(
                    peer.getClient(), from, bundle, msg, attention);
            }
        }
    }

    @Override // from PeerManager
    public void shutdown ()
    {
        super.shutdown();

        // unregister our invocation service
        if (_nodeobj != null) {
            _invmgr.clearDispatcher(((CrowdNodeObject)_nodeobj).crowdPeerService);
        }

        // clear our chat forwarder registration
        _chatprov.setChatForwarder(null);
    }

    @Override // from PeerManager
    protected NodeObject createNodeObject ()
    {
        return new CrowdNodeObject();
    }

    @Override // from PeerManager
    protected ClientInfo createClientInfo ()
    {
        return new CrowdClientInfo();
    }

    @Override // from PeerManager
    protected void initClientInfo (PresentsSession client, ClientInfo info)
    {
        super.initClientInfo(client, info);
        ((CrowdClientInfo)info).visibleName =
            ((BodyObject)client.getClientObject()).getVisibleName();
    }

    @Override // from PeerManager
    protected void didInit ()
    {
        super.didInit();

        // register and initialize our invocation service
        CrowdNodeObject cnobj = (CrowdNodeObject)_nodeobj;
        cnobj.setCrowdPeerService(_invmgr.registerDispatcher(new CrowdPeerDispatcher(this)));

        // register ourselves as a chat forwarder
        _chatprov.setChatForwarder(this);
    }

    @Override // from PeerManager
    protected void clientLoggedOn (String nodeName, ClientInfo clinfo)
    {
        super.clientLoggedOn(nodeName, clinfo);

        // keep a mapping from visibleName to auth username
        if (clinfo instanceof CrowdClientInfo) {
            CrowdClientInfo ccinfo = (CrowdClientInfo)clinfo;
            _viztoauth.put(ccinfo.visibleName, ccinfo.username);
        }
    }

    @Override // from PeerManager
    protected void clientLoggedOff (String nodeName, ClientInfo clinfo)
    {
        super.clientLoggedOff(nodeName, clinfo);

        // update our mapping from visibleName to auth username
        if (clinfo instanceof CrowdClientInfo) {
            CrowdClientInfo ccinfo = (CrowdClientInfo)clinfo;
            _viztoauth.remove(ccinfo.visibleName);
        }
    }

    /** A mapping of visible name to username for all clients on all *remote* nodes. */
    protected Map<Name, Name> _viztoauth = Maps.newHashMap();

    @Inject protected InvocationManager _invmgr;
    @Inject protected ChatProvider _chatprov;
}
