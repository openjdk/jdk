/*
 * Copyright (c) 2002, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.java.accessibility.util;

/**
 * EventID contains integer constants that map to event support in
 * AWT and Swing.  They are used by primarily by AWTEventMonitor,
 * AWTEventsListener, SwingEventMonitor, and SwingEventListener, but
 * can be freely used by any other class.
 *
 * @see AWTEventMonitor
 * @see SwingEventMonitor
 *
 */
public class EventID {

    /**
     * Constructs an {@code EventID}.
     */
    public EventID() {}

    /**
     * Maps to AWT Action support (i.e., ActionListener and ActionEvent)
     */
    public static final int ACTION              = 0;

    /**
     * Maps to AWT Adjustment support (i.e., AdjustmentListener
     * and AdjustmentEvent)
     */
    public static final int ADJUSTMENT          = 1;

    /**
     * Maps to AWT Component support (i.e., ComponentListener
     * and ComponentEvent)
     */
    public static final int COMPONENT           = 2;

    /**
     * Maps to AWT Container support (i.e., ContainerListener
     * and ContainerEvent)
     */
    public static final int CONTAINER           = 3;

    /**
     * Maps to AWT Focus support (i.e., FocusListener and FocusEvent)
     */
    public static final int FOCUS               = 4;

    /**
     * Maps to AWT Item support (i.e., ItemListener and ItemEvent)
     */
    public static final int ITEM                = 5;

    /**
     * Maps to AWT Key support (i.e., KeyListener and KeyEvent)
     */
    public static final int KEY                 = 6;

    /**
     * Maps to AWT Mouse support (i.e., MouseListener and MouseEvent)
     */
    public static final int MOUSE               = 7;

    /**
     * Maps to AWT MouseMotion support (i.e., MouseMotionListener
     * and MouseMotionEvent)
     */
    public static final int MOTION              = 8;

    /**
     * Maps to AWT Text support (i.e., TextListener and TextEvent)
     */
    public static final int TEXT                = 10;

    /**
     * Maps to AWT Window support (i.e., WindowListener and WindowEvent)
     */
    public static final int WINDOW              = 11;

    /**
     * Maps to Swing Ancestor support (i.e., AncestorListener and
     * AncestorEvent)
     */
    public static final int ANCESTOR           = 12;

    /**
     * Maps to Swing Text Caret support (i.e., CaretListener and
     * CaretEvent)
     */
    public static final int CARET              = 13;

    /**
     * Maps to Swing CellEditor support (i.e., CellEditorListener and
     * CellEditorEvent)
     */
    public static final int CELLEDITOR         = 14;

    /**
     * Maps to Swing Change support (i.e., ChangeListener and
     * ChangeEvent)
     */
    public static final int CHANGE             = 15;

    /**
     * Maps to Swing TableColumnModel support (i.e.,
     * TableColumnModelListener and TableColumnModelEvent)
     */
    public static final int COLUMNMODEL        = 16;

    /**
     * Maps to Swing Document support (i.e., DocumentListener and
     * DocumentEvent)
     */
    public static final int DOCUMENT           = 17;

    /**
     * Maps to Swing ListData support (i.e., ListDataListener and
     * ListDataEvent)
     */
    public static final int LISTDATA           = 18;

    /**
     * Maps to Swing ListSelection support (i.e., ListSelectionListener and
     * ListSelectionEvent)
     */
    public static final int LISTSELECTION      = 19;

    /**
     * Maps to Swing Menu support (i.e., MenuListener and
     * MenuEvent)
     */
    public static final int MENU               = 20;

    /**
     * Maps to Swing PopupMenu support (i.e., PopupMenuListener and
     * PopupMenuEvent)
     */
    public static final int POPUPMENU          = 21;

    /**
     * Maps to Swing TableModel support (i.e., TableModelListener and
     * TableModelEvent)
     */
    public static final int TABLEMODEL         = 22;

    /**
     * Maps to Swing TreeExpansion support (i.e., TreeExpansionListener and
     * TreeExpansionEvent)
     */
    public static final int TREEEXPANSION      = 23;

    /**
     * Maps to Swing TreeModel support (i.e., TreeModelListener and
     * TreeModelEvent)
     */
    public static final int TREEMODEL          = 24;

    /**
     * Maps to Swing TreeSelection support (i.e., TreeSelectionListener and
     * TreeSelectionEvent)
     */
    public static final int TREESELECTION      = 25;

    /**
     * Maps to Swing UndoableEdit support (i.e., UndoableEditListener and
     * UndoableEditEvent)
     */
    public static final int UNDOABLEEDIT       = 26;

    /**
     * Maps to Beans PropertyChange support (i.e., PropertyChangeListener
     * and PropertyChangeEvent)
     */
    public static final int PROPERTYCHANGE     = 27;

    /**
     * Maps to Beans VetoableChange support (i.e., VetoableChangeListener
     * and VetoableChangeEvent)
     */
    public static final int VETOABLECHANGE     = 28;

    /**
     * Maps to Swing InternalFrame support (i.e., InternalFrameListener)
     */
    public static final int INTERNALFRAME      = 29;
}
