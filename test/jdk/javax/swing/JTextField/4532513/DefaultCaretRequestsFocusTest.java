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

import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.swing.InputVerifier;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;

import static javax.swing.UIManager.getInstalledLookAndFeels;

/*
 * @test
 * @key headful
 * @bug 4532513
 * @summary Verifies that DefaultCaret doesn't requests focus in mouseClick and mousePressed
 *          causing the associated input verifier to fire twice.
 * @run main DefaultCaretRequestsFocusTest
 */
public class DefaultCaretRequestsFocusTest {

    private static JTextField jTextField1;
    private static JTextField jTextField2;
    private static JTextField jTextField3;
    private static JFrame frame;
    private static Robot robot;
    private static volatile int shouldYieldFocusCount;

    public static void main(String[] args) throws Exception {
        runTest();
    }

    public static void runTest() throws Exception {
        robot = new Robot();
        robot.setAutoWaitForIdle(true);
        robot.setAutoDelay(200);

        List<String> lafs = Arrays.stream(getInstalledLookAndFeels())
                                  .map(LookAndFeelInfo::getClassName)
                                  .collect(Collectors.toList());
        for (final String laf : lafs) {
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

                AtomicReference<Point> jTextField1LocRef = new AtomicReference<>();
                AtomicReference<Point> jTextField2LocRef = new AtomicReference<>();
                SwingUtilities.invokeAndWait(() -> {
                    jTextField1LocRef.set(jTextField1.getLocationOnScreen());
                    jTextField2LocRef.set(jTextField2.getLocationOnScreen());
                });
                final Point jTextField1Loc = jTextField1LocRef.get();
                final Point jTextField2Loc = jTextField2LocRef.get();

                shouldYieldFocusCount = 0;

                // Click on TextField2
                robot.mouseMove(jTextField2Loc.x + 5, jTextField2Loc.y + 5);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

                typeSomeText();

                // Click on TextField1
                robot.mouseMove(jTextField1Loc.x + 5, jTextField1Loc.y + 5);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

                typeSomeText();

                if (shouldYieldFocusCount == 1) {
                    System.out.println("Test passed for " + laf);
                } else {
                    throw new RuntimeException("Test failed for " + laf
                            + " as InputVerifier.shouldYieldFocus() was called " + shouldYieldFocusCount
                            + " times on jTextField2, but it is expected to be called only once.");
                }

            } finally {
                SwingUtilities.invokeAndWait(DefaultCaretRequestsFocusTest::disposeFrame);
            }
        }
    }

    private static void typeSomeText() {
        robot.keyPress(KeyEvent.VK_T);
        robot.keyRelease(KeyEvent.VK_T);
        robot.keyPress(KeyEvent.VK_E);
        robot.keyRelease(KeyEvent.VK_E);
        robot.keyPress(KeyEvent.VK_X);
        robot.keyRelease(KeyEvent.VK_X);
        robot.keyPress(KeyEvent.VK_T);
        robot.keyRelease(KeyEvent.VK_T);
    }

    private static void createUI() {
        frame = new JFrame();
        jTextField1 = new JTextField(6);
        jTextField2 = new JTextField(6);
        jTextField3 = new JTextField(6);
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(3, 1));
        panel.add(jTextField1);
        panel.add(jTextField2);
        panel.add(jTextField3);

        InputVerifier iv = new InputVerifier() {
            public boolean verify(JComponent input) {
                System.out.println("InputVerifier.verify() called");
                return false;
            }

            public boolean shouldYieldFocus(JComponent input) {
                ++shouldYieldFocusCount;
                System.out.println("InputVerifier.shouldYieldFocus() called " + shouldYieldFocusCount);
                return false;
            }
        };

        jTextField2.setInputVerifier(iv);

        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.add(panel);
        frame.pack();
        frame.setAlwaysOnTop(true);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
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

    private static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
            frame = null;
        }
    }

}
