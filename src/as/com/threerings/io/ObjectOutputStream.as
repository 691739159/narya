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

package com.threerings.io {

import flash.utils.ByteArray;
import flash.utils.IDataOutput;

import com.threerings.util.ClassUtil;
import com.threerings.util.Map;
import com.threerings.util.Maps;
import com.threerings.util.Log;
import com.threerings.util.Long;
import com.threerings.util.Short;

public class ObjectOutputStream
{
    private static const log :Log = Log.getLog(ObjectOutputStream);

    public function ObjectOutputStream (targ :IDataOutput)
    {
        _targ = targ;
    }

    public function writeObject (obj :Object) :void
        //throws IOError
    {
        // if the object to be written is null (or undefined) write a zero
        if (obj == null) {
            writeShort(0);
            return;
        }

        var cname :String;
        if (obj is TypedArray) {
            cname = (obj as TypedArray).getJavaType();
        } else {
            cname = ClassUtil.getClassName(obj);
        }
        // look up the class mapping record
        var cmap :ClassMapping = (_classMap.get(cname) as ClassMapping);

        // create a class mapping if we've not got one
        if (cmap == null) {
            var streamer :Streamer = Streamer.getStreamer(obj);
            if (streamer == null) {
                throw new Error("Unable to stream " + cname);
            }

            cmap = new ClassMapping(_nextCode++, streamer);
            _classMap.put(cname, cmap);

            if (_nextCode > Short.MAX_VALUE) {
                throw new Error("Too many unique classes written to ObjectOutputStream");
            }

            if (ObjectInputStream.DEBUG) {
                log.debug("Assigning class code", "code", cmap.code, "class", cname);
            }

            writeShort(-cmap.code);
            writeUTF(streamer.getJavaClassName());

        } else {
            writeShort(cmap.code);
        }

        writeBareObjectImpl(obj, cmap.streamer);
    }

    public function writeBareObject (obj :Object) :void
        //throws IOError
    {
        writeBareObjectImpl(obj, Streamer.getStreamer(obj));
    }

    public function writeBareObjectImpl (obj :Object, streamer :Streamer) :void
    {
        // otherwise, stream it!
        _current = obj;
        _streamer = streamer;
        try {
            _streamer.writeObject(obj, this);
        } finally {
            _current = null;
            _streamer = null;
        }
    }

    /**
     * This is equivalent to marshalling a field for which there is a basic streamer.
     */
    public function writeField (val :Object) :void
        //throws IOError
    {
        var b :Boolean = (val != null);
        writeBoolean(b);
        if (b) {
            writeBareObject(val);
        }
    }

    /**
     * Uses the default streamable mechanism to write the contents of the object currently being
     * streamed. This can only be called from within a <code>writeObject</code> implementation in a
     * {@link Streamable} object.
     */
    public function defaultWriteObject () :void
        //throws IOError
    {
        // sanity check
        if (_current == null) {
            throw new Error("defaultWriteObject() called illegally.");
        }

        // write the instance data
        _streamer.writeObject(_current, this);
    }

    public function writeBoolean (value :Boolean) :void
        //throws IOError
    {
        _targ.writeBoolean(value);
    }

    public function writeByte (value :int) :void
        //throws IOError
    {
        _targ.writeByte(value);
    }

    public function writeBytes (bytes :ByteArray, offset :uint=0,
            length :uint = 0) :void
        //throws IOError
    {
        _targ.writeBytes(bytes, offset, length);
    }

    public function writeDouble (value :Number) :void
        //throws IOError
    {
        _targ.writeDouble(value);
    }

    public function writeFloat (value :Number) :void
        //throws IOError
    {
        _targ.writeFloat(value);
    }

    public function writeLong (value :Long) :void
    {
        writeBareObject(value);
    }

    public function writeInt (value :int) :void
        //throws IOError
    {
        _targ.writeInt(value);
    }

    public function writeShort (value :int) :void
        //throws IOError
    {
        _targ.writeShort(value);
    }

    public function writeUTF (value :String) :void
        //throws IOError
    {
        _targ.writeUTF(value);
    }

    // these two are defined in IDataOutput, but have no java equivalent so we skip them.
    //public function writeUnsignedInt (value :int) :void
    //public function writeUTFBytes (value :int) :void

    /** The target DataOutput that we route things to. */
    protected var _targ :IDataOutput;
    
    /** A counter used to assign codes  to streamed classes. */
    protected var _nextCode :int = 1;

    /** The object currently being written out. */
    protected var _current :Object;

    /** The streamer being used currently. */
    protected var _streamer :Streamer;

    /** A map of classname to ClassMapping info. */
    protected var _classMap :Map = Maps.newMapOf(String);
}
}
