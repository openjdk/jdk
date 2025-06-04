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

/*
 * @test
 * @key headful
 * @bug 8021961
 * @summary To test setAlwaysOnTop functionality.
 * @run main/othervm -Dsun.java2d.uiScale=1 ChildAlwaysOnTopTest
 */

import java.awt.Color;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Window;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;

import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

public class ChildAlwaysOnTopTest {

    private static Window win1;
    private static Window win2;
    private static Point point;
    private static Robot robot;
    private static int caseNo = 0;
    private static StringBuffer errorLog = new StringBuffer();
    private static String[] errorMsg= new String[] {
            " Scenario 1 Failed: alwaysOnTop window is sent back by another" +
                    " child window with setVisible().",
            " Scenario 2 Failed: alwaysOnTop window is" +
                    " sent back by another child window with toFront().",
            " Scenario 3 Failed: Failed to unset alwaysOnTop ",
    };

    public static void main(String[] args) throws Exception {

        if (!Toolkit.getDefaultToolkit().isAlwaysOnTopSupported()) {
            System.out.println("alwaysOnTop not supported by: "+
                    Toolkit.getDefaultToolkit().getClass().getName());
            return;
        }

        // CASE 1 - JDialog without parent/owner
        System.out.println("Testing CASE 1: JDialog without parent/owner");
        caseNo = 1;
        test(null);
        System.out.println("CASE 1 Completed");
        System.out.println();

        // CASE 2 - JDialog with JFrame as owner
        System.out.println("Testing CASE 2: JDialog with JFrame as owner");
        caseNo = 2;
        Window f = new Frame();
        f.setBackground(Color.darkGray);
        f.setSize(500, 500);
        try {
            test(f);
        } finally {
            f.dispose();
        }
        System.out.println("CASE 2 Completed");
        System.out.println();

        // CASE 3 - JDialog within another JDialog as owner
        System.out.println("Testing CASE 3:Dialog within another"+
                " JDialog as owner");
        caseNo = 3;
        f = new Frame();
        f.setBackground(Color.darkGray);
        f.setSize(500, 500);
        f.setVisible(true);
        f = new Dialog((Frame)f);
        try {
            test(f);
        } finally {
            ((Frame)f.getParent()).dispose();
        }
        System.out.println("CASE 3 Completed");
        System.out.println();

        if (errorLog.isEmpty()) {
            System.out.println("All three cases passed !!");
        }
        else {
            throw new RuntimeException("Following cases and scenarios failed."+
                    " Please check the saved screenshots.\n"+ errorLog);
        }
    }

    public static void test(Window parent) throws Exception {
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    win1 = parent == null ? new JDialog() : new JDialog(parent);
                    win1.setName("Top");

                    win2 = parent == null ? new JDialog() : new JDialog(parent);
                    win2.setName("Behind");

                    JLabel label = new JLabel("TOP WINDOW");
                    // top window - green and smaller
                    win1.setSize(200, 200);
                    Panel panel = new Panel();
                    panel.setBackground(Color.GREEN);
                    panel.add(label);
                    win1.add(panel);
                    win1.setAlwaysOnTop(true);

                    // behind window - red and bigger
                    label = new JLabel("BEHIND WINDOW");
                    win2.setSize(300, 300);
                    panel = new Panel();
                    panel.setBackground(Color.RED);
                    panel.add(label);
                    win2.add(panel);

                    win1.setVisible(true);
                    win2.setVisible(true);
                }
            });

            robot = new Robot();
            robot.setAutoDelay(300);
            robot.waitForIdle();

            // Scenario 1: Trying to unset the alwaysOnTop (green window)
            // by setting the setVisible to true for behind (red) window
            System.out.println(" >> Testing Scenario 1 ...");
            SwingUtilities.invokeAndWait(()-> {
                point = win1.getLocationOnScreen();
                win2.setVisible(true);
            });

            checkTopWindow(caseNo, 1, Color.GREEN);

            /*---------------------------------------------------------------*/

            // Scenario 2: Trying to unset the alwaysOnTop (green window)
            // by setting toFront() to true for behind (red) window
            System.out.println(" >> Testing Scenario 2 ...");
            SwingUtilities.invokeAndWait(()-> {
                win2.toFront();
                if (parent != null) {
                    parent.setLocation(win1.getLocation());
                    parent.toFront();
                }
            });

            checkTopWindow(caseNo, 2, Color.GREEN);

            /*----------------------------------------------------------------*/

            // Scenario 3: Trying to unset the alwaysOnTop (green window)
            // by setting alwaysOnTop to false. The unsetting should work
            // in this case and bring the red window to the top.
            System.out.println(" >> Testing Scenario 3 ...");
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    win1.setAlwaysOnTop(false);
                    if (parent != null) {
                        parent.setVisible(false);
                        parent.setVisible(true);
                    }
                }
            });

            robot.delay(300);
            robot.waitForIdle();

            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    win2.toFront();
                }
            });

            checkTopWindow(caseNo, 3, Color.RED);

        } finally {
            if (win1 != null) {
                SwingUtilities.invokeAndWait(()-> win1.dispose());
            }
            if (win2 != null) {
                SwingUtilities.invokeAndWait(()-> win2.dispose());
            }
        }
    }
    // to check if the current top window background color
    // matches the expected color
    private static void checkTopWindow(int caseNo, int scenarioNo,
                                       Color expectedColor) {

        robot.delay(500);
        robot.waitForIdle();
        Color actualColor = robot.getPixelColor(point.x + 100, point.y + 100);

        saveScreenCapture(caseNo , scenarioNo);

        if (!actualColor.equals(expectedColor)) {
            System.out.println(" >> Scenario "+ scenarioNo +" FAILED !!");
            errorLog.append("Case "+ caseNo + errorMsg[scenarioNo - 1]
                    +" Expected Color: "+ expectedColor +" vs Actual Color: "
                    + actualColor +"\n");
        }
        else {
            System.out.println(" >> Scenario "+ scenarioNo +" Passed");
        }
    }

    // For Debugging purpose - method used to save the screen capture as
    // BufferedImage in the event the test fails
    private static void saveScreenCapture(int caseNo, int scenarioNo) {
        String filename = "img_"+ caseNo +"_"+ scenarioNo;
        BufferedImage image = robot.createScreenCapture(
                new Rectangle(0, 0, 500, 500));
        try {
            ImageIO.write(image, "png", new File(filename));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}