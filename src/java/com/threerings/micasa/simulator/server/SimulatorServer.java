//
// $Id: SimulatorServer.java,v 1.8 2004/02/25 14:43:37 mdb Exp $

package com.threerings.micasa.simulator.server;

import com.samskivert.util.ResultListener;

/**
 * The simulator manager needs a mechanism for faking body object
 * registrations, which is provided by implementations of this interface.
 */
public interface SimulatorServer
{
    /**
     * Called to initialize this server instance.
     *
     * @param obs the observer to notify when the server has finished
     * starting up, or <code>null</code> if no notification is desired.
     *
     * @exception Exception thrown if anything goes wrong initializing the
     * server.
     */
    public void init (ResultListener obs) throws Exception;

    /**
     * Called to perform the main body of server processing. This is
     * called from the server thread and should do the simulator server's
     * primary business.
     */
    public void run ();
}
