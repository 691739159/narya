//
// $Id: GameMarshaller.java,v 1.4 2004/02/25 14:44:54 mdb Exp $

package com.threerings.parlor.game;

import com.threerings.parlor.game.GameService;
import com.threerings.presents.client.Client;
import com.threerings.presents.data.InvocationMarshaller;

/**
 * Provides the implementation of the {@link GameService} interface
 * that marshalls the arguments and delivers the request to the provider
 * on the server. Also provides an implementation of the response listener
 * interfaces that marshall the response arguments and deliver them back
 * to the requesting client.
 */
public class GameMarshaller extends InvocationMarshaller
    implements GameService
{
    /** The method id used to dispatch {@link #playerReady} requests. */
    public static final int PLAYER_READY = 1;

    // documentation inherited from interface
    public void playerReady (Client arg1)
    {
        sendRequest(arg1, PLAYER_READY, new Object[] {
            
        });
    }

    /** The method id used to dispatch {@link #startPartyGame} requests. */
    public static final int START_PARTY_GAME = 2;

    // documentation inherited from interface
    public void startPartyGame (Client arg1)
    {
        sendRequest(arg1, START_PARTY_GAME, new Object[] {
            
        });
    }

    // Generated on 12:49:14 09/06/02.
}
