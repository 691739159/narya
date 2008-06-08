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

package com.threerings.presents.server;

import com.samskivert.io.PersistenceException;

import com.samskivert.util.Invoker;
import com.samskivert.util.ResultListener;

import com.threerings.presents.annotation.MainInvoker;
import com.threerings.presents.data.AuthCodes;

import com.threerings.presents.net.AuthRequest;
import com.threerings.presents.net.AuthResponse;
import com.threerings.presents.net.AuthResponseData;

import com.threerings.presents.server.net.AuthingConnection;
import com.threerings.presents.server.net.ConnectionManager;

import static com.threerings.presents.Log.log;

/**
 * The authenticator is a pluggable component of the authentication framework. The base class
 * handles the basic mechanics of authentication and a system would extend the base authenticator
 * and add code that does the actual client authentication.
 */
public abstract class Authenticator
{
    /**
     * Called by the connection management code when an authenticating connection has received its
     * authentication request from the client.
     */
    public void authenticateConnection (Invoker invoker, final AuthingConnection conn,
                                        final ResultListener<AuthingConnection> onComplete)
    {
        final AuthRequest req = conn.getAuthRequest();
        final AuthResponseData rdata = createResponseData();
        final AuthResponse rsp = new AuthResponse(rdata);

        invoker.postUnit(new Invoker.Unit("authenticateConnection") {
            public boolean invoke() {
                try {
                    processAuthentication(conn, rsp);
                } catch (Exception e) { // Persistence or Runtime
                    log.warning("Error authenticating user [areq=" + req + "].", e);
                    rdata.code = AuthCodes.SERVER_ERROR;
                }
                return true;
            }

            public void handleResult () {
                // stuff a reference to the auth response into the connection so that we have
                // access to it later in the authentication process
                conn.setAuthResponse(rsp);

                // send the response back to the client
                conn.postMessage(rsp);

                // if the authentication request was granted, let the connection manager know that
                // we just authed
                if (AuthResponseData.SUCCESS.equals(rdata.code)) {
                    onComplete.requestCompleted(conn);
                }
            }
        });
    }

    /**
     * Create a new AuthResponseData instance to use for authenticating a connection.
     */
    protected AuthResponseData createResponseData ()
    {
        return new AuthResponseData();
    }

    /**
     * Process the authentication for the specified connection. The method may return after it has
     * stuffed a valid response code in rsp.getData().code.
     *
     * @param conn The client connection.
     * @param rsp The response to the client, which will already contain an AuthResponseData
     * created by {@link #createResponseData}.
     */
    protected abstract void processAuthentication (AuthingConnection conn, AuthResponse rsp)
        throws PersistenceException;
}
