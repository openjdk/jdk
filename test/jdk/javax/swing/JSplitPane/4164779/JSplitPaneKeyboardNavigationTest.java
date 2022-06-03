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

import java.awt.BorderLayout;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;

import static javax.swing.UIManager.getInstalledLookAndFeels;

/*
 * @test
 * @key headful
 * @bug 4164779
 * @summary This test confirms that JSplitPane keyboard navigation supports F6 and Ctrl+Tab.
 * @run main JSplitPaneKeyboardNavigationTest
 */
public class JSplitPaneKeyboardNavigationTest {

    private static final StringBuffer failedVerifiers = new StringBuffer();
    private static JPanel panel;
    private static JButton leftButton;
    private static JButton rightButton1;
    private static JButton rightButton2;
    private static JButton topButton;
    private static JButton bottomButton;
    private static Robot robot;
    private static JFrame frame;

    public static void main(String[] s) throws Exception {
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

                // Press Right button 1 and move focus to it.
                pressButton(rightButton1);
                hitKeys(KeyEvent.VK_F6);

                // Verifier1 - Verifies that, F6 transfers focus to the right/bottom side of the splitpane
                if (isFocusOwner(rightButton2)) {
                    System.out.println("Verifier 1 passed");
                } else {
                    failedVerifiers.append("1,");
                    System.out.println("Verifier 1 failed, rightButton2 is not focus owner," +
                            "F6 doesn't transfer focus to the right/bottom side of the splitpane");
                }

                // Press Right button 2 and move focus to it.
                pressButton(rightButton2);
                hitKeys(KeyEvent.VK_F6);

                // Verifier2 - Verifies that, F6 transfers focus to the left side of the parent splitpane,
                // if the right/bottom side of splitpane already has focus, and it is contained within another splitpane
                if (isFocusOwner(leftButton)) {
                    System.out.println("Verifier 2 passed");
                } else {
                    failedVerifiers.append("2,");
                    System.out.println("Verifier 2 failed, leftButton is not focus owner, " +
                            "F6 doesn't transfer focus to the left side of the splitpane");
                }

                // Press Left button and move focus to it.
                pressButton(leftButton);
                hitKeys(KeyEvent.VK_CONTROL, KeyEvent.VK_TAB);
                // Verifier3 - Verifies that, CTRL-TAB navigates forward outside the JSplitPane
                if (isFocusOwner(bottomButton)) {
                    System.out.println("Verifier 3 passed");
                } else {
                    failedVerifiers.append("3,");
                    System.out.println("Verifier 3 failed, bottomButton is not focus owner, " +
                            "CTRL-TAB doesn't navigate forward outside the JSplitPane");
                }

                // Press Left button and move focus to it.
                pressButton(leftButton);
                hitKeys(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_TAB);

                // Verifier4 - Verifies that, CTRL-SHIFT-TAB navigates backward outside the JSplitPane
                if (isFocusOwner(topButton)) {
                    System.out.println("Verifier 4 passed");
                } else {
                    failedVerifiers.append("4");
                    System.out.println("Verifier 4 failed, topButton is not focus owner, " +
                            "CTRL-SHIFT-TAB doesn't navigate backward outside the JSplitPane");
                }

                if (failedVerifiers.toString().isEmpty()) {
                    System.out.println("Test passed, All verifiers succeeded for " + laf);
                } else {
                    throw new RuntimeException("Test failed, verifiers " + failedVerifiers.toString() + " failed for " + laf);
                }
            } finally {
                SwingUtilities.invokeAndWait(JSplitPaneKeyboardNavigationTest::disposeFrame);
            }
        }
    }

    private static boolean isFocusOwner(JButton button) throws Exception {
        final AtomicBoolean isFocusOwner = new AtomicBoolean(false);
        SwingUtilities.invokeAndWait(() -> {
            isFocusOwner.set(button.isFocusOwner());
        });
        return isFocusOwner.get();
    }

    private static void pressButton(JButton button) throws Exception {
        final AtomicReference<Point> loc = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            loc.set(button.getLocationOnScreen());
        });
        final Point buttonLoc = loc.get();
        robot.mouseMove(buttonLoc.x + 8, buttonLoc.y + 8);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    public static void createUI() {
        frame = new JFrame();
        panel = new JPanel();
        panel.setLayout(new BorderLayout());
        leftButton = new JButton("Left Button");
        rightButton1 = new JButton("Right Button 1");
        rightButton2 = new JButton("Right Button 2");
        topButton = new JButton("Top Button");
        bottomButton = new JButton("Bottom Button");
        panel.add(topButton, BorderLayout.NORTH);
        panel.add(bottomButton, BorderLayout.SOUTH);
        final JSplitPane splitPane2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true, rightButton1, rightButton2);
        final JSplitPane splitPane1 = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, leftButton, splitPane2);
        panel.add(splitPane1, BorderLayout.CENTER);
        frame.setContentPane(panel);
        frame.setSize(200, 200);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.pack();
        frame.setAlwaysOnTop(true);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void hitKeys(int... keys) {
        for (int key : keys) {
            robot.keyPress(key);
        }

        for (int i = keys.length - 1; i >= 0; i--) {
            robot.keyRelease(keys[i]);
        }
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
