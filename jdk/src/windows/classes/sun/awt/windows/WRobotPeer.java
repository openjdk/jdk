/*
 * Copyright 1998-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.awt.windows;

import java.awt.*;
import java.awt.peer.RobotPeer;

class WRobotPeer extends WObjectPeer implements RobotPeer
{
    WRobotPeer() {
        create();
    }
    WRobotPeer(GraphicsDevice screen) {
        create();
    }

    private synchronized native void _dispose();

    protected void disposeImpl() {
        _dispose();
    }

    public native void create();
    public native void mouseMoveImpl(int x, int y);
    public void mouseMove(int x, int y) {
        mouseMoveImpl(x, y);
    }
    public native void mousePress(int buttons);
    public native void mouseRelease(int buttons);
    public native void mouseWheel(int wheelAmt);

    public native void keyPress( int keycode );
    public native void keyRelease( int keycode );

    public int getRGBPixel(int x, int y) {
        return getRGBPixelImpl(x, y);
    }
    public native int getRGBPixelImpl(int x, int y);

    public int [] getRGBPixels(Rectangle bounds) {
        int pixelArray[] = new int[bounds.width*bounds.height];
        getRGBPixels(bounds.x, bounds.y, bounds.width, bounds.height, pixelArray);
        return pixelArray;
    }

    private native void getRGBPixels(int x, int y, int width, int height, int pixelArray[]);
}
