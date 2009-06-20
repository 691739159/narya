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

package com.threerings.presents.tools;

import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.Properties;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;

import com.google.common.collect.Lists;

/**
 * Generates our own ResourceBundle classes.
 *
 * NOTE: This is not used. We are just using the standard ResourceBundles
 * after all.
 */
public class GenActionScriptBundlesTask extends Task
{
    public void addFileset (FileSet set)
    {
        _filesets.add(set);
    }

    public void setAsroot (File asroot)
    {
        _asroot = asroot;
    }

    @Override
    public void execute ()
    {
        // boilerplate
        for (FileSet fs : _filesets) {
            DirectoryScanner ds = fs.getDirectoryScanner(getProject());
            File fromDir = fs.getDir(getProject());
            String[] srcFiles = ds.getIncludedFiles();
            for (String srcFile : srcFiles) {
                try {
                    processBundle(new File(fromDir, srcFile));
                } catch (IOException ioe) {
                    throw new BuildException(ioe);
                }
            }
        }
    }

    protected void processBundle (File source)
        throws IOException
    {
        Properties props = new Properties();
        props.load(new FileInputStream(source));

        String name = source.getName();
        name = name.replace('.', '_');
        File outfile = new File(_asroot, name + ".as");
        PrintWriter out = new PrintWriter(outfile);

        out.println("package {");
        out.println();
        out.println("import com.threerings.util.ResourceBundle;");
        out.println();
        out.println("// Generated at " + new Date());
        out.println("public class " + name + " extends ResourceBundle");
        out.println("{");
        out.println("    override protected function getContent () :Object");
        out.println("    {");
        if (true) {
            // create an array with all the values, then populate in a loop
            out.println("        var data :Array = [");
            for (Map.Entry<Object, Object> entry : props.entrySet()) {
                String key = saveConvert((String) entry.getKey());
                String val = saveConvert((String) entry.getValue());
                out.println("            \"" + key + "\", \"" + val + "\",");
            }
            out.println("            null];");
            out.println("        var o :Object = new Object();");
            out.println("        for (var ii :int = 0; ii < data.length; ii += 2) {");
            out.println("            o[data[ii]] = data[ii + 1];");
            out.println("        }");

        } else {
            // alternate impl: just set each value directly. For non-trivial
            // resource bundles, this generates a larger class after compilation
            out.println("        var o :Object = new Object();");
            for (Map.Entry<Object, Object> entry : props.entrySet()) {
                String key = saveConvert((String) entry.getKey());
                String val = saveConvert((String) entry.getValue());
                out.println("        o[\"" + key + "\"] = \"" + val + "\";");
            }
        }
        out.println("        return o;");
        out.println("   }");
        out.println("}}");
        out.close();
    }

    /**
     * Convert a string to be safe to output inside a string constant.
     */
    protected String saveConvert (String str)
    {
        int len = str.length();
        StringBuilder buf = new StringBuilder(len * 2);

        for (int ii = 0; ii < len; ii++) {
            char ch = str.charAt(ii);
            switch (ch) {
            case '\\':
            case '"':
                buf.append('\\').append(ch);
                break;

            case '\t':
                buf.append('\\').append('t');
                break;

            case '\n':
                buf.append('\\').append('n');
                break;

            case '\r':
                buf.append('\\').append('r');
                break;

            case '\f':
                buf.append('\\').append('f');
                break;

            default:
                buf.append(ch);
                break;
            }
        }
        return buf.toString();
    }

    protected ArrayList<FileSet> _filesets = Lists.newArrayList();

    protected File _asroot;
}
