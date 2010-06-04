/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.awt;

import java.awt.*;
import java.awt.geom.*;

import sun.awt.AWTAccessor;


/**
 * Security Warning control interface.
 *
 * This class provides a couple of methods that help a developer relocate
 * the AWT security warning to an appropriate position relative to the current
 * window size. A "top-level window" is an instance of the {@code Window}
 * class (or its descendant, such as {@code JFrame}). The security warning
 * is applied to all windows created by an untrusted code. All such windows
 * have a non-null "warning string" (see {@link Window#getWarningString()}).
 * <p>
 * <b>WARNING</b>: This class is an implementation detail and only meant
 * for limited use outside of the core platform. This API may change
 * drastically between update release, and it may even be
 * removed or be moved to some other packages or classes.
 */
public final class SecurityWarning {

    /**
     * The SecurityWarning class should not be instantiated
     */
    private SecurityWarning() {
    }

    /**
     * Gets the size of the security warning.
     *
     * The returned value is not valid until the peer has been created. Before
     * invoking this method a developer must call the {@link Window#pack()},
     * {@link Window#setVisible()}, or some other method that creates the peer.
     *
     * @param window the window to get the security warning size for
     *
     * @throws NullPointerException if the window argument is null
     * @throws IllegalArgumentException if the window is trusted (i.e.
     * the {@code getWarningString()} returns null)
     */
    public static Dimension getSize(Window window) {
        if (window == null) {
            throw new NullPointerException(
                    "The window argument should not be null.");
        }
        if (window.getWarningString() == null) {
            throw new IllegalArgumentException(
                    "The window must have a non-null warning string.");
        }
        // We don't check for a non-null peer since it may be destroyed
        // after assigning a valid value to the security warning size.

        return AWTAccessor.getWindowAccessor().getSecurityWarningSize(window);
    }

    /**
     * Sets the position of the security warning.
     * <p>
     * The {@code alignmentX} and {@code alignmentY} arguments specify the
     * origin of the coordinate system used to calculate the position of the
     * security warning. The values must be in the range [0.0f...1.0f].  The
     * {@code 0.0f} value represents the left (top) edge of the rectangular
     * bounds of the window. The {@code 1.0f} value represents the right
     * (bottom) edge of the bounds. Whenever the size of the window changes,
     * the origin of the coordinate system gets relocated accordingly. For
     * convenience a developer may use the {@code Component.*_ALIGNMENT}
     * constants to pass predefined values for these arguments.
     * <p>
     * The {@code point} argument specifies the location of the security
     * warning in the coordinate system described above. If both {@code x} and
     * {@code y} coordinates of the point are equal to zero, the warning will
     * be located right in the origin of the coordinate system. On the other
     * hand, if both {@code alignmentX} and {@code alignmentY} are equal to
     * zero (i.e. the origin of the coordinate system is placed at the top-left
     * corner of the window), then the {@code point} argument represents the
     * absolute location of the security warning relative to the location of
     * the window. The "absolute" in this case means that the position of the
     * security warning is not effected by resizing of the window.
     * <p>
     * Note that the security warning managment code guarantees that:
     * <ul>
     * <li>The security warning cannot be located farther than two pixels from
     * the rectangular bounds of the window (see {@link Window#getBounds}), and
     * <li>The security warning is always visible on the screen.
     * </ul>
     * If either of the conditions is violated, the calculated position of the
     * security warning is adjusted by the system to meet both these
     * conditions.
     * <p>
     * The default position of the security warning is in the upper-right
     * corner of the window, two pixels to the right from the right edge. This
     * corresponds to the following arguments passed to this method:
     * <ul>
     * <li>{@code alignmentX = Component.RIGHT_ALIGNMENT}
     * <li>{@code alignmentY = Component.TOP_ALIGNMENT}
     * <li>{@code point = (2, 0)}
     * </ul>
     *
     * @param window the window to set the position of the security warning for
     * @param alignmentX the horizontal origin of the coordinate system
     * @param alignmentY the vertical origin of the coordinate system
     * @param point the position of the security warning in the specified
     * coordinate system
     *
     * @throws NullPointerException if the window argument is null
     * @throws NullPointerException if the point argument is null
     * @throws IllegalArgumentException if the window is trusted (i.e.
     * the {@code getWarningString()} returns null
     * @throws IllegalArgumentException if the alignmentX or alignmentY
     * arguments are not within the range [0.0f ... 1.0f]
     */
    public static void setPosition(Window window, Point2D point,
            float alignmentX, float alignmentY)
    {
        if (window == null) {
            throw new NullPointerException(
                    "The window argument should not be null.");
        }
        if (window.getWarningString() == null) {
            throw new IllegalArgumentException(
                    "The window must have a non-null warning string.");
        }
        if (point == null) {
            throw new NullPointerException(
                    "The point argument must not be null");
        }
        if (alignmentX < 0.0f || alignmentX > 1.0f) {
            throw new IllegalArgumentException(
                    "alignmentX must be in the range [0.0f ... 1.0f].");
        }
        if (alignmentY < 0.0f || alignmentY > 1.0f) {
            throw new IllegalArgumentException(
                    "alignmentY must be in the range [0.0f ... 1.0f].");
        }

        AWTAccessor.getWindowAccessor().setSecurityWarningPosition(window,
                point, alignmentX, alignmentY);
    }
}

