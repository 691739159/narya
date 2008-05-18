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

import com.threerings.presents.dobj.RootDObjectManager;

import com.threerings.presents.data.ClientObject;

import com.threerings.presents.server.InvocationException;
import com.threerings.presents.server.InvocationManager;
import com.threerings.presents.server.InvocationProvider;
import com.threerings.presents.server.PresentsClient;

import com.threerings.crowd.Log;
import com.threerings.crowd.client.LocationService;
import com.threerings.crowd.data.BodyObject;
import com.threerings.crowd.data.LocationCodes;
import com.threerings.crowd.data.Place;
import com.threerings.crowd.data.PlaceConfig;
import com.threerings.crowd.data.PlaceObject;
import com.threerings.crowd.server.CrowdServer;

/**
 * This class provides the server end of the location services.
 */
public class LocationProvider
    implements LocationCodes, InvocationProvider
{
    /**
     * Creates a location provider and prepares it for operation.
     */
    public LocationProvider (InvocationManager invmgr, RootDObjectManager omgr, PlaceRegistry plreg)
    {
        // we'll need these later
        _invmgr = invmgr;
        _omgr = omgr;
        _plreg = plreg;
    }

    /**
     * Requests that this client's body be moved to the specified location.
     *
     * @param caller the client object of the client that invoked this remotely callable method.
     * @param place the oid of the place to which the body should be moved.
     * @param listener the listener that will be informed of success or failure.
     */
    public void moveTo (ClientObject caller, int placeOid, LocationService.MoveListener listener)
        throws InvocationException
    {
        // do the move and send the response
        listener.moveSucceeded(moveTo((BodyObject)caller, placeOid));
    }

    /**
     * Requests that we leave our current place and move to nowhere land.
     */
    public void leavePlace (ClientObject caller)
        throws InvocationException
    {
        leaveOccupiedPlace((BodyObject)caller);
    }

    /**
     * Moves the specified body from whatever location they currently occupy to the location
     * identified by the supplied place oid.
     *
     * @return the config object for the new location.
     *
     * @exception ServiceFaildException thrown if the move was not successful for some reason
     * (which will be communicated as an error code in the exception's message data).
     */
    public PlaceConfig moveTo (BodyObject source, int placeOid)
        throws InvocationException
    {
        int bodoid = source.getOid();

        // make sure the place in question actually exists
        PlaceManager pmgr = _plreg.getPlaceManager(placeOid);
        if (pmgr == null) {
            Log.info("Requested to move to non-existent place [who=" + source.who() +
                     ", placeOid=" + placeOid + "].");
            throw new InvocationException(NO_SUCH_PLACE);
        }

        // if they're already in the location they're asking to move to, just give them the config
        // because we don't need to update anything in distributed object world
        Place place = pmgr.getLocation();
        if (place.equals(source.location)) {
            Log.debug("Going along with client request to move to where they already are " +
                      "[source=" + source.who() + ", place=" + place + "].");
            return pmgr.getConfig();
        }

        // make sure they have access to the specified place
        String errmsg;
        if ((errmsg = pmgr.ratifyBodyEntry(source)) != null) {
            throw new InvocationException(errmsg);
        }

        // acquire a lock on the body object to avoid breakage by rapid fire moveTo requests
        if (!source.acquireLock("moveToLock")) {
            // if we're still locked, a previous moveTo request hasn't been fully processed
            throw new InvocationException(MOVE_IN_PROGRESS);
        }

        // configure the client accordingly if the place uses a custom class loader
        PresentsClient client = CrowdServer.clmgr.getClient(source.username);
        if (client != null) {
            client.setClassLoader(_plreg.getClassLoader(pmgr.getConfig()));
        }

        try {
            PlaceObject plobj = pmgr.getPlaceObject();

            // the doubly nested try catch is to prevent failure if one or the other of the
            // transactions fails to start
            plobj.startTransaction();
            try {
                source.startTransaction();
                try {
                    // remove them from any previous location
                    leaveOccupiedPlace(source);

                    // generate a new occinfo record (which will add it to the target location)
                    pmgr.buildOccupantInfo(source);

                    // set the body's new location
                    source.willEnterPlace(place, plobj);

                    // add the body oid to the place object's occupant list
                    plobj.addToOccupants(bodoid);

                } finally {
                    source.commitTransaction();
                }
            } finally {
                plobj.commitTransaction();
            }

        } finally {
            // and finally queue up an event to release the lock once these events are processed
            source.releaseLock("moveToLock");
        }

        return pmgr.getConfig();
    }

    /**
     * Removes the specified body from the place object they currently occupy. Does nothing if the
     * body is not currently in a place.
     */
    public void leaveOccupiedPlace (BodyObject source)
    {
        Place oldloc = source.location;
        int bodoid = source.getOid();

        // nothing to do if they weren't previously in some location
        if (oldloc == null) {
            return;
        }

        // remove them from the occupant list
        PlaceObject plobj = null;
        try {
            plobj = (PlaceObject)_omgr.getObject(oldloc.placeOid);
            if (plobj != null) {
                Integer key = Integer.valueOf(bodoid);
                plobj.startTransaction();
                try {
                    // remove their occupant info (which is keyed on oid)
                    plobj.removeFromOccupantInfo(key);
                    // and remove them from the occupant list
                    plobj.removeFromOccupants(bodoid);

                } finally {
                    plobj.commitTransaction();
                }

            } else {
                Log.info("Body's prior location no longer around? [boid=" + bodoid +
                         ", place=" + oldloc + "].");
            }

        } catch (ClassCastException cce) {
            Log.warning("Body claims to occupy non-PlaceObject!? [boid=" + bodoid +
                        ", place=" + oldloc + ", error=" + cce + "].");
        }

        // clear out their location
        source.didLeavePlace(plobj);
    }

    /**
     * Forcibly moves the specified body object to the new place. This is accomplished by first
     * removing the client from their old location and then sending the client a notification,
     * instructing it to move to the new location (which it does using the normal moveTo service).
     * This has the benefit that the client is removed from their old place regardless of whether
     * or not they are cooperating.  If they choose to ignore the forced move request, they will
     * remain in limbo, unable to do much of anything.
     */
    public void moveBody (BodyObject source, Place place)
    {
        // first remove them from their old place
        leaveOccupiedPlace(source);

        // then send a forced move notification
        LocationSender.forcedMove(source, place.placeOid);
    }

    /** The invocation manager with which we interoperate. */
    protected InvocationManager _invmgr;

    /** The distributed object manager with which we interoperate. */
    protected RootDObjectManager _omgr;

    /** The place registry with which we interoperate. */
    protected PlaceRegistry _plreg;
}
