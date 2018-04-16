/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.Robot;
import java.awt.Color;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * @test
 * @key headful
 * @bug 8181910
 * @requires (os.family == "mac")
 * @summary [macos] Support dark title bars on macOS
 * @run main DarkTitleBar
 */

public final class DarkTitleBar {
    private static BufferedImage image = null;
    private static Rectangle bounds;
    private static JFrame frame;
    private static Robot robot;

    public static void main(final String[] args) throws Exception {
        robot = new Robot();
        robot.setAutoDelay(50);

        // Capture and compare pixel color
        testFrame();
    }

    private static void testFrame() throws Exception {
        createUI();
        image = robot.createScreenCapture(bounds);
        SwingUtilities.invokeAndWait(frame::dispose);
        robot.waitForIdle();

        Color titleColor = new Color(image.getRGB(100, 5), true);
        if(titleColor.getRed() > 50 || titleColor.getGreen() > 50 || titleColor.getBlue() > 50) {
            throw new RuntimeException("Test failed: Title bar is not of dark colored - " + titleColor);
        }
    }

    private static void createUI() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            frame = new JFrame();
            frame.setUndecorated(false);
            frame.setSize(400, 400);
            frame.setLocationRelativeTo(null);
            frame.getRootPane().putClientProperty("apple.awt.windowDarkAppearance", true);
            frame.setVisible(true);
        });

        robot.waitForIdle();
        SwingUtilities.invokeAndWait(() -> {
            bounds = frame.getBounds();
        });
        robot.waitForIdle();

        robot.mouseMove(frame.getX() + 100, frame.getY() + 5); // pixel at which color will be captured
        robot.waitForIdle();
    }
}
