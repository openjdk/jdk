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

public class TestFileChooserSingleDirectorySelection  {
    private static JFrame frame;
    private static JFileChooser fileChooser;
    private static JButton getSelectedFilesButton;
    private static Robot robot;
    private static boolean passed;

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

            Point pt = fileChooser.getLocationOnScreen();
            ClickMouse(pt);

            pt = getSelectedFilesButton.getLocationOnScreen();
            robot.mouseMove(pt.x + getSelectedFilesButton.getWidth() / 2,
                            pt.y + getSelectedFilesButton.getHeight() / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(100);
            if (!passed)
                throw new RuntimeException("getSelectedFiles returned empty array.");
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
        frame = new JFrame("Test File Chooser Single Directory Selection");
        frame.getContentPane().setLayout(new BorderLayout());
        fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setControlButtonsAreShown(false);

	    getSelectedFilesButton = new JButton();
        getSelectedFilesButton.setText("Print selected Files");
        getSelectedFilesButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
	        passed = false;
                File files[] = fileChooser.getSelectedFiles();
                if(files.length != 0) {
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

    private static void ClickMouse(Point point) {
        robot.mouseMove(point.x + 50 , point.y + frame.getHeight()/2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(100);
    }
}
