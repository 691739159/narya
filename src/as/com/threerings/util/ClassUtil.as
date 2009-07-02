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

package com.threerings.util {

import flash.utils.describeType;
import flash.utils.getQualifiedClassName;
import flash.utils.getDefinitionByName;

import com.threerings.util.env.Environment;

public class ClassUtil
{
    /**
     * Get the full class name, e.g. "com.threerings.util.ClassUtil".
     * Calling getClassName with a Class object will return the same value as calling it with an
     * instance of that class. That is, getClassName(Foo) == getClassName(new Foo()).
     */
    public static function getClassName (obj :Object) :String
    {
        return getQualifiedClassName(obj).replace("::", ".");
    }

    /**
     * Get the class name with the last part of the package, e.g. "util.ClassUtil".
     */
    public static function shortClassName (obj :Object) :String
    {
        var s :String = getQualifiedClassName(obj);
        var dex :int = s.lastIndexOf(".");
        s = s.substring(dex + 1); // works even if dex is -1
        return s.replace("::", ".");
    }

    /**
     * Get just the class name, e.g. "ClassUtil".
     */
    public static function tinyClassName (obj :Object) :String
    {
        var s :String = getClassName(obj);
        var dex :int = s.lastIndexOf(".");
        return s.substring(dex + 1); // works even if dex is -1
    }

    /**
     * Return a new instance that is the same class as the specified
     * object. The class must have a zero-arg constructor.
     */
    public static function newInstance (obj :Object) :Object
    {
        var clazz :Class = getClass(obj);
        return new clazz();
    }

    public static function isSameClass (obj1 :Object, obj2 :Object) :Boolean
    {
        return (getQualifiedClassName(obj1) == getQualifiedClassName(obj2));
    }

    public static function getClass (obj :Object) :Class
    {
        if (obj.constructor is Class) {
            return Class(obj.constructor);
        }
        return getClassByName(getQualifiedClassName(obj));
    }

    public static function getClassByName (cname :String) :Class
    {
        try {
            return (getDefinitionByName(cname.replace("::", ".")) as Class);

        } catch (error :ReferenceError) {
            var log :Log = Log.getLog(ClassUtil);
            log.warning("Unknown class: " + cname);
            log.logStackTrace(error);
        }
        return null; // error case
    }

    public static function isFinal (type :Class) :Boolean
    {
        if (type === String) {
            return true;
        }

        // all enums are final, even if you forget to make your enum class final, you punk
        if (isAssignableAs(Enum, type)) {
            return true;
        }

        // TODO: there's currently no way to determine final from the class
        // I thought examining the prototype might do it, but no dice.
        // Fuckers!
        return false;
    }

    /**
     * Returns true if an object of type srcClass is a subclass of or
     * implements the interface represented by the asClass paramter.
     *
     * <code>
     * if (ClassUtil.isAssignableAs(Streamable, someClass)) {
     *     var s :Streamable = (new someClass() as Streamable);
     * </code>
     */
    public static function isAssignableAs (asClass :Class, srcClass :Class) :Boolean
    {
        return Environment.isAssignableAs(asClass, srcClass);
    }
}
}
