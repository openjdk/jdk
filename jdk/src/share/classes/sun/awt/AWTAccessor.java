/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
import sun.misc.Unsafe;

/** The AWTAccessor utility class.
 * The main purpose of this class is to enable accessing
 * private and package-private fields of classes from
 * different classes/packages. See sun.misc.SharedSecretes
 * for another example.
 */
public final class AWTAccessor {
    private static final Unsafe unsafe = Unsafe.getUnsafe();

    /** We don't need any objects of this class.
     * It's rather a collection of static methods
     * and interfaces.
     */
    private AWTAccessor() {
    }

    /** An accessor for the java.awt.Component class.
     */
    public interface ComponentAccessor {
        // See 6797587
        // Also see: 6776743, 6768307, and 6768332.
        /**
         * Sets the shape of a lw component to cut out from hw components.
         */
        void setMixingCutoutShape(Component comp, Shape shape);
    }

    /* The java.awt.Component class accessor object.
     */
    private static ComponentAccessor componentAccessor;

    /** Set an accessor object for the java.awt.Component class.
     */
    public static void setComponentAccessor(ComponentAccessor ca) {
        componentAccessor = ca;
    }

    /** Retrieve the accessor object for the java.awt.Window class.
     */
    public static ComponentAccessor getComponentAccessor() {
        if (componentAccessor == null) {
            unsafe.ensureClassInitialized(Component.class);
        }

        return componentAccessor;
    }
}
