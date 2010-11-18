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

package com.threerings.crowd.client {

import com.threerings.crowd.data.OccupantInfo;

public class OccupantAdapter
    implements OccupantObserver
{
    public function OccupantAdapter (
        entered :Function = null, left :Function = null, updated :Function = null)
    {
        _entered = entered;
        _left = left;
        _updated = updated;
    }

    // from interface OccupantObserver
    public function occupantEntered (info :OccupantInfo) :void
    {
        if (_entered != null) {
            _entered(info);
        }
    }

    // from interface OccupantObserver
    public function occupantLeft (info :OccupantInfo) :void
    {
        if (_left != null) {
            _left(info);
        }
    }

    // from interface OccupantObserver
    public function occupantUpdated (oldinfo :OccupantInfo, newinfo :OccupantInfo) :void
    {
        if (_updated != null) {
            _updated(oldinfo, newinfo);
        }
    }

    protected var _entered :Function;
    protected var _left :Function;
    protected var _updated :Function;
}
}
