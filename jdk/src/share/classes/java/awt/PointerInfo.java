/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.awt;

/**
 * A class that describes the pointer position.
 * It provides the <code>GraphicsDevice</code> where the
 * pointer is and the <code>Point</code> that represents
 * the coordinates of the pointer.
 * <p>
 * Instances of this class should be obtained via
 * {@link MouseInfo#getPointerInfo}.
 * The <code>PointerInfo</code> instance is not updated dynamically
 * as the mouse moves. To get the updated location, you must call
 * {@link MouseInfo#getPointerInfo} again.
 *
 * @see MouseInfo#getPointerInfo
 * @author      Roman Poborchiy
 * @since       1.5
 */

public class PointerInfo {

    private GraphicsDevice device;
    private Point location;

    /**
     * Package-private constructor to prevent instantiation.
     */
    PointerInfo(GraphicsDevice device, Point location) {
        this.device = device;
        this.location = location;
    }

    /**
     * Returns the <code>GraphicsDevice</code> where the mouse pointer
     * was at the moment this <code>PointerInfo</code> was created.
     *
     * @return   <code>GraphicsDevice</code> corresponding to the pointer
     * @since    1.5
     */
    public GraphicsDevice getDevice() {
        return device;
    }

    /**
     * Returns the <code>Point</code> that represents the coordinates
     * of the pointer on the screen. See {@link MouseInfo#getPointerInfo}
     * for more information about coordinate calculation for multiscreen
     * systems.
     *
     * @see MouseInfo
     * @see MouseInfo#getPointerInfo
     * @return   coordinates of mouse pointer
     * @since    1.5
     */
    public Point getLocation() {
        return location;
    }

}
