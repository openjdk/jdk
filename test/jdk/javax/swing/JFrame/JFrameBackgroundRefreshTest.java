/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/*
 * @test
 * @key headful
 * @bug 8187759
 * @summary Test to check if JFrame background is refreshed in Linux.
 * @requires (os.family == "linux")
 * @run main JFrameBackgroundRefreshTest
 */

public class JFrameBackgroundRefreshTest {
    public static JFrame frame;
    private static final BufferedImage test = generateImage();
    private static Point p = new Point();
    private static Robot robot;
    private static JFrame whiteFrame;
    private static Point frameLocation;
    private static int frameCenterX, frameCenterY, awayX, awayY;
    private static int imageCenterX, imageCenterY;

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    JFrameBackgroundRefreshTest.initialize();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            SwingUtilities.invokeAndWait(() -> {
                frameLocation = whiteFrame.getLocationOnScreen();
                frameCenterX = frameLocation.x + whiteFrame.getWidth() / 2;
                frameCenterY = frameLocation.y + whiteFrame.getHeight() / 2;
                awayX = frameLocation.x + whiteFrame.getWidth() + 100;
                awayY = frameLocation.y + whiteFrame.getHeight() / 2;
                imageCenterX = p.x + test.getWidth() / 2;
                imageCenterY = p.y + test.getHeight() / 2;
            });
            robot.delay(100);
            robot.waitForIdle();
            robot.mouseMove(imageCenterX, imageCenterY);
            robot.delay(100);
            robot.waitForIdle();
            moveMouseSlowly(frameCenterX, frameCenterY);
            robot.delay(1000);
            robot.waitForIdle();

            moveMouseSlowly(awayX, awayY);
            robot.delay(100);
            robot.waitForIdle();
            Rectangle screenCaptureRect = new Rectangle(frameCenterX - 50,
                    frameCenterY - 50, 100, 100);
            BufferedImage bufferedImage = robot.createScreenCapture(screenCaptureRect);

            if (!compareImages(bufferedImage)) {
                try {
                    ImageIO.write(bufferedImage, "png",
                            new File("FailureImage.png"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                throw new RuntimeException("Test Failed!");
            }
            System.out.println("Test Passed!");
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
                if (whiteFrame != null) {
                    whiteFrame.dispose();
                }
            });
        }
    }

    private static void moveMouseSlowly( int targetX, int targetY) {
        Point currentMousePos = MouseInfo.getPointerInfo().getLocation();
        int currentX = currentMousePos.x;
        int currentY = currentMousePos.y;
        int deltaX = targetX - currentX;
        int deltaY = targetY - currentY;
        int steps = 50;
        double stepX = (double) deltaX / steps;
        double stepY = (double) deltaY / steps;
        for (int i = 1; i <= steps; i++) {
            int nextX = currentX + (int) Math.round(i * stepX);
            int nextY = currentY + (int) Math.round(i * stepY);
            robot.mouseMove(nextX, nextY);
            robot.delay(10);
        }
        robot.mouseMove(targetX, targetY);
    }

    private static boolean compareImages(BufferedImage bufferedImage) {
        int sampleRGB = bufferedImage.getRGB(0,0);
        for (int x = 0; x < bufferedImage.getWidth(); x++) {
            for (int y = 0; y < bufferedImage.getHeight(); y++) {
                if (bufferedImage.getRGB(x, y) != sampleRGB) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void initialize() throws Exception {
        frame = new JFrame("JFrame Background refresh test");
        whiteFrame = new JFrame("White Frame");
        robot = new Robot();
        whiteFrame.setSize(200, 200);
        whiteFrame.setBackground(Color.WHITE);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setUndecorated(true);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setBackground(new Color(0, 0, 0, 0));
        frame.setContentPane(new TranslucentPane());
        frame.addMouseMotionListener(new MouseDragListener());
        whiteFrame.setLocationRelativeTo(null);
        whiteFrame.setVisible(true);
        frame.setVisible(true);
        frame.setAlwaysOnTop(true);
    }
    private static class MouseDragListener extends MouseAdapter {
        @Override
        public void mouseMoved(MouseEvent e) {
            p = e.getPoint();
            frame.repaint();
        }
    }

    /** Capture an image of any component **/
    private static BufferedImage getImage(Component c) {
        if (c == null) {
            return null;
        }
        BufferedImage image = new BufferedImage(c.getWidth(),
                c.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        c.printAll(g);
        g.dispose();
        return image;
    }

    /** Generates a dummy image to be painted on the frame **/
    private static BufferedImage generateImage() {
        JLabel label = new JLabel("test");
        label.setFont(new Font("Arial", Font.BOLD, 24));
        label.setSize(label.getPreferredSize());
        return getImage(label);
    }

    public static class TranslucentPane extends JPanel {
        public TranslucentPane() {
            setOpaque(false);
        }
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setColor(new Color(0,0,0,0));
            g2d.fillRect(0, 0, getWidth(), getHeight());
            g2d.drawImage(test, p.x, p.y, this);
            g2d.dispose();
        }
    }
}
