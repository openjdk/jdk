/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/*
 * @test
 * @bug 4237529
 * @key headful
 * @summary Test repainting of an empty frame
 * @run main EmptyFrameTest
 */

public class EmptyFrameTest {
    private static Frame f;
    private static Robot robot;
    private static volatile Point p;
    private static volatile Dimension d;
    private static final int TOLERANCE = 5;
    public static void main(String[] args) throws Exception {
        robot = new Robot();
        robot.setAutoDelay(100);
        try {
            EventQueue.invokeAndWait(() -> {
                createAndShowUI();
            });
            robot.delay(1000);
            f.setSize(50, 50);
            robot.delay(500);
            EventQueue.invokeAndWait(() -> {
                p = f.getLocation();
                d = f.getSize();
            });
            Rectangle rect = new Rectangle(p, d);
            BufferedImage img = robot.createScreenCapture(rect);
            if (chkImgBackgroundColor(img)) {
                try {
                    ImageIO.write(img, "png", new File("Frame.png"));
                } catch (IOException ignored) {}
                throw new RuntimeException("Frame doesn't repaint itself on resize");
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (f != null) {
                    f.dispose();
                }
            });
        }
    }

    private static void createAndShowUI() {
        f = new Frame("EmptyFrameTest");
        f.setUndecorated(true);
        f.setBackground(Color.RED);
        f.setVisible(true);
    }

    private static boolean chkImgBackgroundColor(BufferedImage img) {
        for (int x = 1; x < img.getWidth() - 1; ++x) {
            for (int y = 1; y < img.getHeight() - 1; ++y) {
                Color c = new Color(img.getRGB(x, y));
                if ((c.getRed() - Color.RED.getRed()) > TOLERANCE) {
                    return true;
                }
            }
        }
        return false;
    }
}
