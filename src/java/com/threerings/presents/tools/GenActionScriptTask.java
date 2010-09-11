//
// $Id$
//
// Narya library - tools for developing networked games
// Copyright (C) 2002-2010 Three Rings Design, Inc., All Rights Reserved
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

package com.threerings.presents.tools;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import java.util.ArrayList;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;

import com.google.common.collect.Lists;

import com.samskivert.io.StreamUtil;

import com.threerings.io.ObjectInputStream;
import com.threerings.io.ObjectOutputStream;
import com.threerings.io.Streamable;

import com.threerings.util.ActionScript;

import com.threerings.presents.data.InvocationMarshaller;
import com.threerings.presents.dobj.DObject;

/**
 * Generates ActionScript versions of {@link Streamable} classes and provides routines used by the
 * {@link GenDObjectTask} to create ActionScript versions of distributed objects.
 */
public class GenActionScriptTask extends Task
{
    /**
     * Adds a nested &lt;fileset&gt; element which enumerates streamable source files.
     */
    public void addFileset (FileSet set)
    {
        _filesets.add(set);
    }

    /**
     * Configures the path to our ActionScript source files.
     */
    public void setAsroot (File asroot)
    {
        _asroot = asroot;
    }

    /**
     * Configures us with a header file that we'll prepend to all generated source files.
     */
    public void setHeader (File header)
    {
        try {
            _header = StreamUtil.toString(new FileReader(header));
        } catch (IOException ioe) {
            System.err.println("Unabled to load header '" + header + ": " + ioe.getMessage());
        }
    }

    /**
     * Performs the actual work of the task.
     */
    @Override
    public void execute ()
    {
        for (FileSet fs : _filesets) {
            DirectoryScanner ds = fs.getDirectoryScanner(getProject());
            File fromDir = fs.getDir(getProject());
            String[] srcFiles = ds.getIncludedFiles();
            for (String srcFile : srcFiles) {
                processClass(new File(fromDir, srcFile));
            }
        }
    }

    /**
     * Processes a Streamable source file.
     */
    protected void processClass (File source)
    {
        // System.err.println("Processing " + source + "...");

        // load up the file and determine it's package and classname
        String name = null;
        try {
            name = GenUtil.readClassName(source);
        } catch (Exception e) {
            System.err.println("Failed to parse " + source + ": " + e.getMessage());
            return;
        }

        try {
            // in order for annotations to work, this task and all the classes it uses must be
            // loaded from the same class loader as the classes on which we are going to
            // introspect; this is non-ideal but unavoidable
            processClass(source, getClass().getClassLoader().loadClass(name));
        } catch (ClassNotFoundException cnfe) {
            System.err.println("Failed to load " + name + ".\nMissing class: " + cnfe.getMessage());
            System.err.println("Be sure to set the 'classpathref' attribute to a classpath\n" +
                               "that contains your projects invocation service classes.");
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    /**
     * Processes a resolved Streamable class instance.
     */
    protected void processClass (File source, Class<?> sclass)
        throws IOException
    {
        // make sure we implement Streamable but don't extend DObject or InvocationMarshaller and
        // that we're a class not an interface
        if (!Streamable.class.isAssignableFrom(sclass) ||
            DObject.class.isAssignableFrom(sclass) ||
            InvocationMarshaller.class.isAssignableFrom(sclass) ||
            ((sclass.getModifiers() & Modifier.INTERFACE) != 0)) {
            // System.err.println("Skipping " + sclass.getName() + "...");
            return;
        }

        // if we have an ActionScript(omit=true) annotation, skip this class
        Class<?> cclass = sclass;
        do {
            ActionScript asa = cclass.getAnnotation(ActionScript.class);
            if (asa != null && asa.omit()) {
                // System.err.println("Skipping " + sclass.getName() + "...");
                return;
            }
            cclass = cclass.getSuperclass();
        } while (cclass != null);

        // determine the path to the corresponding action script source file
        String path = sclass.getPackage().getName();
        path = path.replace(".", File.separator);
        String name = sclass.getName();
        name = name.substring(name.lastIndexOf(".")+1);
        path = path + File.separator + name;
        File asfile = new File(_asroot, path + ".as");

        System.err.println("Converting " + sclass.getName() + "...");

        // parse the existing ActionScript source and generate what we don't
        // have from the Java class
        ActionScriptSource assrc = new ActionScriptSource(sclass);
        assrc.absorbJava(source);
        assrc.imports.add(ObjectInputStream.class.getName());
        assrc.imports.add(ObjectOutputStream.class.getName());

        // see if our parent also implements Streamable
        boolean needSuper = Streamable.class.isAssignableFrom(sclass.getSuperclass());

        // add readObject() and writeObject() definitions
        ActionScriptSource.Member member;
        member = new ActionScriptSource.Member(
            "readObject", (needSuper ? "override " : "") + READ_SIG);
        member.noreplace = true;
        member.comment = "    // from interface Streamable\n";
        StringBuilder body = new StringBuilder("    {\n");
        if (needSuper) {
            body.append("        super.readObject(ins);\n");
        }
        int added = 0;
        for (Field field : sclass.getDeclaredFields()) {
            if (!isStreamable(field)) {
                continue;
            }
            body.append("        ");
            body.append(field.getName()).append(" = ");
            body.append(toReadObject(field.getType()));
            body.append(";\n");
            added++;
        }
        member.body = body.append("    }\n").toString();
        if (added > 0) {
            assrc.publicMethods.add(member);
        }

        member = new ActionScriptSource.Member(
            "writeObject", (needSuper ? "override " : "") + WRITE_SIG);
        member.noreplace = true;
        member.comment = "    // from interface Streamable\n";
        body = new StringBuilder("    {\n");
        if (needSuper) {
            body.append("        super.writeObject(out);\n");
        }
        added = 0;
        for (Field field : sclass.getDeclaredFields()) {
            if (!isStreamable(field)) {
                continue;
            }
            body.append("        out.");
            body.append(toWriteObject(field.getType(), field.getName()));
            body.append(";\n");
            added++;
        }
        member.body = body.append("    }\n").toString();
        if (added > 0) {
            assrc.publicMethods.add(member);
        }

        // now we can parse existing definitions from any extant ActionScript source file
        assrc.absorbActionScript(asfile);

        // make sure our parent directory exists
        asfile.getParentFile().mkdirs();

        // now write all that out to the target source file
        BufferedWriter out = new BufferedWriter(new FileWriter(asfile));
        assrc.write(new PrintWriter(out));
    }

    protected boolean isStreamable (Field field)
    {
        int mods = field.getModifiers();
        return !Modifier.isStatic(mods) && !Modifier.isTransient(mods);
    }

    protected String toReadObject (Class<?> type)
    {
        if (type.equals(String.class)) {
            return "ins.readField(String)";

        } else if (type.equals(Integer.class) ||
                   type.equals(Short.class) ||
                   type.equals(Byte.class)) {
            String name = ActionScriptSource.toSimpleName(type.getName());
            return "ins.readField(" + name + ").value";

        } else if (type.equals(Long.class)) {
            String name = ActionScriptSource.toSimpleName(type.getName());
            return "ins.readField(" + name + ")";

        } else if (type.equals(Boolean.TYPE)) {
            return "ins.readBoolean()";

        } else if (type.equals(Byte.TYPE)) {
            return "ins.readByte()";

        } else if (type.equals(Short.TYPE)) {
            return "ins.readShort()";

        } else if (type.equals(Integer.TYPE)) {
            return "ins.readInt()";

        } else if (type.equals(Long.TYPE)) {
            return "new Long(ins.readInt(), ins.readInt())";

        } else if (type.equals(Float.TYPE)) {
            return "ins.readFloat()";

        } else if (type.equals(Double.TYPE)) {
            return "ins.readDouble()";

        } else if (type.isArray()) {
            if (!type.getComponentType().isPrimitive()) {
                return "ins.readObject(TypedArray)";
            } else {
                if (Double.TYPE.equals(type.getComponentType())) {
                    return "ins.readField(TypedArray.getJavaType(Number))";
                } else if (Boolean.TYPE.equals(type.getComponentType())) {
                    return "ins.readField(TypedArray.getJavaType(Boolean))";
                } else if (Integer.TYPE.equals(type.getComponentType())) {
                    return "ins.readField(TypedArray.getJavaType(int))";
                } else if (Byte.TYPE.equals(type.getComponentType())) {
                    return "ins.readField(ByteArray)";
                } else {
                    throw new IllegalArgumentException(type
                        + " isn't supported to stream to actionscript");
                }
            }
        } else {
            return "ins.readObject(" + ActionScriptSource.toSimpleName(type.getName()) + ")";
        }
    }

    protected String toWriteObject (Class<?> type, String name)
    {
        if (type.equals(Integer.class)) {
            return "writeObject(new Integer(" + name + "))";

        } else if (type.equals(Long.class)) {
            return "writeField(" + name + ")";

        } else if (type.equals(Boolean.TYPE)) {
            return "writeBoolean(" + name + ")";

        } else if (type.equals(Byte.TYPE)) {
            return "writeByte(" + name + ")";

        } else if (type.equals(Short.TYPE)) {
            return "writeShort(" + name + ")";

        } else if (type.equals(Integer.TYPE)) {
            return "writeInt(" + name + ")";

        } else if (type.equals(Long.TYPE)) {
            return "writeInt(" + name + " == null ? 0 : " + name + ".low);\n" +
                "        out.writeInt(" +
                name + " == null ? 0 : " + name + ".high)";

        } else if (type.equals(Float.TYPE)) {
            return "writeFloat(" + name + ")";

        } else if (type.equals(Double.TYPE)) {
            return "writeDouble(" + name + ")";

        } else if (type.equals(String.class) ||
                   (type.isArray() && type.getComponentType().isPrimitive())) {
            return "writeField(" + name + ")";

        } else {
            return "writeObject(" + name + ")";
        }
    }

    /** A list of filesets that contain tile images. */
    protected ArrayList<FileSet> _filesets = Lists.newArrayList();

    /** A header to put on all generated source files. */
    protected String _header;

    /** The path to our ActionScript source files. */
    protected File _asroot;

    protected static final String READ_SIG =
        "public function readObject (ins :ObjectInputStream) :void";
    protected static final String WRITE_SIG =
        "public function writeObject (out :ObjectOutputStream) :void";
}
