/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/*
 * @test
 * @key headful
 * @bug 1256759
 * @summary Checks that Frames with a very small size don't cause Motif
 * to generate VendorShells which consume the entire desktop.
 */

public class MinimumSizeTest {

    private static final Color BG_COLOR = Color.RED;
    private static Frame backgroundFrame;
    private static Frame testedFrame;

    private static Robot robot;
    private static final Point location = new Point(200, 200);
    private static final Point[] testPointLocations = {
            new Point(100, 200),
            new Point(200, 100),
            new Point(450, 210),
            new Point(210, 350),
    };

    public static void main(String[] args) throws Exception {
        robot = new Robot();

        try {
            EventQueue.invokeAndWait(MinimumSizeTest::initAndShowGui);
            robot.waitForIdle();
            robot.delay(500);
            test();
            System.out.println("Test passed.");
        } finally {
            EventQueue.invokeAndWait(() -> {
                backgroundFrame.dispose();
                testedFrame.dispose();
            });
        }
    }

    private static void test() {
        for (Point testLocation : testPointLocations) {
            Color pixelColor = robot.getPixelColor(testLocation.x, testLocation.y);

            if (!pixelColor.equals(BG_COLOR)) {
                Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                BufferedImage screenCapture = robot.createScreenCapture(new Rectangle(screenSize));
                try {
                    ImageIO.write(screenCapture, "png", new File("failure.png"));
                } catch (IOException ignored) {}
                throw new RuntimeException("Pixel color does not match expected color %s at %s"
                        .formatted(pixelColor, testLocation));
            }
        }
    }

    private static void initAndShowGui() {
        backgroundFrame = new Frame("MinimumSizeTest background");
        backgroundFrame.setUndecorated(true);
        backgroundFrame.setBackground(BG_COLOR);
        backgroundFrame.setBounds(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
        backgroundFrame.setVisible(true);

        testedFrame = new MinimumSizeTestFrame();
        testedFrame.setVisible(true);
    }

    private static class MinimumSizeTestFrame extends Frame {
        public MinimumSizeTestFrame() {
            super("MinimumSizeTest");
            setVisible(true);
            setBackground(Color.BLUE);
            setSize(0, 0);
            setLocation(location);
        }
    }
}

