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

import com.samskivert.util.Queue;
import com.samskivert.util.RunQueue;

import com.threerings.util.Name;

import com.threerings.presents.data.TestObject;
import com.threerings.presents.dobj.DEvent;
import com.threerings.presents.dobj.EventListener;
import com.threerings.presents.dobj.ObjectAccessException;
import com.threerings.presents.dobj.Subscriber;
import com.threerings.presents.net.UsernamePasswordCreds;

import static com.threerings.presents.Log.log;

/**
 * A standalone test client.
 */
public class TestClient
    implements RunQueue, SessionObserver, Subscriber<TestObject>, EventListener,
               TestService.TestOidListener, TestReceiver
{
    public void setClient (Client client)
    {
        _client = client;
    }

    public void postRunnable (Runnable run)
    {
        // queue it on up
        _queue.append(run);
    }

    public boolean isDispatchThread ()
    {
        return _main == Thread.currentThread();
    }

    public void run ()
    {
        _main = Thread.currentThread();

        // loop over our queue, running the runnables
        while (true) {
            Runnable run = _queue.get();
            run.run();
        }
    }

    public void clientWillLogon (Client client)
    {
        client.addServiceGroup("test");
    }

    public void clientDidLogon (Client client)
    {
        log.info("Client did logon [client=" + client + "].");

        // register ourselves as a test notification receiver
        client.getInvocationDirector().registerReceiver(new TestDecoder(this));

        TestService service = client.requireService(TestService.class);

        // send a test request
        ArrayList<Integer> three = new ArrayList<Integer>();
        three.add(3);
        three.add(4);
        three.add(5);
        service.test(client, "one", 2, three, new TestService.TestFuncListener() {
            public void testSucceeded (String one, int two) {
                log.info("Got test response [one=" + one + ", two=" + two + "].");
            }
            public void requestFailed (String reason) {
                log.info("Urk! Request failed [reason=" + reason + "].");
            }
        });

        // get the test object id
        service.getTestOid(client, this);
    }

    public void clientObjectDidChange (Client client)
    {
        log.info("Client object did change [client=" + client + "].");
    }

    public void clientDidLogoff (Client client)
    {
        log.info("Client did logoff [client=" + client + "].");
        System.exit(0);
    }

    public void objectAvailable (TestObject object)
    {
        object.addListener(this);
        log.info("Object available: " + object);
        object.postMessage("lawl!");

        // try blowing through our message limit
        for (int ii = 0; ii < 2*Client.DEFAULT_MAX_MSG_RATE[0]+5; ii++) {
            object.postMessage("ZOMG!", new Integer(ii));
        }
    }

    public void requestFailed (int oid, ObjectAccessException cause)
    {
        log.info("Object unavailable [oid=" + oid +
                 ", reason=" + cause + "].");
        // nothing to do, so might as well logoff
        _client.logoff(true);
    }

    public void eventReceived (DEvent event)
    {
        log.info("Got event [event=" + event + "].");

//         // request that we log off
//         _client.logoff(true);
    }

    // documentation inherited from interface
    public void gotTestOid (int testOid)
    {
        // subscribe to the test object
        _client.getDObjectManager().subscribeToObject(testOid, this);
    }

    // documentation inherited from interface
    public void requestFailed (String reason)
    {
        log.info("Urk! Request failed [reason=" + reason + "].");
    }

    // documentation inherited from interface
    public void receivedTest (int one, String two)
    {
        log.info("Received test notification [one=" + one +
                 ", two=" + two + "].");
    }

    public static void main (String[] args)
    {
        TestClient tclient = new TestClient();
        UsernamePasswordCreds creds =
            new UsernamePasswordCreds(new Name("test"), "test");
        Client client = new Client(creds, tclient);
        tclient.setClient(client);
        client.addClientObserver(tclient);
        client.setServer("localhost", Client.DEFAULT_SERVER_PORTS);
        client.logon();
        // start up our event processing loop
        tclient.run();
    }

    protected Thread _main;
    protected Queue<Runnable> _queue = new Queue<Runnable>();
    protected Client _client;
}
