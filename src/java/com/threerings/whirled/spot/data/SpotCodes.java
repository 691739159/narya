//
// $Id: SpotCodes.java,v 1.4 2003/03/26 22:34:24 mdb Exp $

package com.threerings.whirled.spot.data;

import com.threerings.crowd.chat.ChatCodes;

import com.threerings.whirled.data.SceneCodes;
import com.threerings.whirled.spot.client.SpotSceneDirector;

/**
 * Contains codes used by the Spot invocation services.
 */
public interface SpotCodes extends ChatCodes, SceneCodes
{
    /** An error code indicating that the portal specified in a
     * traversePortal request does not exist. */
    public static final String NO_SUCH_PORTAL = "m.no_such_portal";

    /** An error code indicating that a location is occupied. Usually
     * generated by a failed changeLoc request. */
    public static final String LOCATION_OCCUPIED = "m.location_occupied";

    /** An error code indicating that a location is not valid. Usually
     * generated by a failed changeLoc request. */
    public static final String INVALID_LOCATION = "m.invalid_location";

    /** The chat type code with which we register our cluster auxiliary
     * chat objects. Chat display implementations should interpret chat
     * messages with this type accordingly. */
    public static final String CLUSTER_CHAT_TYPE = "clusterChat";
}
