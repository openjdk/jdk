/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Component;
import java.awt.Cursor;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.FlavorMap;
import java.awt.datatransfer.SystemFlavorMap;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.peer.DragSourceContextPeer;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.AccessController;
import java.util.EventListener;
import sun.awt.dnd.SunDragSourceContextPeer;
import sun.security.action.GetIntegerAction;


/**
 * The <code>DragSource</code> is the entity responsible
 * for the initiation of the Drag
 * and Drop operation, and may be used in a number of scenarios:
 * <UL>
 * <LI>1 default instance per JVM for the lifetime of that JVM.
 * <LI>1 instance per class of potential Drag Initiator object (e.g
 * TextField). [implementation dependent]
 * <LI>1 per instance of a particular
 * <code>Component</code>, or application specific
 * object associated with a <code>Component</code>
 * instance in the GUI. [implementation dependent]
 * <LI>Some other arbitrary association. [implementation dependent]
 *</UL>
 *
 * Once the <code>DragSource</code> is
 * obtained, a <code>DragGestureRecognizer</code> should
 * also be obtained to associate the <code>DragSource</code>
 * with a particular
 * <code>Component</code>.
 * <P>
 * The initial interpretation of the user's gesture,
 * and the subsequent starting of the drag operation
 * are the responsibility of the implementing
 * <code>Component</code>, which is usually
 * implemented by a <code>DragGestureRecognizer</code>.
 *<P>
 * When a drag gesture occurs, the
 * <code>DragSource</code>'s
 * startDrag() method shall be
 * invoked in order to cause processing
 * of the user's navigational
 * gestures and delivery of Drag and Drop
 * protocol notifications. A
 * <code>DragSource</code> shall only
 * permit a single Drag and Drop operation to be
 * current at any one time, and shall
 * reject any further startDrag() requests
 * by throwing an <code>IllegalDnDOperationException</code>
 * until such time as the extant operation is complete.
 * <P>
 * The startDrag() method invokes the
 * createDragSourceContext() method to
 * instantiate an appropriate
 * <code>DragSourceContext</code>
 * and associate the <code>DragSourceContextPeer</code>
 * with that.
 * <P>
 * If the Drag and Drop System is
 * unable to initiate a drag operation for
 * some reason, the startDrag() method throws
 * a <code>java.awt.dnd.InvalidDnDOperationException</code>
 * to signal such a condition. Typically this
 * exception is thrown when the underlying platform
 * system is either not in a state to
 * initiate a drag, or the parameters specified are invalid.
 * <P>
 * Note that during the drag, the
 * set of operations exposed by the source
 * at the start of the drag operation may not change
 * until the operation is complete.
 * The operation(s) are constant for the
 * duration of the operation with respect to the
 * <code>DragSource</code>.
 *
 * @since 1.2
 */

public class DragSource implements Serializable {

    private static final long serialVersionUID = 6236096958971414066L;

    /*
     * load a system default cursor
     */

    private static Cursor load(String name) {
        if (GraphicsEnvironment.isHeadless()) {
            return null;
        }

        try {
            return (Cursor)Toolkit.getDefaultToolkit().getDesktopProperty(name);
        } catch (Exception e) {
            e.printStackTrace();

            throw new RuntimeException("failed to load system cursor: " + name + " : " + e.getMessage());
        }
    }


    /**
     * The default <code>Cursor</code> to use with a copy operation indicating
     * that a drop is currently allowed. <code>null</code> if
     * <code>GraphicsEnvironment.isHeadless()</code> returns <code>true</code>.
     *
     * @see java.awt.GraphicsEnvironment#isHeadless
     */
    public static final Cursor DefaultCopyDrop =
        load("DnD.Cursor.CopyDrop");

    /**
     * The default <code>Cursor</code> to use with a move operation indicating
     * that a drop is currently allowed. <code>null</code> if
     * <code>GraphicsEnvironment.isHeadless()</code> returns <code>true</code>.
     *
     * @see java.awt.GraphicsEnvironment#isHeadless
     */
    public static final Cursor DefaultMoveDrop =
        load("DnD.Cursor.MoveDrop");

    /**
     * The default <code>Cursor</code> to use with a link operation indicating
     * that a drop is currently allowed. <code>null</code> if
     * <code>GraphicsEnvironment.isHeadless()</code> returns <code>true</code>.
     *
     * @see java.awt.GraphicsEnvironment#isHeadless
     */
    public static final Cursor DefaultLinkDrop =
        load("DnD.Cursor.LinkDrop");

    /**
     * The default <code>Cursor</code> to use with a copy operation indicating
     * that a drop is currently not allowed. <code>null</code> if
     * <code>GraphicsEnvironment.isHeadless()</code> returns <code>true</code>.
     *
     * @see java.awt.GraphicsEnvironment#isHeadless
     */
    public static final Cursor DefaultCopyNoDrop =
        load("DnD.Cursor.CopyNoDrop");

    /**
     * The default <code>Cursor</code> to use with a move operation indicating
     * that a drop is currently not allowed. <code>null</code> if
     * <code>GraphicsEnvironment.isHeadless()</code> returns <code>true</code>.
     *
     * @see java.awt.GraphicsEnvironment#isHeadless
     */
    public static final Cursor DefaultMoveNoDrop =
        load("DnD.Cursor.MoveNoDrop");

    /**
     * The default <code>Cursor</code> to use with a link operation indicating
     * that a drop is currently not allowed. <code>null</code> if
     * <code>GraphicsEnvironment.isHeadless()</code> returns <code>true</code>.
     *
     * @see java.awt.GraphicsEnvironment#isHeadless
     */
    public static final Cursor DefaultLinkNoDrop =
        load("DnD.Cursor.LinkNoDrop");

    private static final DragSource dflt =
        (GraphicsEnvironment.isHeadless()) ? null : new DragSource();

    /**
     * Internal constants for serialization.
     */
    static final String dragSourceListenerK = "dragSourceL";
    static final String dragSourceMotionListenerK = "dragSourceMotionL";

    /**
     * Gets the <code>DragSource</code> object associated with
     * the underlying platform.
     *
     * @return the platform DragSource
     * @exception HeadlessException if GraphicsEnvironment.isHeadless()
     *            returns true
     * @see java.awt.GraphicsEnvironment#isHeadless
     */
    public static DragSource getDefaultDragSource() {
        if (GraphicsEnvironment.isHeadless()) {
            throw new HeadlessException();
        } else {
            return dflt;
        }
    }

    /**
     * Reports
     * whether or not drag
     * <code>Image</code> support
     * is available on the underlying platform.
     *
     * @return if the Drag Image support is available on this platform
     */

    public static boolean isDragImageSupported() {
        Toolkit t = Toolkit.getDefaultToolkit();

        Boolean supported;

        try {
            supported = (Boolean)Toolkit.getDefaultToolkit().getDesktopProperty("DnD.isDragImageSupported");

            return supported.booleanValue();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Creates a new <code>DragSource</code>.
     *
     * @exception HeadlessException if GraphicsEnvironment.isHeadless()
     *            returns true
     * @see java.awt.GraphicsEnvironment#isHeadless
     */
    public DragSource() throws HeadlessException {
        if (GraphicsEnvironment.isHeadless()) {
            throw new HeadlessException();
        }
    }

    /**
     * Start a drag, given the <code>DragGestureEvent</code>
     * that initiated the drag, the initial
     * <code>Cursor</code> to use,
     * the <code>Image</code> to drag,
     * the offset of the <code>Image</code> origin
     * from the hotspot of the <code>Cursor</code> at
     * the instant of the trigger,
     * the <code>Transferable</code> subject data
     * of the drag, the <code>DragSourceListener</code>,
     * and the <code>FlavorMap</code>.
     *
     * @param trigger        the <code>DragGestureEvent</code> that initiated the drag
     * @param dragCursor     the initial {@code Cursor} for this drag operation
     *                       or {@code null} for the default cursor handling;
     *                       see <a href="DragSourceContext.html#defaultCursor">DragSourceContext</a>
     *                       for more details on the cursor handling mechanism during drag and drop
     * @param dragImage      the image to drag or {@code null}
     * @param imageOffset    the offset of the <code>Image</code> origin from the hotspot
     *                       of the <code>Cursor</code> at the instant of the trigger
     * @param transferable   the subject data of the drag
     * @param dsl            the <code>DragSourceListener</code>
     * @param flavorMap      the <code>FlavorMap</code> to use, or <code>null</code>
     *
     * @throws java.awt.dnd.InvalidDnDOperationException
     *    if the Drag and Drop
     *    system is unable to initiate a drag operation, or if the user
     *    attempts to start a drag while an existing drag operation
     *    is still executing
     */

    public void startDrag(DragGestureEvent   trigger,
                          Cursor             dragCursor,
                          Image              dragImage,
                          Point              imageOffset,
                          Transferable       transferable,
                          DragSourceListener dsl,
                          FlavorMap          flavorMap) throws InvalidDnDOperationException {

        SunDragSourceContextPeer.setDragDropInProgress(true);

        try {
            if (flavorMap != null) this.flavorMap = flavorMap;

            DragSourceContextPeer dscp = Toolkit.getDefaultToolkit().createDragSourceContextPeer(trigger);

            DragSourceContext     dsc = createDragSourceContext(dscp,
                                                                trigger,
                                                                dragCursor,
                                                                dragImage,
                                                                imageOffset,
                                                                transferable,
                                                                dsl
                                                                );

            if (dsc == null) {
                throw new InvalidDnDOperationException();
            }

            dscp.startDrag(dsc, dsc.getCursor(), dragImage, imageOffset); // may throw
        } catch (RuntimeException e) {
            SunDragSourceContextPeer.setDragDropInProgress(false);
            throw e;
        }
    }

    /**
     * Start a drag, given the <code>DragGestureEvent</code>
     * that initiated the drag, the initial
     * <code>Cursor</code> to use,
     * the <code>Transferable</code> subject data
     * of the drag, the <code>DragSourceListener</code>,
     * and the <code>FlavorMap</code>.
     *
     * @param trigger        the <code>DragGestureEvent</code> that
     * initiated the drag
     * @param dragCursor     the initial {@code Cursor} for this drag operation
     *                       or {@code null} for the default cursor handling;
     *                       see <a href="DragSourceContext.html#defaultCursor">DragSourceContext</a>
     *                       for more details on the cursor handling mechanism during drag and drop
     * @param transferable   the subject data of the drag
     * @param dsl            the <code>DragSourceListener</code>
     * @param flavorMap      the <code>FlavorMap</code> to use or <code>null</code>
     *
     * @throws java.awt.dnd.InvalidDnDOperationException
     *    if the Drag and Drop
     *    system is unable to initiate a drag operation, or if the user
     *    attempts to start a drag while an existing drag operation
     *    is still executing
     */

    public void startDrag(DragGestureEvent   trigger,
                          Cursor             dragCursor,
                          Transferable       transferable,
                          DragSourceListener dsl,
                          FlavorMap          flavorMap) throws InvalidDnDOperationException {
        startDrag(trigger, dragCursor, null, null, transferable, dsl, flavorMap);
    }

    /**
     * Start a drag, given the <code>DragGestureEvent</code>
     * that initiated the drag, the initial <code>Cursor</code>
     * to use,
     * the <code>Image</code> to drag,
     * the offset of the <code>Image</code> origin
     * from the hotspot of the <code>Cursor</code>
     * at the instant of the trigger,
     * the subject data of the drag, and
     * the <code>DragSourceListener</code>.
     *
     * @param trigger           the <code>DragGestureEvent</code> that initiated the drag
     * @param dragCursor     the initial {@code Cursor} for this drag operation
     *                       or {@code null} for the default cursor handling;
     *                       see <a href="DragSourceContext.html#defaultCursor">DragSourceContext</a>
     *                       for more details on the cursor handling mechanism during drag and drop
     * @param dragImage         the <code>Image</code> to drag or <code>null</code>
     * @param dragOffset        the offset of the <code>Image</code> origin from the hotspot
     *                          of the <code>Cursor</code> at the instant of the trigger
     * @param transferable      the subject data of the drag
     * @param dsl               the <code>DragSourceListener</code>
     *
     * @throws java.awt.dnd.InvalidDnDOperationException
     *    if the Drag and Drop
     *    system is unable to initiate a drag operation, or if the user
     *    attempts to start a drag while an existing drag operation
     *    is still executing
     */

    public void startDrag(DragGestureEvent   trigger,
                          Cursor             dragCursor,
                          Image              dragImage,
                          Point              dragOffset,
                          Transferable       transferable,
                          DragSourceListener dsl) throws InvalidDnDOperationException {
        startDrag(trigger, dragCursor, dragImage, dragOffset, transferable, dsl, null);
    }

    /**
     * Start a drag, given the <code>DragGestureEvent</code>
     * that initiated the drag, the initial
     * <code>Cursor</code> to
     * use,
     * the <code>Transferable</code> subject data
     * of the drag, and the <code>DragSourceListener</code>.
     *
     * @param trigger        the <code>DragGestureEvent</code> that initiated the drag
     * @param dragCursor     the initial {@code Cursor} for this drag operation
     *                       or {@code null} for the default cursor handling;
     *                       see <a href="DragSourceContext.html#defaultCursor">DragSourceContext</a> class
     *                       for more details on the cursor handling mechanism during drag and drop
     * @param transferable      the subject data of the drag
     * @param dsl               the <code>DragSourceListener</code>
     *
     * @throws java.awt.dnd.InvalidDnDOperationException
     *    if the Drag and Drop
     *    system is unable to initiate a drag operation, or if the user
     *    attempts to start a drag while an existing drag operation
     *    is still executing
     */

    public void startDrag(DragGestureEvent   trigger,
                          Cursor             dragCursor,
                          Transferable       transferable,
                          DragSourceListener dsl) throws InvalidDnDOperationException {
        startDrag(trigger, dragCursor, null, null, transferable, dsl, null);
    }

    /**
     * Creates the {@code DragSourceContext} to handle the current drag
     * operation.
     * <p>
     * To incorporate a new <code>DragSourceContext</code>
     * subclass, subclass <code>DragSource</code> and
     * override this method.
     * <p>
     * If <code>dragImage</code> is <code>null</code>, no image is used
     * to represent the drag over feedback for this drag operation, but
     * <code>NullPointerException</code> is not thrown.
     * <p>
     * If <code>dsl</code> is <code>null</code>, no drag source listener
     * is registered with the created <code>DragSourceContext</code>,
     * but <code>NullPointerException</code> is not thrown.
     *
     * @param dscp          The <code>DragSourceContextPeer</code> for this drag
     * @param dgl           The <code>DragGestureEvent</code> that triggered the
     *                      drag
     * @param dragCursor     The initial {@code Cursor} for this drag operation
     *                       or {@code null} for the default cursor handling;
     *                       see <a href="DragSourceContext.html#defaultCursor">DragSourceContext</a> class
     *                       for more details on the cursor handling mechanism during drag and drop
     * @param dragImage     The <code>Image</code> to drag or <code>null</code>
     * @param imageOffset   The offset of the <code>Image</code> origin from the
     *                      hotspot of the cursor at the instant of the trigger
     * @param t             The subject data of the drag
     * @param dsl           The <code>DragSourceListener</code>
     *
     * @return the <code>DragSourceContext</code>
     *
     * @throws NullPointerException if <code>dscp</code> is <code>null</code>
     * @throws NullPointerException if <code>dgl</code> is <code>null</code>
     * @throws NullPointerException if <code>dragImage</code> is not
     *    <code>null</code> and <code>imageOffset</code> is <code>null</code>
     * @throws NullPointerException if <code>t</code> is <code>null</code>
     * @throws IllegalArgumentException if the <code>Component</code>
     *         associated with the trigger event is <code>null</code>.
     * @throws IllegalArgumentException if the <code>DragSource</code> for the
     *         trigger event is <code>null</code>.
     * @throws IllegalArgumentException if the drag action for the
     *         trigger event is <code>DnDConstants.ACTION_NONE</code>.
     * @throws IllegalArgumentException if the source actions for the
     *         <code>DragGestureRecognizer</code> associated with the trigger
     *         event are equal to <code>DnDConstants.ACTION_NONE</code>.
     */

    protected DragSourceContext createDragSourceContext(DragSourceContextPeer dscp, DragGestureEvent dgl, Cursor dragCursor, Image dragImage, Point imageOffset, Transferable t, DragSourceListener dsl) {
        return new DragSourceContext(dscp, dgl, dragCursor, dragImage, imageOffset, t, dsl);
    }

    /**
     * This method returns the
     * <code>FlavorMap</code> for this <code>DragSource</code>.
     *
     * @return the <code>FlavorMap</code> for this <code>DragSource</code>
     */

    public FlavorMap getFlavorMap() { return flavorMap; }

    /**
     * Creates a new <code>DragGestureRecognizer</code>
     * that implements the specified
     * abstract subclass of
     * <code>DragGestureRecognizer</code>, and
     * sets the specified <code>Component</code>
     * and <code>DragGestureListener</code> on
     * the newly created object.
     *
     * @param <T> the type of {@code DragGestureRecognizer} to create
     * @param recognizerAbstractClass the requested abstract type
     * @param actions                 the permitted source drag actions
     * @param c                       the <code>Component</code> target
     * @param dgl        the <code>DragGestureListener</code> to notify
     *
     * @return the new <code>DragGestureRecognizer</code> or <code>null</code>
     *    if the <code>Toolkit.createDragGestureRecognizer</code> method
     *    has no implementation available for
     *    the requested <code>DragGestureRecognizer</code>
     *    subclass and returns <code>null</code>
     */

    public <T extends DragGestureRecognizer> T
        createDragGestureRecognizer(Class<T> recognizerAbstractClass,
                                    Component c, int actions,
                                    DragGestureListener dgl)
    {
        return Toolkit.getDefaultToolkit().createDragGestureRecognizer(recognizerAbstractClass, this, c, actions, dgl);
    }


    /**
     * Creates a new <code>DragGestureRecognizer</code>
     * that implements the default
     * abstract subclass of <code>DragGestureRecognizer</code>
     * for this <code>DragSource</code>,
     * and sets the specified <code>Component</code>
     * and <code>DragGestureListener</code> on the
     * newly created object.
     *
     * For this <code>DragSource</code>
     * the default is <code>MouseDragGestureRecognizer</code>.
     *
     * @param c       the <code>Component</code> target for the recognizer
     * @param actions the permitted source actions
     * @param dgl     the <code>DragGestureListener</code> to notify
     *
     * @return the new <code>DragGestureRecognizer</code> or <code>null</code>
     *    if the <code>Toolkit.createDragGestureRecognizer</code> method
     *    has no implementation available for
     *    the requested <code>DragGestureRecognizer</code>
     *    subclass and returns <code>null</code>
     */

    public DragGestureRecognizer createDefaultDragGestureRecognizer(Component c, int actions, DragGestureListener dgl) {
        return Toolkit.getDefaultToolkit().createDragGestureRecognizer(MouseDragGestureRecognizer.class, this, c, actions, dgl);
    }

    /**
     * Adds the specified <code>DragSourceListener</code> to this
     * <code>DragSource</code> to receive drag source events during drag
     * operations intiated with this <code>DragSource</code>.
     * If a <code>null</code> listener is specified, no action is taken and no
     * exception is thrown.
     *
     * @param dsl the <code>DragSourceListener</code> to add
     *
     * @see      #removeDragSourceListener
     * @see      #getDragSourceListeners
     * @since 1.4
     */
    public void addDragSourceListener(DragSourceListener dsl) {
        if (dsl != null) {
            synchronized (this) {
                listener = DnDEventMulticaster.add(listener, dsl);
            }
        }
    }

    /**
     * Removes the specified <code>DragSourceListener</code> from this
     * <code>DragSource</code>.
     * If a <code>null</code> listener is specified, no action is taken and no
     * exception is thrown.
     * If the listener specified by the argument was not previously added to
     * this <code>DragSource</code>, no action is taken and no exception
     * is thrown.
     *
     * @param dsl the <code>DragSourceListener</code> to remove
     *
     * @see      #addDragSourceListener
     * @see      #getDragSourceListeners
     * @since 1.4
     */
    public void removeDragSourceListener(DragSourceListener dsl) {
        if (dsl != null) {
            synchronized (this) {
                listener = DnDEventMulticaster.remove(listener, dsl);
            }
        }
    }

    /**
     * Gets all the <code>DragSourceListener</code>s
     * registered with this <code>DragSource</code>.
     *
     * @return all of this <code>DragSource</code>'s
     *         <code>DragSourceListener</code>s or an empty array if no
     *         such listeners are currently registered
     *
     * @see      #addDragSourceListener
     * @see      #removeDragSourceListener
     * @since    1.4
     */
    public DragSourceListener[] getDragSourceListeners() {
        return getListeners(DragSourceListener.class);
    }

    /**
     * Adds the specified <code>DragSourceMotionListener</code> to this
     * <code>DragSource</code> to receive drag motion events during drag
     * operations intiated with this <code>DragSource</code>.
     * If a <code>null</code> listener is specified, no action is taken and no
     * exception is thrown.
     *
     * @param dsml the <code>DragSourceMotionListener</code> to add
     *
     * @see      #removeDragSourceMotionListener
     * @see      #getDragSourceMotionListeners
     * @since 1.4
     */
    public void addDragSourceMotionListener(DragSourceMotionListener dsml) {
        if (dsml != null) {
            synchronized (this) {
                motionListener = DnDEventMulticaster.add(motionListener, dsml);
            }
        }
    }

    /**
     * Removes the specified <code>DragSourceMotionListener</code> from this
     * <code>DragSource</code>.
     * If a <code>null</code> listener is specified, no action is taken and no
     * exception is thrown.
     * If the listener specified by the argument was not previously added to
     * this <code>DragSource</code>, no action is taken and no exception
     * is thrown.
     *
     * @param dsml the <code>DragSourceMotionListener</code> to remove
     *
     * @see      #addDragSourceMotionListener
     * @see      #getDragSourceMotionListeners
     * @since 1.4
     */
    public void removeDragSourceMotionListener(DragSourceMotionListener dsml) {
        if (dsml != null) {
            synchronized (this) {
                motionListener = DnDEventMulticaster.remove(motionListener, dsml);
            }
        }
    }

    /**
     * Gets all of the  <code>DragSourceMotionListener</code>s
     * registered with this <code>DragSource</code>.
     *
     * @return all of this <code>DragSource</code>'s
     *         <code>DragSourceMotionListener</code>s or an empty array if no
     *         such listeners are currently registered
     *
     * @see      #addDragSourceMotionListener
     * @see      #removeDragSourceMotionListener
     * @since    1.4
     */
    public DragSourceMotionListener[] getDragSourceMotionListeners() {
        return getListeners(DragSourceMotionListener.class);
    }

    /**
     * Gets all the objects currently registered as
     * <code><em>Foo</em>Listener</code>s upon this <code>DragSource</code>.
     * <code><em>Foo</em>Listener</code>s are registered using the
     * <code>add<em>Foo</em>Listener</code> method.
     *
     * @param <T> the type of listener objects
     * @param listenerType the type of listeners requested; this parameter
     *          should specify an interface that descends from
     *          <code>java.util.EventListener</code>
     * @return an array of all objects registered as
     *          <code><em>Foo</em>Listener</code>s on this
     *          <code>DragSource</code>, or an empty array if no such listeners
     *          have been added
     * @exception ClassCastException if <code>listenerType</code>
     *          doesn't specify a class or interface that implements
     *          <code>java.util.EventListener</code>
     *
     * @see #getDragSourceListeners
     * @see #getDragSourceMotionListeners
     * @since 1.4
     */
    public <T extends EventListener> T[] getListeners(Class<T> listenerType) {
        EventListener l = null;
        if (listenerType == DragSourceListener.class) {
            l = listener;
        } else if (listenerType == DragSourceMotionListener.class) {
            l = motionListener;
        }
        return DnDEventMulticaster.getListeners(l, listenerType);
    }

    /**
     * This method calls <code>dragEnter</code> on the
     * <code>DragSourceListener</code>s registered with this
     * <code>DragSource</code>, and passes them the specified
     * <code>DragSourceDragEvent</code>.
     *
     * @param dsde the <code>DragSourceDragEvent</code>
     */
    void processDragEnter(DragSourceDragEvent dsde) {
        DragSourceListener dsl = listener;
        if (dsl != null) {
            dsl.dragEnter(dsde);
        }
    }

    /**
     * This method calls <code>dragOver</code> on the
     * <code>DragSourceListener</code>s registered with this
     * <code>DragSource</code>, and passes them the specified
     * <code>DragSourceDragEvent</code>.
     *
     * @param dsde the <code>DragSourceDragEvent</code>
     */
    void processDragOver(DragSourceDragEvent dsde) {
        DragSourceListener dsl = listener;
        if (dsl != null) {
            dsl.dragOver(dsde);
        }
    }

    /**
     * This method calls <code>dropActionChanged</code> on the
     * <code>DragSourceListener</code>s registered with this
     * <code>DragSource</code>, and passes them the specified
     * <code>DragSourceDragEvent</code>.
     *
     * @param dsde the <code>DragSourceDragEvent</code>
     */
    void processDropActionChanged(DragSourceDragEvent dsde) {
        DragSourceListener dsl = listener;
        if (dsl != null) {
            dsl.dropActionChanged(dsde);
        }
    }

    /**
     * This method calls <code>dragExit</code> on the
     * <code>DragSourceListener</code>s registered with this
     * <code>DragSource</code>, and passes them the specified
     * <code>DragSourceEvent</code>.
     *
     * @param dse the <code>DragSourceEvent</code>
     */
    void processDragExit(DragSourceEvent dse) {
        DragSourceListener dsl = listener;
        if (dsl != null) {
            dsl.dragExit(dse);
        }
    }

    /**
     * This method calls <code>dragDropEnd</code> on the
     * <code>DragSourceListener</code>s registered with this
     * <code>DragSource</code>, and passes them the specified
     * <code>DragSourceDropEvent</code>.
     *
     * @param dsde the <code>DragSourceEvent</code>
     */
    void processDragDropEnd(DragSourceDropEvent dsde) {
        DragSourceListener dsl = listener;
        if (dsl != null) {
            dsl.dragDropEnd(dsde);
        }
    }

    /**
     * This method calls <code>dragMouseMoved</code> on the
     * <code>DragSourceMotionListener</code>s registered with this
     * <code>DragSource</code>, and passes them the specified
     * <code>DragSourceDragEvent</code>.
     *
     * @param dsde the <code>DragSourceEvent</code>
     */
    void processDragMouseMoved(DragSourceDragEvent dsde) {
        DragSourceMotionListener dsml = motionListener;
        if (dsml != null) {
            dsml.dragMouseMoved(dsde);
        }
    }

    /**
     * Serializes this <code>DragSource</code>. This method first performs
     * default serialization. Next, it writes out this object's
     * <code>FlavorMap</code> if and only if it can be serialized. If not,
     * <code>null</code> is written instead. Next, it writes out
     * <code>Serializable</code> listeners registered with this
     * object. Listeners are written in a <code>null</code>-terminated sequence
     * of 0 or more pairs. The pair consists of a <code>String</code> and an
     * <code>Object</code>; the <code>String</code> indicates the type of the
     * <code>Object</code> and is one of the following:
     * <ul>
     * <li><code>dragSourceListenerK</code> indicating a
     *     <code>DragSourceListener</code> object;
     * <li><code>dragSourceMotionListenerK</code> indicating a
     *     <code>DragSourceMotionListener</code> object.
     * </ul>
     *
     * @serialData Either a <code>FlavorMap</code> instance, or
     *      <code>null</code>, followed by a <code>null</code>-terminated
     *      sequence of 0 or more pairs; the pair consists of a
     *      <code>String</code> and an <code>Object</code>; the
     *      <code>String</code> indicates the type of the <code>Object</code>
     *      and is one of the following:
     *      <ul>
     *      <li><code>dragSourceListenerK</code> indicating a
     *          <code>DragSourceListener</code> object;
     *      <li><code>dragSourceMotionListenerK</code> indicating a
     *          <code>DragSourceMotionListener</code> object.
     *      </ul>.
     * @since 1.4
     */
    private void writeObject(ObjectOutputStream s) throws IOException {
        s.defaultWriteObject();

        s.writeObject(SerializationTester.test(flavorMap) ? flavorMap : null);

        DnDEventMulticaster.save(s, dragSourceListenerK, listener);
        DnDEventMulticaster.save(s, dragSourceMotionListenerK, motionListener);
        s.writeObject(null);
    }

    /**
     * Deserializes this <code>DragSource</code>. This method first performs
     * default deserialization. Next, this object's <code>FlavorMap</code> is
     * deserialized by using the next object in the stream.
     * If the resulting <code>FlavorMap</code> is <code>null</code>, this
     * object's <code>FlavorMap</code> is set to the default FlavorMap for
     * this thread's <code>ClassLoader</code>.
     * Next, this object's listeners are deserialized by reading a
     * <code>null</code>-terminated sequence of 0 or more key/value pairs
     * from the stream:
     * <ul>
     * <li>If a key object is a <code>String</code> equal to
     * <code>dragSourceListenerK</code>, a <code>DragSourceListener</code> is
     * deserialized using the corresponding value object and added to this
     * <code>DragSource</code>.
     * <li>If a key object is a <code>String</code> equal to
     * <code>dragSourceMotionListenerK</code>, a
     * <code>DragSourceMotionListener</code> is deserialized using the
     * corresponding value object and added to this <code>DragSource</code>.
     * <li>Otherwise, the key/value pair is skipped.
     * </ul>
     *
     * @see java.awt.datatransfer.SystemFlavorMap#getDefaultFlavorMap
     * @since 1.4
     */
    private void readObject(ObjectInputStream s)
      throws ClassNotFoundException, IOException {
        s.defaultReadObject();

        // 'flavorMap' was written explicitly
        flavorMap = (FlavorMap)s.readObject();

        // Implementation assumes 'flavorMap' is never null.
        if (flavorMap == null) {
            flavorMap = SystemFlavorMap.getDefaultFlavorMap();
        }

        Object keyOrNull;
        while (null != (keyOrNull = s.readObject())) {
            String key = ((String)keyOrNull).intern();

            if (dragSourceListenerK == key) {
                addDragSourceListener((DragSourceListener)(s.readObject()));
            } else if (dragSourceMotionListenerK == key) {
                addDragSourceMotionListener(
                    (DragSourceMotionListener)(s.readObject()));
            } else {
                // skip value for unrecognized key
                s.readObject();
            }
        }
    }

    /**
     * Returns the drag gesture motion threshold. The drag gesture motion threshold
     * defines the recommended behavior for {@link MouseDragGestureRecognizer}s.
     * <p>
     * If the system property <code>awt.dnd.drag.threshold</code> is set to
     * a positive integer, this method returns the value of the system property;
     * otherwise if a pertinent desktop property is available and supported by
     * the implementation of the Java platform, this method returns the value of
     * that property; otherwise this method returns some default value.
     * The pertinent desktop property can be queried using
     * <code>java.awt.Toolkit.getDesktopProperty("DnD.gestureMotionThreshold")</code>.
     *
     * @return the drag gesture motion threshold
     * @see MouseDragGestureRecognizer
     * @since 1.5
     */
    public static int getDragThreshold() {
        int ts = AccessController.doPrivileged(
                new GetIntegerAction("awt.dnd.drag.threshold", 0)).intValue();
        if (ts > 0) {
            return ts;
        } else {
            Integer td = (Integer)Toolkit.getDefaultToolkit().
                    getDesktopProperty("DnD.gestureMotionThreshold");
            if (td != null) {
                return td.intValue();
            }
        }
        return 5;
    }

    /*
     * fields
     */

    private transient FlavorMap flavorMap = SystemFlavorMap.getDefaultFlavorMap();

    private transient DragSourceListener listener;

    private transient DragSourceMotionListener motionListener;
}
