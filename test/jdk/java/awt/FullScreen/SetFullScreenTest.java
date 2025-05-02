/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Color;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Robot;
import jtreg.SkippedException;

import static java.awt.EventQueue.invokeAndWait;

/*
 * @test
 * @key headful
 * @bug 8312518
 * @library /test/lib
 * @summary Setting fullscreen window using setFullScreenWindow() shows up
 *          as black screen on newer macOS versions (13 & 14).
 */

public class SetFullScreenTest {
    private static Frame frame;
    private static GraphicsDevice gd;
    private static Robot robot;
    private static volatile int width;
    private static volatile int height;

    public static void main(String[] args) throws Exception {
        try {
            robot = new Robot();
            invokeAndWait(() -> {
                gd = GraphicsEnvironment.getLocalGraphicsEnvironment().
                        getDefaultScreenDevice();
                if (!gd.isFullScreenSupported()) {
                    throw new SkippedException("Full Screen mode not supported");
                }
            });

            invokeAndWait(() -> {
                frame = new Frame("Test FullScreen mode");
                frame.setBackground(Color.RED);
                frame.setSize(100, 100);
                frame.setLocation(10, 10);
                frame.setVisible(true);
            });
            robot.delay(1000);

            invokeAndWait(() -> gd.setFullScreenWindow(frame));
            robot.waitForIdle();
            robot.delay(300);

            invokeAndWait(() -> {
                width = gd.getFullScreenWindow().getWidth();
                height = gd.getFullScreenWindow().getHeight();
            });

            if (!robot.getPixelColor(width / 2, height / 2).equals(Color.RED)) {
                System.err.println("Actual color: " + robot.getPixelColor(width / 2, height / 2)
                                    + " Expected color: " + Color.RED);
                throw new RuntimeException("Test Failed! Window not in full screen mode");
            }
        } finally {
            if (frame != null) {
                frame.dispose();
            }
        }
    }
}
