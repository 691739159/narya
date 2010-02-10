//
// $Id$
//
// Narya library - tools for developing networked games
// Copyright (C) 2002-2010 Three Rings Design, Inc., All Rights Reserved
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

package com.threerings.crowd.chat.server;

import com.threerings.crowd.chat.client.ChatService;
import com.threerings.crowd.chat.data.ChatMarshaller;
import com.threerings.presents.client.InvocationService;
import com.threerings.presents.data.ClientObject;
import com.threerings.presents.server.InvocationDispatcher;
import com.threerings.presents.server.InvocationException;
import com.threerings.util.Name;

/**
 * Dispatches requests to the {@link ChatProvider}.
 */
public class ChatDispatcher extends InvocationDispatcher<ChatMarshaller>
{
    /**
     * Creates a dispatcher that may be registered to dispatch invocation
     * service requests for the specified provider.
     */
    public ChatDispatcher (ChatProvider provider)
    {
        this.provider = provider;
    }

    @Override
    public ChatMarshaller createMarshaller ()
    {
        return new ChatMarshaller();
    }

    @Override
    public void dispatchRequest (
        ClientObject source, int methodId, Object[] args)
        throws InvocationException
    {
        switch (methodId) {
        case ChatMarshaller.AWAY:
            ((ChatProvider)provider).away(
                source, (String)args[0]
            );
            return;

        case ChatMarshaller.BROADCAST:
            ((ChatProvider)provider).broadcast(
                source, (String)args[0], (InvocationService.InvocationListener)args[1]
            );
            return;

        case ChatMarshaller.TELL:
            ((ChatProvider)provider).tell(
                source, (Name)args[0], (String)args[1], (ChatService.TellListener)args[2]
            );
            return;

        default:
            super.dispatchRequest(source, methodId, args);
            return;
        }
    }
}
