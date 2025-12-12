/*
 * Copyright (c) 2004, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @bug 5041225
 * @key headful
 * @summary Tests that we can set a display mode with unknown refresh rate
 *          if corresponding system display mode (with equal w/h/d) is available.
 * @run main DisplayModeNoRefreshTest
 */

import java.awt.Color;
import java.awt.DisplayMode;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;

public class DisplayModeNoRefreshTest extends Frame {

    private static DisplayModeNoRefreshTest fs;

    private static final GraphicsDevice gd =
            GraphicsEnvironment.getLocalGraphicsEnvironment()
                               .getDefaultScreenDevice();

    private static final DisplayMode origMode = gd.getDisplayMode();

    public DisplayModeNoRefreshTest() {
        super("DisplayModeNoRefreshTest");
        if (!gd.isFullScreenSupported()) {
            System.out.println("Full Screen is not supported, test considered passed.");
            return;
        }
        setBackground(Color.green);
        gd.setFullScreenWindow(this);
        DisplayMode dlMode = getNoRefreshDisplayMode(gd.getDisplayModes());
        if (dlMode != null) {
            System.out.println("Selected Display Mode: " +
                               " Width " + dlMode.getWidth() +
                               " Height " + dlMode.getHeight() +
                               " BitDepth " + dlMode.getBitDepth() +
                               " Refresh Rate " + dlMode.getRefreshRate());
            try {
                gd.setDisplayMode(dlMode);
            } catch (IllegalArgumentException ex) {
                throw new RuntimeException("Test Failed due to IAE", ex);
            }
        } else {
            System.out.println("No suitable display mode available, test considered passed.");
            return;
        }

        try { Thread.sleep(2000); } catch (InterruptedException e) {}

        System.out.println("Test Passed.");
    }

    public DisplayMode getNoRefreshDisplayMode(DisplayMode dm[]) {
        DisplayMode mode = new DisplayMode(640, 480, 32, DisplayMode.REFRESH_RATE_UNKNOWN);
        int i = 0;
        for (i = 0; i < dm.length; i++) {
            if (mode.getWidth() == dm[i].getWidth()
                && mode.getHeight() == dm[i].getHeight()
                && mode.getBitDepth() == dm[i].getBitDepth()) {
                return mode;
            }
        }
        if (dm.length > 0) {
            return
                new DisplayMode(dm[0].getWidth(), dm[0].getHeight(),
                                dm[0].getBitDepth(),
                                DisplayMode.REFRESH_RATE_UNKNOWN);
        }

        return null;
    }

    public static void main(String[] args) throws Exception {
        try {
            EventQueue.invokeAndWait(() -> {
                System.setProperty("sun.java2d.noddraw", "true");
                fs = new DisplayModeNoRefreshTest();
            });
        } finally {
            gd.setDisplayMode(origMode);
            EventQueue.invokeAndWait(() -> {
                if (fs != null) {
                    fs.dispose();
                }
            });
        }
    }
}
