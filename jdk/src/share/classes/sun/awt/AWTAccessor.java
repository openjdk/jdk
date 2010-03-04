/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

import sun.misc.Unsafe;
import java.awt.peer.ComponentPeer;

/**
 * The AWTAccessor utility class.
 * The main purpose of this class is to enable accessing
 * private and package-private fields of classes from
 * different classes/packages. See sun.misc.SharedSecretes
 * for another example.
 */
public final class AWTAccessor {

    private static final Unsafe unsafe = Unsafe.getUnsafe();

    /*
     * We don't need any objects of this class.
     * It's rather a collection of static methods
     * and interfaces.
     */
    private AWTAccessor() {
    }

    /*
     * An interface of accessor for the java.awt.Component class.
     */
    public interface ComponentAccessor {
        /*
         * Sets whether the native background erase for a component
         * has been disabled via SunToolkit.disableBackgroundErase().
         */
        void setBackgroundEraseDisabled(Component comp, boolean disabled);
        /*
         * Indicates whether the native background erase for a
         * component has been disabled via
         * SunToolkit.disableBackgroundErase().
         */
        boolean getBackgroundEraseDisabled(Component comp);
        /*
         *
         * Gets the bounds of this component in the form of a
         * <code>Rectangle</code> object. The bounds specify this
         * component's width, height, and location relative to
         * its parent.
         */
        Rectangle getBounds(Component comp);
        /*
         * Sets the shape of a lw component to cut out from hw components.
         *
         * See 6797587, 6776743, 6768307, and 6768332 for details
         */
        void setMixingCutoutShape(Component comp, Shape shape);

        /**
         * Sets GraphicsConfiguration value for the component.
         */
        void setGraphicsConfiguration(Component comp, GraphicsConfiguration gc);
        /*
         * Requests focus to the component.
         */
        boolean requestFocus(Component comp, CausedFocusEvent.Cause cause);
        /*
         * Determines if the component can gain focus.
         */
        boolean canBeFocusOwner(Component comp);

        /**
         * Returns whether the component is visible without invoking
         * any client code.
         */
        boolean isVisible(Component comp);

        /**
         * Sets the RequestFocusController.
         */
        void setRequestFocusController(RequestFocusController requestController);

        /**
         * Returns the appContext of the component.
         */
        AppContext getAppContext(Component comp);

        /**
         * Sets the appContext of the component.
         */
        void setAppContext(Component comp, AppContext appContext);

        /**
         * Returns the parent of the component.
         */
        Container getParent(Component comp);

        /**
         * Sets the parent of the component to the specified parent.
         */
        void setParent(Component comp, Container parent);

        /**
         * Resizes the component to the specified width and height.
         */
        void setSize(Component comp, int width, int height);

        /**
         * Returns the location of the component.
         */
        Point getLocation(Component comp);

        /**
         * Moves the component to the new location.
         */
        void setLocation(Component comp, int x, int y);

        /**
         * Determines whether this component is enabled.
         */
        boolean isEnabled(Component comp);

        /**
         * Determines whether this component is displayable.
         */
        boolean isDisplayable(Component comp);

        /**
         * Gets the cursor set in the component.
         */
        Cursor getCursor(Component comp);

        /**
         * Returns the peer of the component.
         */
        ComponentPeer getPeer(Component comp);

        /**
         * Sets the peer of the component to the specified peer.
         */
        void setPeer(Component comp, ComponentPeer peer);

        /**
         * Determines whether this component is lightweight.
         */
        boolean isLightweight(Component comp);

        /**
         * Returns whether or not paint messages received from
         * the operating system should be ignored.
         */
        boolean getIgnoreRepaint(Component comp);

        /**
         * Returns the width of the component.
         */
        int getWidth(Component comp);

        /**
         * Returns the height of the component.
         */
        int getHeight(Component comp);

        /**
         * Returns the x coordinate of the component.
         */
        int getX(Component comp);

        /**
         * Returns the y coordinate of the component.
         */
        int getY(Component comp);

        /**
         * Gets the foreground color of this component.
         */
        Color getForeground(Component comp);

        /**
         * Gets the background color of this component.
         */
        Color getBackground(Component comp);

        /**
         * Sets the background of this component to the specified color.
         */
        void setBackground(Component comp, Color background);

        /**
         * Gets the font of the component.
         */
        Font getFont(Component comp);

        /**
         * Processes events occurring on this component.
         */
        void processEvent(Component comp, AWTEvent e);
    }

    /*
     * An interface of accessor for java.awt.Window class.
     */
    public interface WindowAccessor {
        /*
         * Get opacity level of the given window.
         */
        float getOpacity(Window window);
        /*
         * Set opacity level to the given window.
         */
        void setOpacity(Window window, float opacity);
        /*
         * Get a shape assigned to the given window.
         */
        Shape getShape(Window window);
        /*
         * Set a shape to the given window.
         */
        void setShape(Window window, Shape shape);
        /*
         * Set the opaque preoperty to the given window.
         */
        void setOpaque(Window window, boolean isOpaque);
        /*
         * Update the image of a non-opaque (translucent) window.
         */
        void updateWindow(Window window);

        /** Get the size of the security warning.
         */
        Dimension getSecurityWarningSize(Window w);

        /**
         * Set the size of the security warning.
         */
        void setSecurityWarningSize(Window w, int width, int height);

        /** Set the position of the security warning.
         */
        void setSecurityWarningPosition(Window w, Point2D point,
                float alignmentX, float alignmentY);

        /** Request to recalculate the new position of the security warning for
         * the given window size/location as reported by the native system.
         */
        Point2D calculateSecurityWarningPosition(Window window,
                double x, double y, double w, double h);

        /** Sets the synchronous status of focus requests on lightweight
         * components in the specified window to the specified value.
         */
        void setLWRequestStatus(Window changed, boolean status);

        /**
         * Indicates whether this window should receive focus on subsequently
         * being shown, or being moved to the front.
         */
        boolean isAutoRequestFocus(Window w);

        /**
         * Indicates whether the specified window is an utility window for TrayIcon.
         */
        boolean isTrayIconWindow(Window w);

        /**
         * Marks the specified window as an utility window for TrayIcon.
         */
        void setTrayIconWindow(Window w, boolean isTrayIconWindow);
    }

    /*
     * An accessor for the AWTEvent class.
     */
    public interface AWTEventAccessor {
        /**
         * Marks the event as posted.
         */
        void setPosted(AWTEvent ev);
    }

    /*
     * An accessor for the java.awt.Frame class.
     */
    public interface FrameAccessor {
        /*
         * Sets the state of this frame.
         */
        void setExtendedState(Frame frame, int state);
        /*
         * Gets the state of this frame.
         */
       int getExtendedState(Frame frame);
    }

    /*
     * An interface of accessor for the java.awt.KeyboardFocusManager class.
     */
    public interface KeyboardFocusManagerAccessor {
        /*
         * Indicates whether the native implementation should
         * proceed with a pending focus request for the heavyweight.
         */
        int shouldNativelyFocusHeavyweight(Component heavyweight,
                                           Component descendant,
                                           boolean temporary,
                                           boolean focusedWindowChangeAllowed,
                                           long time,
                                           CausedFocusEvent.Cause cause);
        /*
         * Delivers focus for the lightweight descendant of the heavyweight
         * synchronously.
         */
        boolean processSynchronousLightweightTransfer(Component heavyweight,
                                                      Component descendant,
                                                      boolean temporary,
                                                      boolean focusedWindowChangeAllowed,
                                                      long time);
        /*
         * Removes the last focus request for the heavyweight from the queue.
         */
        void removeLastFocusRequest(Component heavyweight);
    }

    /*
     * An accessor for the MenuComponent class.
     */
    public interface MenuComponentAccessor {
        /**
         * Returns the appContext of the menu component.
         */
        AppContext getAppContext(MenuComponent menuComp);

        /**
         * Sets the appContext of the menu component.
         */
        void setAppContext(MenuComponent menuComp, AppContext appContext);

        /**
         * Returns the menu container of the menu component
         */
        MenuContainer getParent(MenuComponent menuComp);
    }

    /*
     * An accessor for the EventQueue class
     */
    public interface EventQueueAccessor {
        /*
         * Gets the event dispatch thread.
         */
        Thread getDispatchThread(EventQueue eventQueue);
        /*
         * Checks if the current thread is EDT for the given EQ.
         */
        public boolean isDispatchThreadImpl(EventQueue eventQueue);
    }

    /*
     * An accessor for the PopupMenu class
     */
    public interface PopupMenuAccessor {
        /*
         * Returns whether the popup menu is attached to a tray
         */
        boolean isTrayIconPopup(PopupMenu popupMenu);
    }

    /*
     * An accessor for the FileDialog class
     */
    public interface FileDialogAccessor {
        /*
         * Sets the files the user selects
         */
        void setFiles(FileDialog fileDialog, String directory, String files[]);

        /*
         * Sets the file the user selects
         */
        void setFile(FileDialog fileDialog, String file);

        /*
         * Sets the directory the user selects
         */
        void setDirectory(FileDialog fileDialog, String directory);

        /*
         * Returns whether the file dialog allows the multiple file selection.
         */
        boolean isMultipleMode(FileDialog fileDialog);
    }

    /*
     * The java.awt.Component class accessor object.
     */
    private static ComponentAccessor componentAccessor;

    /*
     * The java.awt.Window class accessor object.
     */
    private static WindowAccessor windowAccessor;

    /*
     * The java.awt.AWTEvent class accessor object.
     */
    private static AWTEventAccessor awtEventAccessor;

    /*
     * The java.awt.Frame class accessor object.
     */
    private static FrameAccessor frameAccessor;

    /*
     * The java.awt.KeyboardFocusManager class accessor object.
     */
    private static KeyboardFocusManagerAccessor kfmAccessor;

    /*
     * The java.awt.MenuComponent class accessor object.
     */
    private static MenuComponentAccessor menuComponentAccessor;

    /*
     * The java.awt.EventQueue class accessor object.
     */
    private static EventQueueAccessor eventQueueAccessor;

    /*
     * The java.awt.PopupMenu class accessor object.
     */
    private static PopupMenuAccessor popupMenuAccessor;

    /*
     * The java.awt.FileDialog class accessor object.
     */
    private static FileDialogAccessor fileDialogAccessor;

    /*
     * Set an accessor object for the java.awt.Component class.
     */
    public static void setComponentAccessor(ComponentAccessor ca) {
        componentAccessor = ca;
    }

    /*
     * Retrieve the accessor object for the java.awt.Window class.
     */
    public static ComponentAccessor getComponentAccessor() {
        if (componentAccessor == null) {
            unsafe.ensureClassInitialized(Component.class);
        }

        return componentAccessor;
    }

    /*
     * Set an accessor object for the java.awt.Window class.
     */
    public static void setWindowAccessor(WindowAccessor wa) {
        windowAccessor = wa;
    }

    /*
     * Retrieve the accessor object for the java.awt.Window class.
     */
    public static WindowAccessor getWindowAccessor() {
        if (windowAccessor == null) {
            unsafe.ensureClassInitialized(Window.class);
        }
        return windowAccessor;
    }

    /*
     * Set an accessor object for the java.awt.AWTEvent class.
     */
    public static void setAWTEventAccessor(AWTEventAccessor aea) {
        awtEventAccessor = aea;
    }

    /*
     * Retrieve the accessor object for the java.awt.AWTEvent class.
     */
    public static AWTEventAccessor getAWTEventAccessor() {
        if (awtEventAccessor == null) {
            unsafe.ensureClassInitialized(AWTEvent.class);
        }
        return awtEventAccessor;
    }

    /*
     * Set an accessor object for the java.awt.Frame class.
     */
    public static void setFrameAccessor(FrameAccessor fa) {
        frameAccessor = fa;
    }

    /*
     * Retrieve the accessor object for the java.awt.Frame class.
     */
    public static FrameAccessor getFrameAccessor() {
        if (frameAccessor == null) {
            unsafe.ensureClassInitialized(Frame.class);
        }
        return frameAccessor;
    }

    /*
     * Set an accessor object for the java.awt.KeyboardFocusManager class.
     */
    public static void setKeyboardFocusManagerAccessor(KeyboardFocusManagerAccessor kfma) {
        kfmAccessor = kfma;
    }

    /*
     * Retrieve the accessor object for the java.awt.KeyboardFocusManager class.
     */
    public static KeyboardFocusManagerAccessor getKeyboardFocusManagerAccessor() {
        if (kfmAccessor == null) {
            unsafe.ensureClassInitialized(KeyboardFocusManager.class);
        }
        return kfmAccessor;
    }

    /*
     * Set an accessor object for the java.awt.MenuComponent class.
     */
    public static void setMenuComponentAccessor(MenuComponentAccessor mca) {
        menuComponentAccessor = mca;
    }

    /*
     * Retrieve the accessor object for the java.awt.MenuComponent class.
     */
    public static MenuComponentAccessor getMenuComponentAccessor() {
        if (menuComponentAccessor == null) {
            unsafe.ensureClassInitialized(MenuComponent.class);
        }
        return menuComponentAccessor;
    }

    /*
     * Set an accessor object for the java.awt.EventQueue class.
     */
    public static void setEventQueueAccessor(EventQueueAccessor eqa) {
        eventQueueAccessor = eqa;
    }

    /*
     * Retrieve the accessor object for the java.awt.EventQueue class.
     */
    public static EventQueueAccessor getEventQueueAccessor() {
        if (eventQueueAccessor == null) {
            unsafe.ensureClassInitialized(EventQueue.class);
        }
        return eventQueueAccessor;
    }

    /*
     * Set an accessor object for the java.awt.PopupMenu class.
     */
    public static void setPopupMenuAccessor(PopupMenuAccessor pma) {
        popupMenuAccessor = pma;
    }

    /*
     * Retrieve the accessor object for the java.awt.PopupMenu class.
     */
    public static PopupMenuAccessor getPopupMenuAccessor() {
        if (popupMenuAccessor == null) {
            unsafe.ensureClassInitialized(PopupMenu.class);
        }
        return popupMenuAccessor;
    }

    /*
     * Set an accessor object for the java.awt.FileDialog class.
     */
    public static void setFileDialogAccessor(FileDialogAccessor fda) {
        fileDialogAccessor = fda;
    }

    /*
     * Retrieve the accessor object for the java.awt.FileDialog class.
     */
    public static FileDialogAccessor getFileDialogAccessor() {
        if (fileDialogAccessor == null) {
            unsafe.ensureClassInitialized(FileDialog.class);
        }
        return fileDialogAccessor;
    }

}
