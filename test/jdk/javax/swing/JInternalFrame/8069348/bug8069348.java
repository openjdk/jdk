/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.event.InputEvent;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import static sun.awt.OSInfo.*;

/**
 * @test
 * @key headful
 * @bug 8069348 8159902 8198613
 * @summary SunGraphics2D.copyArea() does not properly work for scaled graphics
 * @author Alexandr Scherbatiy
 * @modules java.desktop/sun.awt
 * @run main/othervm -Dsun.java2d.uiScale=2 bug8069348
 * @run main/othervm -Dsun.java2d.d3d=true -Dsun.java2d.uiScale=2 bug8069348
 */
public class bug8069348 {

    private static final int WIN_WIDTH = 500;
    private static final int WIN_HEIGHT = 500;

    private static final Color DESKTOPPANE_COLOR = Color.YELLOW;
    private static final Color FRAME_COLOR = Color.ORANGE;

     // move away from cursor
    private final static int OFFSET_X = -20;
    private final static int OFFSET_Y = -20;

    private static JFrame frame;
    private static JInternalFrame internalFrame;

    public static void main(String[] args) throws Exception {

        if (!isSupported()) {
            return;
        }

        try {

            SwingUtilities.invokeAndWait(bug8069348::createAndShowGUI);

            Robot robot = new Robot();
            robot.setAutoDelay(100);
            robot.waitForIdle();

            Rectangle screenBounds = getInternalFrameScreenBounds();

            int x = screenBounds.x + screenBounds.width / 2;
            int y = screenBounds.y + 10;
            int dx = screenBounds.width / 2;
            int dy = screenBounds.height / 2;

            robot.mouseMove(x, y);
            robot.waitForIdle();

            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseMove(x + dx, y + dy);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();

            int cx = screenBounds.x + screenBounds.width + dx / 2;
            int cy = screenBounds.y + screenBounds.height + dy / 2;

            robot.mouseMove(cx, cy);
            robot.waitForIdle();
            Color color = robot.getPixelColor(cx - OFFSET_X, cy - OFFSET_Y);

            if (!FRAME_COLOR.equals(color)) {
                System.out.println("cx " + cx + " cy " + cy);
                System.err.println("FRAME_COLOR Red: " + FRAME_COLOR.getRed() + "; Green: " + FRAME_COLOR.getGreen() + "; Blue: " + FRAME_COLOR.getBlue());
                System.err.println("Pixel color Red: " + color.getRed() + "; Green: " + color.getGreen() + "; Blue: " + color.getBlue());

                Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                Rectangle screen = new Rectangle(0, 0, (int) screenSize.getWidth(), (int) screenSize.getHeight());
                BufferedImage img = robot.createScreenCapture(screen);
                javax.imageio.ImageIO.write(img, "png", new java.io.File("image.png"));

                throw new RuntimeException("Internal frame is not correctly dragged!");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
        System.out.println("Test Passed");
    }

    private static boolean isSupported() {
        String d3d = System.getProperty("sun.java2d.d3d");
        System.out.println("d3d " + d3d);
        return !Boolean.getBoolean(d3d) || getOSType() == OSType.WINDOWS;
    }

    private static Rectangle getInternalFrameScreenBounds() throws Exception {
        Rectangle[] points = new Rectangle[1];
        SwingUtilities.invokeAndWait(() -> {
            points[0] = new Rectangle(internalFrame.getLocationOnScreen(),
                    internalFrame.getSize());
        });
        return points[0];
    }

    private static void createAndShowGUI() {

        frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JDesktopPane desktopPane = new JDesktopPane();
        desktopPane.setBackground(DESKTOPPANE_COLOR);

        internalFrame = new JInternalFrame("Test") {

            @Override
            public void paint(Graphics g) {
                super.paint(g);
                g.setColor(FRAME_COLOR);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        internalFrame.setSize(WIN_WIDTH / 3, WIN_HEIGHT / 3);
        internalFrame.setVisible(true);
        desktopPane.add(internalFrame);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(desktopPane, BorderLayout.CENTER);
        frame.add(panel);
        frame.setSize(WIN_WIDTH, WIN_HEIGHT);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.requestFocus();
    }
}
