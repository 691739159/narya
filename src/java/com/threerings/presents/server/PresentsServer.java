//
// $Id$
//
// Narya library - tools for developing networked games
// Copyright (C) 2002-2007 Three Rings Design, Inc., All Rights Reserved
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

package com.threerings.presents.server;

import java.util.ArrayList;

import com.samskivert.util.Interval;
import com.samskivert.util.ObserverList;
import com.samskivert.util.StringUtil;
import com.samskivert.util.SystemInfo;

import com.threerings.presents.client.Client;
import com.threerings.presents.dobj.AccessController;
import com.threerings.presents.server.net.ConnectionManager;

import static com.threerings.presents.Log.log;

/**
 * The presents server provides a central point of access to the various facilities that make up
 * the presents framework. To facilitate extension and customization, a single instance of the
 * presents server should be created and initialized in a process. To facilitate easy access to the
 * services provided by the presents server, static references to the various managers are made
 * available in the <code>PresentsServer</code> class. These will be configured when the singleton
 * instance is initialized.
 */
public class PresentsServer
{
    /** Used to generate "state of the server" reports. See {@link #registerReporter}. */
    public static interface Reporter
    {
        /**
         * Requests that this reporter append its report to the supplied string buffer.
         *
         * @param buffer the string buffer to which the report text should be appended.
         * @param now the time at which the report generation began, in epoch millis.
         * @param sinceLast number of milliseconds since the last time we generated a report.
         * @param reset if true, all accumulating stats should be reset, if false they should be
         * allowed to continue to accumulate.
         */
        public void appendReport (StringBuilder buffer, long now, long sinceLast, boolean reset);
    }

    /** Implementers of this interface will be notified when the server is shutting down. */
    public static interface Shutdowner
    {
        /**
         * Called when the server is shutting down.
         */
        public void shutdown ();
    }

    /** The manager of network connections. */
    public static ConnectionManager conmgr;

    /** The manager of clients. */
    public static ClientManager clmgr;

    /** The distributed object manager. */
    public static PresentsDObjectMgr omgr;

    /** The invocation manager. */
    public static InvocationManager invmgr;

    /** This is used to invoke background tasks that should not be allowed to tie up the
     * distributed object manager thread. This is generally used to talk to databases and other
     * (relatively) slow entities. */
    public static PresentsInvoker invoker;

    /**
     * Registers an entity that will be notified when the server is shutting down.
     */
    public static void registerShutdowner (Shutdowner downer)
    {
        _downers.add(downer);
    }

    /**
     * Unregisters the shutdowner from hearing when the server is shutdown.
     */
    public static void unregisterShutdowner (Shutdowner downer)
    {
        _downers.remove(downer);
    }

    /**
     * The default entry point for the server.
     */
    public static void main (String[] args)
    {
        log.info("Presents server starting...");

        PresentsServer server = new PresentsServer();
        try {
            // initialize the server
            server.init();

            // check to see if we should load and invoke a test module before running the server
            String testmod = System.getProperty("test_module");
            if (testmod != null) {
                try {
                    log.info("Invoking test module [mod=" + testmod + "].");
                    Class tmclass = Class.forName(testmod);
                    Runnable trun = (Runnable)tmclass.newInstance();
                    trun.run();
                } catch (Exception e) {
                    log.warning("Unable to invoke test module '" + testmod + "'.", e);
                }
            }

            // start the server to running (this method call won't return until the server is shut
            // down)
            server.run();

        } catch (Exception e) {
            log.warning("Unable to initialize server.", e);
            System.exit(-1);
        }
    }

    /**
     * Initializes all of the server services and prepares for operation.
     */
    public void init ()
        throws Exception
    {
        // output general system information
        SystemInfo si = new SystemInfo();
        log.info("Starting up server [os=" + si.osToString() + ", jvm=" + si.jvmToString() +
                 ", mem=" + si.memoryToString() + "].");

        // register SIGTERM, SIGINT (ctrl-c) and a SIGHUP handlers
        boolean registered = false;
        try {
            registered = new SunSignalHandler().init(this);
        } catch (Throwable t) {
            log.warning("Unable to register Sun signal handlers [error=" + t + "].");
        }
        if (!registered) {
            new NativeSignalHandler().init(this);
        }

        // create our distributed object manager
        omgr = createDObjectManager();

        // configure the dobject manager with our access controller
        omgr.setDefaultAccessController(createDefaultObjectAccessController());

        // create and start up our invoker
        invoker = new PresentsInvoker(omgr) {
            protected void didShutdown () {
                invokerDidShutdown();
            }
        };
        invoker.start();

        // create our connection manager
        conmgr = new ConnectionManager(getListenPorts(), getDatagramPorts());
        conmgr.setAuthenticator(createAuthenticator());

        // create our client manager
        clmgr = createClientManager(conmgr);

        // create our invocation manager
        invmgr = new InvocationManager(omgr);

        // initialize the time base services
        TimeBaseProvider.init(invmgr, omgr);

        // queue up an interval which will generate reports
        _reportInterval = new Interval(omgr) {
            public void expired () {
                logReport(generateReport(System.currentTimeMillis(), true));
            }
        };
        _reportInterval.schedule(REPORT_INTERVAL, true);
    }

    /**
     * Creates the client Authenticator to be used on this server.
     */
    protected Authenticator createAuthenticator ()
    {
        return new DummyAuthenticator();
    }

    /**
     * Creates the client manager to be used on this server.
     */
    protected ClientManager createClientManager (ConnectionManager conmgr)
    {
        return new ClientManager(conmgr);
    }

    /**
     * Creates the distributed object manager to be used on this server.
     */
    protected PresentsDObjectMgr createDObjectManager ()
    {
        return new PresentsDObjectMgr();
    }

    /**
     * Defines the default object access policy for all {@link DObject} instances. The default
     * default policy is to allow all subscribers but reject all modifications by the client.
     */
    protected AccessController createDefaultObjectAccessController ()
    {
        return PresentsObjectAccess.DEFAULT;
    }

    /**
     * Returns the port on which the connection manager will listen for client connections.
     */
    protected int[] getListenPorts ()
    {
        return Client.DEFAULT_SERVER_PORTS;
    }

    /**
     * Returns the ports on which the connection manager will listen for datagrams.
     */
    protected int[] getDatagramPorts ()
    {
        return Client.DEFAULT_DATAGRAM_PORTS;
    }

    /**
     * Starts up all of the server services and enters the main server event loop.
     */
    public void run ()
    {
        // post a unit that will start up the connection manager when everything else in the
        // dobjmgr queue is processed
        omgr.postRunnable(new Runnable() {
            public void run () {
                // start up the connection manager
                conmgr.start();
            }
        });
        // invoke the dobjmgr event loop
        omgr.run();
    }

    /**
     * A report is generated by the presents server periodically in which server entities can
     * participate by registering a {@link Reporter} with this method.
     */
    public static void registerReporter (Reporter reporter)
    {
        _reporters.add(reporter);
    }

    /**
     * Generates a report for all system services registered as a {@link Reporter}.
     */
    public static String generateReport ()
    {
        return generateReport(System.currentTimeMillis(), false);
    }

    /**
     * Generates and logs a "state of server" report.
     */
    protected static String generateReport (long now, boolean reset)
    {
        long sinceLast = now - _lastReportStamp;
        long uptime = now - _serverStartTime;
        StringBuilder report = new StringBuilder("State of server report:\n");

        report.append("- Uptime: ");
        report.append(StringUtil.intervalToString(uptime)).append("\n");
        report.append("- Report period: ");
        report.append(StringUtil.intervalToString(sinceLast)).append("\n");

        // report on the state of memory
        Runtime rt = Runtime.getRuntime();
        long total = rt.totalMemory(), max = rt.maxMemory();
        long used = (total - rt.freeMemory());
        report.append("- Memory: ").append(used/1024).append("k used, ");
        report.append(total/1024).append("k total, ");
        report.append(max/1024).append("k max\n");

        for (int ii = 0; ii < _reporters.size(); ii++) {
            Reporter rptr = _reporters.get(ii);
            try {
                rptr.appendReport(report, now, sinceLast, reset);
            } catch (Throwable t) {
                log.warning("Reporter choked [rptr=" + rptr + "].", t);
            }
        }

        /* The following Interval debug methods are no longer supported,
         * but they could be added back easily if needed.
        report.append("* samskivert.Interval:\n");
        report.append("- Registered intervals: ");
        report.append(Interval.registeredIntervalCount());
        report.append("\n- Fired since last report: ");
        report.append(Interval.getAndClearFiredIntervals());
        report.append("\n");
        */

        // strip off the final newline
        int blen = report.length();
        if (report.charAt(blen-1) == '\n') {
            report.delete(blen-1, blen);
        }

        // only reset the last report time if this is a periodic report
        if (reset) {
            _lastReportStamp = now;
        }

        return report.toString();
    }

    /**
     * Logs the state of the server report via the default logging mechanism.  Derived classes may
     * wish to log the state of the server report via a different means.
     */
    protected void logReport (String report)
    {
        log.info(report);
    }

    /**
     * Requests that the server shut down. All registered shutdown participants will be shut down,
     * following which the server process will be terminated.
     */
    public void shutdown ()
    {
        ObserverList<Shutdowner> downers = _downers;
        if (downers == null) {
            log.warning("Refusing repeat shutdown request.");
            return;
        }
        _downers = null;

        // shut down all shutdown participants
        downers.apply(new ObserverList.ObserverOp<Shutdowner>() {
            public boolean apply (Shutdowner downer) {
                downer.shutdown();
                return true;
            }
        });

        // shut down the connection manager (this will cease all network activity but not actually
        // close the connections)
        if (conmgr.isRunning()) {
            conmgr.shutdown();
        }

        // finally shut down the invoker and dobj manager (The invoker does both for us.)
        invoker.shutdown();
    }

    /**
     * Queues up a request to shutdown on the dobjmgr thread. This method may be safely called from
     * any thread.
     */
    public void queueShutdown ()
    {
        omgr.postRunnable(new Runnable() {
            public void run () {
                shutdown();
            }
        });
    }

    /**
     * Called once the invoker and distributed object manager have both completed processing all
     * remaining events and are fully shutdown. <em>Note:</em> this is called as the last act of
     * the invoker <em>on the invoker thread</em>. In theory no other (important) threads are
     * running, so thread safety should not be an issue, but be careful!
     */
    protected void invokerDidShutdown ()
    {
    }

    /** Our interval that generates "state of server" reports. */
    protected Interval _reportInterval;

    /** The time at which the server was started. */
    protected static long _serverStartTime = System.currentTimeMillis();

    /** The last time at which {@link #generateReport} was run. */
    protected static long _lastReportStamp = _serverStartTime;

    /** Used to generate "state of server" reports. */
    protected static ArrayList<Reporter> _reporters = new ArrayList<Reporter>();

    /** A list of shutdown participants. */
    protected static ObserverList<Shutdowner> _downers =
        new ObserverList<Shutdowner>(ObserverList.SAFE_IN_ORDER_NOTIFY);

    /** The frequency with which we generate "state of server" reports. */
    protected static final long REPORT_INTERVAL = 15 * 60 * 1000L;
}
