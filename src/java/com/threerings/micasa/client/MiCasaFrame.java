//
// $Id: MiCasaFrame.java,v 1.7 2002/07/12 17:06:52 mdb Exp $

package com.threerings.micasa.client;

import java.awt.BorderLayout;

import javax.swing.JFrame;
import javax.swing.JPanel;

import com.samskivert.swing.Controller;
import com.samskivert.swing.ControllerProvider;

/**
 * Contains the user interface for the MiCasa client application.
 */
public class MiCasaFrame
    extends JFrame implements ControllerProvider
{
    /**
     * Constructs the top-level MiCasa client frame.
     */
    public MiCasaFrame ()
    {
        this("MiCasa Client");
    }

    /**
     * Constructs the top-level MiCasa client frame with the specified
     * window title.
     */
    public MiCasaFrame (String title)
    {
        super(title);

        // we'll handle shutting things down ourselves
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    }

    /**
     * Sets the panel that makes up the entire client display.
     */
    public void setPanel (JPanel panel)
    {
        // remove the old panel
        getContentPane().removeAll();
	// add the new one
	getContentPane().add(panel, BorderLayout.CENTER);
        // swing doesn't properly repaint after adding/removing children
        panel.revalidate();
        repaint();
    }

    /**
     * Sets the controller for the outermost scope. This controller will
     * handle all actions that aren't handled by controllers of tigher
     * scope.
     */
    public void setController (Controller controller)
    {
        _controller = controller;
    }

    // documentation inherited
    public Controller getController ()
    {
        return _controller;
    }

    protected Controller _controller;
}
