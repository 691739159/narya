package com.threerings.io.streamers {

import com.threerings.util.ClassUtil;

import com.threerings.io.ArrayMask;
import com.threerings.io.ObjectInputStream;
import com.threerings.io.ObjectOutputStream;
import com.threerings.io.Streamer;
import com.threerings.io.Translations;
import com.threerings.io.TypedArray;

/**
 * A Streamer for Array objects.
 */
public class ArrayStreamer extends Streamer
{
    public function ArrayStreamer (jname :String = "[Ljava.lang.Object;")
    {
        super(TypedArray, jname);

        var secondChar :String = jname.charAt(1);
        if (secondChar === "[") {
            // if we're a multi-dimensional array then we need a delegate
            _delegate = Streamer.getStreamerByJavaName(jname.substring(1));
            _isFinal = true; // it just is

        } else if (secondChar === "L") {
            // form is "[L<class>;"
            var baseClass :String = jname.substring(2, jname.length - 1);
            baseClass = Translations.getFromServer(baseClass);
            _elementType = ClassUtil.getClassByName(baseClass);
            _isFinal = ClassUtil.isFinal(_elementType);

        } else if (secondChar === "I") {
            _elementType = int;

        } else if (secondChar === "Z") {
            _elementType = Boolean;

        } else {
            Log.getLog(this).warning("Other array types are " +
                "currently not handled yet [jname=" + jname + "].");
            throw new Error("Unimplemented bit");
        }
    }

    override public function isStreamerFor (obj :Object) :Boolean
    {
        if (obj is TypedArray) {
            // TypedArrays need the same element type
            return ((obj as TypedArray).getJavaType() === _jname);

        } else {
            // any other array is streamed as Object[]
            return (obj is Array) && (_jname === "[Ljava.lang.Object;");
        }
    }

    override public function isStreamerForClass (clazz :Class) :Boolean
    {
        if (clazz == TypedArray) {
            return false; // TODO: we're kinda fucked for finding a streamer
            // by class for TypedArrays here. The caller should be passing
            // the java name.

        } else {
            return ClassUtil.isAssignableAs(Array, clazz) &&
                (_jname === "[Ljava.lang.Object;");
        }
    }

    override public function createObject (ins :ObjectInputStream) :Object
    {
        var ta :TypedArray = new TypedArray(_jname);
        ta.length = ins.readInt();
        return ta;
    }

    override public function writeObject (obj :Object, out :ObjectOutputStream)
            :void
    {
        var arr :Array = (obj as Array);
        var ii :int;
        out.writeInt(arr.length);
        if (_elementType == int) {
            for (ii = 0; ii < arr.length; ii++) {
                out.writeInt(arr[ii] as int);
            }

        } else if (_elementType == Boolean) {
            for (ii = 0; ii < arr.length; ii++) {
                out.writeBoolean(arr[ii] as Boolean);
            }

        } else if (_isFinal) {
            var mask :ArrayMask = new ArrayMask(arr.length);
            for (ii = 0; ii < arr.length; ii++) {
                if (arr[ii] != null) {
                    mask.setBit(ii);
                }
            }
            mask.writeTo(out);
            // now write the populated elements
            for (ii = 0; ii < arr.length; ii++) {
                var element :Object = arr[ii];
                if (element != null) {
                    out.writeBareObjectImpl(element, _delegate);
                }
            }

        } else {
            for (ii = 0; ii < arr.length; ii++) {
                out.writeObject(arr[ii]);
            }
        }
    }

    override public function readObject (obj :Object, ins :ObjectInputStream)
            :void
    {
        var arr :Array = (obj as Array);
        var ii :int;
        if (_elementType == int) {
            for (ii = 0; ii < arr.length; ii++) {
                arr[ii] = ins.readInt();
            }

        } else if (_elementType == Boolean) {
            for (ii = 0; ii < arr.length; ii++) {
                arr[ii] = ins.readBoolean();
            }

        } else if (_isFinal) {
            var mask :ArrayMask = new ArrayMask();
            mask.readFrom(ins);
            for (ii = 0; ii < arr.length; ii++) {
                if (mask.isSet(ii)) {
                    var target :Object;
                    if (_delegate == null) {
                        target = new _elementType();
                    } else {
                        target = _delegate.createObject(ins);
                    }
                    ins.readBareObjectImpl(target, _delegate);
                    arr[ii] = target;
                }
            } 

        } else {
            for (ii = 0; ii < arr.length; ii++) {
                arr[ii] = ins.readObject();
            } 
        }
    }

    /** A streamer for our elements. */
    protected var _delegate :Streamer;

    /** If this is the final dimension of the array, the element type. */
    protected var _elementType :Class;

    /** Whether we're final or not: true if we're not the final dimension
     * or if the element type is final. */
    protected var _isFinal :Boolean;
}
}
