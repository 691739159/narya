//
// $Id: DObject.java,v 1.7 2001/06/01 19:56:13 mdb Exp $

package com.threerings.cocktail.cher.dobj;

import java.util.ArrayList;

/**
 * The distributed object forms the foundation of the cocktail system. All
 * information shared among users of the system is done via distributed
 * objects. A distributed object has a set of subscribers. These
 * subscribers have access to the object or a proxy of the object and
 * therefore have access to the data stored in the object's members at all
 * times.
 *
 * <p> When there is any change to that data, initiated by one of the
 * subscribers, an event is generated which is dispatched to all
 * subscribers of the object, notifying them of that change and affecting
 * that change to the copy of the object maintained at each client. In
 * this way, both a respository of shared information and a mechanism for
 * asynchronous notification are made available as a fundamental
 * application building blocks.
 *
 * <p> To define what information is shared, an application creates a
 * distributed object declaration which is much like a class declaration
 * except that it is transformed into a proper derived class of
 * <code>DObject</code> by a script. A declaration looks something like
 * this:
 *
 * <pre>
 * public dclass RoomObject
 * {
 *     public String description;
 *     public int[] occupants;
 * }
 * </pre>
 *
 * which is converted into an actual Java class that looks like this:
 *
 * <pre>
 * public class RoomObject extends DObject
 * {
 *     public String getDescription ()
 *     {
 *         // ...
 *     }
 *
 *     public void setDescription (String description)
 *     {
 *         // ...
 *     }
 *
 *     public int[] getOccupants ()
 *     {
 *         // ...
 *     }
 *
 *     public void setOccupants (int[] occupants)
 *     {
 *         // ...
 *     }
 *
 *     public void setOccupantsAt (int index, int value)
 *     {
 *         // ...
 *     }
 * }
 * </pre>
 *
 * These method calls on the actual distributed object will result in the
 * proper attribute change events being generated and dispatched.
 */
public class DObject
{
    /**
     * Initializes this distributed object with the supplied object id and
     * distributed object manager. This is called by the distributed
     * object manager when an object is created and registered with the
     * system. Don't call this function yourself.
     *
     * @see DObjectManager.createObject
     */
    public void init (int oid, DObjectManager mgr)
    {
        _oid = oid;
        _mgr = mgr;
    }

    /**
     * Returns the object id of this object. All objects in the system
     * have a unique object id.
     */
    public int getOid ()
    {
        return _oid;
    }

    /**
     * Adds the supplied subscriber to the subscriber list for this
     * object. This is done automatically when an object is requested for
     * subscription by the distributed object manager, thus this function
     * should not be called directly except in circumstances where one
     * subscriber has already obtained a subscription to an object and
     * wishes to include a subordinate subscriber in on the fun.
     *
     * <p> If the specified subscriber is already subscribed to this
     * object, they will not be added to the list a second time.
     */
    public void addSubscriber (Subscriber sub)
    {
        if (!_subscribers.contains(sub)) {
            _subscribers.add(sub);
        }
    }

    /**
     * Removes the specified subscriber from the subscriber list for this
     * object. This is done automatically when a subscriber returns false
     * from <code>handleEvent</code>, but can also be done directly
     * through a call to <code>removeSubscriber</code>. If the specified
     * subscriber is not currently on the list of subscribers for this
     * object, nothing happens.
     */
    public void removeSubscriber (Subscriber sub)
    {
        _subscribers.remove(sub);
    }

    /**
     * Checks to ensure that the specified subscriber has access to this
     * object. This will be called before satisfying any fetch or
     * subscription request. By default objects are accessible to all
     * subscriber, but certain objects may wish to implement more fine
     * grained access control.
     *
     * @param sub the subscriber that will fetch or subscribe to this
     * object.
     *
     * @return true if the subscriber has access to the object, false if
     * they do not.
     */
    public boolean checkPermissions (Subscriber sub)
    {
        return true;
    }

    /**
     * Checks to ensure that this event which is about to be processed,
     * has the appropriate permissions. By default objects accept all
     * manner of events, but certain objects may wish to implement more
     * fine grained access control.
     *
     * @param event the event that will be dispatched, object permitting.
     *
     * @return true if the event is valid and should be dispatched, false
     * if the event fails the permissions check and should be aborted.
     */
    public boolean checkPermissions (DEvent event)
    {
        return true;
    }

    /**
     * Called by the distributed object manager after it has applied an
     * event to this object. This dispatches an event notification to all
     * of the subscribers of this object.
     *
     * @param event the event that was just applied.
     */
    public void notifySubscribers (DEvent event)
    {
        for (int i = 0; i < _subscribers.size(); i++) {
            Subscriber sub = (Subscriber)_subscribers.get(i);
            // notify the subscriber
            if (!sub.handleEvent(event, this)) {
                // if they return false, we need to remove them from the
                // subscriber list
                _subscribers.remove(i--);
            }
        }
    }

    protected int _oid;
    protected DObjectManager _mgr;
    protected ArrayList _subscribers = new ArrayList();
}
