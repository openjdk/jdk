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

import java.awt.Checkbox;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;

/*
 * @test
 * @key headful
 * @bug 8233068
 * @summary Tests checkbox checker on scaling
 * @requires (os.family == "linux")
 * @run main CheckboxCheckerScalingTest
 */

public class CheckboxCheckerScalingTest {
    private static Frame frame;
    private static Checkbox checkbox;
    private static BufferedImage imageAfterChecked;
    private static volatile boolean checkmarkFound = false;

    public static void main(String[] args) throws Exception {
        System.setProperty("sun.java2d.uiScale", "2");
        Robot robot = new Robot();
        try {
            EventQueue.invokeAndWait(() -> {
                frame = new Frame("ComboBox checker scaling test");
                checkbox = new Checkbox("one");
                checkbox.setState(true);
                frame.add(checkbox);
                frame.pack();
                frame.setVisible(true);
            });

            robot.waitForIdle();
            robot.delay(100);
            EventQueue.invokeAndWait(() -> {
                Point point = checkbox.getLocationOnScreen();
                Rectangle rect = new Rectangle(point.x + 5, point.y + 7, 8, 8);
                imageAfterChecked = robot.createScreenCapture(rect);

                check: {
                    for (int i = 0; i < imageAfterChecked.getHeight(); i++) {
                        for (int j = 0; j < imageAfterChecked.getWidth(); j++) {
                            if (Color.black.getRGB() == imageAfterChecked.getRGB(i, j)) {
                                checkmarkFound = true;
                                break check;
                            }
                        }
                    }
                }
            });

            if (!checkmarkFound) {
                throw new RuntimeException("Checkmark not scaled");
            }
            System.out.println("Test Passed");
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
