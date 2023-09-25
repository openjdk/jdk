/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Frame;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.MultiResolutionImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/*
 * @test
 * @bug 8015739 8294484
 * @key headful
 * @summary Tests whether Metal borders for JFrame, JDialog and JInternalFrame
 * scales correctly without any distortions by checking the midpoints and
 * corners of the border.
 *
 * @requires (os.family == "windows")
 * @run main/othervm -Dsun.java2d.uiScale=1 ScaledMetalBorderTest
 * @run main/othervm -Dsun.java2d.uiScale=1.25 ScaledMetalBorderTest
 * @run main/othervm -Dsun.java2d.uiScale=1.5 ScaledMetalBorderTest
 * @run main/othervm -Dsun.java2d.uiScale=1.75 ScaledMetalBorderTest
 * @run main/othervm -Dsun.java2d.uiScale=2 ScaledMetalBorderTest
 * @run main/othervm -Dsun.java2d.uiScale=2.5 ScaledMetalBorderTest
 * @run main/othervm -Dsun.java2d.uiScale=3 ScaledMetalBorderTest
 */

/*
 * @test
 * @bug 8015739 8294484
 * @key headful
 * @summary Tests whether Metal borders for JFrame, JDialog and JInternalFrame
 * scales correctly without any distortions by checking the midpoints and
 * corners of the border.
 *
 * @requires (os.family == "mac" | os.family == "linux")
 * @run main/othervm -Dsun.java2d.uiScale=1 ScaledMetalBorderTest
 * @run main/othervm -Dsun.java2d.uiScale=2 ScaledMetalBorderTest
 */

public class ScaledMetalBorderTest {
    private static final int SIZE = 250;
    private static final int INTFRAME_SIZE = 180;
    private static int MIDPOINT = SIZE / 2;
    private static final int BORDER_THICKNESS = 4;

    private static final StringBuffer errorLog = new StringBuffer();

    private static JFrame jFrame;
    private static JDialog jDialog;
    private static JInternalFrame iFrame;
    private static Rectangle windowBounds;
    private static Point windowLoc;
    private static int windowMaxX;
    private static int windowMaxY;

    private static Robot robot;
    private static String uiScale;
    private static JLabel scale;

    public static void main(String[] args) throws AWTException,
            InterruptedException, InvocationTargetException {
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
            JFrame.setDefaultLookAndFeelDecorated(true);
            JDialog.setDefaultLookAndFeelDecorated(true);
        } catch (Exception e) {
            System.out.println("Metal LAF class not supported");
            return;
        }

        try {
            robot = new Robot();
            robot.setAutoDelay(100);
            uiScale = System.getProperty("sun.java2d.uiScale");
            scale = new JLabel("UI Scale: " + uiScale);

            //Case 1: JFrame
            SwingUtilities.invokeAndWait(ScaledMetalBorderTest::createFrame);
            robot.waitForIdle();
            robot.delay(100);
            runTests("JFrame");

            if (!errorLog.isEmpty()) {
                saveScreenCapture("Frame_uiScale_" + uiScale + ".png");
                System.err.println("JFrame at uiScale: " + uiScale);
                throw new RuntimeException("Following error(s) occurred: \n"
                        + errorLog);
            }
            errorLog.setLength(0); // to clear the StringBuffer before next test.

            //Case 2: JDialog
            SwingUtilities.invokeAndWait(ScaledMetalBorderTest::createDialog);
            robot.waitForIdle();
            robot.delay(100);
            runTests("JDialog");

            if (!errorLog.isEmpty()) {
                saveScreenCapture("Dialog_uiScale_" + uiScale + ".png");
                System.err.println("JDialog at uiScale: " + uiScale);
                throw new RuntimeException("Following error(s) occurred: \n"
                        + errorLog);
            }
            errorLog.setLength(0); // to clear the StringBuffer before next test.

            //Case 3: JInternalFrame
            SwingUtilities.invokeAndWait(ScaledMetalBorderTest::createJInternalFrame);
            robot.waitForIdle();
            robot.delay(100);
            runTests("JIF");

            if (!errorLog.isEmpty()) {
                saveScreenCapture("JIF_uiScale_" + uiScale + ".png");
                System.err.println("JInternalFrame at uiScale: " + uiScale);
                throw new RuntimeException("Following error(s) occurred: \n"
                        + errorLog);
            }
        } finally {
            SwingUtilities.invokeAndWait(() ->{
                if (jFrame != null) {
                    jFrame.dispose();
                }
                if (jDialog != null) {
                    jDialog.dispose();
                }
            });
            robot.delay(200);
        }
    }

    private static void runTests(String windowType) throws InterruptedException,
                                                           InvocationTargetException {
        SwingUtilities.invokeAndWait(() -> {
            switch (windowType) {
                case "JFrame" -> {
                    windowLoc = jFrame.getLocationOnScreen();
                    windowBounds = jFrame.getBounds();
                    windowMaxX = windowLoc.x + SIZE;
                    windowMaxY = windowLoc.y + SIZE;
                }
                case "JDialog" -> {
                    windowLoc = jDialog.getLocationOnScreen();
                    windowBounds = jDialog.getBounds();
                    windowMaxX = windowLoc.x + SIZE;
                    windowMaxY = windowLoc.y + SIZE;
                }
                case "JIF" -> {
                    MIDPOINT = INTFRAME_SIZE / 2;
                    windowLoc = iFrame.getLocationOnScreen();
                    windowBounds = jFrame.getBounds();
                    windowMaxX = windowLoc.x + INTFRAME_SIZE;
                    windowMaxY = windowLoc.y + INTFRAME_SIZE;
                }
            }
        });

        // Check Borders
        checkBorderMidPoints("TOP");
        checkBorderMidPoints("RIGHT");
        checkBorderMidPoints("BOTTOM");
        checkBorderMidPoints("LEFT");

        // Check Corner Diagonals
        checkCorners("TOP_LEFT");
        checkCorners("TOP_RIGHT");
        checkCorners("BOTTOM_RIGHT");
        checkCorners("BOTTOM_LEFT");
    }

    private static void checkBorderMidPoints(String borderDirection) {
        int x, y;
        int start, stop;

        switch (borderDirection) {
            case "TOP" -> {
                x = windowLoc.x + MIDPOINT;
                y = windowLoc.y + BORDER_THICKNESS;
                start = windowLoc.y;
                stop = windowLoc.y + BORDER_THICKNESS - 1;
            }
            case "RIGHT" -> {
                x = windowMaxX - BORDER_THICKNESS;
                y = windowLoc.y + MIDPOINT;
                start = windowMaxX - BORDER_THICKNESS + 1;
                stop = windowMaxX;
            }
            case "BOTTOM" -> {
                x = windowLoc.x + MIDPOINT;
                y = windowMaxY - BORDER_THICKNESS;
                start = windowMaxY - BORDER_THICKNESS + 1;
                stop = windowMaxY;
            }
            case "LEFT" -> {
                x = windowLoc.x;
                y = windowLoc.y + MIDPOINT;
                start = windowLoc.x;
                stop = windowLoc.x + BORDER_THICKNESS - 1;
            }
            default -> throw new IllegalStateException("Unexpected value: "
                    + borderDirection);
        }

        boolean isVertical = borderDirection.equals("RIGHT")
                || borderDirection.equals("LEFT");
        boolean isHorizontal = borderDirection.equals("TOP")
                || borderDirection.equals("BOTTOM");

        robot.mouseMove(x, y);
        for (int i = start; i < stop; i++) {
            int locX = isVertical ? i : (windowLoc.x + MIDPOINT);
            int locY = isHorizontal ? i : (windowLoc.y + MIDPOINT);
            if (Color.RED.equals(robot.getPixelColor(locX, locY))) {
                errorLog.append("At uiScale: " + uiScale
                        + ", Red background color detected at "
                        + borderDirection + " border.\n");
                break;
            }
        }
        robot.delay(100);
    }

    private static void checkCorners(String cornerLocation) {
        int x, y;

        switch (cornerLocation) {
            case "TOP_LEFT" -> {
                x = windowLoc.x;
                y = windowLoc.y;
            }
            case "TOP_RIGHT" -> {
                x = windowMaxX;
                y = windowLoc.y;
            }
            case "BOTTOM_RIGHT" -> {
                x = windowMaxX;
                y = windowMaxY;
            }
            case "BOTTOM_LEFT" -> {
                x = windowLoc.x;
                y = windowMaxY;
            }
            default -> throw new IllegalStateException("Unexpected value: "
                    + cornerLocation);
        }

        boolean isTop = cornerLocation.equals("TOP_LEFT")
                || cornerLocation.equals("TOP_RIGHT");
        boolean isLeft = cornerLocation.equals("TOP_LEFT")
                || cornerLocation.equals("BOTTOM_LEFT");

        robot.mouseMove(x, y);
        for (int i = 0; i < BORDER_THICKNESS - 1; i++) {
            int locX = isLeft ? (x + i) : (x - i);
            int locY = isTop ? (y + i) : (y - i);
            if (Color.RED.equals(robot.getPixelColor(locX, locY))) {
                errorLog.append("At uiScale: " + uiScale + ", Red background color"
                        + " detected at " + cornerLocation + " corner.\n");
                break;
            }
        }
        robot.delay(100);
    }

    private static void createFrame() {
        jFrame = new JFrame("Frame with Metal Border");
        jFrame.setSize(SIZE, SIZE);
        jFrame.setBackground(Color.RED);
        jFrame.getContentPane().setBackground(Color.RED);
        jFrame.setLayout(new GridBagLayout());
        jFrame.getContentPane().add(scale);
        jFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        jFrame.setLocation(150, 150);
        jFrame.setVisible(true);
    }

    private static void createDialog() {
        jDialog = new JDialog((Frame) null , "Dialog with Metal Border");
        jDialog.setSize(SIZE, SIZE);
        jDialog.setBackground(Color.RED);
        jDialog.getContentPane().setBackground(Color.RED);
        jDialog.setLayout(new GridBagLayout());
        jDialog.getContentPane().add(scale);
        jDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        jDialog.setLocation(150, 150);
        jDialog.setVisible(true);
    }

    private static void createJInternalFrame() {
        jFrame = new JFrame("JIF with Metal Border");
        jFrame.setSize(SIZE, SIZE);
        jFrame.setLayout(null);
        jFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        iFrame = new JInternalFrame("iframe", true);
        iFrame.setLayout(new GridBagLayout());
        iFrame.setBackground(Color.RED);
        iFrame.add(scale);
        iFrame.setLocation(30, 30);
        jFrame.getContentPane().add(iFrame);
        iFrame.setSize(INTFRAME_SIZE, INTFRAME_SIZE);
        iFrame.setVisible(true);
        jFrame.setLocation(150, 150);
        jFrame.setVisible(true);
    }

    private static void saveScreenCapture(String filename) {
        MultiResolutionImage mrImage = robot.createMultiResolutionScreenCapture(windowBounds);
        List<Image> variants = mrImage.getResolutionVariants();
        RenderedImage image = (RenderedImage) variants.get(variants.size() - 1);
        try {
            ImageIO.write(image, "png", new File(filename));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
