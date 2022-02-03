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

import java.awt.event.KeyEvent;
import java.awt.Robot;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/*
 * @test
 * @key headful
 * @requires (os.family == "windows")
 * @bug 4659800
 * @summary Check whether typing <Enter> key generates
 * ActionEvent on focused Button or not. This is applicable only for
 * WindowsLookAndFeel and WindowsClassicLookAndFeel.
 * @run main EnterKeyActivatesButton
 */
public class EnterKeyActivatesButton {
    private volatile boolean buttonPressed;
    private JFrame frame;

    public static void main(String[] s) throws Exception {
        EnterKeyActivatesButton test = new EnterKeyActivatesButton();
        test.runTest();
    }

    private static void setLookAndFeel(String lafName) {
        try {
            UIManager.setLookAndFeel(lafName);
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Ignoring Unsupported L&F: " + lafName);
        } catch (ClassNotFoundException | InstantiationException
                | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void disposeFrame() {
        if (frame != null) {
            frame.dispose();
            frame = null;
        }
    }

    private void createUI() {
        frame = new JFrame();
        JPanel panel = new JPanel();
        panel.add(new JTextField("Text field"));
        JButton focusedButton = new JButton("Button1");
        focusedButton.addActionListener(e -> buttonPressed = true);
        panel.add(focusedButton);
        panel.add(new JButton("Button2"));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(panel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        focusedButton.requestFocusInWindow();
    }

    public void runTest() throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(100);
        for (UIManager.LookAndFeelInfo laf : UIManager.getInstalledLookAndFeels()) {
            try {
                buttonPressed = false;
                String lafName = laf.getClassName();
                if (lafName.endsWith("WindowsLookAndFeel") || lafName.endsWith("WindowsClassicLookAndFeel")) {
                    System.out.println("Testing L&F: " + lafName);
                    SwingUtilities.invokeAndWait(() -> {
                        setLookAndFeel(lafName);
                        createUI();
                    });

                    robot.waitForIdle();
                    robot.keyPress(KeyEvent.VK_ENTER);
                    robot.keyRelease(KeyEvent.VK_ENTER);
                    robot.waitForIdle();

                    if (buttonPressed) {
                        System.out.println("Test Passed for L&F: " + lafName);
                    } else {
                        throw new RuntimeException("Test Failed, button not pressed for L&F: " + lafName);
                    }
                } else {
                    System.out.println("Skipping L&F: " + lafName);
                }
            } finally {
                SwingUtilities.invokeAndWait(this::disposeFrame);
            }
        }

    }
}
