/*
 * Copyright 2000-2001 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Capabilities and properties of images.
 * @author Michael Martak
 * @since 1.4
 */
public class ImageCapabilities implements Cloneable {

    private boolean accelerated = false;

    /**
     * Creates a new object for specifying image capabilities.
     * @param accelerated whether or not an accelerated image is desired
     */
    public ImageCapabilities(boolean accelerated) {
        this.accelerated = accelerated;
    }

    /**
     * Returns <code>true</code> if the object whose capabilities are
     * encapsulated in this <code>ImageCapabilities</code> can be or is
     * accelerated.
     * @return whether or not an image can be, or is, accelerated.  There are
     * various platform-specific ways to accelerate an image, including
     * pixmaps, VRAM, AGP.  This is the general acceleration method (as
     * opposed to residing in system memory).
     */
    public boolean isAccelerated() {
        return accelerated;
    }

    /**
     * Returns <code>true</code> if the <code>VolatileImage</code>
     * described by this <code>ImageCapabilities</code> can lose
     * its surfaces.
     * @return whether or not a volatile image is subject to losing its surfaces
     * at the whim of the operating system.
     */
    public boolean isTrueVolatile() {
        return false;
    }

    /**
     * @return a copy of this ImageCapabilities object.
     */
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            // Since we implement Cloneable, this should never happen
            throw new InternalError();
        }
    }

}
