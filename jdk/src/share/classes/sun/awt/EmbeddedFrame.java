/*
 * Copyright 1996-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.awt;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.awt.peer.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Field;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import java.util.Set;
import java.awt.AWTKeyStroke;
import java.applet.Applet;
import sun.applet.AppletPanel;

/**
 * A generic container used for embedding Java components, usually applets.
 * An EmbeddedFrame has two related uses:
 *
 * . Within a Java-based application, an EmbeddedFrame serves as a sort of
 *   firewall, preventing the contained components or applets from using
 *   getParent() to find parent components, such as menubars.
 *
 * . Within a C-based application, an EmbeddedFrame contains a window handle
 *   which was created by the application, which serves as the top-level
 *   Java window.  EmbeddedFrames created for this purpose are passed-in a
 *   handle of an existing window created by the application.  The window
 *   handle should be of the appropriate native type for a specific
 *   platform, as stored in the pData field of the ComponentPeer.
 *
 * @author      Thomas Ball
 */
public abstract class EmbeddedFrame extends Frame
                          implements KeyEventDispatcher, PropertyChangeListener {

    private boolean isCursorAllowed = true;
    private static Field fieldPeer;
    private static Field currentCycleRoot;
    private boolean supportsXEmbed = false;
    private KeyboardFocusManager appletKFM;
    // JDK 1.1 compatibility
    private static final long serialVersionUID = 2967042741780317130L;

    // Use these in traverseOut method to determine directions
    protected static final boolean FORWARD = true;
    protected static final boolean BACKWARD = false;

    public boolean supportsXEmbed() {
        return supportsXEmbed && SunToolkit.needsXEmbed();
    }

    protected EmbeddedFrame(boolean supportsXEmbed) {
        this((long)0, supportsXEmbed);
    }


    protected EmbeddedFrame() {
        this((long)0);
    }

    /**
     * @deprecated This constructor will be removed in 1.5
     */
    @Deprecated
    protected EmbeddedFrame(int handle) {
        this((long)handle);
    }

    protected EmbeddedFrame(long handle) {
        this(handle, false);
    }

    protected EmbeddedFrame(long handle, boolean supportsXEmbed) {
        this.supportsXEmbed = supportsXEmbed;
        registerListeners();
    }

    /**
     * Block introspection of a parent window by this child.
     */
    public Container getParent() {
        return null;
    }

    /**
     * Needed to track which KeyboardFocusManager is current. We want to avoid memory
     * leaks, so when KFM stops being current, we remove ourselves as listeners.
     */
    public void propertyChange(PropertyChangeEvent evt) {
        // We don't handle any other properties. Skip it.
        if (!evt.getPropertyName().equals("managingFocus")) {
            return;
        }

        // We only do it if it stops being current. Technically, we should
        // never get an event about KFM starting being current.
        if (evt.getNewValue() == Boolean.TRUE) {
            return;
        }

        // should be the same as appletKFM
        removeTraversingOutListeners((KeyboardFocusManager)evt.getSource());

        appletKFM = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        if (isVisible()) {
            addTraversingOutListeners(appletKFM);
        }
    }

    /**
     * Register us as KeyEventDispatcher and property "managingFocus" listeners.
     */
    private void addTraversingOutListeners(KeyboardFocusManager kfm) {
        kfm.addKeyEventDispatcher(this);
        kfm.addPropertyChangeListener("managingFocus", this);
    }

    /**
     * Deregister us as KeyEventDispatcher and property "managingFocus" listeners.
     */
    private void removeTraversingOutListeners(KeyboardFocusManager kfm) {
        kfm.removeKeyEventDispatcher(this);
        kfm.removePropertyChangeListener("managingFocus", this);
    }

    /**
     * Because there may be many AppContexts, and we can't be sure where this
     * EmbeddedFrame is first created or shown, we can't automatically determine
     * the correct KeyboardFocusManager to attach to as KeyEventDispatcher.
     * Those who want to use the functionality of traversing out of the EmbeddedFrame
     * must call this method on the Applet's AppContext. After that, all the changes
     * can be handled automatically, including possible replacement of
     * KeyboardFocusManager.
     */
    public void registerListeners() {
        if (appletKFM != null) {
            removeTraversingOutListeners(appletKFM);
        }
        appletKFM = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        if (isVisible()) {
            addTraversingOutListeners(appletKFM);
        }
    }

    /**
     * Needed to avoid memory leak: we register this EmbeddedFrame as a listener with
     * KeyboardFocusManager of applet's AppContext. We don't want the KFM to keep
     * reference to our EmbeddedFrame forever if the Frame is no longer in use, so we
     * add listeners in show() and remove them in hide().
     */
    public void show() {
        if (appletKFM != null) {
            addTraversingOutListeners(appletKFM);
        }
        super.show();
    }

    /**
     * Needed to avoid memory leak: we register this EmbeddedFrame as a listener with
     * KeyboardFocusManager of applet's AppContext. We don't want the KFM to keep
     * reference to our EmbeddedFrame forever if the Frame is no longer in use, so we
     * add listeners in show() and remove them in hide().
     */
    public void hide() {
        if (appletKFM != null) {
            removeTraversingOutListeners(appletKFM);
        }
        super.hide();
    }

    /**
     * Need this method to detect when the focus may have chance to leave the
     * focus cycle root which is EmbeddedFrame. Mostly, the code here is copied
     * from DefaultKeyboardFocusManager.processKeyEvent with some minor
     * modifications.
     */
    public boolean dispatchKeyEvent(KeyEvent e) {

        // We can't guarantee that this is called on the same AppContext as EmbeddedFrame
        // belongs to. That's why we can't use public methods to find current focus cycle
        // root. Instead, we access KFM's private field directly.
        if (currentCycleRoot == null) {
            currentCycleRoot = (Field)AccessController.doPrivileged(new PrivilegedAction() {
                public Object run() {
                    try {
                        Field unaccessibleRoot = KeyboardFocusManager.class.
                                                     getDeclaredField("currentFocusCycleRoot");
                        if (unaccessibleRoot != null) {
                            unaccessibleRoot.setAccessible(true);
                        }
                        return unaccessibleRoot;
                    } catch (NoSuchFieldException e1) {
                        assert false;
                    } catch (SecurityException e2) {
                        assert false;
                    }
                    return null;
                }
            });
        }

        Container currentRoot = null;
        if (currentCycleRoot != null) {
            try {
                // The field is static, so we can pass null to Field.get() as the argument.
                currentRoot = (Container)currentCycleRoot.get(null);
            } catch (IllegalAccessException e3) {
                // This is impossible: currentCycleRoot would be null if setAccessible failed.
                assert false;
            }
        }

        // if we are not in EmbeddedFrame's cycle, we should not try to leave.
        if (this != currentRoot) {
            return false;
        }

        // KEY_TYPED events cannot be focus traversal keys
        if (e.getID() == KeyEvent.KEY_TYPED) {
            return false;
        }

        if (!getFocusTraversalKeysEnabled() || e.isConsumed()) {
            return false;
        }

        AWTKeyStroke stroke = AWTKeyStroke.getAWTKeyStrokeForEvent(e);
        Set toTest;
        Component currentFocused = e.getComponent();

        toTest = getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS);
        if (toTest.contains(stroke)) {
            // 6581899: performance improvement for SortingFocusTraversalPolicy
            Component last = getFocusTraversalPolicy().getLastComponent(this);
            if (currentFocused == last || last == null) {
                if (traverseOut(FORWARD)) {
                    e.consume();
                    return true;
                }
            }
        }

        toTest = getFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS);
        if (toTest.contains(stroke)) {
            // 6581899: performance improvement for SortingFocusTraversalPolicy
            Component first = getFocusTraversalPolicy().getFirstComponent(this);
            if (currentFocused == first || first == null) {
                if (traverseOut(BACKWARD)) {
                    e.consume();
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * This method is called from dispatchKeyEvent in the following two cases:
     * 1. The focus is on the first Component of this EmbeddedFrame and we are
     *    about to transfer the focus backward.
     * 2. The focus in on the last Component of this EmbeddedFrame and we are
     *    about to transfer the focus forward.
     * This is needed to give the opportuity for keyboard focus to leave the
     * EmbeddedFrame. Override this method, initiate focus transfer in it and
     * return true if you want the focus to leave EmbeddedFrame's cycle.
     * The direction parameter specifies which of the two mentioned cases is
     * happening. Use FORWARD and BACKWARD constants defined in EmbeddedFrame
     * to avoid confusing boolean values.
     *
     * @param direction FORWARD or BACKWARD
     * @return true, if EmbeddedFrame wants the focus to leave it,
     *         false otherwise.
     */
    protected boolean traverseOut(boolean direction) {
        return false;
    }

    /**
     * Block modifying any frame attributes, since they aren't applicable
     * for EmbeddedFrames.
     */
    public void setTitle(String title) {}
    public void setIconImage(Image image) {}
    public void setIconImages(java.util.List<? extends Image> icons) {}
    public void setMenuBar(MenuBar mb) {}
    public void setResizable(boolean resizable) {}
    public void remove(MenuComponent m) {}

    public boolean isResizable() {
        return true;
    }

    public void addNotify() {
        synchronized (getTreeLock()) {
            if (getPeer() == null) {
                setPeer(new NullEmbeddedFramePeer());
            }
            super.addNotify();
        }
    }

    // These three functions consitute RFE 4100710. Do not remove.
    public void setCursorAllowed(boolean isCursorAllowed) {
        this.isCursorAllowed = isCursorAllowed;
        getPeer().updateCursorImmediately();
    }
    public boolean isCursorAllowed() {
        return isCursorAllowed;
    }
    public Cursor getCursor() {
        return (isCursorAllowed)
            ? super.getCursor()
            : Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
    }

    protected  void setPeer(final ComponentPeer p){
        if (fieldPeer == null) {
            fieldPeer = (Field)AccessController.doPrivileged(new PrivilegedAction() {
                    public Object run() {
                        try {
                            Field lnkPeer = Component.class.getDeclaredField("peer");
                            if (lnkPeer != null) {
                                lnkPeer.setAccessible(true);
                            }
                            return lnkPeer;
                        } catch (NoSuchFieldException e) {
                            assert false;
                        } catch (SecurityException e) {
                            assert false;
                        }
                        return null;
                    }//run
                });
        }
        try{
            if (fieldPeer !=null){
                fieldPeer.set(EmbeddedFrame.this, p);
            }
        } catch (IllegalAccessException e) {
            assert false;
        }
    };  //setPeer method ends

    /**
     * Synthesize native message to activate or deactivate EmbeddedFrame window
     * depending on the value of parameter <code>b</code>.
     * Peers should override this method if they are to implement
     * this functionality.
     * @param doActivate  if <code>true</code>, activates the window;
     * otherwise, deactivates the window
     */
    public void synthesizeWindowActivation(boolean doActivate) {}

    /**
     * Moves this embedded frame to a new location. The top-left corner of
     * the new location is specified by the <code>x</code> and <code>y</code>
     * parameters relative to the native parent component.
     * <p>
     * setLocation() and setBounds() for EmbeddedFrame really don't move it
     * within the native parent. These methods always put embedded frame to
     * (0, 0) for backward compatibility. To allow moving embedded frame
     * setLocationPrivate() and setBoundsPrivate() were introduced, and they
     * work just the same way as setLocation() and setBounds() for usual,
     * non-embedded components.
     * </p>
     * <p>
     * Using usual get/setLocation() and get/setBounds() together with new
     * get/setLocationPrivate() and get/setBoundsPrivate() is not recommended.
     * For example, calling getBoundsPrivate() after setLocation() works fine,
     * but getBounds() after setBoundsPrivate() may return unpredictable value.
     * </p>
     * @param x the new <i>x</i>-coordinate relative to the parent component
     * @param y the new <i>y</i>-coordinate relative to the parent component
     * @see java.awt.Component#setLocation
     * @see #getLocationPrivate
     * @see #setBoundsPrivate
     * @see #getBoundsPrivate
     * @since 1.5
     */
    protected void setLocationPrivate(int x, int y) {
        Dimension size = getSize();
        setBoundsPrivate(x, y, size.width, size.height);
    }

    /**
     * Gets the location of this embedded frame as a point specifying the
     * top-left corner relative to parent component.
     * <p>
     * setLocation() and setBounds() for EmbeddedFrame really don't move it
     * within the native parent. These methods always put embedded frame to
     * (0, 0) for backward compatibility. To allow getting location and size
     * of embedded frame getLocationPrivate() and getBoundsPrivate() were
     * introduced, and they work just the same way as getLocation() and getBounds()
     * for ususal, non-embedded components.
     * </p>
     * <p>
     * Using usual get/setLocation() and get/setBounds() together with new
     * get/setLocationPrivate() and get/setBoundsPrivate() is not recommended.
     * For example, calling getBoundsPrivate() after setLocation() works fine,
     * but getBounds() after setBoundsPrivate() may return unpredictable value.
     * </p>
     * @return a point indicating this embedded frame's top-left corner
     * @see java.awt.Component#getLocation
     * @see #setLocationPrivate
     * @see #setBoundsPrivate
     * @see #getBoundsPrivate
     * @since 1.6
     */
    protected Point getLocationPrivate() {
        Rectangle bounds = getBoundsPrivate();
        return new Point(bounds.x, bounds.y);
    }

    /**
     * Moves and resizes this embedded frame. The new location of the top-left
     * corner is specified by <code>x</code> and <code>y</code> parameters
     * relative to the native parent component. The new size is specified by
     * <code>width</code> and <code>height</code>.
     * <p>
     * setLocation() and setBounds() for EmbeddedFrame really don't move it
     * within the native parent. These methods always put embedded frame to
     * (0, 0) for backward compatibility. To allow moving embedded frames
     * setLocationPrivate() and setBoundsPrivate() were introduced, and they
     * work just the same way as setLocation() and setBounds() for usual,
     * non-embedded components.
     * </p>
     * <p>
     * Using usual get/setLocation() and get/setBounds() together with new
     * get/setLocationPrivate() and get/setBoundsPrivate() is not recommended.
     * For example, calling getBoundsPrivate() after setLocation() works fine,
     * but getBounds() after setBoundsPrivate() may return unpredictable value.
     * </p>
     * @param x the new <i>x</i>-coordinate relative to the parent component
     * @param y the new <i>y</i>-coordinate relative to the parent component
     * @param width the new <code>width</code> of this embedded frame
     * @param height the new <code>height</code> of this embedded frame
     * @see java.awt.Component#setBounds
     * @see #setLocationPrivate
     * @see #getLocationPrivate
     * @see #getBoundsPrivate
     * @since 1.5
     */
    protected void setBoundsPrivate(int x, int y, int width, int height) {
        final FramePeer peer = (FramePeer)getPeer();
        if (peer != null) {
            peer.setBoundsPrivate(x, y, width, height);
        }
    }

    /**
     * Gets the bounds of this embedded frame as a rectangle specifying the
     * width, height and location relative to the native parent component.
     * <p>
     * setLocation() and setBounds() for EmbeddedFrame really don't move it
     * within the native parent. These methods always put embedded frame to
     * (0, 0) for backward compatibility. To allow getting location and size
     * of embedded frames getLocationPrivate() and getBoundsPrivate() were
     * introduced, and they work just the same way as getLocation() and getBounds()
     * for ususal, non-embedded components.
     * </p>
     * <p>
     * Using usual get/setLocation() and get/setBounds() together with new
     * get/setLocationPrivate() and get/setBoundsPrivate() is not recommended.
     * For example, calling getBoundsPrivate() after setLocation() works fine,
     * but getBounds() after setBoundsPrivate() may return unpredictable value.
     * </p>
     * @return a rectangle indicating this embedded frame's bounds
     * @see java.awt.Component#getBounds
     * @see #setLocationPrivate
     * @see #getLocationPrivate
     * @see #setBoundsPrivate
     * @since 1.6
     */
    protected Rectangle getBoundsPrivate() {
        final FramePeer peer = (FramePeer)getPeer();
        if (peer != null) {
            return peer.getBoundsPrivate();
        }
        else {
            return getBounds();
        }
    }

    public void toFront() {}
    public void toBack() {}

    public abstract void registerAccelerator(AWTKeyStroke stroke);
    public abstract void unregisterAccelerator(AWTKeyStroke stroke);

    /**
     * Checks if the component is in an EmbeddedFrame. If so,
     * returns the applet found in the hierarchy or null if
     * not found.
     * @return the parent applet or {@ null}
     * @since 1.6
     */
    public static Applet getAppletIfAncestorOf(Component comp) {
        Container parent = comp.getParent();
        Applet applet = null;
        while (parent != null && !(parent instanceof EmbeddedFrame)) {
            if (parent instanceof Applet) {
                applet = (Applet)parent;
            }
            parent = parent.getParent();
        }
        return parent == null ? null : applet;
    }

    /**
     * This method should be overriden in subclasses. It is
     * called when window this frame is within should be blocked
     * by some modal dialog.
     */
    public void notifyModalBlocked(Dialog blocker, boolean blocked) {
    }

    private static class NullEmbeddedFramePeer
        extends NullComponentPeer implements FramePeer {
        public void setTitle(String title) {}
        public void setIconImage(Image im) {}
        public void updateIconImages() {}
        public void setMenuBar(MenuBar mb) {}
        public void setResizable(boolean resizeable) {}
        public void setState(int state) {}
        public int getState() { return Frame.NORMAL; }
        public void setMaximizedBounds(Rectangle b) {}
        public void toFront() {}
        public void toBack() {}
        public void updateFocusableWindowState() {}
        public void updateAlwaysOnTop() {}
        public void setAlwaysOnTop(boolean alwaysOnTop) {}
        public Component getGlobalHeavyweightFocusOwner() { return null; }
        public void setBoundsPrivate(int x, int y, int width, int height) {
            setBounds(x, y, width, height, SET_BOUNDS);
        }
        public Rectangle getBoundsPrivate() {
            return getBounds();
        }
        public void setModalBlocked(Dialog blocker, boolean blocked) {}

        /**
         * @see java.awt.peer.ContainerPeer#restack
         */
        public void restack() {
            throw new UnsupportedOperationException();
        }

        /**
         * @see java.awt.peer.ContainerPeer#isRestackSupported
         */
        public boolean isRestackSupported() {
            return false;
        }
        public boolean requestWindowFocus() {
            return false;
        }
        public void updateMinimumSize() {
        }

        public void setOpacity(float opacity) {
        }

        public void setOpaque(boolean isOpaque) {
        }

        public void updateWindow(BufferedImage bi) {
        }
        public void repositionSecurityWarning() {
        }
     }
} // class EmbeddedFrame
