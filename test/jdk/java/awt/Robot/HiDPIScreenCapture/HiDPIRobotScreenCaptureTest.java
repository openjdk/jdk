/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import javax.swing.UIManager;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

/**
 * @test
 * @key headful
 * @bug 8073320 8280861
 * @summary  Linux and Windows HiDPI support
 * @author Alexander Scherbatiy
 * @requires (os.family == "linux" | os.family == "windows")
 * @run main/othervm -Dsun.java2d.win.uiScaleX=3 -Dsun.java2d.win.uiScaleY=2
 *                    HiDPIRobotScreenCaptureTest
 * @run main/othervm -Dsun.java2d.uiScale=1 HiDPIRobotScreenCaptureTest
 * @run main/othervm -Dsun.java2d.uiScale=2 HiDPIRobotScreenCaptureTest
 */

public class HiDPIRobotScreenCaptureTest {

    private static final Color[] COLORS = {
        Color.GREEN, Color.BLUE, Color.ORANGE, Color.RED};

    public static void main(String[] args) throws Exception {

        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            try {
                UIManager.setLookAndFeel(
                        "com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
            } catch (Exception e) {
                return;
            }
        }

        Frame frame = new Frame();
        // Position the frame on prime number coordinates (mind OFFSET)
        // to avoid them being multiple of the desktop scale; this tests Linux
        // color picker better.
        // Also, the position should be far enough from the top left
        // corner of the screen to reduce the chance of being repositioned
        // by the system because that area's occupied by the global
        // menu bar and such.
        frame.setBounds(78, 92, 100, 100);
        frame.setUndecorated(true);

        Panel panel = new Panel(new BorderLayout());
        Canvas canvas = new Canvas() {
            @Override
            public void paint(Graphics g) {
                super.paint(g);
                int w = getWidth();
                int h = getHeight();
                g.setColor(COLORS[0]);
                g.fillRect(0, 0, w / 2, h / 2);
                g.setColor(COLORS[1]);
                g.fillRect(w / 2, 0, w / 2, h / 2);
                g.setColor(COLORS[2]);
                g.fillRect(0, h / 2, w / 2, h / 2);
                g.setColor(COLORS[3]);
                g.fillRect(w / 2, h / 2, w / 2, h / 2);
            }
        };

        panel.add(canvas);
        frame.add(panel);
        frame.setVisible(true);
        Robot robot = new Robot();
        robot.waitForIdle();
        robot.delay(500);

        Rectangle rect = canvas.getBounds();
        rect.setLocation(canvas.getLocationOnScreen());

        System.out.println("Creating screen capture of " + rect);
        BufferedImage image = robot.createScreenCapture(rect);
        frame.dispose();

        int w = image.getWidth();
        int h = image.getHeight();

        if (w != frame.getWidth() || h != frame.getHeight()) {
            throw new RuntimeException("Wrong image size!");
        }

        checkRectColor(image, new Rectangle(0, 0, w / 2, h / 2), COLORS[0]);
        checkRectColor(image, new Rectangle(w / 2, 0, w / 2, h / 2), COLORS[1]);
        checkRectColor(image, new Rectangle(0, h / 2, w / 2, h / 2), COLORS[2]);
        checkRectColor(image, new Rectangle(w / 2, h / 2, w / 2, h / 2), COLORS[3]);
    }

    private static final int OFFSET = 5;
    static void checkRectColor(BufferedImage image, Rectangle rect, Color expectedColor) {
        System.out.println("Checking rectangle " + rect + " to have color " + expectedColor);
        final Point[] pointsToCheck = new Point[] {
                new Point(rect.x + OFFSET, rect.y + OFFSET),                           // top left corner
                new Point(rect.x + rect.width - OFFSET, rect.y + OFFSET),              // top right corner
                new Point(rect.x + rect.width / 2, rect.y + rect.height / 2),          // center
                new Point(rect.x + OFFSET, rect.y + rect.height - OFFSET),             // bottom left corner
                new Point(rect.x + rect.width - OFFSET, rect.y + rect.height - OFFSET) // bottom right corner
        };

        for (final var point : pointsToCheck) {
            System.out.print("Checking color at " + point + " to be equal to " + expectedColor);
            final int actualColor = image.getRGB(point.x, point.y);
            if (actualColor != expectedColor.getRGB()) {
                System.out.println("... Mismatch: found " + new Color(actualColor) + " instead. Check image.png.");
                try {
                    ImageIO.write(image, "png", new File("image.png"));
                } catch(IOException e) {
                    System.out.println("failed to save image.png.");
                    e.printStackTrace();
                }
                throw new RuntimeException("Wrong image color!");
            } else {
                System.out.println("... OK");
            }
        }
    }
}
