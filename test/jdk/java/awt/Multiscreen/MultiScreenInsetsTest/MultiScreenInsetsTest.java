/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @key headful
  @bug 8020443 6899304
  @summary Test to check if the frame is created on the specified GraphicsDevice
  and if getScreenInsets()returns the correct values across multiple monitors.
  @library /test/lib
  @build jdk.test.lib.Platform
  @run main MultiScreenInsetsTest
 */

import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;

import jdk.test.lib.Platform;

public class MultiScreenInsetsTest {
    private static final int SIZE = 100;

    public static void main(String[] args) throws InterruptedException {

        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gds = ge.getScreenDevices();
        if (gds.length < 2) {
            System.out.println("It's a multi-screen test... skipping!");
            return;
        }

        for (int screen = 0; screen < gds.length; ++screen) {
            GraphicsDevice gd = gds[screen];
            GraphicsConfiguration gc = gd.getDefaultConfiguration();
            Rectangle bounds = gc.getBounds();
            Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);

            Frame frame = new Frame(gc);
            frame.setLocation(bounds.x + (bounds.width - SIZE) / 2,
                              bounds.y + (bounds.height - SIZE) / 2);
            frame.setSize(SIZE, SIZE);

            /*
             * On Windows, undecorated maximized frames are placed over the taskbar.
             * Use a decorated frame instead.
             */
            if (Platform.isWindows()) {
                frame.setUndecorated(false);
            } else {
                frame.setUndecorated(true);
            }

            frame.setVisible(true);

            // Maximize Frame to reach the struts
            frame.setExtendedState(java.awt.Frame.MAXIMIZED_BOTH);
            Thread.sleep(2000);

            Rectangle frameBounds = frame.getBounds();
            frame.dispose();

            /*
             * On Windows, the top-left corner of an undecorated maximized frame may have negative coordinates (x, y).
             * Adjust the frame bounds accordingly.
             */
            if (frameBounds.x < bounds.x)
            {
                frameBounds.width -= (bounds.x - frameBounds.x) * 2;
                frameBounds.x = bounds.x;
            }
            if (frameBounds.y < bounds.y)
            {
                frameBounds.height -= (bounds.y - frameBounds.y) * 2;
                frameBounds.y = bounds.y;
            }

            // Add a margin to compensate for the lost fractional parts when casting to an integer.
            int marginX = getMarginForScaleX(gc);
            int marginY = getMarginForScaleY(gc);

            if (bounds.x + insets.left != frameBounds.x
                || bounds.y + insets.top != frameBounds.y
                || bounds.width - insets.right - insets.left + marginX != frameBounds.width
                || bounds.height - insets.bottom - insets.top + marginY != frameBounds.height) {
                throw new RuntimeException("Test FAILED! Wrong screen #" +
                                           screen + " insets: " + insets);
            }
        }
        System.out.println("Test PASSED!");
    }
    static int getMarginForScaleX(GraphicsConfiguration gc) {
        float scaleFactorX = (float) gc.getDefaultTransform().getScaleX();
        return (scaleFactorX % 1 == 0.5) ? 1 : 0;
    }
    static int getMarginForScaleY(GraphicsConfiguration gc) {
        float scaleFactorY = (float) gc.getDefaultTransform().getScaleY();
        return (scaleFactorY % 1 == 0.5) ? 1 : 0;
    }
}
