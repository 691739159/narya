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

package com.threerings.crowd.chat.server;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import com.samskivert.util.ObserverList;
import com.threerings.util.Name;

import com.threerings.presents.dobj.DObject;
import com.threerings.presents.dobj.RootDObjectManager;

import com.threerings.crowd.data.BodyObject;

import com.threerings.crowd.chat.data.ChatCodes;
import com.threerings.crowd.chat.data.ChatMessage;
import com.threerings.crowd.chat.data.SpeakObject;
import com.threerings.crowd.chat.data.SystemMessage;
import com.threerings.crowd.chat.data.UserMessage;

import static com.threerings.crowd.Log.log;

/**
 * Provides the back-end of the chat speaking facilities.
 */
public class SpeakUtil
{
    /**
     * An interface used to notify external systems whenever a chat message is spoken by one user
     * and heard by another.
     */
    public static interface MessageObserver
    {
        /**
         * Called for each player that hears a particular chat message.
         */
        void messageDelivered (Name hearer, UserMessage message);
    }

    /**
     * Registers a {@link MessageObserver} to be notified whenever a user-originated chat message
     * is heard by another user.
     */
    public static void registerMessageObserver (MessageObserver obs)
    {
        _messageObs.add(obs);
    }

    /**
     * Removes a registration made previously with {@link #registerMessageObserver}.
     */
    public static void removeMessageObserver (MessageObserver obs)
    {
        _messageObs.remove(obs);
    }

    /**
     * Sends a speak notification to the specified place object originating with the specified
     * speaker (the speaker optionally being a server entity that wishes to fake a "speak" message)
     * and with the supplied message content.
     *
     * @param speakObj the object on which to generate the speak message.
     * @param speaker the username of the user that generated the message (or some special speaker
     * name for server messages).
     * @param bundle null when the message originates from a real human, the bundle identifier that
     * will be used by the client to translate the message text when the message originates from a
     * server entity "faking" a chat message.
     * @param message the text of the speak message.
     */
    public static void sendSpeak (DObject speakObj, Name speaker, String bundle, String message)
    {
        sendSpeak(speakObj, speaker, bundle, message, ChatCodes.DEFAULT_MODE);
    }

    /**
     * Sends a speak notification to the specified place object originating with the specified
     * speaker (the speaker optionally being a server entity that wishes to fake a "speak" message)
     * and with the supplied message content.
     *
     * @param speakObj the object on which to generate the speak message.
     * @param speaker the username of the user that generated the message (or some special speaker
     * name for server messages).
     * @param bundle null when the message originates from a real human, the bundle identifier that
     * will be used by the client to translate the message text when the message originates from a
     * server entity "faking" a chat message.
     * @param message the text of the speak message.
     * @param mode the mode of the message, see {@link ChatCodes#DEFAULT_MODE}.
     */
    public static void sendSpeak (DObject speakObj, Name speaker, String bundle, String message,
                                  byte mode)
    {
        sendMessage(speakObj, new UserMessage(speaker, bundle, message, mode));
    }

    /**
     * Sends a system INFO message notification to the specified object with the supplied message
     * content. A system message is one that will be rendered where the speak messages are
     * rendered, but in a way that makes it clear that it is a message from the server.
     *
     * Info messages are sent when something happens that was neither directly triggered by the
     * user, nor requires direct action.
     *
     * @param speakObj the object on which to deliver the message.
     * @param bundle the name of the localization bundle that should be used to translate this
     * system message prior to displaying it to the client.
     * @param message the text of the message.
     */
    public static void sendInfo (DObject speakObj, String bundle, String message)
    {
        sendSystem(speakObj, bundle, message, SystemMessage.INFO);
    }

    /**
     * Sends a system FEEDBACK message notification to the specified object with the supplied
     * message content. A system message is one that will be rendered where the speak messages are
     * rendered, but in a way that makes it clear that it is a message from the server.
     *
     * Feedback messages are sent in direct response to a user action, usually to indicate success
     * or failure of the user's action.
     *
     * @param speakObj the object on which to deliver the message.
     * @param bundle the name of the localization bundle that should be used to translate this
     * system message prior to displaying it to the client.
     * @param message the text of the message.
     */
    public static void sendFeedback (DObject speakObj, String bundle, String message)
    {
        sendSystem(speakObj, bundle, message, SystemMessage.FEEDBACK);
    }

    /**
     * Sends a system ATTENTION message notification to the specified object with the supplied
     * message content. A system message is one that will be rendered where the speak messages are
     * rendered, but in a way that makes it clear that it is a message from the server.
     *
     * Attention messages are sent when something requires user action that did not result from
     * direct action by the user.
     *
     * @param speakObj the object on which to deliver the message.
     * @param bundle the name of the localization bundle that should be used to translate this
     * system message prior to displaying it to the client.
     * @param message the text of the message.
     */
    public static void sendAttention (DObject speakObj, String bundle, String message)
    {
        sendSystem(speakObj, bundle, message, SystemMessage.ATTENTION);
    }

    /**
     * Send the specified message on the specified object.
     */
    public static void sendMessage (DObject speakObj, ChatMessage msg)
    {
        if (speakObj == null) {
            log.warning("Dropping speak message, no speak obj '" + msg + "'.", new Exception());
            return;
        }

        // post the message to the relevant object
        speakObj.postMessage(ChatCodes.CHAT_NOTIFICATION, new Object[] { msg });

        // if this is a user message; add it to the heard history of all users that can "hear" it
        if (!(msg instanceof UserMessage)) {
            return;

        } else if (speakObj instanceof SpeakObject) {
            _messageMapper.omgr = (RootDObjectManager)speakObj.getManager();
            _messageMapper.message = (UserMessage)msg;
            ((SpeakObject)speakObj).applyToListeners(_messageMapper);
            _messageMapper.omgr = null;
            _messageMapper.message = null;

        } else {
            log.info("Unable to note listeners [dclass=" + speakObj.getClass() +
                     ", msg=" + msg + "].");
        }
    }

    /**
     * Returns a list of {@link ChatMessage} objects to which this user has been privy in the
     * recent past.
     */
    public static List<ChatMessage> getChatHistory (Name username)
    {
        List<ChatMessage> history = getHistoryList(username);
        pruneHistory(System.currentTimeMillis(), history);
        return history;
    }

    /**
     * Called to clear the chat history for the specified user.
     */
    public static void clearHistory (Name username)
    {
        // Log.info("Clearing history for " + username + ".");
        _histories.remove(username);
    }

    /**
     * Records the specified message to the specified users' chat histories.  If {@link
     * ChatMessage#timestamp} is not already filled in, it will be.
     */
    public static void recordToChatHistory (UserMessage msg, Name... usernames)
    {
        // fill in the message's time stamp if necessary
        if (msg.timestamp == 0L) {
            msg.timestamp = System.currentTimeMillis();
        }

        for (Name username : usernames) {
            // add the message to this user's chat history
            List<ChatMessage> history = getHistoryList(username);
            history.add(msg);

            // if the history is big enough, potentially prune it (we always prune when asked for
            // the history, so this is just to balance memory usage with CPU expense)
            if (history.size() > 15) {
                pruneHistory(msg.timestamp, history);
            }
        }
    }

    /**
     * Notes that the specified user was privy to the specified message. If {@link
     * ChatMessage#timestamp} is not already filled in, it will be.
     */
    protected static void noteMessage (Name username, UserMessage msg)
    {
        // fill in the message's time stamp if necessary
        if (msg.timestamp == 0L) {
            msg.timestamp = System.currentTimeMillis();
        }

        recordToChatHistory(msg, username);
        // Log.info("Noted that " + username + " heard " + msg + ".");

        // notify any message observers
        _messageOp.init(username, msg);
        _messageObs.apply(_messageOp);
    }

    /**
     * Send the specified system message on the specified dobj.
     */
    protected static void sendSystem (DObject speakObj, String bundle, String message, byte level)
    {
        sendMessage(speakObj, new SystemMessage(message, bundle, level));
    }

    /**
     * Returns this user's chat history, creating one if necessary.
     */
    protected static List<ChatMessage> getHistoryList (Name username)
    {
        List<ChatMessage> history = _histories.get(username);
        if (history == null) {
            _histories.put(username, history = Lists.newArrayList());
        }
        return history;
    }

    /**
     * Prunes all messages from this history which are expired.
     */
    protected static void pruneHistory (long now, List<ChatMessage> history)
    {
        int prunepos = 0;
        for (int ll = history.size(); prunepos < ll; prunepos++) {
            ChatMessage msg = history.get(prunepos);
            if (now - msg.timestamp < HISTORY_EXPIRATION) {
                break; // stop when we get to the first valid message
            }
        }
        history.subList(0, prunepos).clear();
    }

    /** Used to note the recipients of a chat message. */
    protected static class MessageMapper implements SpeakObject.ListenerOp
    {
        public RootDObjectManager omgr;
        public UserMessage message;

        public void apply (int bodyOid) {
            DObject dobj = omgr.getObject(bodyOid);
            if (dobj != null && dobj instanceof BodyObject) {
                noteMessage(((BodyObject)dobj).getVisibleName(), message);
            }
        }

        public void apply (Name username) {
            noteMessage(username, message);
        }
    }

    /** Used to notify our {@link MessageObserver}s. */
    protected static class MessageObserverOp
        implements ObserverList.ObserverOp<MessageObserver>
    {
        public void init (Name hearer, UserMessage message) {
            _hearer = hearer;
            _message = message;
        }

        public boolean apply (MessageObserver observer) {
            observer.messageDelivered(_hearer, _message);
            return true;
        }

        protected Name _hearer;
        protected UserMessage _message;
    }

    /** Recent chat history for the server. */
    protected static Map<Name, List<ChatMessage>> _histories = Maps.newHashMap();

    /** Used to note the recipients of a chat message. */
    protected static MessageMapper _messageMapper = new MessageMapper();

    /** A list of {@link MessageObserver}s. */
    protected static ObserverList<MessageObserver> _messageObs = ObserverList.newFastUnsafe();

    /** Used to notify our {@link MessageObserver}s. */
    protected static MessageObserverOp _messageOp = new MessageObserverOp();

    /** The amount of time before chat history becomes... history. */
    protected static final long HISTORY_EXPIRATION = 5L * 60L * 1000L;
}
