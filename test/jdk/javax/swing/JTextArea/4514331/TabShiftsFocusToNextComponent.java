/*
 * Copyright (c) 2002, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Point;
import java.awt.Robot;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;


import static javax.swing.UIManager.getInstalledLookAndFeels;

/*
 * @test
 * @key headful
 * @bug 4514331
 * @summary Check whether pressing <Tab> key always shift focus to next component,
 *          even though the current focus is in JTextArea and some text is already selected.
 * @run main TabShiftsFocusToNextComponent
 */
public class TabShiftsFocusToNextComponent {

    private static JFrame frame;
    private static JTextArea textArea;
    private static Robot robot;
    private static CountDownLatch textAreaGainedFocusLatch;
    private static CountDownLatch buttonGainedFocusLatch;

    public static void main(String[] s) throws Exception {
        runTest();
    }

    public static void runTest() throws Exception {
        robot = new Robot();
        robot.setAutoWaitForIdle(true);
        robot.setAutoDelay(200);
        List<String> lafs = Arrays.stream(getInstalledLookAndFeels())
                                  .map(UIManager.LookAndFeelInfo::getClassName)
                                  .collect(Collectors.toList());
        for (final String laf : lafs) {
            textAreaGainedFocusLatch = new CountDownLatch(1);
            buttonGainedFocusLatch = new CountDownLatch(1);
            try {
                AtomicBoolean lafSetSuccess = new AtomicBoolean(false);
                SwingUtilities.invokeAndWait(() -> {
                    lafSetSuccess.set(setLookAndFeel(laf));
                    if (lafSetSuccess.get()) {
                        createUI();
                    }
                });
                if (!lafSetSuccess.get()) {
                    continue;
                }
                robot.waitForIdle();

                SwingUtilities.invokeAndWait(() -> textArea.requestFocusInWindow());

                // Waits until the textArea gains focus.
                if (!textAreaGainedFocusLatch.await(3, TimeUnit.SECONDS)) {
                    throw new RuntimeException("Test Failed, waited for long, " +
                            "but the JTextArea can't gain focus for L&F: " + laf);
                }

                AtomicReference<Point> textAreaLoc = new AtomicReference<Point>();
                SwingUtilities.invokeAndWait(() -> {
                    textAreaLoc.set(textArea.getLocationOnScreen());
                });

                final int x = textAreaLoc.get().x;
                final int y = textAreaLoc.get().y;
                robot.mouseMove(x + 5, y + 5);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseMove(x + 20, y + 5);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                robot.keyPress(KeyEvent.VK_TAB);
                robot.keyRelease(KeyEvent.VK_TAB);

                // Waits until the button gains focus.
                if (!buttonGainedFocusLatch.await(3, TimeUnit.SECONDS)) {
                    throw new RuntimeException("Test Failed, waited for long, " +
                            "but the Button can't gain focus when 'Tab' key pressed for L&F: " + laf);
                } else {
                    System.out.println(" Test passed for " + laf);
                }
            } finally {
                SwingUtilities.invokeAndWait(TabShiftsFocusToNextComponent::disposeFrame);
            }
        }
    }


    private static void createUI() {
        frame = new JFrame();
        JPanel panel = new JPanel();
        textArea = new JTextArea("I am a JTextArea");
        textArea.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                textAreaGainedFocusLatch.countDown();
            }
        });
        textArea.setEditable(false);
        panel.add(textArea);
        JButton button = new JButton("Button");
        panel.add(button);
        button.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                buttonGainedFocusLatch.countDown();
            }
        });

        frame.add(panel);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setUndecorated(true);
        frame.pack();
        frame.setAlwaysOnTop(true);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static boolean setLookAndFeel(String lafName) {
        try {
            UIManager.setLookAndFeel(lafName);
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Ignoring Unsupported L&F: " + lafName);
            return false;
        } catch (ClassNotFoundException | InstantiationException
                | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    private static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
            frame = null;
        }
    }
}
