/*
 * Copyright (c) 2010, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/*
 * @test
 * @key headful
 * @bug 6988428
 * @summary Tests whether shape is always set
 * @run main/othervm/timeout=300 -Dsun.java2d.uiScale=1 ShapeNotSetSometimes
 */

public class ShapeNotSetSometimes {

    private Frame backgroundFrame;
    private Frame window;

    private Point[] pointsOutsideToCheck;
    private Point[] shadedPointsToCheck;
    private Point innerPoint;

    private static Robot robot;
    private static final Color BACKGROUND_COLOR = Color.GREEN;
    private static final Color SHAPE_COLOR = Color.WHITE;
    private static final int DIM = 300;
    private static final int DELTA = 2;

    public ShapeNotSetSometimes() throws Exception {
        EventQueue.invokeAndWait(this::initializeGUI);
        robot.waitForIdle();
        robot.delay(500);
    }

    private void initializeGUI() {
        backgroundFrame = new BackgroundFrame();
        backgroundFrame.setUndecorated(true);
        backgroundFrame.setSize(DIM, DIM);
        backgroundFrame.setLocationRelativeTo(null);
        backgroundFrame.setVisible(true);

        Area area = new Area();
        area.add(new Area(new Rectangle2D.Float(100, 50, 100, 150)));
        area.add(new Area(new Rectangle2D.Float(50, 100, 200, 50)));
        area.add(new Area(new Ellipse2D.Float(50, 50, 100, 100)));
        area.add(new Area(new Ellipse2D.Float(50, 100, 100, 100)));
        area.add(new Area(new Ellipse2D.Float(150, 50, 100, 100)));
        area.add(new Area(new Ellipse2D.Float(150, 100, 100, 100)));

        // point at the center of white ellipse
        innerPoint = new Point(150, 130);

        // mid points on the 4 sides - on the green background frame
        pointsOutsideToCheck = new Point[] {
                new Point(150, 20),
                new Point(280, 120),
                new Point(150, 250),
                new Point(20, 120)
        };

        // points just outside the ellipse (opposite side of diagonal)
        shadedPointsToCheck = new Point[] {
                new Point(62, 62),
                new Point(240, 185)
        };

        window = new TestFrame();
        window.setUndecorated(true);
        window.setSize(DIM, DIM);
        window.setLocationRelativeTo(null);
        window.setShape(area);
        window.setVisible(true);
    }

    static class BackgroundFrame extends Frame {

        @Override
        public void paint(Graphics g) {

            g.setColor(BACKGROUND_COLOR);
            g.fillRect(0, 0, DIM, DIM);

            super.paint(g);
        }
    }

    class TestFrame extends Frame {

        @Override
        public void paint(Graphics g) {

            g.setColor(SHAPE_COLOR);
            g.fillRect(0, 0, DIM, DIM);

            super.paint(g);
        }
    }

    public static void main(String[] args) throws Exception {
        robot = new Robot();

        for (int i = 1; i <= 50; i++) {
            System.out.println("Attempt " + i);
            new ShapeNotSetSometimes().doTest();
        }
    }

    private void doTest() throws Exception {
        EventQueue.invokeAndWait(backgroundFrame::toFront);
        robot.waitForIdle();

        EventQueue.invokeAndWait(window::toFront);
        robot.waitForIdle();
        robot.delay(500);

        Rectangle screenBounds = window.getGraphicsConfiguration().getBounds();
        BufferedImage screenCapture = robot.createScreenCapture(screenBounds);
        try {
            colorCheck(innerPoint.x, innerPoint.y, SHAPE_COLOR,
                    true, screenCapture);

            for (Point point : pointsOutsideToCheck) {
                colorCheck(point.x, point.y, BACKGROUND_COLOR,
                        true, screenCapture);
            }

            for (Point point : shadedPointsToCheck) {
                colorCheck(point.x, point.y, SHAPE_COLOR,
                        false, screenCapture);
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (backgroundFrame != null) {
                    backgroundFrame.dispose();
                }
                if (window != null) {
                    window.dispose();
                }
            });
        }
    }

    private void colorCheck(int x, int y, Color expectedColor,
                            boolean mustBeExpectedColor, BufferedImage screenCapture) {
        int screenX = window.getX() + x;
        int screenY = window.getY() + y;

        Color actualColor = new Color(screenCapture.getRGB(screenX, screenY));

        System.out.printf(
                "Checking %3d, %3d, %35s should %sbe %35s\n",
                x, y,
                actualColor,
                (mustBeExpectedColor) ? "" : "not ",
                expectedColor
        );

        if (mustBeExpectedColor != colorCompare(expectedColor, actualColor)) {
            captureScreen();
            System.out.printf("window.getX() = %3d, window.getY() = %3d\n", window.getX(), window.getY());
            System.err.printf(
                    "Checking for transparency failed: point: %3d, %3d\n\tactual    %s\n\texpected %s%s\n",
                    screenX,
                    screenY,
                    actualColor,
                    mustBeExpectedColor ? "" : "not ",
                    expectedColor);
            throw new RuntimeException("Test failed. The shape has not been applied.");
        }
    }

    private static boolean colorCompare(Color expected, Color actual) {
        if (Math.abs(expected.getRed() - actual.getRed()) <= DELTA
                && Math.abs(expected.getGreen() - actual.getGreen()) <= DELTA
                && Math.abs(expected.getBlue() - actual.getBlue()) <= DELTA) {
            return true;
        }
        return false;
    }

    private static void captureScreen() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Rectangle screenBounds = new Rectangle(0, 0, screenSize.width, screenSize.height);
        try {
            ImageIO.write(
                    robot.createScreenCapture(screenBounds),
                    "png",
                    new File("Screenshot.png")
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
