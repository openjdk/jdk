/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.awt;

import java.awt.Component;
import java.awt.Shape;

import com.sun.awt.AWTUtilities;

/**
 * A class to allow access to JDK-specific utility methods.
 * Methods in this class are always deprecated since a caller
 * should be aware they may be removed and replaced in the future.
 * Access using reflection is highly recommended.
 * @since 9
 */
public final class AWTUtils {

    /**
     * No-one should be creating instances of this class.
     */
    private AWTUtils() {
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
     * @deprecated This API may be removed or replaced.
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public static void setComponentMixingCutoutShape(Component component,
                                                     Shape shape) {

        AWTUtilities.setComponentMixingCutoutShape(component, shape);
    }
}
