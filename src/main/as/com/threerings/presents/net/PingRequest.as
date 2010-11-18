//
// $Id$
//
// Narya library - tools for developing networked games
// Copyright (C) 2002-2010 Three Rings Design, Inc., All Rights Reserved
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

package com.threerings.presents.net {

import flash.utils.getTimer;

import com.threerings.io.ObjectOutputStream;

public class PingRequest extends UpstreamMessage
{
    /** The number of milliseconds of idle upstream that are allowed to elapse
     * before the client sends a ping message to the server to let it
     * know that we're still alive. */
    public static const PING_INTERVAL :uint = 60 * 1000;

    public function PingRequest ()
    {
        super();
    }

    public function getPackStamp () :uint
    {
        return _packStamp;
    }

    // documentation inherited
    override public function writeObject (out :ObjectOutputStream) :void
    {
        _packStamp = getTimer();
        super.writeObject(out);
    }

    /** A time stamp obtained when we serialize this object. */
    protected var _packStamp :uint;
}
}
