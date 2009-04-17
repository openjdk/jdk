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

package com.sun.awt;

import java.awt.*;

import sun.awt.AWTAccessor;
import sun.awt.SunToolkit;

/**
 * A collection of utility methods for AWT.
 *
 * The functionality provided by the static methods of the class includes:
 * <ul>
 * <li>Setting shapes on top-level windows
 * <li>Setting a constant alpha value for each pixel of a top-level window
 * <li>Making a window non-opaque, after that it paints only explicitly
 * painted pixels on the screen, with arbitrary alpha values for every pixel.
 * <li>Setting a 'mixing-cutout' shape for a component.
 * </ul>
 * <p>
 * A "top-level window" is an instance of the {@code Window} class (or its
 * descendant, such as {@code JFrame}).
 * <p>
 * Some of the mentioned features may not be supported by the native platform.
 * To determine whether a particular feature is supported, the user must use
 * the {@code isTranslucencySupported()} method of the class passing a desired
 * translucency kind (a member of the {@code Translucency} enum) as an
 * argument.
 * <p>
 * The per-pixel alpha feature also requires the user to create her/his
 * windows using a translucency-capable graphics configuration.
 * The {@code isTranslucencyCapable()} method must
 * be used to verify whether any given GraphicsConfiguration supports
 * the trasnlcency effects.
 * <p>
 * <b>WARNING</b>: This class is an implementation detail and only meant
 * for limited use outside of the core platform. This API may change
 * drastically between update release, and it may even be
 * removed or be moved in some other package(s)/class(es).
 */
public final class AWTUtilities {

    /**
     * The AWTUtilities class should not be instantiated
     */
    private AWTUtilities() {
    }

    /** Kinds of translucency supported by the underlying system.
     *  @see #isTranslucencySupported
     */
    public static enum Translucency {
        /**
         * Represents support in the underlying system for windows each pixel
         * of which is guaranteed to be either completely opaque, with
         * an alpha value of 1.0, or completely transparent, with an alpha
         * value of 0.0.
         */
        PERPIXEL_TRANSPARENT,

        /**
         * Represents support in the underlying system for windows all of
         * the pixels of which have the same alpha value between or including
         * 0.0 and 1.0.
         */
        TRANSLUCENT,

        /**
         * Represents support in the underlying system for windows that
         * contain or might contain pixels with arbitrary alpha values
         * between and including 0.0 and 1.0.
         */
        PERPIXEL_TRANSLUCENT;
    }


    /**
     * Returns whether the given level of translucency is supported by
     * the underlying system.
     *
     * Note that this method may sometimes return the value
     * indicating that the particular level is supported, but
     * the native windowing system may still not support the
     * given level of translucency (due to the bugs in
     * the windowing system).
     *
     * @param translucencyKind a kind of translucency support
     *                         (either PERPIXEL_TRANSPARENT,
     *                         TRANSLUCENT, or PERPIXEL_TRANSLUCENT)
     * @return whether the given translucency kind is supported
     */
    public static boolean isTranslucencySupported(Translucency translucencyKind) {
        switch (translucencyKind) {
            case PERPIXEL_TRANSPARENT:
                return isWindowShapingSupported();
            case TRANSLUCENT:
                return isWindowOpacitySupported();
            case PERPIXEL_TRANSLUCENT:
                return isWindowTranslucencySupported();
        }
        return false;
    }


    /**
     * Returns whether the windowing system supports changing the opacity
     * value of top-level windows.
     * Note that this method may sometimes return true, but the native
     * windowing system may still not support the concept of
     * translucency (due to the bugs in the windowing system).
     */
    private static boolean isWindowOpacitySupported() {
        Toolkit curToolkit = Toolkit.getDefaultToolkit();
        if (!(curToolkit instanceof SunToolkit)) {
            return false;
        }
        return ((SunToolkit)curToolkit).isWindowOpacitySupported();
    }

    /**
     * Set the opacity of the window. The opacity is at the range [0..1].
     * Note that setting the opacity level of 0 may or may not disable
     * the mouse event handling on this window. This is
     * a platform-dependent behavior.
     *
     * In order for this method to enable the translucency effect,
     * the isTranslucencySupported() method should indicate that the
     * TRANSLUCENT level of translucency is supported.
     *
     * <p>Also note that the window must not be in the full-screen mode
     * when setting the opacity value &lt; 1.0f. Otherwise
     * the IllegalArgumentException is thrown.
     *
     * @param window the window to set the opacity level to
     * @param opacity the opacity level to set to the window
     * @throws NullPointerException if the window argument is null
     * @throws IllegalArgumentException if the opacity is out of
     *                                  the range [0..1]
     * @throws IllegalArgumentException if the window is in full screen mode,
     *                                  and the opacity is less than 1.0f
     * @throws UnsupportedOperationException if the TRANSLUCENT translucency
     *                                       kind is not supported
     */
    public static void setWindowOpacity(Window window, float opacity) {
        if (window == null) {
            throw new NullPointerException(
                    "The window argument should not be null.");
        }

        AWTAccessor.getWindowAccessor().setOpacity(window, opacity);
    }

    /**
     * Get the opacity of the window. If the opacity has not
     * yet being set, this method returns 1.0.
     *
     * @param window the window to get the opacity level from
     * @throws NullPointerException if the window argument is null
     */
    public static float getWindowOpacity(Window window) {
        if (window == null) {
            throw new NullPointerException(
                    "The window argument should not be null.");
        }

        return AWTAccessor.getWindowAccessor().getOpacity(window);
    }

    /**
     * Returns whether the windowing system supports changing the shape
     * of top-level windows.
     * Note that this method may sometimes return true, but the native
     * windowing system may still not support the concept of
     * shaping (due to the bugs in the windowing system).
     */
    public static boolean isWindowShapingSupported() {
        Toolkit curToolkit = Toolkit.getDefaultToolkit();
        if (!(curToolkit instanceof SunToolkit)) {
            return false;
        }
        return ((SunToolkit)curToolkit).isWindowShapingSupported();
    }

    /**
     * Returns an object that implements the Shape interface and represents
     * the shape previously set with the call to the setWindowShape() method.
     * If no shape has been set yet, or the shape has been reset to null,
     * this method returns null.
     *
     * @param window the window to get the shape from
     * @return the current shape of the window
     * @throws NullPointerException if the window argument is null
     */
    public static Shape getWindowShape(Window window) {
        if (window == null) {
            throw new NullPointerException(
                    "The window argument should not be null.");
        }
        return AWTAccessor.getWindowAccessor().getShape(window);
    }

    /**
     * Sets a shape for the given window.
     * If the shape argument is null, this methods restores
     * the default shape making the window rectangular.
     * <p>Note that in order to set a shape, the window must be undecorated.
     * If the window is decorated, this method ignores the {@code shape}
     * argument and resets the shape to null.
     * <p>Also note that the window must not be in the full-screen mode
     * when setting a non-null shape. Otherwise the IllegalArgumentException
     * is thrown.
     * <p>Depending on the platform, the method may return without
     * effecting the shape of the window if the window has a non-null warning
     * string ({@link Window#getWarningString()}). In this case the passed
     * shape object is ignored.
     *
     * @param window the window to set the shape to
     * @param shape the shape to set to the window
     * @throws NullPointerException if the window argument is null
     * @throws IllegalArgumentException if the window is in full screen mode,
     *                                  and the shape is not null
     * @throws UnsupportedOperationException if the PERPIXEL_TRANSPARENT
     *                                       translucency kind is not supported
     */
    public static void setWindowShape(Window window, Shape shape) {
        if (window == null) {
            throw new NullPointerException(
                    "The window argument should not be null.");
        }
        AWTAccessor.getWindowAccessor().setShape(window, shape);
    }

    private static boolean isWindowTranslucencySupported() {
        /*
         * Per-pixel alpha is supported if all the conditions are TRUE:
         *    1. The toolkit is a sort of SunToolkit
         *    2. The toolkit supports translucency in general
         *        (isWindowTranslucencySupported())
         *    3. There's at least one translucency-capable
         *        GraphicsConfiguration
         */

        Toolkit curToolkit = Toolkit.getDefaultToolkit();
        if (!(curToolkit instanceof SunToolkit)) {
            return false;
        }

        if (!((SunToolkit)curToolkit).isWindowTranslucencySupported()) {
            return false;
        }

        GraphicsEnvironment env =
            GraphicsEnvironment.getLocalGraphicsEnvironment();

        // If the default GC supports translucency return true.
        // It is important to optimize the verification this way,
        // see CR 6661196 for more details.
        if (isTranslucencyCapable(env.getDefaultScreenDevice()
                    .getDefaultConfiguration()))
        {
            return true;
        }

        // ... otherwise iterate through all the GCs.
        GraphicsDevice[] devices = env.getScreenDevices();

        for (int i = 0; i < devices.length; i++) {
            GraphicsConfiguration[] configs = devices[i].getConfigurations();
            for (int j = 0; j < configs.length; j++) {
                if (isTranslucencyCapable(configs[j])) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Enables the per-pixel alpha support for the given window.
     * Once the window becomes non-opaque (the isOpaque is set to false),
     * the drawing sub-system is starting to respect the alpha value of each
     * separate pixel. If a pixel gets painted with alpha color component
     * equal to zero, it becomes visually transparent, if the alpha of the
     * pixel is equal to 255, the pixel is fully opaque. Interim values
     * of the alpha color component make the pixel semi-transparent (i.e.
     * translucent).
     * <p>Note that in order for the window to support the per-pixel alpha
     * mode, the window must be created using the GraphicsConfiguration
     * for which the {@link #isTranslucencyCapable}
     * method returns true.
     * <p>Also note that some native systems enable the per-pixel translucency
     * mode for any window created using the translucency-compatible
     * graphics configuration. However, it is highly recommended to always
     * invoke the setWindowOpaque() method for these windows, at least for
     * the sake of cross-platform compatibility reasons.
     * <p>Also note that the window must not be in the full-screen mode
     * when making it non-opaque. Otherwise the IllegalArgumentException
     * is thrown.
     * <p>If the window is a {@code Frame} or a {@code Dialog}, the window must
     * be undecorated prior to enabling the per-pixel translucency effect (see
     * {@link Frame#setUndecorated()} and/or {@link Dialog#setUndecorated()}).
     * If the window becomes decorated through a subsequent call to the
     * corresponding {@code setUndecorated()} method, the per-pixel
     * translucency effect will be disabled and the opaque property reset to
     * {@code true}.
     * <p>Depending on the platform, the method may return without
     * effecting the opaque property of the window if the window has a non-null
     * warning string ({@link Window#getWarningString()}). In this case
     * the passed 'isOpaque' value is ignored.
     *
     * @param window the window to set the shape to
     * @param isOpaque whether the window must be opaque (true),
     *                 or translucent (false)
     * @throws NullPointerException if the window argument is null
     * @throws IllegalArgumentException if the window uses
     *                                  a GraphicsConfiguration for which the
     *                                  {@code isTranslucencyCapable()}
     *                                  method returns false
     * @throws IllegalArgumentException if the window is in full screen mode,
     *                                  and the isOpaque is false
     * @throws IllegalArgumentException if the window is decorated and the
     * isOpaque argument is {@code false}.
     * @throws UnsupportedOperationException if the PERPIXEL_TRANSLUCENT
     *                                       translucency kind is not supported
     */
    public static void setWindowOpaque(Window window, boolean isOpaque) {
        if (window == null) {
            throw new NullPointerException(
                    "The window argument should not be null.");
        }
        if (!isOpaque && !isTranslucencySupported(Translucency.PERPIXEL_TRANSLUCENT)) {
            throw new UnsupportedOperationException(
                    "The PERPIXEL_TRANSLUCENT translucency kind is not supported");
        }
        AWTAccessor.getWindowAccessor().setOpaque(window, isOpaque);
    }

    /**
     * Returns whether the window is opaque or translucent.
     *
     * @param window the window to set the shape to
     * @return whether the window is currently opaque (true)
     *         or translucent (false)
     * @throws NullPointerException if the window argument is null
     */
    public static boolean isWindowOpaque(Window window) {
        if (window == null) {
            throw new NullPointerException(
                    "The window argument should not be null.");
        }

        return AWTAccessor.getWindowAccessor().isOpaque(window);
    }

    /**
     * Verifies whether a given GraphicsConfiguration supports
     * the PERPIXEL_TRANSLUCENT kind of translucency.
     * All windows that are intended to be used with the {@link #setWindowOpaque}
     * method must be created using a GraphicsConfiguration for which this method
     * returns true.
     * <p>Note that some native systems enable the per-pixel translucency
     * mode for any window created using a translucency-capable
     * graphics configuration. However, it is highly recommended to always
     * invoke the setWindowOpaque() method for these windows, at least
     * for the sake of cross-platform compatibility reasons.
     *
     * @param gc GraphicsConfiguration
     * @throws NullPointerException if the gc argument is null
     * @return whether the given GraphicsConfiguration supports
     *         the translucency effects.
     */
    public static boolean isTranslucencyCapable(GraphicsConfiguration gc) {
        if (gc == null) {
            throw new NullPointerException("The gc argument should not be null");
        }
        /*
        return gc.isTranslucencyCapable();
        */
        Toolkit curToolkit = Toolkit.getDefaultToolkit();
        if (!(curToolkit instanceof SunToolkit)) {
            return false;
        }
        return ((SunToolkit)curToolkit).isTranslucencyCapable(gc);
    }

    /**
     * Sets a 'mixing-cutout' shape for the given component.
     *
     * By default a lightweight component is treated as an opaque rectangle for
     * the purposes of the Heavyweight/Lightweight Components Mixing feature.
     * This method enables developers to set an arbitrary shape to be cut out
     * from heavyweight components positioned underneath the lightweight
     * component in the z-order.
     * <p>
     * The {@code shape} argument may have the following values:
     * <ul>
     * <li>{@code null} - reverts the default cutout shape (the rectangle equal
     * to the component's {@code getBounds()})
     * <li><i>empty-shape</i> - does not cut out anything from heavyweight
     * components. This makes the given lightweight component effectively
     * transparent. Note that descendants of the lightweight component still
     * affect the shapes of heavyweight components.  An example of an
     * <i>empty-shape</i> is {@code new Rectangle()}.
     * <li><i>non-empty-shape</i> - the given shape will be cut out from
     * heavyweight components.
     * </ul>
     * <p>
     * The most common example when the 'mixing-cutout' shape is needed is a
     * glass pane component. The {@link JRootPane#setGlassPane()} method
     * automatically sets the <i>empty-shape</i> as the 'mixing-cutout' shape
     * for the given glass pane component.  If a developer needs some other
     * 'mixing-cutout' shape for the glass pane (which is rare), this must be
     * changed manually after installing the glass pane to the root pane.
     * <p>
     * Note that the 'mixing-cutout' shape neither affects painting, nor the
     * mouse events handling for the given component. It is used exclusively
     * for the purposes of the Heavyweight/Lightweight Components Mixing
     * feature.
     *
     * @param component the component that needs non-default
     * 'mixing-cutout' shape
     * @param shape the new 'mixing-cutout' shape
     * @throws NullPointerException if the component argument is {@code null}
     */
    public static void setComponentMixingCutoutShape(Component component,
            Shape shape)
    {
        if (component == null) {
            throw new NullPointerException(
                    "The component argument should not be null.");
        }

        AWTAccessor.getComponentAccessor().setMixingCutoutShape(component,
                shape);
    }
}

