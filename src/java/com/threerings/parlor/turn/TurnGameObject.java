//
// $Id: TurnGameObject.java,v 1.5 2004/02/25 14:44:54 mdb Exp $

package com.threerings.parlor.turn;

/**
 * Games that wish to support turn-based play must implement this
 * interface with their {@link GameObject}.
 */
public interface TurnGameObject
{
    /**
     * Returns the distributed object field name of the
     * <code>turnHolder</code> field in the object that implements this
     * interface.
     */
    public String getTurnHolderFieldName ();

    /**
     * Returns the username of the player who is currently taking their
     * turn in this turn-based game or <code>null</code> if no user
     * currently holds the turn.
     */
    public String getTurnHolder ();

    /**
     * Requests that the <code>turnHolder</code> field be set to the specified
     * value.
     */
    public void setTurnHolder (String turnHolder);

    /**
     * Returns the array of player names involved in the game.
     */
    public String[] getPlayers ();
}
