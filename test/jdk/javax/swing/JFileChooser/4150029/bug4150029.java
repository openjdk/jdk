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

import java.awt.BorderLayout;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import jdk.test.lib.Platform;

/*
 * @test
 * @bug 4150029 8006087
 * @key headful
 * @summary BackSpace keyboard button does not lead to parent directory
 * @library /test/lib
 * @build jdk.test.lib.Platform
 * @run main bug4150029
 */

public class bug4150029 {
    private static JFrame frame;
    private static JFileChooser fileChooser;
    private static Robot robot;
    private static File prevDir;
    private static File crntDir;
    private static volatile Point p;

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        robot.setAutoDelay(100);

        try {
            if (Platform.isOSX()) {
                try {
                    UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            String tmpDir = System.getProperty("java.io.tmpdir");

            //'java.io.tmpdir' isn't guaranteed to be defined
            if (tmpDir.length() == 0) {
                tmpDir = System.getProperty("user.home");
            }
            System.out.println("Temp directory: " + tmpDir);

            File testDir = new File(tmpDir, "testDir");
            testDir.mkdir();
            testDir.deleteOnExit();
            System.out.println("Created directory: " + testDir);

            File subDir = new File(testDir, "subDir");
            subDir.mkdir();
            subDir.deleteOnExit();
            System.out.println("Created sub-directory: " + subDir);

            SwingUtilities.invokeAndWait(() -> {
                createAndShowUI();
                fileChooser.setCurrentDirectory(subDir);
            });

            doTesting();
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void createAndShowUI() {
        frame = new JFrame("Backspace Shortcut for Directory Navigation Test");
        frame.setLayout(new BorderLayout());
        fileChooser = new JFileChooser();
        fileChooser.setControlButtonsAreShown(false);
        frame.add(fileChooser, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    private static void doTesting() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            p = frame.getLocationOnScreen();
        });
        robot.mouseMove(p.x + 200, p.y + 200);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        robot.waitForIdle();

        // check backspace key at subDir level
        clickBackSpace();

        if (prevDir.equals(crntDir)) {
            throw new RuntimeException("BackSpace does not lead to parent directory");
        }
    }

    private static void clickBackSpace() {
        prevDir = fileChooser.getCurrentDirectory();
        robot.keyPress(KeyEvent.VK_BACK_SPACE);
        robot.keyRelease(KeyEvent.VK_BACK_SPACE);
        crntDir = fileChooser.getCurrentDirectory();
    }
}
