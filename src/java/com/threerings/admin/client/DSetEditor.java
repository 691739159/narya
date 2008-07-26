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

package com.threerings.admin.client;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

import com.samskivert.swing.ObjectEditorTable;
import com.samskivert.swing.event.CommandEvent;

import com.samskivert.util.ComparableArrayList;

import com.threerings.presents.dobj.AttributeChangeListener;
import com.threerings.presents.dobj.AttributeChangedEvent;
import com.threerings.presents.dobj.DObject;
import com.threerings.presents.dobj.DSet;
import com.threerings.presents.dobj.EntryAddedEvent;
import com.threerings.presents.dobj.EntryRemovedEvent;
import com.threerings.presents.dobj.EntryUpdatedEvent;
import com.threerings.presents.dobj.SetListener;

/**
 * Allows simple editing of DSets within a distributed object.
 */
public class DSetEditor<E extends DSet.Entry> extends JPanel
    implements AttributeChangeListener, SetListener<E>, ActionListener
{
    /**
     * Construct a DSet editor to merely display the specified set.
     *
     * @param setter The object that contains the set.
     * @param setName The name of the set in the object.
     * @param entryClass the Class of the DSet.Entry elements contained in the set.
     */
    public DSetEditor (DObject setter, String setName, Class entryClass)
    {
        this(setter, setName, entryClass, null);
    }

    /**
     * Construct a DSetEditor, allowing the specified fields to be edited.
     *
     * @param setter The object that contains the set.
     * @param setName The name of the set in the object.
     * @param entryClass the Class of the DSet.Entry elements contained in the set.
     * @param editableFields the names of the fields in the entryClass that should be editable.
     */
    public DSetEditor (DObject setter, String setName, Class entryClass,
                       String[] editableFields)
    {
        this(setter, setName, entryClass, editableFields, null);
    }

    /**
     * Construct a DSetEditor with a custom FieldInterpreter.
     *
     * @param setter The object that contains the set.
     * @param setName The name of the set in the object.
     * @param entryClass the Class of the DSet.Entry elements contained in the set.
     * @param editableFields the names of the fields in the entryClass that should be editable.
     * @param interp The FieldInterpreter to use.
     */
    public DSetEditor (DObject setter, String setName, Class entryClass,
                       String[] editableFields, ObjectEditorTable.FieldInterpreter interp)
    {
        super(new BorderLayout());

        _setter = setter;
        _setName = setName;
        _set = _setter.getSet(setName);

        _table = new ObjectEditorTable(entryClass, editableFields, interp);

        add(new JScrollPane(_table), BorderLayout.CENTER);
    }

    /**
     * Get the table being used to display the set.
     */
    public JTable getTable ()
    {
        return _table;
    }

    /**
     * Get the currently selected entry.
     */
    public DSet.Entry getSelectedEntry ()
    {
        return (DSet.Entry)_table.getSelectedObject();
    }

    @Override
    public Dimension getPreferredSize ()
    {
        Dimension d = super.getPreferredSize();
        d.height = Math.min(d.height, MIN_HEIGHT);
        return d;
    }

    @Override
    public void addNotify ()
    {
        super.addNotify();
        _setter.addListener(this);
        _table.addActionListener(this);

        // populate the table
        refreshData();
    }

    @Override
    public void removeNotify ()
    {
        _setter.removeListener(this);
        _table.removeActionListener(this);
        super.removeNotify();
    }

    // documentation inherited from interface SetListener
    public void entryAdded (EntryAddedEvent<E> event)
    {
        if (event.getName().equals(_setName)) {
            E entry = event.getEntry();
            @SuppressWarnings("unchecked") Comparable<Object> key = entry.getKey();
            int index = _keys.insertSorted(key);
            _table.insertDatum(entry, index);
        }
    }

    // documentation inherited from interface SetListener
    public void entryRemoved (EntryRemovedEvent<E> event)
    {
        if (event.getName().equals(_setName)) {
            Comparable key = event.getKey();
            int index = _keys.indexOf(key);
            _keys.remove(index);
            _table.removeDatum(index);
        }
    }

    // documentation inherited from interface SetListener
    public void entryUpdated (EntryUpdatedEvent<E> event)
    {
        if (event.getName().equals(_setName)) {
            E entry = event.getEntry();
            int index = _keys.indexOf(entry.getKey());
            _table.updateDatum(entry, index);
        }
    }

    // documentation inherited from interface SetListener
    public void attributeChanged (AttributeChangedEvent event)
    {
        if (event.getName().equals(_setName)) {
            // the whole set changed so we need to refetch it from the object
            _set = _setter.getSet(_setName);
            refreshData();
        }
    }

    // documentation inherited from interface ActionListener
    public void actionPerformed (ActionEvent event)
    {
        CommandEvent ce = (CommandEvent)event;
        _setter.updateSet(_setName, (DSet.Entry)ce.getArgument());
    }

    protected void refreshData ()
    {
        _keys = new ComparableArrayList<Comparable<Object>>();
        @SuppressWarnings("unchecked") E[] entries =  (E[])new DSet.Entry[_set.size()];
        _set.toArray(entries);
        for (int ii = 0; ii < entries.length; ii++) {
            @SuppressWarnings("unchecked") Comparable<Object> key = entries[ii].getKey();
            _keys.insertSorted(key);
        }
        _table.setData(entries); // this works because DSet itself is sorted
    }

    /** The object that contains the set we're displaying. */
    protected DObject _setter;

    /** The name of the set in that object. */
    protected String _setName;

    /** The set itself. */
    protected DSet<E> _set;

    /** An array we use to track our entries' positions by key. */
    protected ComparableArrayList<Comparable<Object>> _keys;

    /** The table used to edit. */
    protected ObjectEditorTable _table;

    /** The minimum height for our editor UI. */
    protected static final int MIN_HEIGHT = 200;
}
