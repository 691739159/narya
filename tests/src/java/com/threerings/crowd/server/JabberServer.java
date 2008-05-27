//
// $Id$

package com.threerings.crowd.server;

import com.threerings.crowd.data.JabberConfig;
import com.threerings.crowd.data.PlaceObject;

import static com.threerings.crowd.Log.log;

/**
 * A basic server that creates a single room and sticks everyone in it
 * where they can chat with one another.
 */
public class JabberServer extends CrowdServer
{
    // documentation inherited
    public void init ()
        throws Exception
    {
        super.init();

        // create a single location
        _pmgr = plreg.createPlace(new JabberConfig());
    }

    public static void main (String[] args)
    {
        JabberServer server = new JabberServer();
        try {
            server.init();
            server.run();
        } catch (Exception e) {
            log.warning("Unable to initialize server.", e);
        }
    }

    protected PlaceManager _pmgr;
}
