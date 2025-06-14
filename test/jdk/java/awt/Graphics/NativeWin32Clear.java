/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4216180
 * @summary This test verifies that Graphics2D.setBackground and clearRect
 *   performs correctly regardless of antialiasing hint.
 * @key headful
 * @run main NativeWin32Clear
 */

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;

public class NativeWin32Clear extends Frame {

    public void initialize() {
        setLocationRelativeTo(null);
        setSize(300, 200);
        setBackground(Color.red);
        setVisible(true);
    }

    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        Dimension d = getSize();
        g2.setBackground(Color.green);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
        g2.clearRect(0, 0, d.width / 2, d.height);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.clearRect(d.width / 2, 0, d.width / 2, d.height);
        g2.setColor(Color.black);
    }

    public void cleanup() {
        setVisible(false);
        dispose();
    }

    public void requestCoordinates(Rectangle r) {
        Insets insets = getInsets();
        Point location = getLocationOnScreen();
        Dimension size = getSize();
        r.x = location.x + insets.left + 5;
        r.y = location.y + insets.top + 5;
        r.width = size.width - (insets.left + insets.right + 10);
        r.height = size.height - (insets.top + insets.bottom + 10);
    }

    /*
     * Check color match within allowed deviation.
     * Prints first non-matching pixel coordinates and actual and expected values.
     * Returns true if image is filled with the provided color, false otherwise.
     */
    private boolean checkColor(BufferedImage img, Color c, int delta) {
        int cRed = c.getRed();
        int cGreen = c.getGreen();
        int cBlue = c.getBlue();
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y);
                int red = (rgb & 0x00ff0000) >> 16;
                int green = (rgb & 0x0000ff00) >> 8;
                int blue = rgb & 0x000000ff;
                if (cRed > (red + delta) || cRed < (red - delta)
                 || cGreen > (green + delta) || cGreen < (green - delta)
                 || cBlue > (blue + delta) || cBlue < (blue - delta)) {
                    System.err.println("Color at coordinates (" + x + ", " + y + ") does not match");
                    System.err.println("Expected color: " + c.getRGB());
                    System.err.println("Actual color: " + rgb);
                    System.err.println("Allowed deviation: " + delta);
                    return false;
                }
            }
        }
        return true;
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException {
        NativeWin32Clear test = new NativeWin32Clear();
        try {
            EventQueue.invokeAndWait(test::initialize);
            Robot robot = new Robot();
            Rectangle coords = new Rectangle();
            EventQueue.invokeAndWait(() -> {
                test.requestCoordinates(coords);
            });
            robot.delay(2000);
            robot.mouseMove(coords.x - 50, coords.y - 50);
            robot.waitForIdle();
            BufferedImage capture = robot.createScreenCapture(coords);
            robot.delay(2000);
            if (!test.checkColor(capture, Color.green, 5)) {
                throw new RuntimeException("Incorrect color encountered, check error log for details");
            }
        } finally {
            EventQueue.invokeAndWait(test::cleanup);
        }
    }
}
