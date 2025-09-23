/*
 * Copyright (c) 2010, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @key headful
 * @bug 6895647
 * @summary X11 Frame locations should be what we set them to
 * @run main FrameLocation
 */

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.io.File;

public class FrameLocation {
    private static final int X = 250;
    private static final int Y = 250;
    private static Frame f;
    private static volatile int xPos;
    private static volatile int yPos;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        try {
            EventQueue.invokeAndWait(() -> {
                f = new Frame("Frame Location Test");
                f.setBounds(X, Y, 250, 250); // the size doesn't matter
                f.setVisible(true);
            });

            for (int i = 0; i < 10; i++) {
                // 2 seconds must be enough for the WM to show the window
                robot.waitForIdle();
                robot.delay(2000);

                EventQueue.invokeAndWait(() -> {
                    // Check the location
                    xPos = f.getX();
                    yPos = f.getY();
                });

                if (xPos != X || yPos != Y) {
                    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                    ImageIO.write(robot.createScreenCapture(
                                    new Rectangle(0, 0, screenSize.width, screenSize.height)),
                            "png", new File("FailureImage.png"));
                    throw new RuntimeException("The frame location is wrong! Current: " +
                            xPos + ", " + yPos + ";  expected: " + X + ", " + Y);
                }

                // Emulate what happens when setGraphicsConfiguration() is called
                synchronized (f.getTreeLock()) {
                    f.removeNotify();
                    f.addNotify();
                }
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (f != null) {
                    f.dispose();
                }
            });
        }
    }
}
