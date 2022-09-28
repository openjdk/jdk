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
import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.Panel;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/*
 * @test
 * @bug 8265586
 * @key headful
 * @summary Tests whether correct native frame insets are obtained
 * for Resizable & Non-Resizable AWT Frame by checking the actual
 * and expected/preferred frame sizes.
 * @run main AwtFramePackTest
 */
public class AwtFramePackTest {
    private static Frame frame;
    private static Robot robot;
    private static StringBuffer errorLog = new StringBuffer();

    public static void main(String[] args) throws AWTException {
        robot = new Robot();
        robot.setAutoDelay(300);

        // Resizable frame
        createAWTFrame(true);

        robot.waitForIdle();
        robot.delay(500);

        // Non-Resizable frame
        createAWTFrame(false);

        if (!errorLog.isEmpty()) {
            throw new RuntimeException("Test failed due to the following" +
                    " one or more errors: \n" + errorLog);
        }
    }

    private static void createAWTFrame(boolean isResizable) {
        try {
            frame = new Frame();
            frame.setLayout(new BorderLayout());

            Panel panel = new Panel();
            panel.add(new Button("Panel Button B1"));
            panel.add(new Button("Panel Button B2"));
            frame.add(panel, BorderLayout.CENTER);

            MenuBar mb = new MenuBar();
            Menu m = new Menu("Menu");
            mb.add(m);
            frame.setMenuBar(mb);

            frame.setResizable(isResizable);
            frame.pack();
            frame.setVisible(true);

            robot.waitForIdle();
            robot.delay(500);

            Dimension actualFrameSize = frame.getSize();
            Dimension expectedFrameSize = frame.getPreferredSize();

            if (!actualFrameSize.equals(expectedFrameSize)) {
                String frameType = isResizable ? "ResizableFrame" : "NonResizableFrame";
                System.out.println("Expected frame size: " + expectedFrameSize);
                System.out.println("Actual frame size: " + actualFrameSize);
                saveScreenCapture(frameType + ".png");
                errorLog.append(frameType + ": Expected and Actual frame size" +
                        " are different. frame.pack() does not work!! \n");
            }
        } finally {
            if (frame != null) {
                frame.dispose();
            }
        }
    }

    // for debugging purpose, saves screen capture when test fails.
    private static void saveScreenCapture(String filename) {
        BufferedImage image = robot.createScreenCapture(frame.getBounds());
        try {
            ImageIO.write(image,"png", new File(filename));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
