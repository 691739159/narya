package com.threerings.nio;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

import static com.threerings.presents.Log.log;

/**
 * Exposes selected keys from {@link Selector#selectedKeys()} as an Iterable, removing them from the
 * selected set as they're iterated over.
 */
public class SelectorIterable
    implements Iterable<SelectionKey>
{
    /**
     * Callback for the Selector failing.
     */
    public interface SelectFailureHandler {
        void handleSelectFailure(Exception e);
    }

    /**
     * Creates an iterable for the given selector's selectedKeys.
     * @param selectLoopTime - the amount of time to wait in select.
     * @param handler - a callback for the Selector going awol.
     */
    public SelectorIterable (Selector selector, int selectLoopTime, SelectFailureHandler handler)
    {
        _selector = selector;
        _selectLoopTime = selectLoopTime;
        _failureHandler = handler;
    }

    public Iterator<SelectionKey> iterator ()
    {
        final Iterator<SelectionKey> it = select().iterator();
        return new Iterator<SelectionKey>() {
            public boolean hasNext () {
                return it.hasNext();
            }

            public SelectionKey next () {
                SelectionKey key = it.next();
                it.remove();
                return key;
            }

            public void remove () {
                throw new UnsupportedOperationException(
                    "Removing from the selected keys is done automatically on next()");
            }
        };
    }

    protected Set<SelectionKey> select ()
    {
        try {
            // log.debug("Selecting from " + _selector.keys() + " (" + _selectLoopTime + ").");

            // check for incoming network events
            int eventCount = _selector.select(_selectLoopTime);
            Set<SelectionKey> ready = _selector.selectedKeys();
            if (eventCount == 0 && ready.size() != 0) {
                log.warning("select() returned no selected sockets, but there are "
                    + ready.size() + " in the ready set.");
            }
            // clear the runtime error count
            _runtimeExceptionCount = 0;
            return ready;

        } catch (IOException ioe) {
            log.warning("Failure select()ing", "ioe", ioe);

        } catch (RuntimeException re) {
            // this block of code deals with a bug in the _selector that we observed on 2005-05-02,
            // instead of looping indefinitely after things go pear-shaped, shut us down in an
            // orderly fashion
            log.warning("Failure select()ing.", re);
            if (_runtimeExceptionCount++ >= 20) {
                _failureHandler.handleSelectFailure(re);
            }
        }
        return Collections.emptySet();
    }

    protected int _runtimeExceptionCount;

    protected final int _selectLoopTime;

    protected final Selector _selector;

    protected final SelectFailureHandler _failureHandler;
}
