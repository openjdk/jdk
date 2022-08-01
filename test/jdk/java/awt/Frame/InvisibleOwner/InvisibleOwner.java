/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @key headful
  @bug 7154177 8285094
  @summary An invisible owner frame should never become visible
  @run main InvisibleOwner
*/

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class InvisibleOwner {

    private static volatile boolean invisibleOwnerClicked = false;
    private static volatile boolean backgroundClicked = false;

    private static final int F_X = 200, F_Y = 200, F_W = 200, F_H = 200;
    private static final int H_X = F_X - 10, H_Y = F_Y - 10, H_W = F_W + 20, H_H = F_H + 20;
    private static final int C_X = F_X + F_W / 2, C_Y = F_Y + F_H / 2;
    static final Color helperFrameBgColor = Color.blue;
    static final Color invisibleFrameBgColor = Color.green;
    static Frame invisibleFrame;
    static Frame helperFrame;
    static Window ownedWindow;
    static Robot robot;

    static void createUI() {
        /* A background frame to compare a pixel color against
         * It should be centered in the same location as the invisible
         * frame but extend beyond its bounds.
         */
        helperFrame = new Frame("Background frame");
        helperFrame.setBackground(helperFrameBgColor);
        helperFrame.setLocation(H_X, H_Y);
        helperFrame.setSize(H_W, H_H);
        System.out.println("Helper requested bounds : x=" +
                           H_X + " y="+ H_Y +" w="+ H_W +" h="+ H_H);
        helperFrame.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent ev) {
                System.out.println("Background helper frame clicked");
                backgroundClicked = true;
            }
        });
        helperFrame.setVisible(true);

        /* An owner frame that should stay invisible but theoretical
         * bounds are within the helper frame.
         */
        invisibleFrame = new Frame("Invisible Frame");
        invisibleFrame.setBackground(invisibleFrameBgColor);
        invisibleFrame.setLocation(F_X, F_Y);
        invisibleFrame.setSize(F_W, F_H);
        invisibleFrame.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent ev) {
                System.out.println("Invisible owner clicked");
                invisibleOwnerClicked = true;
            }
        });

        /* An owned window of the invisible frame that is located
         * such that it does not overlap either the helper or
         * the invisisible frame.
         */
        ownedWindow = new Window(invisibleFrame);
        ownedWindow.setBackground(Color.RED);
        ownedWindow.setLocation(H_X+H_W+100, H_Y+H_W+100);
        ownedWindow.setSize(100, 100);
        ownedWindow.setVisible(true);

        Toolkit.getDefaultToolkit().sync();
    }

    static void captureScreen() throws Exception {
        System.out.println("Writing screen capture");
        Rectangle screenRect = helperFrame.getGraphicsConfiguration().getBounds();
        java.awt.image.BufferedImage bi = robot.createScreenCapture(screenRect);
        javax.imageio.ImageIO.write(bi, "png", new java.io.File("screen_IO.png"));
    }

    public static void main(String[] args) throws Exception {

        try {
            EventQueue.invokeAndWait(() -> createUI());
            robot = new Robot();
            robot.waitForIdle();
            robot.setAutoDelay(100);
            robot.setAutoWaitForIdle(true);

            Rectangle helperBounds = helperFrame.getBounds();
            System.out.println("helperFrame bounds = " + helperBounds);
            if (!helperBounds.contains(C_X, C_Y)) {
                System.out.println("Helper not positioned where it needs to be");
                return;
            }

            // Clicking the owned window shouldn't make its owner visible
            Rectangle ownedWindowBounds = ownedWindow.getBounds();
            robot.mouseMove(ownedWindowBounds.x + ownedWindowBounds.width / 2,
                            ownedWindowBounds.y + ownedWindowBounds.height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(1000);

            // 1. Check the color at the center of the invisible & helper frame location
            Color c = robot.getPixelColor(C_X, C_Y);
            System.out.println("Sampled pixel at " + C_X +"," + C_Y);
            System.out.println("Pixel color: " + c);
            if (c == null) {
                captureScreen();
                throw new RuntimeException("Robot.getPixelColor() failed");
            }
            if (c.equals(invisibleFrameBgColor)) {
                captureScreen();
                throw new RuntimeException("The invisible frame has become visible");
            }
            if (!c.equals(helperFrameBgColor)) {
                captureScreen();
                throw new RuntimeException(
                    "Background frame was covered by something unexpected");
            }

            // 2. Try to click it - event should be delivered to the
            // helper frame, not the invisible frame.
            robot.mouseMove(C_X, C_Y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(1000);

            if (invisibleOwnerClicked) {
                captureScreen();
                throw new RuntimeException(
                    "The invisible owner frame got clicked. Looks like it became visible.");
            }
            if (!backgroundClicked) {
                captureScreen();
                throw new RuntimeException(
                    "The background helper frame hasn't been clicked");
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (ownedWindow != null) ownedWindow.dispose();
                if (invisibleFrame != null) invisibleFrame.dispose();
                if (helperFrame != null) helperFrame.dispose();
            });
        }

    }
}
