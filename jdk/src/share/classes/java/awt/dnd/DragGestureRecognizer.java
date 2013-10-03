/*
 * Copyright (c) 1998, 2006, Oracle and/or its affiliates. All rights reserved.
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

package java.awt.dnd;

import java.awt.event.InputEvent;
import java.awt.Component;
import java.awt.Point;

import java.util.TooManyListenersException;
import java.util.ArrayList;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * The <code>DragGestureRecognizer</code> is an
 * abstract base class for the specification
 * of a platform-dependent listener that can be associated with a particular
 * <code>Component</code> in order to
 * identify platform-dependent drag initiating gestures.
 * <p>
 * The appropriate <code>DragGestureRecognizer</code>
 * subclass instance is obtained from the
 * {@link DragSource} asssociated with
 * a particular <code>Component</code>, or from the <code>Toolkit</code> object via its
 * {@link java.awt.Toolkit#createDragGestureRecognizer createDragGestureRecognizer()}
 * method.
 * <p>
 * Once the <code>DragGestureRecognizer</code>
 * is associated with a particular <code>Component</code>
 * it will register the appropriate listener interfaces on that
 * <code>Component</code>
 * in order to track the input events delivered to the <code>Component</code>.
 * <p>
 * Once the <code>DragGestureRecognizer</code> identifies a sequence of events
 * on the <code>Component</code> as a drag initiating gesture, it will notify
 * its unicast <code>DragGestureListener</code> by
 * invoking its
 * {@link java.awt.dnd.DragGestureListener#dragGestureRecognized gestureRecognized()}
 * method.
 * <P>
 * When a concrete <code>DragGestureRecognizer</code>
 * instance detects a drag initiating
 * gesture on the <code>Component</code> it is associated with,
 * it fires a {@link DragGestureEvent} to
 * the <code>DragGestureListener</code> registered on
 * its unicast event source for <code>DragGestureListener</code>
 * events. This <code>DragGestureListener</code> is responsible
 * for causing the associated
 * <code>DragSource</code> to start the Drag and Drop operation (if
 * appropriate).
 * <P>
 * @author Laurence P. G. Cable
 * @see java.awt.dnd.DragGestureListener
 * @see java.awt.dnd.DragGestureEvent
 * @see java.awt.dnd.DragSource
 */

public abstract class DragGestureRecognizer implements Serializable {

    private static final long serialVersionUID = 8996673345831063337L;

    /**
     * Construct a new <code>DragGestureRecognizer</code>
     * given the <code>DragSource</code> to be used
     * in this Drag and Drop operation, the <code>Component</code>
     * this <code>DragGestureRecognizer</code> should "observe"
     * for drag initiating gestures, the action(s) supported
     * for this Drag and Drop operation, and the
     * <code>DragGestureListener</code> to notify
     * once a drag initiating gesture has been detected.
     * <P>
     * @param ds  the <code>DragSource</code> this
     * <code>DragGestureRecognizer</code>
     * will use to process the Drag and Drop operation
     *
     * @param c the <code>Component</code>
     * this <code>DragGestureRecognizer</code>
     * should "observe" the event stream to,
     * in order to detect a drag initiating gesture.
     * If this value is <code>null</code>, the
     * <code>DragGestureRecognizer</code>
     * is not associated with any <code>Component</code>.
     *
     * @param sa  the set (logical OR) of the
     * <code>DnDConstants</code>
     * that this Drag and Drop operation will support
     *
     * @param dgl the <code>DragGestureRecognizer</code>
     * to notify when a drag gesture is detected
     * <P>
     * @throws IllegalArgumentException
     * if ds is <code>null</code>.
     */

    protected DragGestureRecognizer(DragSource ds, Component c, int sa, DragGestureListener dgl) {
        super();

        if (ds == null) throw new IllegalArgumentException("null DragSource");

        dragSource    = ds;
        component     = c;
        sourceActions = sa & (DnDConstants.ACTION_COPY_OR_MOVE | DnDConstants.ACTION_LINK);

        try {
            if (dgl != null) addDragGestureListener(dgl);
        } catch (TooManyListenersException tmle) {
            // cant happen ...
        }
    }

    /**
     * Construct a new <code>DragGestureRecognizer</code>
     * given the <code>DragSource</code> to be used in this
     * Drag and Drop
     * operation, the <code>Component</code> this
     * <code>DragGestureRecognizer</code> should "observe"
     * for drag initiating gestures, and the action(s)
     * supported for this Drag and Drop operation.
     * <P>
     * @param ds  the <code>DragSource</code> this
     * <code>DragGestureRecognizer</code> will use to
     * process the Drag and Drop operation
     *
     * @param c   the <code>Component</code> this
     * <code>DragGestureRecognizer</code> should "observe" the event
     * stream to, in order to detect a drag initiating gesture.
     * If this value is <code>null</code>, the
     * <code>DragGestureRecognizer</code>
     * is not associated with any <code>Component</code>.
     *
     * @param sa the set (logical OR) of the <code>DnDConstants</code>
     * that this Drag and Drop operation will support
     * <P>
     * @throws IllegalArgumentException
     * if ds is <code>null</code>.
     */

    protected DragGestureRecognizer(DragSource ds, Component c, int sa) {
        this(ds, c, sa, null);
    }

    /**
     * Construct a new <code>DragGestureRecognizer</code>
     * given the <code>DragSource</code> to be used
     * in this Drag and Drop operation, and
     * the <code>Component</code> this
     * <code>DragGestureRecognizer</code>
     * should "observe" for drag initiating gestures.
     * <P>
     * @param ds the <code>DragSource</code> this
     * <code>DragGestureRecognizer</code>
     * will use to process the Drag and Drop operation
     *
     * @param c the <code>Component</code>
     * this <code>DragGestureRecognizer</code>
     * should "observe" the event stream to,
     * in order to detect a drag initiating gesture.
     * If this value is <code>null</code>,
     * the <code>DragGestureRecognizer</code>
     * is not associated with any <code>Component</code>.
     * <P>
     * @throws IllegalArgumentException
     * if ds is <code>null</code>.
     */

    protected DragGestureRecognizer(DragSource ds, Component c) {
        this(ds, c, DnDConstants.ACTION_NONE);
    }

    /**
     * Construct a new <code>DragGestureRecognizer</code>
     * given the <code>DragSource</code> to be used in this
     * Drag and Drop operation.
     * <P>
     * @param ds the <code>DragSource</code> this
     * <code>DragGestureRecognizer</code> will
     * use to process the Drag and Drop operation
     * <P>
     * @throws IllegalArgumentException
     * if ds is <code>null</code>.
     */

    protected DragGestureRecognizer(DragSource ds) {
        this(ds, null);
    }

    /**
     * register this DragGestureRecognizer's Listeners with the Component
     *
     * subclasses must override this method
     */

    protected abstract void registerListeners();

    /**
     * unregister this DragGestureRecognizer's Listeners with the Component
     *
     * subclasses must override this method
     */

    protected abstract void unregisterListeners();

    /**
     * This method returns the <code>DragSource</code>
     * this <code>DragGestureRecognizer</code>
     * will use in order to process the Drag and Drop
     * operation.
     * <P>
     * @return the DragSource
     */

    public DragSource getDragSource() { return dragSource; }

    /**
     * This method returns the <code>Component</code>
     * that is to be "observed" by the
     * <code>DragGestureRecognizer</code>
     * for drag initiating gestures.
     * <P>
     * @return The Component this DragGestureRecognizer
     * is associated with
     */

    public synchronized Component getComponent() { return component; }

    /**
     * set the Component that the DragGestureRecognizer is associated with
     *
     * registerListeners() and unregisterListeners() are called as a side
     * effect as appropriate.
     * <P>
     * @param c The <code>Component</code> or <code>null</code>
     */

    public synchronized void setComponent(Component c) {
        if (component != null && dragGestureListener != null)
            unregisterListeners();

        component = c;

        if (component != null && dragGestureListener != null)
            registerListeners();
    }

    /**
     * This method returns an int representing the
     * type of action(s) this Drag and Drop
     * operation will support.
     * <P>
     * @return the currently permitted source action(s)
     */

    public synchronized int getSourceActions() { return sourceActions; }

    /**
     * This method sets the permitted source drag action(s)
     * for this Drag and Drop operation.
     * <P>
     * @param actions the permitted source drag action(s)
     */

    public synchronized void setSourceActions(int actions) {
        sourceActions = actions & (DnDConstants.ACTION_COPY_OR_MOVE | DnDConstants.ACTION_LINK);
    }

    /**
     * This method returns the first event in the
     * series of events that initiated
     * the Drag and Drop operation.
     * <P>
     * @return the initial event that triggered the drag gesture
     */

    public InputEvent getTriggerEvent() { return events.isEmpty() ? null : events.get(0); }

    /**
     * Reset the Recognizer, if its currently recognizing a gesture, ignore
     * it.
     */

    public void resetRecognizer() { events.clear(); }

    /**
     * Register a new <code>DragGestureListener</code>.
     * <P>
     * @param dgl the <code>DragGestureListener</code> to register
     * with this <code>DragGestureRecognizer</code>.
     * <P>
     * @throws java.util.TooManyListenersException if a
     * <code>DragGestureListener</code> has already been added.
     */

    public synchronized void addDragGestureListener(DragGestureListener dgl) throws TooManyListenersException {
        if (dragGestureListener != null)
            throw new TooManyListenersException();
        else {
            dragGestureListener = dgl;

            if (component != null) registerListeners();
        }
    }

    /**
     * unregister the current DragGestureListener
     * <P>
     * @param dgl the <code>DragGestureListener</code> to unregister
     * from this <code>DragGestureRecognizer</code>
     * <P>
     * @throws IllegalArgumentException if
     * dgl is not (equal to) the currently registered <code>DragGestureListener</code>.
     */

    public synchronized void removeDragGestureListener(DragGestureListener dgl) {
        if (dragGestureListener == null || !dragGestureListener.equals(dgl))
            throw new IllegalArgumentException();
        else {
            dragGestureListener = null;

            if (component != null) unregisterListeners();
        }
    }

    /**
     * Notify the DragGestureListener that a Drag and Drop initiating
     * gesture has occurred. Then reset the state of the Recognizer.
     * <P>
     * @param dragAction The action initially selected by the users gesture
     * @param p          The point (in Component coords) where the gesture originated
     */
    protected synchronized void fireDragGestureRecognized(int dragAction, Point p) {
        try {
            if (dragGestureListener != null) {
                dragGestureListener.dragGestureRecognized(new DragGestureEvent(this, dragAction, p, events));
            }
        } finally {
            events.clear();
        }
    }

    /**
     * Listeners registered on the Component by this Recognizer shall record
     * all Events that are recognized as part of the series of Events that go
     * to comprise a Drag and Drop initiating gesture via this API.
     *<P>
     * This method is used by a <code>DragGestureRecognizer</code>
     * implementation to add an <code>InputEvent</code>
     * subclass (that it believes is one in a series
     * of events that comprise a Drag and Drop operation)
     * to the array of events that this
     * <code>DragGestureRecognizer</code> maintains internally.
     * <P>
     * @param awtie the <code>InputEvent</code>
     * to add to this <code>DragGestureRecognizer</code>'s
     * internal array of events. Note that <code>null</code>
     * is not a valid value, and will be ignored.
     */

    protected synchronized void appendEvent(InputEvent awtie) {
        events.add(awtie);
    }

    /**
     * Serializes this <code>DragGestureRecognizer</code>. This method first
     * performs default serialization. Then, this object's
     * <code>DragGestureListener</code> is written out if and only if it can be
     * serialized. If not, <code>null</code> is written instead.
     *
     * @serialData The default serializable fields, in alphabetical order,
     *             followed by either a <code>DragGestureListener</code>, or
     *             <code>null</code>.
     * @since 1.4
     */
    private void writeObject(ObjectOutputStream s) throws IOException {
        s.defaultWriteObject();

        s.writeObject(SerializationTester.test(dragGestureListener)
                      ? dragGestureListener : null);
    }

    /**
     * Deserializes this <code>DragGestureRecognizer</code>. This method first
     * performs default deserialization for all non-<code>transient</code>
     * fields. This object's <code>DragGestureListener</code> is then
     * deserialized as well by using the next object in the stream.
     *
     * @since 1.4
     */
    private void readObject(ObjectInputStream s)
        throws ClassNotFoundException, IOException
    {
        s.defaultReadObject();

        dragGestureListener = (DragGestureListener)s.readObject();
    }

    /*
     * fields
     */

    /**
     * The <code>DragSource</code>
     * associated with this
     * <code>DragGestureRecognizer</code>.
     *
     * @serial
     */
    protected DragSource          dragSource;

    /**
     * The <code>Component</code>
     * associated with this <code>DragGestureRecognizer</code>.
     *
     * @serial
     */
    protected Component           component;

    /**
     * The <code>DragGestureListener</code>
     * associated with this <code>DragGestureRecognizer</code>.
     */
    protected transient DragGestureListener dragGestureListener;

  /**
   * An <code>int</code> representing
   * the type(s) of action(s) used
   * in this Drag and Drop operation.
   *
   * @serial
   */
  protected int  sourceActions;

   /**
    * The list of events (in order) that
    * the <code>DragGestureRecognizer</code>
    * "recognized" as a "gesture" that triggers a drag.
    *
    * @serial
    */
   protected ArrayList<InputEvent> events = new ArrayList<InputEvent>(1);
}
