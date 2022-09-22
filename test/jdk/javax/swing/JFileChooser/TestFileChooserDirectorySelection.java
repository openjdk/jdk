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

/*
 * @test
 * @bug 6777156
 * @key headful
 * @requires (os.family == "linux")
 * @summary Verifies if user is not able to select "../" beyond root file system
 * @run main TestFileChooserDirectorySelection
 */

import java.io.File;
import java.awt.event.InputEvent;
import java.awt.Point;
import java.awt.Robot;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class TestFileChooserDirectorySelection  {
    private static JFrame frame;
    private static JFileChooser fileChooser;
    private static Robot robot;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
        robot = new Robot();
        robot.setAutoDelay(100);
        try {
            SwingUtilities.invokeAndWait(() -> {
                createAndShowUI();
            });

            robot.waitForIdle();
            robot.delay(1000);
            Point pt = frame.getLocationOnScreen();
            int i = 1;
            boolean passed = false;
            File crntDir = fileChooser.getCurrentDirectory();
            File prevDir = null;
            while (true) {
                prevDir = crntDir;
                doubleClickMouse(pt);
                crntDir = fileChooser.getCurrentDirectory();
                robot.delay(1000);
                if (prevDir == crntDir) {
                    passed = true;
                    break;
                } else if (++i > 5) {
                    break;
                }
            }
            robot.delay(1000);
            if (!passed)
                throw new RuntimeException("User is able to select ../ " +
                        "beyond root directory");
            else
                System.out.println("passed");
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void createAndShowUI() {
        frame = new JFrame("Test File Chooser Directory Selection");
        fileChooser = new JFileChooser();
        frame.add(fileChooser);
        frame.setSize(500,500);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    private static void doubleClickMouse(Point p) {
        robot.mouseMove(p.x+75, p.y+frame.getHeight()/2 - 90);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(100);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(1000);
    }
}
