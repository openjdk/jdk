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
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
 * @bug 8281738
 * @summary Check whether pressing <Space> key generates
 *          ActionEvent on focused Button or not.
 * @run main SpaceKeyActivatesButton
 */
public class SpaceKeyActivatesButton {

    private static volatile boolean buttonPressed;
    private static JFrame frame;
    private static JButton focusedButton;
    private static CountDownLatch buttonGainedFocusLatch;

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
            buttonGainedFocusLatch = new CountDownLatch(1);
            try {
                buttonPressed = false;
                System.out.println("Testing laf : " + laf);
                AtomicBoolean lafSetSuccess = new AtomicBoolean(false);
                SwingUtilities.invokeAndWait(() -> {
                    lafSetSuccess.set(setLookAndFeel(laf));
                    // Call createUI() only if setting laf succeeded
                    if (lafSetSuccess.get()) {
                        createUI();
                    }
                });
                // If setting laf failed, then just get next laf and continue
                if (!lafSetSuccess.get()) {
                    continue;
                }
                robot.waitForIdle();

                // Wait until the button2 gains focus.
                if (!buttonGainedFocusLatch.await(3, TimeUnit.SECONDS)) {
                    throw new RuntimeException("Test Failed, waited too long, " +
                            "but the button can't gain focus for laf : " + laf);
                }

                robot.keyPress(KeyEvent.VK_SPACE);
                robot.keyRelease(KeyEvent.VK_SPACE);

                if (buttonPressed) {
                    System.out.println("Test Passed for laf : " + laf);
                } else {
                    throw new RuntimeException("Test Failed, button not pressed for laf : " + laf);
                }

            } finally {
                SwingUtilities.invokeAndWait(SpaceKeyActivatesButton::disposeFrame);
            }
        }

    }

    private static boolean setLookAndFeel(String lafName) {
        try {
            UIManager.setLookAndFeel(lafName);
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Ignoring Unsupported laf : " + lafName);
            return false;
        } catch (ClassNotFoundException | InstantiationException
                | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    private static void createUI() {
        frame = new JFrame();
        JPanel panel = new JPanel();
        panel.add(new JButton("Button1"));
        focusedButton = new JButton("Button2");
        focusedButton.addActionListener(e -> buttonPressed = true);
        focusedButton.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                buttonGainedFocusLatch.countDown();
            }
        });
        panel.add(focusedButton);

        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
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
