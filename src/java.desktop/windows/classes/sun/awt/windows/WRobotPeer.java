/*
 * Copyright (c) 1998, 2014, Oracle and/or its affiliates. All rights reserved.
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

package sun.awt.windows;

import java.awt.*;
import java.awt.peer.RobotPeer;

final class WRobotPeer extends WObjectPeer implements RobotPeer
{
    WRobotPeer() {
        create();
    }
    WRobotPeer(GraphicsDevice screen) {
        create();
    }

    private synchronized native void _dispose();

    @Override
    protected void disposeImpl() {
        _dispose();
    }

    public native void create();
    public native void mouseMoveImpl(int x, int y);
    @Override
    public void mouseMove(int x, int y) {
        mouseMoveImpl(x, y);
    }
    @Override
    public native void mousePress(int buttons);
    @Override
    public native void mouseRelease(int buttons);
    @Override
    public native void mouseWheel(int wheelAmt);

    @Override
    public native void keyPress( int keycode );
    @Override
    public native void keyRelease( int keycode );

    @Override
    public int getRGBPixel(int x, int y) {
         // See 7002846: that's ineffective, but works correctly with non-opaque windows
        return getRGBPixels(new Rectangle(x, y, 1, 1))[0];
    }

    @Override
    public int [] getRGBPixels(Rectangle bounds) {
        int pixelArray[] = new int[bounds.width*bounds.height];
        getRGBPixels(bounds.x, bounds.y, bounds.width, bounds.height, pixelArray);
        return pixelArray;
    }

    private native void getRGBPixels(int x, int y, int width, int height, int pixelArray[]);
}
