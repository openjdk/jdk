/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

import jtreg.SkippedException;

/*
 * @test
 * @key headful
 * @bug 4862945
 * @summary Undecorated frames miss certain mwm functions in the mwm hints.
 * @library /test/lib
 * @build jtreg.SkippedException
 * @run main MaximizeUndecoratedTest
 */

public class MaximizeUndecoratedTest {
    private static final int SIZE = 300;
    private static final int OFFSET = 2;

    private static Frame frame;
    private static Robot robot;

    private static volatile Dimension screenSize;
    private static volatile Rectangle maxBounds;

    public static void main(String[] args) throws Exception {
        if (!Toolkit.getDefaultToolkit()
                    .isFrameStateSupported(Frame.MAXIMIZED_BOTH)) {
            throw new SkippedException("Test is not applicable as"
                    + " the Window manager does not support MAXIMIZATION");
        }

        try {
            robot = new Robot();

            EventQueue.invokeAndWait(MaximizeUndecoratedTest::createUI);
            robot.waitForIdle();
            robot.delay(1000);

            EventQueue.invokeAndWait(() -> {
                screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                maxBounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
                                               .getMaximumWindowBounds();
                System.out.println("Maximum Window Bounds: " + maxBounds);
                frame.setExtendedState(Frame.MAXIMIZED_BOTH);
            });
            robot.waitForIdle();
            robot.delay(500);

            // Colors sampled at top-left, top-right, bottom-right & bottom-left
            // corners of maximized frame.
            Point[] points = new Point[] {
                    new Point(maxBounds.x + OFFSET, maxBounds.y + OFFSET),
                    new Point(maxBounds.width - OFFSET, maxBounds.y + OFFSET),
                    new Point(maxBounds.width - OFFSET, maxBounds.height - OFFSET),
                    new Point(maxBounds.x + OFFSET, maxBounds.height - OFFSET)
            };

            if (!Stream.of(points)
                       .map(p -> robot.getPixelColor(p.x, p.y))
                       .allMatch(c -> c.equals(Color.GREEN))) {
                saveScreenCapture();
                throw new RuntimeException("Test Failed !! Frame not maximized.");
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.setExtendedState(Frame.NORMAL);
                    frame.dispose();
                }
            });
        }
    }

    private static void createUI() {
        frame = new Frame("Test Maximization of Frame");
        frame.setSize(SIZE, SIZE);
        frame.setBackground(Color.GREEN);
        frame.setResizable(true);
        frame.setUndecorated(true);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void saveScreenCapture() {
        BufferedImage image = robot.createScreenCapture(new Rectangle(new Point(),
                                                                      screenSize));
        try {
            ImageIO.write(image, "png", new File("MaximizedFrame.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
