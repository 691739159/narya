//
// $Id$
//
// Narya library - tools for developing networked games
// Copyright (C) 2002-2012 Three Rings Design, Inc., All Rights Reserved
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

package com.threerings.io;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import java.nio.channels.Pipe;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.spi.SelectorProvider;

import junit.framework.Test;
import junit.framework.TestCase;

public class FrameTest extends TestCase
{
    public FrameTest ()
    {
        super(FrameTest.class.getName());
    }

    public void writeFrames (WritableByteChannel out)
        throws IOException
    {
        FramingOutputStream fout = new FramingOutputStream();
        DataOutputStream dout = new DataOutputStream(fout);

        // create a few frames and write them to the output stream
        dout.writeUTF(STRING1);
        dout.writeUTF(STRING2);
        dout.writeUTF(STRING3);
        out.write(fout.frameAndReturnBuffer());
        fout.resetFrame();

        dout.writeUTF(STRING4);
        dout.writeUTF(STRING5);
        dout.writeUTF(STRING6);
        out.write(fout.frameAndReturnBuffer());
        fout.resetFrame();

        dout.writeUTF(STRING7);
        out.write(fout.frameAndReturnBuffer());
        fout.resetFrame();
    }

    public void readFrames (ReadableByteChannel in)
        throws IOException
    {
        FramedInputStream fin = new FramedInputStream();
        DataInputStream din = new DataInputStream(fin);

        // read the first frame
        fin.readFrame(in);
        assertTrue("string1", STRING1.equals(din.readUTF()));
        assertTrue("string2", STRING2.equals(din.readUTF()));
        assertTrue("string3", STRING3.equals(din.readUTF()));
        assertTrue("hit eof", fin.read() == -1);

        // read the second frame
        fin.readFrame(in);
        assertTrue("string4", STRING4.equals(din.readUTF()));
        assertTrue("string5", STRING5.equals(din.readUTF()));
        assertTrue("string6", STRING6.equals(din.readUTF()));
        assertTrue("hit eof", fin.read() == -1);

        // read the third frame
        fin.readFrame(in);
        assertTrue("string7", STRING7.equals(din.readUTF()));
        assertTrue("hit eof", fin.read() == -1);
    }

    @Override
    public void runTest ()
    {
        try {
            Pipe pipe = SelectorProvider.provider().openPipe();
            writeFrames(pipe.sink());
            readFrames(pipe.source());

        } catch (IOException ioe) {
            ioe.printStackTrace(System.err);
        }
    }

    public static Test suite ()
    {
        return new FrameTest();
    }

    public static void main (String[] args)
    {
        FrameTest test = new FrameTest();
        test.runTest();
    }

    protected static final String STRING1 = "This is a test.";
    protected static final String STRING2 = "This is only a test.";
    protected static final String STRING3 =
        "If this were not a test, there would be meaningful data in " +
        "this frame and someone would probably be enjoying themselves.";

    protected static final String STRING4 =
        "Now is the time for all good men to come to the aid of " +
        "their country.";
    protected static final String STRING5 = "Every good boy deserves fudge.";
    protected static final String STRING6 =
        "The quick brown fox jumped over the lazy dog.";

    protected static final String STRING7 = "Third time is the charm.";
}
