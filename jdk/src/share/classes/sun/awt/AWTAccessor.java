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
        boolean isVisible_NoClientCode(Component comp);
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
         * Identify whether the given window is opaque (true)
         *  or translucent (false).
         */
        boolean isOpaque(Window window);
        /*
         * Set the opaque preoperty to the given window.
         */
        void setOpaque(Window window, boolean isOpaque);
        /*
         * Update the image of a non-opaque (translucent) window.
         */
        void updateWindow(Window window, BufferedImage backBuffer);

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
    }

    /*
     * An accessor for the AWTEvent class.
     */
    public interface AWTEventAccessor {
        /*
         *
         * Sets the flag on this AWTEvent indicating that it was
         * generated by the system.
         */
        void setSystemGenerated(AWTEvent ev);
        /*
         *
         * Indicates whether this AWTEvent was generated by the system.
         */
        boolean isSystemGenerated(AWTEvent ev);
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
}
