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

package com.threerings.presents.data {

import com.threerings.util.Name;

import com.threerings.presents.dobj.DObject;
import com.threerings.presents.dobj.DSet;
import com.threerings.presents.dobj.DSet_Entry;

import com.threerings.io.ObjectInputStream;
import com.threerings.io.ObjectOutputStream;

/**
 * Every client in the system has an associated client object to which
 * only they subscribe. The client object can be used to deliver messages
 * solely to a particular client as well as to publish client-specific
 * data.
 */
public class ClientObject extends DObject
{
    // AUTO-GENERATED: FIELDS START
    /** The field name of the <code>receivers</code> field. */
    public static const RECEIVERS :String = "receivers";
    // AUTO-GENERATED: FIELDS END

    /** The name of a message event delivered to the client when they
     * switch usernames (and therefore user objects). */
    public static const CLOBJ_CHANGED :String = "!clobj_changed!";

    /** The authenticated user name of this client. */
    public var username :Name;

    /** Used to publish all invocation service receivers registered on
     * this client. */
    public var receivers :DSet;

    /**
     * Returns a short string identifying this client.
     */
    public function who () :String
    {
        return "(" + username + ":" + getOid() + ")";
    }

    /**
     * Convenience wrapper around {@link #checkAccess(Permission)} that simply returns a boolean
     * indicating whether or not this client has the permission rather than an explanation.
     */
    public function hasAccess (perm :Permission) :Boolean
    {
        return checkAccess(perm) == null;
    }

    /**
     * Checks whether or not this client has the specified permission.
     *
     * @return null if the user has access, a fully-qualified translatable message string
     * indicating the reason for denial of access.
     *
     * @see PermissionPolicy
     */
    public function checkAccess (perm :Permission, context :Object = null) :String
    {
        return _permPolicy.checkAccess(this, perm, context);
    }

    // AUTO-GENERATED: METHODS START
    public function addToReceivers (elem :DSet_Entry) :void
    {
        requestEntryAdd(RECEIVERS, elem);
    }

    public function removeFromReceivers (key :Object) :void
    {
        requestEntryRemove(RECEIVERS, key);
    }

    public function updateReceivers (elem :DSet_Entry) :void
    {
        requestEntryUpdate(RECEIVERS, elem);
    }

    public function setReceivers (value :DSet) :void
    {
        requestAttributeChange(RECEIVERS, value, this.receivers);
        this.receivers = (value == null) ? null
                                         : (value.clone() as DSet);
    }
    // AUTO-GENERATED: METHODS END

//    override public function writeObject (out :ObjectOutputStream) :void
//    {
//        super.writeObject(out);
//        out.writeObject(receivers);
//    }
                            
    override public function readObject (ins :ObjectInputStream) :void
    {
        super.readObject(ins);
        username = Name(ins.readObject());
        receivers = DSet(ins.readObject());
        _permPolicy = PermissionPolicy(ins.readObject());
    }

    protected var _permPolicy :PermissionPolicy;
}
}
