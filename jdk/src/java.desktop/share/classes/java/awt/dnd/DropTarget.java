/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.util.TooManyListenersException;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.datatransfer.FlavorMap;
import java.awt.datatransfer.SystemFlavorMap;
import javax.swing.Timer;
import java.awt.peer.ComponentPeer;
import java.awt.peer.LightweightPeer;
import java.awt.dnd.peer.DropTargetPeer;


/**
 * The <code>DropTarget</code> is associated
 * with a <code>Component</code> when that <code>Component</code>
 * wishes
 * to accept drops during Drag and Drop operations.
 * <P>
 *  Each
 * <code>DropTarget</code> is associated with a <code>FlavorMap</code>.
 * The default <code>FlavorMap</code> hereafter designates the
 * <code>FlavorMap</code> returned by <code>SystemFlavorMap.getDefaultFlavorMap()</code>.
 *
 * @since 1.2
 */

public class DropTarget implements DropTargetListener, Serializable {

    private static final long serialVersionUID = -6283860791671019047L;

    /**
     * Creates a new DropTarget given the <code>Component</code>
     * to associate itself with, an <code>int</code> representing
     * the default acceptable action(s) to
     * support, a <code>DropTargetListener</code>
     * to handle event processing, a <code>boolean</code> indicating
     * if the <code>DropTarget</code> is currently accepting drops, and
     * a <code>FlavorMap</code> to use (or null for the default <CODE>FlavorMap</CODE>).
     * <P>
     * The Component will receive drops only if it is enabled.
     * @param c         The <code>Component</code> with which this <code>DropTarget</code> is associated
     * @param ops       The default acceptable actions for this <code>DropTarget</code>
     * @param dtl       The <code>DropTargetListener</code> for this <code>DropTarget</code>
     * @param act       Is the <code>DropTarget</code> accepting drops.
     * @param fm        The <code>FlavorMap</code> to use, or null for the default <CODE>FlavorMap</CODE>
     * @exception HeadlessException if GraphicsEnvironment.isHeadless()
     *            returns true
     * @see java.awt.GraphicsEnvironment#isHeadless
     */
    public DropTarget(Component c, int ops, DropTargetListener dtl,
                      boolean act, FlavorMap fm)
        throws HeadlessException
    {
        if (GraphicsEnvironment.isHeadless()) {
            throw new HeadlessException();
        }

        component = c;

        setDefaultActions(ops);

        if (dtl != null) try {
            addDropTargetListener(dtl);
        } catch (TooManyListenersException tmle) {
            // do nothing!
        }

        if (c != null) {
            c.setDropTarget(this);
            setActive(act);
        }

        if (fm != null) {
            flavorMap = fm;
        } else {
            flavorMap = SystemFlavorMap.getDefaultFlavorMap();
        }
    }

    /**
     * Creates a <code>DropTarget</code> given the <code>Component</code>
     * to associate itself with, an <code>int</code> representing
     * the default acceptable action(s)
     * to support, a <code>DropTargetListener</code>
     * to handle event processing, and a <code>boolean</code> indicating
     * if the <code>DropTarget</code> is currently accepting drops.
     * <P>
     * The Component will receive drops only if it is enabled.
     * @param c         The <code>Component</code> with which this <code>DropTarget</code> is associated
     * @param ops       The default acceptable actions for this <code>DropTarget</code>
     * @param dtl       The <code>DropTargetListener</code> for this <code>DropTarget</code>
     * @param act       Is the <code>DropTarget</code> accepting drops.
     * @exception HeadlessException if GraphicsEnvironment.isHeadless()
     *            returns true
     * @see java.awt.GraphicsEnvironment#isHeadless
     */
    public DropTarget(Component c, int ops, DropTargetListener dtl,
                      boolean act)
        throws HeadlessException
    {
        this(c, ops, dtl, act, null);
    }

    /**
     * Creates a <code>DropTarget</code>.
     * @exception HeadlessException if GraphicsEnvironment.isHeadless()
     *            returns true
     * @see java.awt.GraphicsEnvironment#isHeadless
     */
    public DropTarget() throws HeadlessException {
        this(null, DnDConstants.ACTION_COPY_OR_MOVE, null, true, null);
    }

    /**
     * Creates a <code>DropTarget</code> given the <code>Component</code>
     * to associate itself with, and the <code>DropTargetListener</code>
     * to handle event processing.
     * <P>
     * The Component will receive drops only if it is enabled.
     * @param c         The <code>Component</code> with which this <code>DropTarget</code> is associated
     * @param dtl       The <code>DropTargetListener</code> for this <code>DropTarget</code>
     * @exception HeadlessException if GraphicsEnvironment.isHeadless()
     *            returns true
     * @see java.awt.GraphicsEnvironment#isHeadless
     */
    public DropTarget(Component c, DropTargetListener dtl)
        throws HeadlessException
    {
        this(c, DnDConstants.ACTION_COPY_OR_MOVE, dtl, true, null);
    }

    /**
     * Creates a <code>DropTarget</code> given the <code>Component</code>
     * to associate itself with, an <code>int</code> representing
     * the default acceptable action(s) to support, and a
     * <code>DropTargetListener</code> to handle event processing.
     * <P>
     * The Component will receive drops only if it is enabled.
     * @param c         The <code>Component</code> with which this <code>DropTarget</code> is associated
     * @param ops       The default acceptable actions for this <code>DropTarget</code>
     * @param dtl       The <code>DropTargetListener</code> for this <code>DropTarget</code>
     * @exception HeadlessException if GraphicsEnvironment.isHeadless()
     *            returns true
     * @see java.awt.GraphicsEnvironment#isHeadless
     */
    public DropTarget(Component c, int ops, DropTargetListener dtl)
        throws HeadlessException
    {
        this(c, ops, dtl, true);
    }

    /**
     * Note: this interface is required to permit the safe association
     * of a DropTarget with a Component in one of two ways, either:
     * <code> component.setDropTarget(droptarget); </code>
     * or <code> droptarget.setComponent(component); </code>
     * <P>
     * The Component will receive drops only if it is enabled.
     * @param c The new <code>Component</code> this <code>DropTarget</code>
     * is to be associated with.
     */

    public synchronized void setComponent(Component c) {
        if (component == c || component != null && component.equals(c))
            return;

        Component     old;
        ComponentPeer oldPeer = null;

        if ((old = component) != null) {
            clearAutoscroll();

            component = null;

            if (componentPeer != null) {
                oldPeer = componentPeer;
                removeNotify(componentPeer);
            }

            old.setDropTarget(null);

        }

        if ((component = c) != null) try {
            c.setDropTarget(this);
        } catch (Exception e) { // undo the change
            if (old != null) {
                old.setDropTarget(this);
                addNotify(oldPeer);
            }
        }
    }

    /**
     * Gets the <code>Component</code> associated
     * with this <code>DropTarget</code>.
     *
     * @return the current <code>Component</code>
     */

    public synchronized Component getComponent() {
        return component;
    }

    /**
     * Sets the default acceptable actions for this <code>DropTarget</code>
     *
     * @param ops the default actions
     * @see java.awt.dnd.DnDConstants
     */

    public void setDefaultActions(int ops) {
        getDropTargetContext().setTargetActions(ops & (DnDConstants.ACTION_COPY_OR_MOVE | DnDConstants.ACTION_REFERENCE));
    }

    /*
     * Called by DropTargetContext.setTargetActions()
     * with appropriate synchronization.
     */
    void doSetDefaultActions(int ops) {
        actions = ops;
    }

    /**
     * Gets an <code>int</code> representing the
     * current action(s) supported by this <code>DropTarget</code>.
     *
     * @return the current default actions
     */

    public int getDefaultActions() {
        return actions;
    }

    /**
     * Sets the DropTarget active if <code>true</code>,
     * inactive if <code>false</code>.
     *
     * @param isActive sets the <code>DropTarget</code> (in)active.
     */

    public synchronized void setActive(boolean isActive) {
        if (isActive != active) {
            active = isActive;
        }

        if (!active) clearAutoscroll();
    }

    /**
     * Reports whether or not
     * this <code>DropTarget</code>
     * is currently active (ready to accept drops).
     *
     * @return <CODE>true</CODE> if active, <CODE>false</CODE> if not
     */

    public boolean isActive() {
        return active;
    }

    /**
     * Adds a new <code>DropTargetListener</code> (UNICAST SOURCE).
     *
     * @param dtl The new <code>DropTargetListener</code>
     *
     * @throws TooManyListenersException if a
     * <code>DropTargetListener</code> is already added to this
     * <code>DropTarget</code>.
     */

    public synchronized void addDropTargetListener(DropTargetListener dtl) throws TooManyListenersException {
        if (dtl == null) return;

        if (equals(dtl)) throw new IllegalArgumentException("DropTarget may not be its own Listener");

        if (dtListener == null)
            dtListener = dtl;
        else
            throw new TooManyListenersException();
    }

    /**
     * Removes the current <code>DropTargetListener</code> (UNICAST SOURCE).
     *
     * @param dtl the DropTargetListener to deregister.
     */

    public synchronized void removeDropTargetListener(DropTargetListener dtl) {
        if (dtl != null && dtListener != null) {
            if(dtListener.equals(dtl))
                dtListener = null;
            else
                throw new IllegalArgumentException("listener mismatch");
        }
    }

    /**
     * Calls <code>dragEnter</code> on the registered
     * <code>DropTargetListener</code> and passes it
     * the specified <code>DropTargetDragEvent</code>.
     * Has no effect if this <code>DropTarget</code>
     * is not active.
     *
     * @param dtde the <code>DropTargetDragEvent</code>
     *
     * @throws NullPointerException if this <code>DropTarget</code>
     *         is active and <code>dtde</code> is <code>null</code>
     *
     * @see #isActive
     */
    public synchronized void dragEnter(DropTargetDragEvent dtde) {
        isDraggingInside = true;

        if (!active) return;

        if (dtListener != null) {
            dtListener.dragEnter(dtde);
        } else
            dtde.getDropTargetContext().setTargetActions(DnDConstants.ACTION_NONE);

        initializeAutoscrolling(dtde.getLocation());
    }

    /**
     * Calls <code>dragOver</code> on the registered
     * <code>DropTargetListener</code> and passes it
     * the specified <code>DropTargetDragEvent</code>.
     * Has no effect if this <code>DropTarget</code>
     * is not active.
     *
     * @param dtde the <code>DropTargetDragEvent</code>
     *
     * @throws NullPointerException if this <code>DropTarget</code>
     *         is active and <code>dtde</code> is <code>null</code>
     *
     * @see #isActive
     */
    public synchronized void dragOver(DropTargetDragEvent dtde) {
        if (!active) return;

        if (dtListener != null && active) dtListener.dragOver(dtde);

        updateAutoscroll(dtde.getLocation());
    }

    /**
     * Calls <code>dropActionChanged</code> on the registered
     * <code>DropTargetListener</code> and passes it
     * the specified <code>DropTargetDragEvent</code>.
     * Has no effect if this <code>DropTarget</code>
     * is not active.
     *
     * @param dtde the <code>DropTargetDragEvent</code>
     *
     * @throws NullPointerException if this <code>DropTarget</code>
     *         is active and <code>dtde</code> is <code>null</code>
     *
     * @see #isActive
     */
    public synchronized void dropActionChanged(DropTargetDragEvent dtde) {
        if (!active) return;

        if (dtListener != null) dtListener.dropActionChanged(dtde);

        updateAutoscroll(dtde.getLocation());
    }

    /**
     * Calls <code>dragExit</code> on the registered
     * <code>DropTargetListener</code> and passes it
     * the specified <code>DropTargetEvent</code>.
     * Has no effect if this <code>DropTarget</code>
     * is not active.
     * <p>
     * This method itself does not throw any exception
     * for null parameter but for exceptions thrown by
     * the respective method of the listener.
     *
     * @param dte the <code>DropTargetEvent</code>
     *
     * @see #isActive
     */
    public synchronized void dragExit(DropTargetEvent dte) {
        isDraggingInside = false;

        if (!active) return;

        if (dtListener != null && active) dtListener.dragExit(dte);

        clearAutoscroll();
    }

    /**
     * Calls <code>drop</code> on the registered
     * <code>DropTargetListener</code> and passes it
     * the specified <code>DropTargetDropEvent</code>
     * if this <code>DropTarget</code> is active.
     *
     * @param dtde the <code>DropTargetDropEvent</code>
     *
     * @throws NullPointerException if <code>dtde</code> is null
     *         and at least one of the following is true: this
     *         <code>DropTarget</code> is not active, or there is
     *         no a <code>DropTargetListener</code> registered.
     *
     * @see #isActive
     */
    public synchronized void drop(DropTargetDropEvent dtde) {
        isDraggingInside = false;

        clearAutoscroll();

        if (dtListener != null && active)
            dtListener.drop(dtde);
        else { // we shouldn't get here ...
            dtde.rejectDrop();
        }
    }

    /**
     * Gets the <code>FlavorMap</code>
     * associated with this <code>DropTarget</code>.
     * If no <code>FlavorMap</code> has been set for this
     * <code>DropTarget</code>, it is associated with the default
     * <code>FlavorMap</code>.
     *
     * @return the FlavorMap for this DropTarget
     */

    public FlavorMap getFlavorMap() { return flavorMap; }

    /**
     * Sets the <code>FlavorMap</code> associated
     * with this <code>DropTarget</code>.
     *
     * @param fm the new <code>FlavorMap</code>, or null to
     * associate the default FlavorMap with this DropTarget.
     */

    public void setFlavorMap(FlavorMap fm) {
        flavorMap = fm == null ? SystemFlavorMap.getDefaultFlavorMap() : fm;
    }

    /**
     * Notify the DropTarget that it has been associated with a Component
     *
     **********************************************************************
     * This method is usually called from java.awt.Component.addNotify() of
     * the Component associated with this DropTarget to notify the DropTarget
     * that a ComponentPeer has been associated with that Component.
     *
     * Calling this method, other than to notify this DropTarget of the
     * association of the ComponentPeer with the Component may result in
     * a malfunction of the DnD system.
     **********************************************************************
     *
     * @param peer The Peer of the Component we are associated with!
     *
     */

    @SuppressWarnings("deprecation")
    public void addNotify(ComponentPeer peer) {
        if (peer == componentPeer) return;

        componentPeer = peer;

        for (Component c = component;
             c != null && peer instanceof LightweightPeer; c = c.getParent()) {
            peer = c.getPeer();
        }

        if (peer instanceof DropTargetPeer) {
            nativePeer = peer;
            ((DropTargetPeer)peer).addDropTarget(this);
        } else {
            nativePeer = null;
        }
    }

    /**
     * Notify the DropTarget that it has been disassociated from a Component
     *
     **********************************************************************
     * This method is usually called from java.awt.Component.removeNotify() of
     * the Component associated with this DropTarget to notify the DropTarget
     * that a ComponentPeer has been disassociated with that Component.
     *
     * Calling this method, other than to notify this DropTarget of the
     * disassociation of the ComponentPeer from the Component may result in
     * a malfunction of the DnD system.
     **********************************************************************
     *
     * @param peer The Peer of the Component we are being disassociated from!
     */

    public void removeNotify(ComponentPeer peer) {
        if (nativePeer != null)
            ((DropTargetPeer)nativePeer).removeDropTarget(this);

        componentPeer = nativePeer = null;

        synchronized (this) {
            if (isDraggingInside) {
                dragExit(new DropTargetEvent(getDropTargetContext()));
            }
        }
    }

    /**
     * Gets the <code>DropTargetContext</code> associated
     * with this <code>DropTarget</code>.
     *
     * @return the <code>DropTargetContext</code> associated with this <code>DropTarget</code>.
     */

    public DropTargetContext getDropTargetContext() {
        return dropTargetContext;
    }

    /**
     * Creates the DropTargetContext associated with this DropTarget.
     * Subclasses may override this method to instantiate their own
     * DropTargetContext subclass.
     *
     * This call is typically *only* called by the platform's
     * DropTargetContextPeer as a drag operation encounters this
     * DropTarget. Accessing the Context while no Drag is current
     * has undefined results.
     * @return the DropTargetContext associated with this DropTarget
     */

    protected DropTargetContext createDropTargetContext() {
        return new DropTargetContext(this);
    }

    /**
     * Serializes this <code>DropTarget</code>. Performs default serialization,
     * and then writes out this object's <code>DropTargetListener</code> if and
     * only if it can be serialized. If not, <code>null</code> is written
     * instead.
     *
     * @serialData The default serializable fields, in alphabetical order,
     *             followed by either a <code>DropTargetListener</code>
     *             instance, or <code>null</code>.
     * @since 1.4
     */
    private void writeObject(ObjectOutputStream s) throws IOException {
        s.defaultWriteObject();

        s.writeObject(SerializationTester.test(dtListener)
                      ? dtListener : null);
    }

    /**
     * Deserializes this <code>DropTarget</code>. This method first performs
     * default deserialization for all non-<code>transient</code> fields. An
     * attempt is then made to deserialize this object's
     * <code>DropTargetListener</code> as well. This is first attempted by
     * deserializing the field <code>dtListener</code>, because, in releases
     * prior to 1.4, a non-<code>transient</code> field of this name stored the
     * <code>DropTargetListener</code>. If this fails, the next object in the
     * stream is used instead.
     *
     * @since 1.4
     */
    private void readObject(ObjectInputStream s)
        throws ClassNotFoundException, IOException
    {
        ObjectInputStream.GetField f = s.readFields();

        try {
            dropTargetContext =
                (DropTargetContext)f.get("dropTargetContext", null);
        } catch (IllegalArgumentException e) {
            // Pre-1.4 support. 'dropTargetContext' was previously transient
        }
        if (dropTargetContext == null) {
            dropTargetContext = createDropTargetContext();
        }

        component = (Component)f.get("component", null);
        actions = f.get("actions", DnDConstants.ACTION_COPY_OR_MOVE);
        active = f.get("active", true);

        // Pre-1.4 support. 'dtListener' was previously non-transient
        try {
            dtListener = (DropTargetListener)f.get("dtListener", null);
        } catch (IllegalArgumentException e) {
            // 1.4-compatible byte stream. 'dtListener' was written explicitly
            dtListener = (DropTargetListener)s.readObject();
        }
    }

    /*********************************************************************/

    /**
     * this protected nested class implements autoscrolling
     */

    protected static class DropTargetAutoScroller implements ActionListener {

        /**
         * construct a DropTargetAutoScroller
         *
         * @param c the <code>Component</code>
         * @param p the <code>Point</code>
         */

        protected DropTargetAutoScroller(Component c, Point p) {
            super();

            component  = c;
            autoScroll = (Autoscroll)component;

            Toolkit t  = Toolkit.getDefaultToolkit();

            Integer    initial  = Integer.valueOf(100);
            Integer    interval = Integer.valueOf(100);

            try {
                initial = (Integer)t.getDesktopProperty("DnD.Autoscroll.initialDelay");
            } catch (Exception e) {
                // ignore
            }

            try {
                interval = (Integer)t.getDesktopProperty("DnD.Autoscroll.interval");
            } catch (Exception e) {
                // ignore
            }

            timer  = new Timer(interval.intValue(), this);

            timer.setCoalesce(true);
            timer.setInitialDelay(initial.intValue());

            locn = p;
            prev = p;

            try {
                hysteresis = ((Integer)t.getDesktopProperty("DnD.Autoscroll.cursorHysteresis")).intValue();
            } catch (Exception e) {
                // ignore
            }

            timer.start();
        }

        /**
         * update the geometry of the autoscroll region
         */

        @SuppressWarnings("deprecation")
        private void updateRegion() {
           Insets    i    = autoScroll.getAutoscrollInsets();
           Dimension size = component.getSize();

           if (size.width != outer.width || size.height != outer.height)
                outer.reshape(0, 0, size.width, size.height);

           if (inner.x != i.left || inner.y != i.top)
                inner.setLocation(i.left, i.top);

           int newWidth  = size.width -  (i.left + i.right);
           int newHeight = size.height - (i.top  + i.bottom);

           if (newWidth != inner.width || newHeight != inner.height)
                inner.setSize(newWidth, newHeight);

        }

        /**
         * cause autoscroll to occur
         *
         * @param newLocn the <code>Point</code>
         */

        protected synchronized void updateLocation(Point newLocn) {
            prev = locn;
            locn = newLocn;

            if (Math.abs(locn.x - prev.x) > hysteresis ||
                Math.abs(locn.y - prev.y) > hysteresis) {
                if (timer.isRunning()) timer.stop();
            } else {
                if (!timer.isRunning()) timer.start();
            }
        }

        /**
         * cause autoscrolling to stop
         */

        protected void stop() { timer.stop(); }

        /**
         * cause autoscroll to occur
         *
         * @param e the <code>ActionEvent</code>
         */

        public synchronized void actionPerformed(ActionEvent e) {
            updateRegion();

            if (outer.contains(locn) && !inner.contains(locn))
                autoScroll.autoscroll(locn);
        }

        /*
         * fields
         */

        private Component  component;
        private Autoscroll autoScroll;

        private Timer      timer;

        private Point      locn;
        private Point      prev;

        private Rectangle  outer = new Rectangle();
        private Rectangle  inner = new Rectangle();

        private int        hysteresis = 10;
    }

    /*********************************************************************/

    /**
     * create an embedded autoscroller
     *
     * @param c the <code>Component</code>
     * @param p the <code>Point</code>
     * @return an embedded autoscroller
     */

    protected DropTargetAutoScroller createDropTargetAutoScroller(Component c, Point p) {
        return new DropTargetAutoScroller(c, p);
    }

    /**
     * initialize autoscrolling
     *
     * @param p the <code>Point</code>
     */

    protected void initializeAutoscrolling(Point p) {
        if (component == null || !(component instanceof Autoscroll)) return;

        autoScroller = createDropTargetAutoScroller(component, p);
    }

    /**
     * update autoscrolling with current cursor location
     *
     * @param dragCursorLocn the <code>Point</code>
     */

    protected void updateAutoscroll(Point dragCursorLocn) {
        if (autoScroller != null) autoScroller.updateLocation(dragCursorLocn);
    }

    /**
     * clear autoscrolling
     */

    protected void clearAutoscroll() {
        if (autoScroller != null) {
            autoScroller.stop();
            autoScroller = null;
        }
    }

    /**
     * The DropTargetContext associated with this DropTarget.
     *
     * @serial
     */
    private DropTargetContext dropTargetContext = createDropTargetContext();

    /**
     * The Component associated with this DropTarget.
     *
     * @serial
     */
    private Component component;

    /*
     * That Component's  Peer
     */
    private transient ComponentPeer componentPeer;

    /*
     * That Component's "native" Peer
     */
    private transient ComponentPeer nativePeer;


    /**
     * Default permissible actions supported by this DropTarget.
     *
     * @see #setDefaultActions
     * @see #getDefaultActions
     * @serial
     */
    int     actions = DnDConstants.ACTION_COPY_OR_MOVE;

    /**
     * <code>true</code> if the DropTarget is accepting Drag &amp; Drop operations.
     *
     * @serial
     */
    boolean active = true;

    /*
     * the auto scrolling object
     */

    private transient DropTargetAutoScroller autoScroller;

    /*
     * The delegate
     */

    private transient DropTargetListener dtListener;

    /*
     * The FlavorMap
     */

    private transient FlavorMap flavorMap;

    /*
     * If the dragging is currently inside this drop target
     */
    private transient boolean isDraggingInside;
}
