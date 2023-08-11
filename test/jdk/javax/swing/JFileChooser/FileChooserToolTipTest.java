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
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Robot;

import javax.swing.JFrame;
import javax.swing.JFileChooser;
import javax.swing.JToolTip;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

/*
 * @test
 * @bug 6616245
 * @key headful
 * @requires (os.family == "windows")
 * @library ../regtesthelpers
 * @build Util
 * @summary Test to check if ToolTip is shown for shell folders
 * @run main FileChooserToolTipTest
 */
public class FileChooserToolTipTest {
    static Robot robot;
    static JFrame frame;
    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        robot = new Robot();
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                initialize();
            }
        });
        robot.delay(1000);
        robot.waitForIdle();
        Point movePoint = getFramePoint(frame);
        robot.mouseMove(movePoint.x, movePoint.y);
        robot.delay(2000);
        robot.waitForIdle();
        handleToolTip();
        System.out.println("Test Pass");
    }

    static void initialize() {
        JFileChooser jfc;
        frame = new JFrame("JFileChooser ToolTip test");
        jfc = new JFileChooser();
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.add(jfc, BorderLayout.CENTER);
        frame.pack();
        frame.setVisible(true);
    }

    static Point getFramePoint(JFrame frame) throws Exception {
        final Point[] result = new Point[1];

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                Point p = frame.getLocationOnScreen();
                Dimension size = frame.getSize();
                result[0] = new Point(p.x + size.width / 10, p.y + size.height / 2);
            }
        });
        return result[0];
    }

    static void handleToolTip() throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                try {
                    JToolTip tooltip = (JToolTip) Util.findSubComponent(
                            JFrame.getFrames()[0], "JToolTip");

                    if (tooltip == null) {
                        throw new RuntimeException("Basic Tooltip not been found");
                    }
                    System.out.println("ToolTp :"+tooltip.getTipText());

                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    frame.dispose();
                }
            }
        });
    }
}
