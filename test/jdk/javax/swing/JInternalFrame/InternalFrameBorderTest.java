/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/*
 * @test
 * @bug 8015739
 * @key headful
 * @summary Tests whether background color of JInternalFrame is visible
 * in the border region at different scales by checking the midpoints
 * and corners of the border.
 *
 * @requires (os.family == "windows")
 * @run main/othervm -Dsun.java2d.uiScale=1 InternalFrameBorderTest
 * @run main/othervm -Dsun.java2d.uiScale=1.25 InternalFrameBorderTest
 * @run main/othervm -Dsun.java2d.uiScale=1.5 InternalFrameBorderTest
 * @run main/othervm -Dsun.java2d.uiScale=1.75 InternalFrameBorderTest
 * @run main/othervm -Dsun.java2d.uiScale=2 InternalFrameBorderTest
 * @run main/othervm -Dsun.java2d.uiScale=2.5 InternalFrameBorderTest
 * @run main/othervm -Dsun.java2d.uiScale=3 InternalFrameBorderTest
 */

/*
 * @test
 * @bug 8015739
 * @key headful
 * @summary Tests whether background color of JInternalFrame is visible
 * in the border region at different scales by checking the midpoints
 * and corners of the border.
 *
 * @requires (os.family == "mac" | os.family == "linux")
 * @run main/othervm -Dsun.java2d.uiScale=1 InternalFrameBorderTest
 * @run main/othervm -Dsun.java2d.uiScale=2 InternalFrameBorderTest
 */

public class InternalFrameBorderTest {
    private static final int FRAME_SIZE = 300;
    private static final int INTFRAME_SIZE = 200;
    private static final int MIDPOINT = INTFRAME_SIZE/2;
    private static final int BORDER_THICKNESS = 5;
    private static StringBuffer errorLog = new StringBuffer();

    private static JFrame jFrame;
    private static JInternalFrame iFrame;
    private static Robot robot;

    private static Point iFrameLoc;
    private static int iFrameMaxX;
    private static int iFrameMaxY;
    private static String uiScale;

    public static void main(String[] args) throws AWTException,
            InterruptedException, InvocationTargetException {
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        } catch (Exception e) {
            System.out.println("Metal LAF class not supported");
            return;
        }

        try {
            robot = new Robot();
            robot.setAutoDelay(200);
            uiScale = System.getProperty("sun.java2d.uiScale");

            SwingUtilities.invokeAndWait(InternalFrameBorderTest::createAndShowGUI);
            robot.waitForIdle();
            robot.delay(500);

            SwingUtilities.invokeAndWait(() -> {
                iFrameLoc = iFrame.getLocationOnScreen();
                iFrameMaxX = iFrameLoc.x + INTFRAME_SIZE;
                iFrameMaxY = iFrameLoc.y + INTFRAME_SIZE;
            });

            // Check Borders
            SwingUtilities.invokeAndWait(() -> {
                checkBorderMidPoints("TOP");
                checkBorderMidPoints("RIGHT");
                checkBorderMidPoints("BOTTOM");
                checkBorderMidPoints("LEFT");
            });

            // Check Corners
            SwingUtilities.invokeAndWait(() -> {
                checkCorners("TOP_LEFT");
                checkCorners("TOP_RIGHT");
                checkCorners("BOTTOM_RIGHT");
                checkCorners("BOTTOM_LEFT");
            });
            if (!errorLog.isEmpty()) {
                throw new RuntimeException("Following error(s) occurred: \n"
                        + errorLog);
            }
        } finally {
            if (jFrame != null) {
                jFrame.dispose();
            }
            robot.delay(500);
        }
    }

    private static void checkBorderMidPoints(String borderDirection) {
        int x = 0, y = 0, start = 0, stop = 0;
        switch (borderDirection) {
            case "TOP" -> {
                x = iFrameLoc.x + MIDPOINT;
                y = iFrameLoc.y + BORDER_THICKNESS;
                start = iFrameLoc.y;
                stop = iFrameLoc.y + BORDER_THICKNESS;
            }
            case "RIGHT" -> {
                x = iFrameMaxX - BORDER_THICKNESS;
                y = iFrameLoc.y + MIDPOINT;
                start = iFrameMaxX - BORDER_THICKNESS;
                stop = iFrameMaxX;
            }
            case "BOTTOM" -> {
                x = iFrameLoc.x + MIDPOINT;
                y = iFrameMaxY - BORDER_THICKNESS;
                start = iFrameMaxY;
                stop = iFrameMaxY - BORDER_THICKNESS;
            }
            case "LEFT" -> {
                x = iFrameLoc.x;
                y = iFrameLoc.y + MIDPOINT;
                start = iFrameLoc.x;
                stop = iFrameLoc.x + BORDER_THICKNESS;
            }
            default -> throw new IllegalStateException("Unexpected value: "
                    + borderDirection);
        }

        boolean isVertical =  borderDirection.equals("RIGHT")
                || borderDirection.equals("LEFT");
        boolean isHorizontal =  borderDirection.equals("TOP")
                || borderDirection.equals("BOTTOM");

        robot.mouseMove(x, y);
        for (int i = start; i < stop; i++) {
            if (Color.RED.equals(robot.getPixelColor(
                    isVertical ? i : (iFrameLoc.x + MIDPOINT),
                    isHorizontal ? i : (iFrameLoc.y + MIDPOINT)))) {
                        saveScreenCapture(borderDirection + "_" + uiScale + ".png");
                        errorLog.append("uiScale: "+ uiScale +
                                " Red background color" + " detected at "
                                + borderDirection + " border\n");
            }
        }
        robot.delay(300);
    }

    private static void checkCorners(String cornerLocation) {
        int x, y = 0;

        switch (cornerLocation) {
            case "TOP_LEFT" -> {
                x = iFrameLoc.x;
                y = iFrameLoc.y;
            }
            case "TOP_RIGHT" -> {
                x = iFrameMaxX;
                y = iFrameLoc.y;
            }
            case "BOTTOM_RIGHT" -> {
                x = iFrameMaxX;
                y = iFrameMaxY;
            }
            case "BOTTOM_LEFT" -> {
                x = iFrameLoc.x;
                y = iFrameMaxY;
            }
            default -> throw new IllegalStateException("Unexpected value: "
                    + cornerLocation);
        }
        robot.mouseMove(x, y);
        if (Color.RED.equals(robot.getPixelColor(x, y))) {
            saveScreenCapture(cornerLocation + "_" + uiScale + ".png");
            errorLog.append("uiScale: "+ uiScale + " Red background color" +
                    " detected at " + cornerLocation +  " corner\n");
        }
        robot.delay(300);
    }

    private static void createAndShowGUI() {
        jFrame = new JFrame();
        jFrame.setSize(FRAME_SIZE, FRAME_SIZE);
        jFrame.setLayout(null);
        jFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        JLabel scale = new JLabel("UI Scale: "+ uiScale);

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
    // for debugging purpose, saves screen capture when test fails.
    private static void saveScreenCapture(String filename) {
        BufferedImage image = robot.createScreenCapture(jFrame.getBounds());
        try {
            ImageIO.write(image,"png", new File(filename));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
