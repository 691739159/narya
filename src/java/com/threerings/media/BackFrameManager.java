//
// $Id: BackFrameManager.java,v 1.1 2003/04/26 17:56:26 mdb Exp $

package com.threerings.media;

import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Rectangle;
import java.awt.image.VolatileImage;
import javax.swing.JFrame;

import com.samskivert.util.StringUtil;

import com.threerings.media.timer.MediaTimer;

/**
 * A {@link FrameManager} extension that uses a volatile off-screen image
 * to do its rendering.
 */
public class BackFrameManager extends FrameManager
{
    // documentation inherited
    protected void paint (long tickStamp)
    {
        // start out assuming we can do an incremental render
        boolean incremental = true;

        do {
            GraphicsConfiguration gc = _frame.getGraphicsConfiguration();

            // create our off-screen buffer if necessary
            if (_backimg == null || _backimg.getWidth() != _frame.getWidth() ||
                _backimg.getHeight() != _frame.getHeight()) {
                createBackBuffer(gc);
            }

            // make sure our back buffer hasn't disappeared
            int valres = _backimg.validate(gc);

            // if we've changed resolutions, recreate the buffer
            if (valres == VolatileImage.IMAGE_INCOMPATIBLE) {
                Log.info("Back buffer incompatible, recreating.");
                createBackBuffer(gc);
            }

            // if the image wasn't A-OK, we need to rerender the whole
            // business rather than just the dirty parts
            if (valres != VolatileImage.IMAGE_OK) {
                Log.info("Lost back buffer, redrawing.");
                incremental = false;
            }

            // dirty everything if we're not incrementally rendering
            if (!incremental) {
                _frame.update(_bgfx);
            }

            if (!paint(_bgfx)) {
                return;
            }

            // we cache our frame's graphics object so that we can avoid
            // instantiating a new one on every tick
            if (_fgfx == null) {
                _fgfx = (Graphics2D)_frame.getGraphics();
            }
            _fgfx.drawImage(_backimg, 0, 0, null);

            // if we loop through a second time, we'll need to rerender
            // everything
            incremental = false;

        } while (_backimg.contentsLost());
    }

    // documentation inherited
    protected void restoreFromBack (Rectangle dirty)
    {
        if (_fgfx == null || _backimg == null) {
            return;
        }
        Log.info("Restoring from back " + StringUtil.toString(dirty) + ".");
        _fgfx.setClip(dirty);
        _fgfx.drawImage(_backimg, 0, 0, null);
        _fgfx.setClip(null);
    }

    /**
     * Creates the off-screen buffer used to perform double buffered
     * rendering of the animated panel.
     */
    protected void createBackBuffer (GraphicsConfiguration gc)
    {
        // if we have an old image, clear it out
        if (_backimg != null) {
            _backimg.flush();
            _bgfx.dispose();
        }

        // create the offscreen buffer
        int width = _frame.getWidth(), height = _frame.getHeight();
        _backimg = gc.createCompatibleVolatileImage(width, height);

        // fill the back buffer with white
        _bgfx = (Graphics2D)_backimg.getGraphics();
        _bgfx.fillRect(0, 0, width, height);

        // clear out our frame graphics in case that became invalid for
        // the same reasons our back buffer became invalid
        if (_fgfx != null) {
            _fgfx.dispose();
            _fgfx = null;
        }

//         Log.info("Created back buffer [" + width + "x" + height + "].");
    }

    /** The image used to render off-screen. */
    protected VolatileImage _backimg;

    /** The graphics object from our back buffer. */
    protected Graphics2D _bgfx;

    /** The graphics object from our frame. */
    protected Graphics2D _fgfx;
}
