//
// $Id$
//
// Narya library - tools for developing networked games
// Copyright (C) 2002-2004 Three Rings Design, Inc., All Rights Reserved
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

package com.threerings.presents.client;

import java.util.ArrayList;

/**
 * A test of the invocation services.
 */
public interface TestService extends InvocationService
{
    /** Used to dispatch responses to {@link TestService#test} requests. */
    public static interface TestFuncListener extends InvocationListener
    {
        /** Informs listener of successful {@link TestService#test} request. */
        public void testSucceeded (String one, int two);
    }

    /** Issues a test request. */
    public void test (
        Client client, String one, int two, ArrayList<Integer> three, TestFuncListener listener);

    /** Used to dispatch responses to {@link TestService#getTestOid} requests. */
    public static interface TestOidListener extends InvocationListener
    {
        /** Communicates test oid to listener. */
        public void gotTestOid (int testOid);
    }

    /** Issues a request for the test oid. */
    public void getTestOid (Client client, TestOidListener listener);
}
