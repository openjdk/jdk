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

/*
 * @test
 * @bug 6972078
 * @key headful
 * @requires (os.family == "linux")
 * @summary Verifies if user is able to select single directory if multi selection
 * is enabled for JFileChooser.
 * @run main TestFileChooserSingleDirectorySelection
 */

import java.io.File;
import java.awt.BorderLayout;
import java.awt.event.InputEvent;
import java.awt.Point;
import java.awt.Robot;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public class TestFileChooserSingleDirectorySelection {
    private static JFrame frame;
    private static JFileChooser fileChooser;
    private static Robot robot;
    private static volatile Point clickLocation;

    public static void main(String[] args) throws Exception {
        System.setProperty("sun.java2d.uiScale", "1.0");
        robot = new Robot();
        robot.setAutoDelay(100);
        File testDirDirs = createFoldersOnlyDir();
        File testDirFiles = createFilesOnlyDir();
        populateDirs(testDirDirs);
        populateFiles(testDirFiles);
        for (UIManager.LookAndFeelInfo laf :
                UIManager.getInstalledLookAndFeels()) {
            System.out.println("Testing LAF: " + laf.getClassName());
            SwingUtilities.invokeAndWait(() -> setLookAndFeel(laf));
            checkFileOnlyTest(laf, testDirFiles);
            checkDirectoriesOnlyTest(laf, testDirDirs);
            checkFilesAndDirectoriesTest(laf, testDirDirs);
            System.out.println("Passed");
        }
    }

    private static File createFoldersOnlyDir() {
        String tmpDir = System.getProperty("java.io.tmpdir");
        File dirsDir = new File(tmpDir, "dirsDir");
        if (!dirsDir.exists()) {
            dirsDir.mkdir();
        }
        dirsDir.deleteOnExit();
        return dirsDir;
    }

    private static void populateDirs(File parent) {
        for (int i = 0; i < 10; ++i) {
            File subDir = new File(parent, "subDir_" + (i+1));
            subDir.mkdir();
            subDir.deleteOnExit();
        }
    }

    private static File createFilesOnlyDir() {
        String tmpDir = System.getProperty("java.io.tmpdir");
        File filesDir = new File(tmpDir, "filesDir");
        if (!filesDir.exists()) {
            filesDir.mkdir();
        }
        filesDir.deleteOnExit();
        return filesDir;
    }

    private static void populateFiles(File parent) throws Exception {
        for (int i = 0; i < 10; ++i) {
            File subFile = new File(parent, "subFiles_" + (i+1));
            subFile.createNewFile();
            subFile.deleteOnExit();
        }
    }

    private static void checkFileOnlyTest(UIManager.LookAndFeelInfo laf,
                                          File dir) throws Exception {
        System.out.println("Testing File Only mode");
        try {
            SwingUtilities.invokeAndWait(() -> {
                createAndShowUI();
                fileChooser.setCurrentDirectory(dir);
                fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            });

            robot.waitForIdle();
            robot.delay(1000);
            if (laf.getClassName().contains("Motif")
                || laf.getClassName().contains("GTK")) {
                doTesting(laf, 230);
            } else {
                doTesting(laf, 50);
            }
        } finally {
            disposeFrame();
        }
    }

    private static void checkDirectoriesOnlyTest(UIManager.LookAndFeelInfo laf,
                                                 File dir) throws Exception {
        System.out.println("Testing Directories Only mode");
        try {
            SwingUtilities.invokeAndWait(() -> {
                createAndShowUI();
                fileChooser.setCurrentDirectory(dir);
                fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            });
            robot.waitForIdle();
            robot.delay(1000);
            doTesting(laf, 50);
        } finally {
            disposeFrame();
        }
    }

    private static void checkFilesAndDirectoriesTest(UIManager.LookAndFeelInfo laf,
                                                     File dir) throws Exception {
        System.out.println("Testing Files and Directories mode");
        try {
            SwingUtilities.invokeAndWait(() -> {
                createAndShowUI();
                fileChooser.setCurrentDirectory(dir);
                fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            });
            robot.waitForIdle();
            robot.delay(1000);
            doTesting(laf, 50);
        } finally {
            disposeFrame();
        }
    }

    private static void createAndShowUI() {
        frame = new JFrame("Test File Chooser Single Directory Selection");
        frame.getContentPane().setLayout(new BorderLayout());
        fileChooser = new JFileChooser();
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setControlButtonsAreShown(false);
        frame.getContentPane().add(fileChooser, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    private static void setLookAndFeel(UIManager.LookAndFeelInfo laf) {
        try {
            UIManager.setLookAndFeel(laf.getClassName());
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Unsupported LAF: " + laf.getClassName());
        } catch (ClassNotFoundException | InstantiationException
                 | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static void doTesting(UIManager.LookAndFeelInfo laf, int xOffset)
                                    throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Point fileChooserLocation = fileChooser.getLocationOnScreen();
            fileChooserLocation.y += frame.getHeight() / 3;
            clickLocation = new Point(fileChooserLocation);
        });
        clickMouse(clickLocation, xOffset);
        checkResult(laf);
    }

    private static void clickMouse(Point point, int xOffset) {
        robot.mouseMove(point.x + xOffset , point.y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(100);
        robot.waitForIdle();
    }

    private static void checkResult(UIManager.LookAndFeelInfo laf) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            File[] files = fileChooser.getSelectedFiles();
            if (files.length == 0) {
                throw new RuntimeException("getSelectedFiles returned " +
                        "empty array for LAF: " + laf.getClassName());
            }
        });
    }

    private static void disposeFrame() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            if (frame != null) {
                frame.dispose();
            }
        });
    }
}
