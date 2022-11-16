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
 * @bug 6972078
 * @key headful
 * @requires (os.family == "linux")
 * @summary Verifies if user is able to select single directory if multi selection
 * is enabled for JFileChooser.
 * @run main TestFileChooserSingleDirectorySelection
 */

import java.io.File;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.Point;
import java.awt.Robot;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public class TestFileChooserSingleDirectorySelection {
    private static JFrame frame;
    private static JFileChooser fileChooser;
    private static JButton getSelectedFilesButton;
    private static Robot robot;
    private static boolean passed;
    private static File[] testDir;
    private static File[] tempFile;

    public static void main(String[] args) throws Exception {
        System.setProperty("sun.java2d.uiScale", "1.0");
        robot = new Robot();
        robot.setAutoDelay(100);

        try {
            // create test directory
            String tmpDir = System.getProperty("user.home");
            testDir = new File[1];
            testDir[0] = new File(tmpDir, "testDir");
            if (!testDir[0].exists())
                testDir[0].mkdir();
            testDir[0].deleteOnExit();

            // create temporary files inside testDir
            tempFile = new File[5];
            for (int i = 0; i < 5; ++i) {
                tempFile[i] = File.createTempFile("temp", ".txt",
                        new File(testDir[0].getAbsolutePath()));
                tempFile[i].deleteOnExit();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (UIManager.LookAndFeelInfo laf :
                        UIManager.getInstalledLookAndFeels()) {
            System.out.println("Testing LAF: " + laf.getClassName());
            SwingUtilities.invokeAndWait(() -> setLookAndFeel(laf));
            checkFileOnlyTest(laf);
            checkDirectoriesOnlyTest(laf);
            checkFilesAndDirectoriesTest(laf);
            System.out.println("Passed");
        }
    }

    private static void checkFileOnlyTest(UIManager.LookAndFeelInfo laf)
            throws Exception {
        System.out.println("Testing File Only mode");
        try {
            SwingUtilities.invokeAndWait(() -> {
                createAndShowUI();
                fileChooser.setCurrentDirectory(testDir[0]);
            });

            robot.waitForIdle();
            robot.delay(1000);
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            doTesting(laf, 230);
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void checkDirectoriesOnlyTest(UIManager.LookAndFeelInfo laf)
            throws Exception {
        System.out.println("Testing Directories Only mode");
        try {
            SwingUtilities.invokeAndWait(() -> {
                createAndShowUI();
            });
            robot.waitForIdle();
            robot.delay(1000);
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            doTesting(laf, 50);
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void checkFilesAndDirectoriesTest(UIManager.LookAndFeelInfo laf)
            throws Exception {
        System.out.println("Testing Files and Directories mode");
        try {
            SwingUtilities.invokeAndWait(() -> {
                createAndShowUI();
            });
            robot.waitForIdle();
            robot.delay(1000);
            fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            doTesting(laf, 50);
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void createAndShowUI() {
        frame = new JFrame("Test File Chooser Single Directory Selection");
        frame.getContentPane().setLayout(new BorderLayout());
        fileChooser = new JFileChooser("user.home");
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setControlButtonsAreShown(false);

        getSelectedFilesButton = new JButton();
        getSelectedFilesButton.setText("Print selected Files");
        getSelectedFilesButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                passed = false;
                File files[] = fileChooser.getSelectedFiles();
                if (files.length != 0) {
                    passed = true;
                }
            }
        });

        frame.getContentPane().add(fileChooser, BorderLayout.CENTER);
        frame.getContentPane().add(getSelectedFilesButton, BorderLayout.SOUTH);
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

    private static void doTesting(UIManager.LookAndFeelInfo laf, int xOffset) {
        Point frameLocation = fileChooser.getLocationOnScreen();
        int frameWidth = frame.getWidth();
        int frameHeight = frame.getHeight();

        Point btnLocation = getSelectedFilesButton.getLocationOnScreen();
        int btnWidth = getSelectedFilesButton.getWidth();
        int btnHeight = getSelectedFilesButton.getHeight();
        clickMouse(frameLocation, 0, frameHeight, xOffset);
        clickMouse(btnLocation, btnWidth, btnHeight, 0);
        checkResult(laf);
    }

    private static void clickMouse(Point point, int width, int height,
                                   int xOffset) {
        robot.mouseMove(point.x + width/2 + xOffset , point.y + height/3);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(100);
    }

    private static void checkResult(UIManager.LookAndFeelInfo laf) {
        if (!passed)
            throw new RuntimeException("getSelectedFiles returned " +
                    "empty array for LAF: "+laf.getClassName());
    }
}
