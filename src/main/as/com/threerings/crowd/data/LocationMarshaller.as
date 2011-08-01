//
// $Id$
//
// Narya library - tools for developing networked games
// Copyright (C) 2002-2011 Three Rings Design, Inc., All Rights Reserved
// http://code.google.com/p/narya/
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

package com.threerings.crowd.data {

import com.threerings.util.Integer;

import com.threerings.presents.data.InvocationMarshaller;

import com.threerings.crowd.client.LocationService;
import com.threerings.crowd.client.LocationService_MoveListener;

/**
 * Provides the implementation of the <code>LocationService</code> interface
 * that marshalls the arguments and delivers the request to the provider
 * on the server. Also provides an implementation of the response listener
 * interfaces that marshall the response arguments and deliver them back
 * to the requesting client.
 */
public class LocationMarshaller extends InvocationMarshaller
    implements LocationService
{
    /** The method id used to dispatch <code>leavePlace</code> requests. */
    public static const LEAVE_PLACE :int = 1;

    // from interface LocationService
    public function leavePlace () :void
    {
        sendRequest(LEAVE_PLACE, [
        ]);
    }

    /** The method id used to dispatch <code>moveTo</code> requests. */
    public static const MOVE_TO :int = 2;

    // from interface LocationService
    public function moveTo (arg1 :int, arg2 :LocationService_MoveListener) :void
    {
        var listener2 :LocationMarshaller_MoveMarshaller = new LocationMarshaller_MoveMarshaller();
        listener2.listener = arg2;
        sendRequest(MOVE_TO, [
            Integer.valueOf(arg1), listener2
        ]);
    }
}
}
