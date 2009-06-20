//
// $Id$
//
// Narya library - tools for developing networked games
// Copyright (C) 2002-2009 Three Rings Design, Inc., All Rights Reserved
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

package com.threerings.io.streamers {

import com.threerings.util.Float;

import com.threerings.io.ObjectInputStream;
import com.threerings.io.ObjectOutputStream;
import com.threerings.io.Streamer;

/**
 * A Streamer for Float objects.
 */
public class FloatStreamer extends Streamer
{
    public function FloatStreamer ()
    {
        super(Float, "java.lang.Float");
    }

    override public function createObject (ins :ObjectInputStream) :Object
    {
        return new Float(ins.readFloat());
    }

    override public function writeObject (obj :Object, out :ObjectOutputStream) :void
    {
        var float :Float = (obj as Float);
        out.writeFloat(float.value);
    }

    override public function readObject (obj :Object, ins :ObjectInputStream) :void
    {
        // unneeded, done in createObject
    }
}
}
