//
// $Id: ChatMarshaller.java,v 1.5 2003/06/03 21:41:33 ray Exp $

package com.threerings.crowd.chat.data;

import com.threerings.crowd.chat.client.ChatService;
import com.threerings.crowd.chat.client.ChatService.TellListener;
import com.threerings.presents.client.Client;
import com.threerings.presents.client.InvocationService.InvocationListener;
import com.threerings.presents.data.InvocationMarshaller;
import com.threerings.presents.dobj.InvocationResponseEvent;

/**
 * Provides the implementation of the {@link ChatService} interface
 * that marshalls the arguments and delivers the request to the provider
 * on the server. Also provides an implementation of the response listener
 * interfaces that marshall the response arguments and deliver them back
 * to the requesting client.
 */
public class ChatMarshaller extends InvocationMarshaller
    implements ChatService
{
    // documentation inherited
    public static class TellMarshaller extends ListenerMarshaller
        implements TellListener
    {
        /** The method id used to dispatch {@link #tellSucceeded}
         * responses. */
        public static final int TELL_SUCCEEDED = 1;

        // documentation inherited from interface
        public void tellSucceeded ()
        {
            omgr.postEvent(new InvocationResponseEvent(
                               callerOid, requestId, TELL_SUCCEEDED,
                               new Object[] {  }));
        }

        /** The method id used to dispatch {@link #tellSucceededIdle}
         * responses. */
        public static final int TELL_SUCCEEDED_IDLE = 2;

        // documentation inherited from interface
        public void tellSucceededIdle (long arg1)
        {
            omgr.postEvent(new InvocationResponseEvent(
                               callerOid, requestId, TELL_SUCCEEDED_IDLE,
                               new Object[] { new Long(arg1) }));
        }

        // documentation inherited
        public void dispatchResponse (int methodId, Object[] args)
        {
            switch (methodId) {
            case TELL_SUCCEEDED:
                ((TellListener)listener).tellSucceeded(
                    );
                return;

            case TELL_SUCCEEDED_IDLE:
                ((TellListener)listener).tellSucceededIdle(
                    ((Long)args[0]).longValue());
                return;

            default:
                super.dispatchResponse(methodId, args);
            }
        }
    }

    /** The method id used to dispatch {@link #tell} requests. */
    public static final int TELL = 1;

    // documentation inherited from interface
    public void tell (Client arg1, String arg2, String arg3, TellListener arg4)
    {
        TellMarshaller listener4 = new TellMarshaller();
        listener4.listener = arg4;
        sendRequest(arg1, TELL, new Object[] {
            arg2, arg3, listener4
        });
    }

    /** The method id used to dispatch {@link #broadcast} requests. */
    public static final int BROADCAST = 2;

    // documentation inherited from interface
    public void broadcast (Client arg1, String arg2, InvocationListener arg3)
    {
        ListenerMarshaller listener3 = new ListenerMarshaller();
        listener3.listener = arg3;
        sendRequest(arg1, BROADCAST, new Object[] {
            arg2, listener3
        });
    }

}
