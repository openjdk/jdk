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

import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;


import static java.util.stream.Collectors.toList;

/*
 * @test
 * @key headful
 * @summary Check whether pressing <Space> key generates
 * ActionEvent on focused Button or not.
 * @run main SpaceKeyActivatesButton
 */
public class SpaceKeyActivatesButton {

    private static volatile boolean buttonPressed;
    private static JFrame frame;
    private static JButton focusedButton;

    public static void main(String[] s) throws Exception {
        runTest();
    }

    public static void runTest() throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(100);
        robot.setAutoWaitForIdle(true);
        List<String> lafs = Arrays.stream(UIManager.getInstalledLookAndFeels())
                                  .map(laf -> laf.getClassName())
                                  .collect(toList());
        for (String laf : lafs) {
            try {
                buttonPressed = false;
                System.out.println("Testing L&F: " + laf);
                SwingUtilities.invokeAndWait(() -> frame = new JFrame());
                robot.waitForIdle();
                SwingUtilities.invokeAndWait(() -> {
                    setLookAndFeel(laf);
                    createUI();
                });

                int waitCount = 0;
                while (!isFocusOwner()) {
                    robot.delay(100);
                    waitCount++;
                    if (waitCount > 20) {
                        throw new RuntimeException("Test Failed, waited for long, " +
                                "but the button can't gain focus for L&F: " + laf);
                    }
                }

                robot.keyPress(KeyEvent.VK_SPACE);
                robot.keyRelease(KeyEvent.VK_SPACE);

                if (buttonPressed) {
                    System.out.println("Test Passed for L&F: " + laf);
                } else {
                    throw new RuntimeException("Test Failed, button not pressed for L&F: " + laf);
                }

            } finally {
                SwingUtilities.invokeAndWait(SpaceKeyActivatesButton::disposeFrame);
            }
        }

    }

    private static boolean isFocusOwner() throws Exception {
        AtomicBoolean isFocusOwner = new AtomicBoolean(false);
        SwingUtilities.invokeAndWait(() -> isFocusOwner.set(focusedButton.isFocusOwner()));
        return isFocusOwner.get();
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

    private static void createUI() {
        JPanel panel = new JPanel();
        panel.add(new JButton("Button1"));
        focusedButton = new JButton("Button2");
        focusedButton.addActionListener(e -> buttonPressed = true);
        panel.add(focusedButton);

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(panel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        focusedButton.requestFocusInWindow();
    }

    private static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
            frame = null;
        }
    }
}
