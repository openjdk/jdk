/*
 * Copyright 2003-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
package sun.awt.X11;

import java.awt.*;
import java.awt.peer.*;
import sun.awt.X11GraphicsConfig;

class XRobotPeer implements RobotPeer {
    private X11GraphicsConfig   xgc = null;
    /*
     * native implementation uses some static shared data (pipes, processes)
     * so use a class lock to synchronize native method calls
     */
    static Object robotLock = new Object();

    XRobotPeer(GraphicsConfiguration gc) {
        this.xgc = (X11GraphicsConfig)gc;
        setup();
    }

    public void dispose() {
        // does nothing
    }

    public void mouseMove(int x, int y) {
        mouseMoveImpl(xgc, x, y);
    }

    public void mousePress(int buttons) {
        mousePressImpl(buttons);
    }

    public void mouseRelease(int buttons) {
        mouseReleaseImpl(buttons);
    }

    public void mouseWheel(int wheelAmt) {
    mouseWheelImpl(wheelAmt);
    }

    public void keyPress(int keycode) {
        keyPressImpl(keycode);
    }

    public void keyRelease(int keycode) {
        keyReleaseImpl(keycode);
    }

    public int getRGBPixel(int x, int y) {
        int pixelArray[] = new int[1];
        getRGBPixelsImpl(xgc, x, y, 1, 1, pixelArray);
        return pixelArray[0];
    }

    public int [] getRGBPixels(Rectangle bounds) {
        int pixelArray[] = new int[bounds.width*bounds.height];
        getRGBPixelsImpl(xgc, bounds.x, bounds.y, bounds.width, bounds.height, pixelArray);
        return pixelArray;
    }

    private static native synchronized void setup();

    private static native synchronized void mouseMoveImpl(X11GraphicsConfig xgc, int x, int y);
    private static native synchronized void mousePressImpl(int buttons);
    private static native synchronized void mouseReleaseImpl(int buttons);
    private static native synchronized void mouseWheelImpl(int wheelAmt);

    private static native synchronized void keyPressImpl(int keycode);
    private static native synchronized void keyReleaseImpl(int keycode);

    private static native synchronized void getRGBPixelsImpl(X11GraphicsConfig xgc, int x, int y, int width, int height, int pixelArray[]);
}
