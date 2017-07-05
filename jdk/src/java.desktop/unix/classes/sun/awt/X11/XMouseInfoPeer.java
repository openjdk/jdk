/*
 * Copyright (c) 2003, 2006, Oracle and/or its affiliates. All rights reserved.
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

package sun.awt.X11;

import java.awt.Point;
import java.awt.Window;
import java.awt.GraphicsEnvironment;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.peer.MouseInfoPeer;

public class XMouseInfoPeer implements MouseInfoPeer {

    /**
     * Package-private constructor to prevent instantiation.
     */
    XMouseInfoPeer() {
    }

    public int fillPointWithCoords(Point point) {
        long display = XToolkit.getDisplay();
        GraphicsEnvironment ge = GraphicsEnvironment.
                                     getLocalGraphicsEnvironment();
        GraphicsDevice[] gds = ge.getScreenDevices();
        int gdslen = gds.length;

        XToolkit.awtLock();
        try {
            for (int i = 0; i < gdslen; i++) {
                long screenRoot = XlibWrapper.RootWindow(display, i);
                boolean pointerFound = XlibWrapper.XQueryPointer(
                                           display, screenRoot,
                                           XlibWrapper.larg1,  // root_return
                                           XlibWrapper.larg2,  // child_return
                                           XlibWrapper.larg3,  // xr_return
                                           XlibWrapper.larg4,  // yr_return
                                           XlibWrapper.larg5,  // xw_return
                                           XlibWrapper.larg6,  // yw_return
                                           XlibWrapper.larg7); // mask_return
                if (pointerFound) {
                    point.x = Native.getInt(XlibWrapper.larg3);
                    point.y = Native.getInt(XlibWrapper.larg4);
                    return i;
                }
            }
        } finally {
            XToolkit.awtUnlock();
        }

        // this should never happen
        assert false : "No pointer found in the system.";
        return 0;
    }

    public boolean isWindowUnderMouse(Window w) {

        long display = XToolkit.getDisplay();

        // java.awt.Component.findUnderMouseInWindow checks that
        // the peer is non-null by checking that the component
        // is showing.

        long contentWindow = ((XWindow)w.getPeer()).getContentWindow();
        long parent = XlibUtil.getParentWindow(contentWindow);

        XToolkit.awtLock();
        try
        {
            boolean windowOnTheSameScreen = XlibWrapper.XQueryPointer(display, parent,
                                  XlibWrapper.larg1, // root_return
                                  XlibWrapper.larg8, // child_return
                                  XlibWrapper.larg3, // root_x_return
                                  XlibWrapper.larg4, // root_y_return
                                  XlibWrapper.larg5, // win_x_return
                                  XlibWrapper.larg6, // win_y_return
                                  XlibWrapper.larg7); //  mask_return
            long siblingWindow = Native.getWindow(XlibWrapper.larg8);
            return (siblingWindow == contentWindow && windowOnTheSameScreen);
        }
        finally
        {
            XToolkit.awtUnlock();
        }
    }
}
