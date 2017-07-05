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

package sun.awt.motif;

import sun.awt.X11CustomCursor;
import sun.awt.CustomCursor;
import java.awt.*;
import java.awt.image.*;
import sun.awt.image.ImageRepresentation;

public class MCustomCursor extends X11CustomCursor {

    public MCustomCursor(Image cursor, Point hotSpot, String name)
            throws IndexOutOfBoundsException {
        super(cursor, hotSpot, name);
    }
    /**
     * Returns the supported cursor size
     */
    public static Dimension getBestCursorSize(
        int preferredWidth, int preferredHeight) {

        // Fix for bug 4212593 The Toolkit.createCustomCursor does not
        //                     check absence of the image of cursor
        // We use XQueryBestCursor which accepts unsigned ints to obtain
        // the largest cursor size that could be dislpayed
        Dimension d = new Dimension(Math.abs(preferredWidth), Math.abs(preferredHeight));

        queryBestCursor(d);
        return d;
    }

    private static native void queryBestCursor(Dimension d);

    protected native void createCursor(byte[] xorMask, byte[] andMask,
                                     int width, int height,
                                     int fcolor, int bcolor,
                                     int xHotSpot, int yHotSpot);

    static {
        cacheInit();
    }

    private native static void cacheInit();
}
