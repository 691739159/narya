//
// $Id: Invitation.java,v 1.2 2004/02/25 14:44:54 mdb Exp $

package com.threerings.parlor.client;

import com.threerings.parlor.Log;
import com.threerings.parlor.data.ParlorCodes;
import com.threerings.parlor.game.GameConfig;
import com.threerings.parlor.util.ParlorContext;

/**
 * The invitation class is used to track information related to
 * outstanding invitations generated by or targeted to this client.
 */
public class Invitation
    implements ParlorCodes, ParlorService.InviteListener
{
    /** The unique id for this invitation (as assigned by the
     * server). This is -1 until we receive an acknowledgement from
     * the server that our invitation was delivered. */
    public int inviteId = -1;

    /** The name of the other user involved in this invitation. */
    public String opponent;

    /** The configuration of the game to be created. */
    public GameConfig config;

    /** Constructs a new invitation record. */
    public Invitation (ParlorContext ctx, ParlorService pservice,
                       String opponent, GameConfig config,
                       InvitationResponseObserver observer)
    {
        _ctx = ctx;
        _pservice = pservice;
        _observer = observer;
        this.opponent = opponent;
        this.config = config;
    }

    /**
     * Accepts this invitation.
     */
    public void accept ()
    {
        // generate the invocation service request
        _pservice.respond(_ctx.getClient(), inviteId,
                          INVITATION_ACCEPTED, null, this);
    }

    /**
     * Refuses this invitation.
     *
     * @param message the message to deliver to the inviting user
     * explaining the reason for the refusal or null if no message is to
     * be provided.
     */
    public void refuse (String message)
    {
        // generate the invocation service request
        _pservice.respond(_ctx.getClient(), inviteId,
                          INVITATION_REFUSED, message, this);
    }

    /**
     * Cancels this invitation.
     */
    public void cancel ()
    {
        // if the invitation has not yet been acknowleged by the
        // server, we make a note that it should be cancelled when we
        // do receive the acknowlegement
        if (inviteId == -1) {
            _cancelled = true;

        } else {
            // otherwise, generate the invocation service request
            _pservice.cancel(_ctx.getClient(), inviteId, this);
            // and remove it from the pending table
            _ctx.getParlorDirector().clearInvitation(this);
        }
    }

    /**
     * Counters this invitation with an invitation with different game
     * configuration parameters.
     *
     * @param config the updated game configuration.
     * @param observer the entity that will be notified if this
     * counter-invitation is accepted, refused or countered.
     */
    public void counter (GameConfig config, InvitationResponseObserver observer)
    {
        // update our observer (who will eventually be hearing back from
        // the other client about their counter-invitation)
        _observer = observer;

        // generate the invocation service request
        _pservice.respond(_ctx.getClient(), inviteId,
                          INVITATION_COUNTERED, config, this);
    }

    // documentation inherited from interface
    public void inviteReceived (int inviteId)
    {
        // fill in our invitation id
        this.inviteId = inviteId;

        // if the invitation was cancelled before we heard back about
        // it, we need to send off a cancellation request now
        if (_cancelled) {
            _pservice.cancel(_ctx.getClient(), inviteId, this);
        } else {
            // otherwise, put it in the pending invites table
            _ctx.getParlorDirector().registerInvitation(this);
        }
    }

    // documentation inherited from interface
    public void requestFailed (String reason)
    {
        // let the observer know what's up
        _observer.invitationRefused(this, reason);
    }

    /**
     * Called by the parlor director when we receive a response to an
     * invitation initiated by this client.
     */
    protected void receivedResponse (int code, Object arg)
    {
        // make sure we have an observer to notify
        if (_observer == null) {
            Log.warning("No observer registered for invitation " +
                        this + ".");
            return;
        }

        // notify the observer
        try {
            switch (code) {
            case INVITATION_ACCEPTED:
                _observer.invitationAccepted(this);
                break;

            case INVITATION_REFUSED:
                _observer.invitationRefused(this, (String)arg);
                break;

            case INVITATION_COUNTERED:
                _observer.invitationCountered(this, (GameConfig)arg);
                break;
            }

        } catch (Exception e) {
            Log.warning("Invitation response observer choked on response " +
                        "[code=" + code + ", arg=" + arg +
                        ", invite=" + this + "].");
            Log.logStackTrace(e);
        }

        // unless the invitation was countered, we can remove it from the
        // pending table because it's resolved
        if (code != INVITATION_COUNTERED) {
            _ctx.getParlorDirector().clearInvitation(this);
        }
    }

    /** Returns a string representation of this invitation record. */
    public String toString ()
    {
        return "[inviteId=" + inviteId + ", opponent=" + opponent +
            ", config=" + config + ", observer=" + _observer +
            ", cancelled=" + _cancelled + "]";
    }

    /** Provides access to client services. */
    protected ParlorContext _ctx;

    /** Provides access to parlor services. */
    protected ParlorService _pservice;

    /** The entity to notify when we receive a response for this
     * invitation. */
    protected InvitationResponseObserver _observer;

    /** A flag indicating that we were requested to cancel this
     * invitation before we even heard back with an acknowledgement
     * that it was received by the server. */
    protected boolean _cancelled = false;
}
