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

package com.threerings.crowd.server;

import com.threerings.presents.dobj.AccessController;
import com.threerings.presents.server.PresentsClient;

import com.threerings.crowd.chat.server.SpeakUtil;
import com.threerings.crowd.data.BodyObject;
import com.threerings.crowd.data.OccupantInfo;
import com.threerings.crowd.server.CrowdServer;

/**
 * The crowd client extends the presents client with crowd-specific client handling.
 */
public class CrowdClient extends PresentsClient
{
    // documentation inherited
    protected void sessionConnectionClosed ()
    {
        super.sessionConnectionClosed();

        if (_clobj != null) {
            // note that the user is disconnected
            BodyObject bobj = (BodyObject)_clobj;
            CrowdServer.bodyman.updateOccupantStatus(bobj, bobj.location, OccupantInfo.DISCONNECTED);
        }
    }

    // documentation inherited
    protected void sessionWillResume ()
    {
        super.sessionWillResume();

        // note that the user's active once more
        BodyObject bobj = (BodyObject)_clobj;
        CrowdServer.bodyman.updateOccupantStatus(bobj, bobj.location, OccupantInfo.ACTIVE);
    }

    // documentation inherited
    protected void sessionDidEnd ()
    {
        super.sessionDidEnd();

        BodyObject body = (BodyObject)_clobj;

        // clear out our location so that anyone listening will know that we've left
        clearLocation(body);

        // reset our status in case this object remains around until they start their next session
        // (which could happen very soon)
        CrowdServer.bodyman.updateOccupantStatus(body, null, OccupantInfo.ACTIVE);

        // clear our chat history
        if (body != null) {
            SpeakUtil.clearHistory(body.getVisibleName());
        }
    }

    /**
     * When the user ends their session, this method is called to clear out any location they might
     * occupy. The default implementation takes care of standard crowd location occupancy, but
     * users of other services may which to override this method and clear the user out of a scene,
     * zone or other location-derived occupancy.
     */
    protected void clearLocation (BodyObject bobj)
    {
        CrowdServer.locman.leaveOccupiedPlace(bobj);
    }
}
