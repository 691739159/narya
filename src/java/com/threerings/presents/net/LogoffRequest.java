//
// $Id: LogoffRequest.java,v 1.3 2001/06/05 21:29:51 mdb Exp $

package com.threerings.cocktail.cher.net;

public class LogoffRequest extends UpstreamMessage
{
    /** The code for a logoff request. */
    public static final short TYPE = TYPE_BASE + 6;

    /**
     * Zero argument constructor used when unserializing an instance.
     */
    public LogoffRequest ()
    {
        super();
    }

    public short getType ()
    {
        return TYPE;
    }
}
