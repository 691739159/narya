//
// $Id: AnimationSequencer.java,v 1.4 2002/11/05 21:17:32 mdb Exp $

package com.threerings.media.animation;

import java.awt.Graphics2D;
import java.awt.Rectangle;

import java.util.ArrayList;

import com.threerings.media.Log;

/**
 * An animation that provides facilities for adding a sequence of
 * animations with a standard or per-animation-specifiable delay between
 * each.
 */
public abstract class AnimationSequencer extends Animation
{
    /**
     * Constructs an animation sequencer with the expectation that
     * animations will be added via subsequent calls to {@link
     * #addAnimation}.
     */
    public AnimationSequencer ()
    {
        super(new Rectangle());
    }

    /**
     * Adds the supplied animation to the sequence with the given
     * parameters.  Note that this cannot be called after the animation
     * sequence has begun executing without endangering your sanity and
     * the robustness of your code.
     *
     * @param anim the animation to be sequenced, or null if the
     * completion action should be run immediately when this animation is
     * ready to fired.
     * @param delta the number of milliseconds following the start of the last
     * animation currently in the sequence that this animation should be
     * started; or -1 which means that this animation should be started
     * when the last animation has completed.
     * @param completionAction a runnable to be executed when this
     * animation completes.
     */
    public void addAnimation (
        Animation anim, long delta, Runnable completionAction)
    {
        // sanity check
        if (_finished) {
            throw new IllegalStateException(
                "Animation added to finished sequencer");
        }

        AnimRecord arec = new AnimRecord(anim, delta, completionAction);
        if (delta == -1) {
            int size = _queued.size();
            if (size == 0) {
                // if there's no predecessor then this guy has nobody to
                // wait for, so we run him immediately
                arec.delta = 0;
            } else {
                ((AnimRecord)_queued.get(size - 1)).dependent = arec;
            }
        }
        _queued.add(arec);
    }

    /**
     * Clears out the animations being managed by this sequencer.
     */
    public void clear ()
    {
        _queued.clear();
        _lastStamp = 0;
    }

    /**
     * Returns the number of animations being managed by this sequencer.
     */
    public int getAnimationCount ()
    {
        return _queued.size();
    }

    // documentation inherited
    public void tick (long tickStamp)
    {
        if (_lastStamp == 0) {
            _lastStamp = tickStamp;
        }

        // add all animations whose time has come
        while (_queued.size() > 0) {
            AnimRecord arec = (AnimRecord)_queued.get(0);
            if (!arec.readyToFire(tickStamp, _lastStamp)) {
                // if it's not time to add this animation, all subsequent
                // animations must surely wait as well
                break;
            }

            // remove it from queued and put it on the running list
            _queued.remove(0);
            _running.add(arec);

            // note that we've advanced to the next animation
            _lastStamp = tickStamp;

            // fire in the hole!
            arec.fire(tickStamp);
        }

        // we're done when both lists are empty
        _finished = ((_queued.size() + _running.size()) == 0);
    }

    /**
     * Called when the time comes to add an animation.  Derived classes
     * must implement this method and do whatever they deem necessary in
     * order to add the given animation to the animation manager and any
     * other interested parties.
     *
     * @param anim the animation to be added.
     * @param tickStamp the tick stamp provided by the animation manager
     * when the sequencer animation decided the time had come to add the
     * animation.
     */
    public abstract void addAnimation (Animation anim, long tickStamp);

    // documentation inherited
    public void paint (Graphics2D gfx)
    {
        // don't care
    }

    // documentation inherited
    public void fastForward (long timeDelta)
    {
        _lastStamp += timeDelta;
    }

    protected class AnimRecord
        implements AnimationObserver
    {
        public long delta;
        public AnimRecord dependent;

        public AnimRecord (
            Animation anim, long delta, Runnable completionAction)
        {
            _anim = anim;
            this.delta = delta;
            _completionAction = completionAction;
        }

        public boolean readyToFire (long now, long lastStamp)
        {
            return (delta != -1) && (lastStamp + delta >= now);
        }

        public void fire (long when)
        {
//             Log.info("Firing animation [anim=" + anim +
//                      ", tickStamp=" + tickStamp + "].");

            // if we have an animation, start it up and await its
            // completion
            if (_anim != null) {
                addAnimation(_anim, when);
                _anim.addAnimationObserver(this);

            } else {
                // since there's no animation, we need to fire our
                // completion routine immediately
                fireCompletion(when);
            }
        }

        public void fireCompletion (long when)
        {
            // call the completion action, if there is one
            if (_completionAction != null) {
                try {
                    _completionAction.run();
                } catch (Throwable t) {
                    Log.logStackTrace(t);
                }
            }

            // make a note that this animation is complete
            _running.remove(this);

            // if the next animation is triggered on the completion of
            // this animation...
            if (dependent != null) {
                // ...fiddle its delta so that it becomes immediately
                // ready to fire
                dependent.delta = when - _lastStamp;

                // kids, don't try this at home; we call tick()
                // immediately so that this dependent animation and
                // any simultaneous subsequent animations are fired
                // immediately rather than waiting for the next call
                // to tick
                tick(when);
            }
        }

        public void handleEvent (AnimationEvent event)
        {
            if (event instanceof AnimationCompletedEvent) {
                fireCompletion(event.getWhen());
            }
        }

        protected Animation _anim;
        protected Runnable _completionAction;
    }

    /** The animation records detailing the animations to be sequenced. */
    protected ArrayList _queued = new ArrayList();

    /** The animations that are currently running. */
    protected ArrayList _running = new ArrayList();

    /** The index of the last animation that was added. */
    protected int _lastidx = -1;

    /** The timestamp at which we added the last animation. */
    protected long _lastStamp;
}
