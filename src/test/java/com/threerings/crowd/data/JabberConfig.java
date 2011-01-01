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

package com.threerings.crowd.data;

import com.threerings.crowd.client.JabberController;
import com.threerings.crowd.client.PlaceController;

/**
 * Defines the necessary bits for our chat room.
 */
public class JabberConfig extends PlaceConfig
{
    // documentation inherited
    @Override
    public PlaceController createController ()
    {
        return new JabberController();
    }

    // documentation inherited
    @Override
    public String getManagerClassName ()
    {
        // nothing special needed on the server side
        return "com.threerings.crowd.server.PlaceManager";
    }
}
