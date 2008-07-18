//
// $Id$
//
// Narya library - tools for developing networked games
// Copyright (C) 2002-2008 Three Rings Design, Inc., All Rights Reserved
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

package com.threerings.bureau.server;

import java.util.Map;
import java.util.Set;
import java.io.IOException;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import com.samskivert.util.StringUtil;
import com.samskivert.util.Invoker;
import com.samskivert.util.ProcessLogger;

import com.threerings.presents.annotation.MainInvoker;
import com.threerings.presents.data.ClientObject;
import com.threerings.presents.dobj.ObjectDeathListener;
import com.threerings.presents.dobj.ObjectDestroyedEvent;
import com.threerings.presents.dobj.RootDObjectManager;
import com.threerings.presents.server.ClientManager;
import com.threerings.presents.server.InvocationManager;
import com.threerings.presents.server.PresentsClient;

import com.threerings.bureau.data.AgentObject;
import com.threerings.bureau.data.BureauCodes;
import com.threerings.bureau.data.BureauCredentials;

import static com.threerings.bureau.Log.log;

/**
 *  Abstracts the launching and termination of external processes (bureaus) that host instances of
 *  server-side code (agents).
 */
@Singleton
public class BureauRegistry
{
    /**
     * Defines how a bureau is launched. Instances are associated to bureau types by the server on 
     * startup. The instances are used whenever the registry needs to launch a bureau for an agent 
     * with the assocated bureau type.
     */
    public static interface Launcher
    {
        /**
         * Kicks off a new bureau. This method will always be called on the unit invocation
         * thread since it may do extensive I/O.
         * @param bureauId the id of the bureau being launched
         * @param token the secret string for the bureau to use in its credentials
         */
        void launchBureau (String bureauId, String token)
            throws IOException;
    }

    /**
     * Defines how to generate a command to launch a bureau in a local process.
     * @see #setCommandGenerator
     * @see Launcher
     */
    public static interface CommandGenerator
    {
        /**
         * Creates the command line to launch a new bureau using the given information.
         * Called by the registry when a new bureau is needed whose type was registered
         * with <code>setCommandGenerator</code>.
         * @param bureauId the id of the bureau being launched
         * @param token the token string to use for the credentials when logging in
         * @return command line arguments, including executable name
         */
        String[] createCommand (String bureauId, String token);
    }

    /**
     * Creates an uninitialized registry.
     */
    @Inject public BureauRegistry (InvocationManager invmgr)
    {
        invmgr.registerDispatcher(new BureauDispatcher(new BureauProvider () {
            public void bureauInitialized (ClientObject client, String bureauId) {
                BureauRegistry.this.bureauInitialized(client, bureauId);
            }
            public void agentCreated (ClientObject client, int agentId) {
                BureauRegistry.this.agentCreated(client, agentId);
            }
            public void agentCreationFailed (ClientObject client, int agentId) {
                BureauRegistry.this.agentCreationFailed(client, agentId);
            }
            public void agentDestroyed (ClientObject client, int agentId) {
                BureauRegistry.this.agentDestroyed(client, agentId);
            }
        }), BureauCodes.BUREAU_GROUP);
    }

    /**
     * Provides the Bureau registry with necessary runtime configuration. Inserts the bureau
     * client factory into the client manager and registers observers to track the progress
     * of launched bureaus.
     */
    public void init ()
    {
        _clmgr.addClientObserver(new ClientManager.ClientObserver() {
            public void clientSessionDidStart (PresentsClient client) {
                String id = BureauCredentials.extractBureauId(client.getUsername());
                if (id != null) {
                    sessionDidStart(client, id);
                }
            }
            public void clientSessionDidEnd (PresentsClient client) {
                String id = BureauCredentials.extractBureauId(client.getUsername());
                if (id != null) {
                    sessionDidEnd(client, id);
                }
            }
        });

        // add the client factory, but later, after all the other modules have been initialized
        _omgr.postRunnable(new Runnable() {
            public void run () {
                addClientFactory(_clmgr);
            }
        });
    }

    /**
     * Install the bureau client factory in the manager, delegating to the current factory
     * for non-bureau connections.
     */
    @Deprecated public void addClientFactory (ClientManager clmgr)
    {
        clmgr.setClientFactory(
            new BureauClientFactory(clmgr.getClientFactory()));
    }

    /**
     * Check the credentials to make sure this is one of our bureaus.
     * @return null if all's well, otherwise a string describing the authentication failure
     */
    public String checkToken (BureauCredentials creds)
    {
        Bureau bureau = _bureaus.get(creds.bureauId);
        if (bureau == null) {
            return "Bureau " + creds.bureauId + " not found";
        }

        if (bureau.clientObj != null) {
            return "Bureau " + creds.bureauId + " already logged in";
        }

        if (!bureau.token.equals(creds.sessionToken)) {
            return "Bureau " + creds.bureauId + 
                " does not match credentials token";
        }

        return null;
    }

    /**
     * Registers a command generator for a given type. When an agent is started and no bureaus are
     * running, the <code>bureauType</code> is used to determine the <code>CommandGenerator</code>
     * instance to call.
     * @param bureauType the type of bureau that will be launched
     * @param cmdGenerator the generator to be used for bureaus of <code>bureauType</code>
     */
    public void setCommandGenerator (
        String bureauType, 
        final CommandGenerator cmdGenerator)
    {
        setLauncher(bureauType, new Launcher () {
            public void launchBureau (String bureauId, String token) 
                throws IOException {
                ProcessBuilder builder = new ProcessBuilder(
                    cmdGenerator.createCommand(bureauId, token));
                builder.redirectErrorStream(true);
                Process process = builder.start();
                // log the output of the process and prefix with bureau id
                ProcessLogger.copyMergedOutput(log, bureauId, process);
            }

            public String toString () {
                return "DefaultLauncher for " + cmdGenerator;
            }
        });
    }

    /**
     * Registers a launcher for a given type. When an agent is started and no bureaus are
     * running, the <code>bureauType</code> is used to determine the <code>Launcher</code>
     * instance to call.
     * @param bureauType the type of bureau that will be launched
     * @param launcher the launcher to be used for bureaus of <code>bureauType</code>
     */
    public void setLauncher (String bureauType, Launcher launcher)
    {
        if (_launchers.get(bureauType) != null) {
            log.warning("Launcher for type already exists [type=" +
                bureauType + "]");
            return;
        }

        _launchers.put(bureauType, launcher);
    }

    /**
     * Starts a new agent using the data in the given object, creating a new bureau if necessary.
     */
    public void startAgent (AgentObject agent)
    {
        Bureau bureau = _bureaus.get(agent.bureauId);
        if (bureau != null && bureau.ready()) {

            _omgr.registerObject(agent);

            log.info("Bureau ready, sending createAgent " +
                StringUtil.toString(agent));

            BureauSender.createAgent(bureau.clientObj, agent.getOid());
            bureau.agentStates.put(agent, Bureau.STARTED);

            bureau.summarize();

            return;
        }

        if (bureau == null) {

            Launcher launcher = _launchers.get(agent.bureauType);
            if (launcher == null) {
                log.warning("Launcher not found for agent's " +
                       "bureau type " + StringUtil.toString(agent));
                return;
            }

            log.info("Creating new bureau " +
                StringUtil.toString(agent.bureauId) + " " +
                StringUtil.toString(launcher));

            bureau = new Bureau();
            bureau.bureauId = agent.bureauId;
            bureau.token = generateToken(bureau.bureauId);

            bureau.launcher = launcher;

            _invoker.postUnit(new LauncherUnit(bureau));

            _bureaus.put(agent.bureauId, bureau);
        }

        _omgr.registerObject(agent);
        bureau.agentStates.put(agent, Bureau.PENDING);

        log.info("Bureau not ready, pending agent " +
            StringUtil.toString(agent));

        bureau.summarize();
    }

    /**
     * Destroys a previously started agent using the data in the given object.
     */
    public void destroyAgent (AgentObject agent)
    {
        FoundAgent found = resolve(null, agent.getOid(), "destroyAgent");

        if (found == null) {
            return;
        }

        log.info("Destroying agent " + StringUtil.toString(agent));

        // transition the agent to a new state and perform the effect of the transition
        switch (found.state) {

        case Bureau.PENDING:
            found.bureau.agentStates.remove(found.agent);
            // !TODO: is the the right place to destroy it?
            _omgr.destroyObject(found.agent.getOid());
            break;

        case Bureau.STARTED:
            found.bureau.agentStates.put(found.agent, Bureau.STILL_BORN);
            break;

        case Bureau.RUNNING:
            BureauSender.destroyAgent(found.bureau.clientObj, agent.getOid());
            found.bureau.agentStates.put(found.agent, Bureau.DESTROYED);
            break;

        case Bureau.DESTROYED:
        case Bureau.STILL_BORN:
            log.warning("Acknowledging a request to destory an agent, but agent " +
                "is in state " + found.state + ", ignoring request " +
                StringUtil.toString(found.agent));
            break;
        }

        found.bureau.summarize();
    }

    /**
     * Returns the active session for a bureau of the given id.
     */
    public PresentsClient lookupClient (String bureauId)
    {
        Bureau bureau = _bureaus.get(bureauId);
        if (bureau == null) {
            return null;
        }
        return bureau.client;
    }

    protected void sessionDidStart (PresentsClient client, String id)
    {
        Bureau bureau = _bureaus.get(id);
        if (bureau == null) {
            log.warning("Starting session for unknoqn bureau", "id", id, "client", client);
            return;
        }
        if (bureau.client != null) {
            log.warning(
                "Multiple sessions for the same bureau", "id", id, "client", client, "bureau", 
                bureau);
        }
        bureau.client = client;
    }

    protected void sessionDidEnd (PresentsClient client, String id)
    {
        Bureau bureau = _bureaus.get(id);
        if (bureau == null) {
            log.warning("Ending session for unknown bureau", "id", id, "client", client);
            return;
        }
        if (bureau.client == null) {
            log.warning(
                "Multiple logouts from the same bureau", "id", id, "client", client, "bureau", 
                bureau);
        }
        bureau.client = null;

        clientDestroyed(bureau);
    }

    /**
     * Callback for when the bureau client acknowledges starting up. Starts all pending agents and
     * causes subsequent agent start requests to be sent directly to the bureau.
     */
    protected void bureauInitialized (ClientObject client, String bureauId)
    {
        final Bureau bureau = _bureaus.get(bureauId);
        if (bureau == null) {
            log.warning("Acknowledging initialization of non-existent bureau " +
                StringUtil.toString(bureauId));
            return;
        }

        bureau.clientObj = client;

        bureau.clientObj.addListener(new ObjectDeathListener() {
            public void objectDestroyed (ObjectDestroyedEvent e) {
                BureauRegistry.this.clientDestroyed(bureau);
            }
        });

        log.info("Bureau created " + StringUtil.toString(bureau) +
            ", launching pending agents");

        // find all pending agents
        Set<AgentObject> pending = Sets.newHashSet();

        for (Map.Entry<AgentObject, Integer> entry :
            bureau.agentStates.entrySet()) {

            if (entry.getValue() == Bureau.PENDING) {
                pending.add(entry.getKey());
            }
        }

        // create them
        for (AgentObject agent : pending) {
            log.info("Creating agent " + StringUtil.toString(agent));
            BureauSender.createAgent(bureau.clientObj, agent.getOid());
            bureau.agentStates.put(agent, Bureau.STARTED);
        }

        bureau.summarize();
    }

    /**
     * Callback for when the bureau client acknowledges the creation of an agent.
     */
    protected void agentCreated (ClientObject client, int agentId)
    {
        FoundAgent found = resolve(client, agentId, "agentCreated");
        if (found == null) {
            return;
        }

        log.info("Agent creation confirmed " + StringUtil.toString(found.agent));

        switch (found.state) {
        case Bureau.STARTED:
            found.bureau.agentStates.put(found.agent, Bureau.RUNNING);
            found.agent.setClientOid(client.getOid());
            break;

        case Bureau.STILL_BORN:
            BureauSender.destroyAgent(found.bureau.clientObj, agentId);
            found.bureau.agentStates.put(found.agent, Bureau.DESTROYED);
            break;

        case Bureau.PENDING:
        case Bureau.RUNNING:
        case Bureau.DESTROYED:
            log.warning("Received acknowledgement of the creation of an " +
                "agent in state " + found.state + ", ignoring request " +
                StringUtil.toString(found.agent));
            break;
        }

        found.bureau.summarize();
    }

    /**
     * Callback for when the bureau client acknowledges the failure to create an agent.
     */
    protected void agentCreationFailed (ClientObject client, int agentId)
    {
        FoundAgent found = resolve(client, agentId, "agentCreationFailed");
        if (found == null) {
            return;
        }

        log.info("Agent creation failed " + StringUtil.toString(found.agent));

        switch (found.state) {
        case Bureau.STARTED:
        case Bureau.STILL_BORN:
            found.bureau.agentStates.remove(found.agent);
            break;

        case Bureau.PENDING:
        case Bureau.RUNNING:
        case Bureau.DESTROYED:
            log.warning("Received acknowledgement of creation failure for " +
                "agent in state " + found.state + ", ignoring request " +
                StringUtil.toString(found.agent));
            break;
        }

        found.bureau.summarize();
    }

    /**
     * Callback for when the bureau client acknowledges the destruction of an agent.
     */
    protected void agentDestroyed (ClientObject client, int agentId)
    {
        FoundAgent found = resolve(client, agentId, "agentDestroyed");
        if (found == null) {
            return;
        }

        log.info("Agent destruction confirmed " + StringUtil.toString(found.agent));

        switch (found.state) {
        case Bureau.DESTROYED:
            found.bureau.agentStates.remove(found.agent);
            break;

        case Bureau.PENDING:
        case Bureau.STARTED:
        case Bureau.RUNNING:
        case Bureau.STILL_BORN:
            log.warning("Acknowledging agent destruction, but state is " +
                found.state + ", ignoring request " +
                StringUtil.toString(found.agent));
            break;
        }

        found.bureau.summarize();
    }

    /**
     * Callback for when a client is destroyed.
     */
    protected void clientDestroyed (Bureau bureau)
    {
        log.info("Client destroyed, destroying all agents " +
            StringUtil.toString(bureau));

        // clean up any agents attached to this bureau
        for (AgentObject agent : bureau.agentStates.keySet()) {
            _omgr.destroyObject(agent.getOid());
        }
        bureau.agentStates.clear();

        if (_bureaus.remove(bureau.bureauId) == null) {
            log.info("Bureau not found to remove", "bureau", bureau);
        }
    }

    /**
     * Does lots of null checks and lookups and resolves the given information into FoundAgent.
     */
    protected FoundAgent resolve (ClientObject client, int agentId, String resolver)
    {
        com.threerings.presents.dobj.DObject dobj = _omgr.getObject(agentId);
        if (dobj == null) {
            log.warning("Non-existent agent in " + resolver +
                " [agentId=" + agentId + "]");
            return null;
        }

        if (!(dobj instanceof AgentObject)) {
            log.warning("Object not an agent in " + resolver +
                " " + StringUtil.toString(dobj));
            return null;
        }

        AgentObject agent = (AgentObject)dobj;
        Bureau bureau = _bureaus.get(agent.bureauId);
        if (bureau == null) {
            log.warning("Bureau not found for agent in " + resolver +
                " " + StringUtil.toString(agent));
            return null;
        }

        if (!bureau.agentStates.containsKey(agent)) {
            log.warning("Bureau does not have agent in " + resolver +
                " " + StringUtil.toString(agent));
            return null;
        }

        if (client != null && bureau.clientObj != client) {
            log.warning("Masquerading request in " + resolver +
                " " + StringUtil.toString(agent) +
                " " + StringUtil.toString(bureau.clientObj) +
                " " + StringUtil.toString(client));
            return null;
        }

        return new FoundAgent(bureau, agent, bureau.agentStates.get(agent));
    }

    /**
     * Create a hard-to-guess token that the bureau can use to authenticate itself when it tries 
     * to log in.
     */
    protected String generateToken (String bureauId)
    {
        String tokenSource = bureauId + "@" + 
            System.currentTimeMillis() + "r" + Math.random();
        return StringUtil.md5hex(tokenSource);
    }

    /**
     * Invoker unit to launch a bureau's process, then assign the result on the main thread.
     */
    protected static class LauncherUnit extends Invoker.Unit
    {
        LauncherUnit (Bureau bureau)
        {
            super("LauncherUnit for " + bureau + ": " + 
                StringUtil.toString(bureau.launcher));
            _bureau = bureau;
        }

        public boolean invoke ()
        {
            try {
                _bureau.launch();

            } catch (Exception e) {
                log.warning("Could not launch bureau", e);
            }
            return true;
        }

        public void handleResult ()
        {
            _bureau.launched = true;
            _bureau.launcher = null;
            log.info("Bureau launched", "bureau", _bureau);
        }

        protected Bureau _bureau;
        protected Process _result;
    }

    // Models the results of searching for an agent
    protected static class FoundAgent
    {
        FoundAgent (
           Bureau bureau,
           AgentObject agent,
           int state)
        {
            this.bureau = bureau;
            this.agent = agent;
            this.state = state;
        }

        // Bureau containing the agent
        Bureau bureau;

        // The object
        AgentObject agent;

        // The state of the agent
        int state;
    }

    // Models a bureau, including the process handle, all running agents and their states
    protected static class Bureau
    {
        // Agent states {

        // Not yet stated, waiting for bureau to ack
        static final int PENDING = 0;

        // Bureau acked, agent told to start
        static final int STARTED = 1;

        // Agent ack'ed, now live and hosting, ready to tell other clients
        static final int RUNNING = 2;

        // Agent destruction requested, waiting for acknowledge (after which the agent is removed
        // from the Bureau, so has no state)
        static final int DESTROYED = 3;

        // Edge case: destroy request prior to RUNNING
        static final int STILL_BORN = 4;

        // }

        // non-null once the bureau is scheduled but not yet kicked off
        Launcher launcher;

        // non-null once the bureau is kicked off
        boolean launched;

        // The token given to this bureau for authentication
        String token;

        // The bureau's key in the map of bureaus. All requests for this bureau
        // with this id should be associated with one instance
        String bureauId;

        // The client object of the bureau that has opened a dobj connection to
        // the registry
        ClientObject clientObj;

        // The client session
        PresentsClient client;

        // The states of the various agents allocated to this bureau
        Map<AgentObject, Integer> agentStates = Maps.newHashMap();

        public String toString ()
        {
            StringBuilder builder = new StringBuilder();
            builder.append("[Bureau id=").append(bureauId).append(", client=");
            if (clientObj == null) {
                builder.append("null");
            }
            else {
                builder.append(clientObj.getOid());
            }
            builder.append(", launcher=").append(launcher);
            builder.append(", launched=").append(launched);
            builder.append(", totalAgents=").append(agentStates.size());
            agentSummary(builder.append(", ")).append("]");
            return builder.toString();
        }

        boolean ready ()
        {
            return clientObj != null;
        }

        StringBuilder agentSummary (StringBuilder str)
        {
            int counts[] = {0, 0, 0, 0, 0};
            for (Map.Entry<AgentObject, Integer> me : agentStates.entrySet()) {
                counts[me.getValue()]++;
            }

            str.append(counts[PENDING]).append(" pending, ").
                append(counts[STARTED]).append(" started, ").
                append(counts[RUNNING]).append(" running, ").
                append(counts[DESTROYED]).append(" destroyed, ").
                append(counts[STILL_BORN]).append(" still born");
            return str;
        }

        void summarize ()
        {
            StringBuilder str = new StringBuilder();
            str.append("Bureau ").append(bureauId).append(" [");
            agentSummary(str).append("]");
            log.info(str.toString());
        }

        void launch ()
            throws IOException
        {
            launcher.launchBureau(bureauId, token);
        }
    }

    protected Map<String, Launcher> _launchers = Maps.newHashMap();
    protected Map<String, Bureau> _bureaus = Maps.newHashMap();

    @Inject protected RootDObjectManager _omgr;
    @Inject protected @MainInvoker Invoker _invoker;
    @Inject protected ClientManager _clmgr;
}
