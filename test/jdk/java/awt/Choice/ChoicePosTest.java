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

import java.awt.Choice;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/*
 * @test
 * @bug 4075194
 * @summary 4075194, Choice may not be displayed at the location requested
 * @key headful
 */

public class ChoicePosTest {

    private static Robot robot;
    private static Frame frame;
    private static final int GAP = 10;
    private static volatile Choice c1,c2;

    public static void main(String[] args) throws Exception {
        try {
            EventQueue.invokeAndWait(ChoicePosTest::createAndShowGUI);

            robot = new Robot();
            robot.waitForIdle();
            robot.delay(500);

            captureAndTestChoices();
        } finally {
            EventQueue.invokeAndWait(frame::dispose);
        }

        System.out.println("Passed");
    }

    private static void createAndShowGUI() {
        frame = new Frame("ChoicePosTest");
        Insets insets = frame.getInsets();
        frame.setSize( insets.left + 400 + insets.right, insets.top + 400 + insets.bottom );
        frame.setBackground(Color.RED);
        frame.setLayout(null);
        frame.setLocationRelativeTo(null);

        c1 = new Choice();
        c1.setBackground(Color.GREEN);
        frame.add( c1 );
        c1.setBounds( 20, 50, 100, 100 );

        c2 = new Choice();
        c2.setBackground(Color.GREEN);
        frame.add(c2);
        c2.addItem("One");
        c2.addItem("Two");
        c2.addItem("Three");
        c2.setBounds( 125, 50, 100, 100 );

        frame.validate();
        frame.setVisible(true);
    }

    private static void captureAndTestChoices() {
        Point c1loc = c1.getLocationOnScreen();
        Point c2loc = c2.getLocationOnScreen();

        int startX = c1loc.x - GAP;
        int startY = c1loc.y - GAP;
        int captureWidth = c2loc.x + c2.getWidth() + GAP - startX;
        int captureHeight = c2loc.y + c2.getHeight() + GAP - startY;

        BufferedImage bi = robot.createScreenCapture(
                new Rectangle(startX, startY, captureWidth, captureHeight)
        );

        int redPix = Color.RED.getRGB();

        int lastNonRedCount = 0;

        for (int y = 0; y < captureHeight; y++) {
            int nonRedCount = 0;
            for (int x = 0; x < captureWidth; x++) {
                int pix = bi.getRGB(x, y);
                if (pix != redPix) {
                    nonRedCount++;
                }
            }

            if (nonRedCount > 0 && lastNonRedCount > 0) {
                if (lastNonRedCount - nonRedCount > 0) {
                    System.err.printf(
                            "Failed at %d, nonRedCount: %d lastNonRedCount: %d\n",
                            y, nonRedCount, lastNonRedCount
                    );

                    try {
                        ImageIO.write(bi, "png", new File("choices.png"));
                    } catch (IOException ignored) {
                    }

                    throw new RuntimeException("Choices are not aligned");
                }
            }

            lastNonRedCount = nonRedCount;
        }
    }
}
