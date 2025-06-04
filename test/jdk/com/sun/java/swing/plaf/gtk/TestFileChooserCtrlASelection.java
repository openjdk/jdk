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
 * @bug 4912623
 * @key headful
 * @requires (os.family == "linux")
 * @summary Verifies if all files or folders are selected on CTRL+A press for
 * JFileChooser.
 * @run main TestFileChooserCtrlASelection
 */

import java.awt.BorderLayout;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.Point;
import java.awt.Robot;
import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class TestFileChooserCtrlASelection {
    private static JFrame frame;
    private static JFileChooser fileChooser;
    private static Robot robot;
    private static File testDir;
    private static File testFile;
    private static File[] SubDirs;
    private static File[] subFiles;
    private static boolean passed_1 = false;
    private static boolean passed_2 = false;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
        robot = new Robot();
        robot.setAutoDelay(100);
        try {
            // create test directory
            String tmpDir = System.getProperty("user.home");

            // Create a test directory that contains only folders
            testDir = new File(tmpDir, "testDir");
            if (!testDir.exists()) {
                testDir.mkdir();
            }
            testDir.deleteOnExit();

            // create sub directories inside test directory
            SubDirs = new File[5];
            for (int i = 0; i < 5; ++i) {
                SubDirs[i] = new File(testDir, "subDir_" + (i+1));
                SubDirs[i].mkdir();
                SubDirs[i].deleteOnExit();
            }

            // Create a test directory that contains only files
            testFile = new File(tmpDir, "testFile");
            if (!testFile.exists()) {
                testFile.mkdir();
            }
            testFile.deleteOnExit();

            // create temporary files inside testFile
            subFiles = new File[5];
            for (int i = 0; i < 5; ++i) {
                subFiles[i] = File.createTempFile("subFiles_" + (i+1),
                        ".txt", new File(testFile.getAbsolutePath()));
                subFiles[i].deleteOnExit();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        doTesting();
    }

    private static void doTesting() throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                createAndShowUI();
            });
            robot.delay(1000);
            checkMultiSelectionDefault();
            checkMultiSelectionDisabled();
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void createAndShowUI() {
        frame = new JFrame("Test File Chooser Ctrl+A Action");
        frame.getContentPane().setLayout(new BorderLayout());
        fileChooser = new JFileChooser(testDir);
        fileChooser.setControlButtonsAreShown(false);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        frame.getContentPane().add(fileChooser, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    /*
     * JFileChooser MultiSelection property is set true by default.
     */
    private static void checkMultiSelectionDefault() {
        System.out.println("Testing MultiSelection enabled by default");
        Point frameLocation = fileChooser.getLocationOnScreen();
        int frameHeight = frame.getHeight();

        // check Ctrl+A on folders list
        doMouseClick(frameLocation, frameHeight, 50);
        doKeyPressAction();
        File files[] = fileChooser.getSelectedFiles();
        System.out.println("files length: " + files.length);
        if (files.length > 1) {
            passed_1 = true;
        }

        // check Ctrl+A on files list
        fileChooser.setCurrentDirectory(testFile);
        doMouseClick(frameLocation, frameHeight, 230);
        doKeyPressAction();
        files = fileChooser.getSelectedFiles();
        System.out.println("files length: " + files.length);
        if (files.length > 1) {
            passed_2 = true;
        }

        if (passed_1 && passed_2) {
            System.out.println("Passed");
        } else {
            throw new RuntimeException("Unable to select all files " +
                    "or folder");
        }
    }

    private static void checkMultiSelectionDisabled() {
        System.out.println("Testing MultiSelection disabled");
        passed_1 = false;
        passed_2 = false;
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setCurrentDirectory(testDir);
        Point frameLocation = fileChooser.getLocationOnScreen();
        int frameHeight = frame.getHeight();

        // check Ctrl+A on folders list
        doMouseClick(frameLocation, frameHeight, 50);
        doKeyPressAction();
        File files[] = fileChooser.getSelectedFiles();
        System.out.println("files length: " + files.length);
        if (files.length == 0) {
            passed_1 = true;
        }

        // check Ctrl+A on files list
        fileChooser.setCurrentDirectory(testFile);
        doMouseClick(frameLocation, frameHeight, 230);
        doKeyPressAction();
        files = fileChooser.getSelectedFiles();
        System.out.println("files length: " + files.length);
        if (files.length == 0) {
            passed_2 = true;
        }

        if (passed_1 && passed_2) {
            System.out.println("Passed");
        } else {
            throw new RuntimeException("All files or folder selected for" +
                    "MultiSelection disabled");
        }
    }

    private static void doMouseClick(Point frameLocation, int frameHeight,
                                     int offset) {
        robot.mouseMove(frameLocation.x + offset, frameLocation.y +
                frameHeight / 3);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(100);
    }

    private static void doKeyPressAction() {
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyPress(KeyEvent.VK_A);
        robot.keyRelease(KeyEvent.VK_A);
        robot.keyRelease(KeyEvent.VK_CONTROL);
        robot.delay(100);
    }
}
