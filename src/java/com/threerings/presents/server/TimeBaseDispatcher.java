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

package com.threerings.presents.server;

import javax.annotation.Generated;

import com.threerings.presents.client.TimeBaseService;
import com.threerings.presents.data.ClientObject;
import com.threerings.presents.data.TimeBaseMarshaller;

/**
 * Dispatches requests to the {@link TimeBaseProvider}.
 */
@Generated(value={"com.threerings.presents.tools.GenServiceTask"},
           comments="Derived from TimeBaseService.java.")
public class TimeBaseDispatcher extends InvocationDispatcher<TimeBaseMarshaller>
{
    /**
     * Creates a dispatcher that may be registered to dispatch invocation
     * service requests for the specified provider.
     */
    public TimeBaseDispatcher (TimeBaseProvider provider)
    {
        this.provider = provider;
    }

    @Override
    public TimeBaseMarshaller createMarshaller ()
    {
        return new TimeBaseMarshaller();
    }

    @Override
    public void dispatchRequest (
        ClientObject source, int methodId, Object[] args)
        throws InvocationException
    {
        switch (methodId) {
        case TimeBaseMarshaller.GET_TIME_OID:
            ((TimeBaseProvider)provider).getTimeOid(
                source, (String)args[0], (TimeBaseService.GotTimeBaseListener)args[1]
            );
            return;

        default:
            super.dispatchRequest(source, methodId, args);
            return;
        }
    }
}
