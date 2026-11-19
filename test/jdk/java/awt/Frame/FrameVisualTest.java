/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/*
 * @test
 * @bug 4328588
 * @key headful
 * @summary Non-default visual on top-level Frame should work
 * @run main FrameVisualTest
 */

public class FrameVisualTest {
    private static GraphicsConfiguration[] gcs;
    private static volatile Frame[] frames;

    private static Robot robot;
    private static volatile int frameNum;
    private static volatile Point p;
    private static volatile Dimension d;
    private static final int TOLERANCE = 5;
    private static final int MAX_FRAME_COUNT = 30;

    public static void main(String[] args) throws Exception {
        gcs = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getConfigurations();
        robot = new Robot();
        robot.setAutoDelay(100);

        // Limit the number of frames tested if needed
        if (gcs.length > MAX_FRAME_COUNT) {
            frames = new Frame[MAX_FRAME_COUNT];
        } else {
            frames = new Frame[gcs.length];
        }
        System.out.println(gcs.length + " gcs found. Testing "
                + frames.length + " frame(s).");

        for (frameNum = 0; frameNum < frames.length; frameNum++) {
            try {
                EventQueue.invokeAndWait(() -> {
                    frames[frameNum] = new Frame("Frame w/ gc "
                            + frameNum, gcs[frameNum]);
                    frames[frameNum].setSize(100, 100);
                    frames[frameNum].setUndecorated(true);
                    frames[frameNum].setBackground(Color.WHITE);
                    frames[frameNum].setVisible(true);
                    System.out.println("Frame " + frameNum + " created");
                });

                robot.delay(1000);

                EventQueue.invokeAndWait(() -> {
                    p = frames[frameNum].getLocation();
                    d = frames[frameNum].getSize();
                });

                Rectangle rect = new Rectangle(p, d);
                BufferedImage img = robot.createScreenCapture(rect);
                if (chkImgBackgroundColor(img)) {
                    try {
                        ImageIO.write(img, "png",
                                new File("Frame_"
                                        + frameNum + ".png"));
                    } catch (IOException ignored) {}
                    throw new RuntimeException("Frame visual test " +
                            "failed with non-white background color");
                }
            } finally {
                EventQueue.invokeAndWait(() -> {
                    if (frames[frameNum] != null) {
                        frames[frameNum].dispose();
                        System.out.println("Frame " + frameNum + " disposed");
                    }
                });
            }
        }
    }

    private static boolean chkImgBackgroundColor(BufferedImage img) {
        // scan for mid-line and if it is non-white color then return true.
        for (int x = 1; x < img.getWidth() - 1; ++x) {
            Color c = new Color(img.getRGB(x, img.getHeight() / 2));
            if ((c.getRed() - Color.WHITE.getRed()) > TOLERANCE &&
                    (c.getGreen() - Color.WHITE.getGreen()) > TOLERANCE &&
                    (c.getBlue() - Color.WHITE.getBlue()) > TOLERANCE) {
                return true;
            }
        }
        return false;
    }
}

