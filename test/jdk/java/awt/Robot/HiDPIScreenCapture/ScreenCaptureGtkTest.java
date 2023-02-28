/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, JetBrains s.r.o.. All rights reserved.
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
 * @bug 8280861
 * @summary  Verifies Robot screen capture capabilities with different
 *           Gtk backends and presence of UI scaling
 * @requires os.family == "linux"
 * @run main/othervm -Djdk.gtk.version=2 -Dsun.java2d.uiScale=1 ScreenCaptureGtkTest
 * @run main/othervm -Djdk.gtk.version=3 -Dsun.java2d.uiScale=1 ScreenCaptureGtkTest
 */

public class ScreenCaptureGtkTest {
    private static final Color[] COLORS = {
            Color.GREEN, Color.BLUE, Color.ORANGE, Color.RED};

    public static void main(String[] args) throws Exception {
        final int topOffset = 50;
        final int leftOffset = 50;

        Frame frame = new Frame();
        // Position the frame such that color picker will work with
        // prime number coordinates (mind the offset) to avoid them being
        // multiple of the desktop scale; this tests Linux color picker better.
        // Also, the position should be far enough from the top left
        // corner of the screen to reduce the chance of being repositioned
        // by the system because that area's occupied by the global
        // menu bar and such.
        frame.setBounds(89, 99, 100, 100);
        frame.setUndecorated(true);

        Panel panel = new Panel(new BorderLayout());
        Canvas canvas = new Canvas() {
            @Override
            public void paint(Graphics g) {
                super.paint(g);
                int w = getWidth();
                int h = getHeight();
                g.setColor(COLORS[0]);
                g.fillRect(0, 0, w, h);
                // Paint several distinct pixels next to one another
                // in order to test color picker's precision.
                for (int i = 1; i < COLORS.length; i++) {
                    g.setColor(COLORS[i]);
                    g.fillRect(leftOffset + i, topOffset, 1, 1);
                }
            }
        };

        panel.add(canvas);
        frame.add(panel);
        frame.setVisible(true);
        Robot robot = new Robot();
        robot.waitForIdle();
        robot.delay(500);

        captureImageOf(frame, robot);

        final Point screenLocation = frame.getLocationOnScreen();
        try {
            checkPixelColors(robot, screenLocation.x + leftOffset,
                    screenLocation.y + topOffset);
        } finally {
            robot.delay(100);
            frame.dispose();
        }
    }

    static void checkPixelColors(Robot robot, int x, int y) {
        for (int i = 0; i < COLORS.length; i++) {
            final Color actualColor = robot.getPixelColor(x + i, y);
            System.out.print("Checking color at " + (x + i) + ", " + y + " to be equal to " + COLORS[i]);
            if (!actualColor.equals(COLORS[i])) {
                System.out.println("... Mismatch: found " + actualColor + " instead");
                saveImage();
                throw new RuntimeException("Wrong screen pixel color");

            } else {
                System.out.println("... OK");
            }
        }
    }

    private static BufferedImage image;

    static void captureImageOf(Frame frame, Robot robot) {
        Rectangle rect = frame.getBounds();
        rect.setLocation(frame.getLocationOnScreen());

        System.out.println("Creating screen capture of " + rect);
        image = robot.createScreenCapture(rect);
    }

    static void saveImage() {
        System.out.println("Check image.png");
        try {
            ImageIO.write(image, "png", new File("image.png"));
        } catch(IOException e) {
            System.out.println("failed to save image.png.");
            e.printStackTrace();
        }
    }
}
