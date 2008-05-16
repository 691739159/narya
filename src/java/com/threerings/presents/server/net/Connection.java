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

package com.threerings.presents.server.net;

import java.io.EOFException;
import java.io.IOException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.samskivert.util.StringUtil;

import com.threerings.io.ByteBufferInputStream;
import com.threerings.io.ByteBufferOutputStream;
import com.threerings.io.FramedInputStream;
import com.threerings.io.FramingOutputStream;
import com.threerings.io.ObjectInputStream;
import com.threerings.io.ObjectOutputStream;
import com.threerings.io.UnreliableObjectInputStream;
import com.threerings.io.UnreliableObjectOutputStream;

import com.threerings.presents.Log;
import com.threerings.presents.net.DownstreamMessage;
import com.threerings.presents.net.PingRequest;
import com.threerings.presents.net.UpstreamMessage;
import com.threerings.presents.util.DatagramSequencer;

/**
 * The base connection class implements the net event handler interface and processes raw incoming
 * network data into a stream of parsed <code>UpstreamMessage</code> objects. It also provides the
 * means to send messages to the client and facilities for checking delinquency.
 */
public abstract class Connection implements NetEventHandler
{
    /**
     * Constructs a connection object that is associated with the supplied socket.
     *
     * @param cmgr The connection manager with which this connection is associated.
     * @param selkey the key used by the NIO code to track this connection.
     * @param channel The socket channel from which we'll be reading messages.
     * @param createStamp The time at which this connection was created.
     */
    public Connection (ConnectionManager cmgr, SelectionKey selkey, SocketChannel channel,
                       long createStamp)
        throws IOException
    {
        _cmgr = cmgr;
        _selkey = selkey;
        _channel = channel;
        _lastEvent = createStamp;
        _connectionId = ++_lastConnectionId;
    }

    /**
     * Instructs the connection to pass parsed messages on to this handler for processing. This
     * should be done before the connection is turned loose to process messages.
     */
    public void setMessageHandler (MessageHandler handler)
    {
        _handler = handler;
    }

    /**
     * Configures this connection with a custom class loader.
     */
    public void setClassLoader (ClassLoader loader)
    {
        _loader = loader;
        if (_oin != null) {
            _oin.setClassLoader(loader);
        }
    }

    /**
     * Returns the connection's unique identifier.
     */
    public int getConnectionId ()
    {
        return _connectionId;
    }

    /**
     * Returns the selection key associated with our socket channel.
     */
    public SelectionKey getSelectionKey ()
    {
        return _selkey;
    }

    /**
     * Returns the non-blocking socket object used to construct this connection.
     */
    public SocketChannel getChannel ()
    {
        return _channel;
    }

    /**
     * Returns the address associated with this connection or null if it has no underlying socket
     * channel.
     */
    public InetAddress getInetAddress ()
    {
        return (_channel == null) ? null : _channel.socket().getInetAddress();
    }

    /**
     * Returns the address to which datagrams should be sent or null if no datagram address has
     * been established.
     */
    public InetSocketAddress getDatagramAddress ()
    {
        return _datagramAddress;
    }

    /**
     * Sets the secret string used to authenticate datagrams from the client.
     */
    public void setDatagramSecret (String secret)
    {
        try {
            _datagramSecret = secret.getBytes("UTF-8");
        } catch (Exception e) {
            _datagramSecret = new byte[0]; // shouldn't happen
        }
    }

    /**
     * Returns true if this connection is closed.
     */
    public boolean isClosed ()
    {
        return (_channel == null);
    }

    /**
     * Closes this connection and unregisters it from the connection manager. This should only be
     * called from the conmgr thread.
     */
    public void close ()
    {
        // we shouldn't be closed twice
        if (isClosed()) {
            Log.warning("Attempted to re-close connection " + this + ".");
            Thread.dumpStack();
            return;
        }

        // unregister from the select set
        _cmgr.connectionClosed(this);

        // close our socket
        closeSocket();
    }

    /**
     * Called when there is a failure reading or writing on this connection. We notify the
     * connection manager and close ourselves down.
     */
    public void handleFailure (IOException ioe)
    {
        // if we're already closed, then something is seriously funny
        if (isClosed()) {
            Log.warning("Failure reported on closed connection " + this + ".");
            Thread.dumpStack();
            return;
        }

        // let the connection manager know we're hosed
        _cmgr.connectionFailed(this, ioe);

        // and close our socket
        closeSocket();
    }

    /**
     * Returns the object input stream associated with this connection.  This should only be used
     * by the connection manager.
     */
    protected ObjectInputStream getObjectInputStream ()
    {
        return _oin;
    }

    /**
     * Instructs this connection to inherit its streams from the supplied connection object. This
     * is called by the connection manager when the time comes to pass streams from the authing
     * connection to the running connection.
     */
    protected void inheritStreams (Connection other)
    {
        _fin = other._fin;
        _oin = other._oin;
        _oout = other._oout;
        if (_loader != null) {
            _oin.setClassLoader(_loader);
        }
    }

    /**
     * Returns the object output stream associated with this connection (creating it if
     * necessary). This should only be used by the connection manager.
     */
    protected ObjectOutputStream getObjectOutputStream (FramingOutputStream fout)
    {
        // we're lazy about creating our output stream because we may be inheriting it from our
        // authing connection and we don't want to unnecessarily create it in that case
        if (_oout == null) {
            _oout = new ObjectOutputStream(fout);
        }
        return _oout;
    }

    /**
     * Sets the object output stream used by this connection. This should only be called by the
     * connection manager.
     */
    protected void setObjectOutputStream (ObjectOutputStream oout)
    {
        _oout = oout;
    }

    /**
     * Returns a reference to the connection's datagram sequencer.  This should only be called by
     * the connection manager.
     */
    protected DatagramSequencer getDatagramSequencer ()
    {
        return _sequencer;
    }

    /**
     * Closes the socket associated with this connection. This happens when we receive EOF, are
     * requested to close down or when our connection fails.
     */
    protected void closeSocket ()
    {
        if (_channel == null) {
            return;
        }

        Log.debug("Closing channel " + this + ".");
        try {
            _channel.close();
        } catch (IOException ioe) {
            Log.warning("Error closing connection [conn=" + this + ", error=" + ioe + "].");
        }

        // clear out our references to prevent repeat closings
        _selkey = null;
        _channel = null;
    }

    /**
     * Called when our client socket has data available for reading.
     */
    public int handleEvent (long when)
    {
        // make a note that we received an event as of this time
        _lastEvent = when;

        int bytesIn = 0;
        try {
            // we're lazy about creating our input streams because we may be inheriting them from
            // our authing connection and we don't want to unnecessarily create them in that case
            if (_fin == null) {
                _fin = new FramedInputStream();
                _oin = new ObjectInputStream(_fin);
                if (_loader != null) {
                    _oin.setClassLoader(_loader);
                }
            }

            // there may be more than one frame in the buffer, so we keep reading them until we run
            // out of data
            while (_fin.readFrame(_channel)) {
                // make a note of how many bytes are in this frame (including the frame length
                // bytes which aren't reported in available())
                bytesIn = _fin.available() + 4;
                // parse the message and pass it on
                UpstreamMessage msg = (UpstreamMessage)_oin.readObject();
                msg.received = when;
//                 Log.info("Read message " + msg + ".");
                _handler.handleMessage(msg);
            }

        } catch (EOFException eofe) {
            // close down the socket gracefully
            close();

        } catch (ClassNotFoundException cnfe) {
            Log.warning("Error reading message from socket [channel=" +
                        StringUtil.safeToString(_channel) + ", error=" + cnfe + "].");
            // deal with the failure
            String errmsg = "Unable to decode incoming message.";
            handleFailure((IOException) new IOException(errmsg).initCause(cnfe));

        } catch (IOException ioe) {
            // don't log a warning for the ever-popular "the client dropped the connection" failure
            String msg = ioe.getMessage();
            if (msg == null || msg.indexOf("reset by peer") == -1) {
                Log.warning("Error reading message from socket [channel=" +
                            StringUtil.safeToString(_channel) + ", error=" + ioe + "].");
            }
            // deal with the failure
            handleFailure(ioe);
        }

        return bytesIn;
    }

    /**
     * Processes a datagram sent to this connection.
     */
    public void handleDatagram (InetSocketAddress source, ByteBuffer buf, long when)
    {
        // lazily create our various bits and bobs
        if (_digest == null) {
            try {
                _digest = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException nsae) {
                Log.warning("Missing MD5 algorithm.");
                return;
            }
            ByteBufferInputStream bin = new ByteBufferInputStream(buf);
            _sequencer = new DatagramSequencer(
                new UnreliableObjectInputStream(bin),
                new UnreliableObjectOutputStream(_cmgr.getFlattener()));
        }

        // verify the hash
        buf.position(12);
        _digest.update(buf);
        byte[] hash = _digest.digest(_datagramSecret);
        buf.position(4);
        for (int ii = 0; ii < 8; ii++) {
            if (hash[ii] != buf.get()) {
                Log.warning("Datagram failed hash check [connectionId=" + _connectionId +
                    ", source=" + source + "].");
                return;
            }
        }

        // update our target address
        _datagramAddress = source;

        // read the contents through the sequencer
        try {
            UpstreamMessage msg = (UpstreamMessage)_sequencer.readDatagram();
            if (msg == null) {
                return; // received out of order
            }
            msg.received = when;
            _handler.handleMessage(msg);

        } catch (ClassNotFoundException cnfe) {
            Log.warning("Error reading datagram [error=" + cnfe + "].");

        } catch (IOException ioe) {
            Log.warning("Error reading datagram [error=" + ioe + "].");
        }
    }

    // documentation inherited from interface
    public boolean checkIdle (long now)
    {
        long idleMillis = now - _lastEvent;
        if (idleMillis < PingRequest.PING_INTERVAL + LATENCY_GRACE) {
            return false;
        }
        if (isClosed()) {
            return true;
        }
        Log.info("Disconnecting non-communicative client [conn=" + this +
                 ", idle=" + idleMillis + "ms].");
        return true;
    }

    /**
     * Posts a downstream message for delivery to this connection. The message will be delivered by
     * the conmgr thread as soon as it gets to it.
     */
    public void postMessage (DownstreamMessage msg)
    {
        // pass this along to the connection manager
        _cmgr.postMessage(this, msg);
    }

    protected ConnectionManager _cmgr;
    protected SelectionKey _selkey;
    protected SocketChannel _channel;

    protected long _lastEvent;

    protected int _connectionId;

    protected FramedInputStream _fin;
    protected ObjectInputStream _oin;
    protected ObjectOutputStream _oout;

    protected InetSocketAddress _datagramAddress;
    protected byte[] _datagramSecret;

    protected MessageDigest _digest;
    protected DatagramSequencer _sequencer;

    protected MessageHandler _handler;
    protected ClassLoader _loader;

    /** The last connection id assigned. */
    protected static int _lastConnectionId;

    /** The number of milliseconds beyond the ping interval that we allow a client's network
     * connection to be idle before we forcibly disconnect them. */
    protected static final long LATENCY_GRACE = 30 * 1000L;
}
