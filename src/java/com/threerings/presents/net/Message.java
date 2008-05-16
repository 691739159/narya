//
// $Id: DownstreamMessage.java 5090 2008-05-13 21:12:39Z andrzej $
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

package com.threerings.presents.net;

import com.threerings.io.SimpleStreamableObject;

/**
 * The superclass of {@link UpstreamMessage} and {@link DownstreamMessage}.
 */
public abstract class Message extends SimpleStreamableObject
{
    /**
     * Sets the message transport parameters.  For messages received over the network, these
     * describe the mode of transport over which the message was received.  When sending messages,
     * they act as a hint as to the type of transport desired.  Calling this method may have no
     * effect, depending on whether non-default modes of transport are supported for this message
     * type.
     */
    public void setTransport (Transport transport)
    {
        // no-op by default
    }

    /**
     * Returns the message transport parameters.
     */
    public Transport getTransport ()
    {
        return Transport.DEFAULT;
    }
}
