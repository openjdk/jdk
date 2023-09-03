/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;

/*
 * @test
 * @bug 4870674
 * @summary JSplitPane's one-touch buttons should deal with resized split panes better
 * @key headful
 * @run main bug4870674
 */

public class bug4870674 {
    private static JSplitPane jsp0, jsp1;
    private static JButton[] leftOneTouchButton = new JButton[2];
    private static JButton[] rightOneTouchButton = new JButton[2];
    private static JFrame frame;
    private static Robot robot;
    private static volatile boolean passed = true;
    private static volatile Point rightBtnPos0;
    private static volatile Point leftBtnPos0;
    private static volatile Point rightBtnPos1;
    private static volatile Point leftBtnPos1;

    public static void main(String[] args) throws Exception {
        try {
            robot = new Robot();
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame("Test");
                frame.getContentPane().setLayout(new GridLayout(2, 1));

                jsp0 = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                        new JButton("Left"),
                        new JButton("Right"));
                frame.getContentPane().add(jsp0);

                jsp0.setUI(new TestSplitPaneUI(0));
                jsp0.setOneTouchExpandable(true);

                jsp1 = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                        new JButton("Left"),
                        new JButton("Right"));
                frame.getContentPane().add(jsp1);

                jsp1.setUI(new TestSplitPaneUI(1));
                jsp1.setOneTouchExpandable(true);

                frame.setSize(300, 100);
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                rightBtnPos0 = rightOneTouchButton[0].getLocationOnScreen();
                rightBtnPos0.x += rightOneTouchButton[0].getWidth() / 2;
                rightBtnPos0.y += rightOneTouchButton[0].getHeight() / 2;

                leftBtnPos1 = leftOneTouchButton[1].getLocationOnScreen();
                leftBtnPos1.x += leftOneTouchButton[0].getWidth() / 2;
                leftBtnPos1.y += leftOneTouchButton[0].getHeight() / 2;

                leftBtnPos0 = leftOneTouchButton[0].getLocationOnScreen();
                leftBtnPos0.x += leftOneTouchButton[0].getWidth() / 2;
                leftBtnPos0.y += leftOneTouchButton[0].getHeight() / 2;

                rightBtnPos1 = rightOneTouchButton[1].getLocationOnScreen();
                rightBtnPos1.x += rightOneTouchButton[0].getWidth() / 2;
                rightBtnPos1.y += rightOneTouchButton[0].getHeight() / 2;

                jsp0.setDividerLocation(250);
            });
            robot.mouseMove(rightBtnPos0.x, rightBtnPos0.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            SwingUtilities.invokeAndWait(() -> {
                jsp1.setDividerLocation(250);
            });
            robot.waitForIdle();
            robot.delay(100);
            robot.mouseMove(leftBtnPos1.x, leftBtnPos1.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            SwingUtilities.invokeAndWait(() -> {
                frame.setSize(200, 100);
            });
            robot.waitForIdle();
            robot.delay(100);
            robot.mouseMove(leftBtnPos0.x, leftBtnPos0.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(100);
            robot.mouseMove(rightBtnPos1.x, rightBtnPos1.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(100);

            SwingUtilities.invokeAndWait(() -> {
                if (jsp0.getDividerLocation() > jsp0.getMaximumDividerLocation() ||
                        jsp1.getDividerLocation() > jsp1.getMaximumDividerLocation()) {
                    passed = false;
                }
            });

            if (!passed) {
                throw new RuntimeException("The divider location couldn't " +
                        "be greater then its maximum location");
            }
            System.out.println("Test Passed!");
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    static class TestSplitPaneUI extends BasicSplitPaneUI {
        int i;

        public TestSplitPaneUI(int i) {
            super();
            this.i = i;
        }

        public BasicSplitPaneDivider createDefaultDivider() {
            return new TestSplitPaneDivider(this, i);
        }
    }

    static class TestSplitPaneDivider extends BasicSplitPaneDivider {
        int i = 0;

        public TestSplitPaneDivider(BasicSplitPaneUI ui, int i) {
            super(ui);
            this.i = i;
        }

        protected JButton createLeftOneTouchButton() {
            leftOneTouchButton[i] = super.createLeftOneTouchButton();
            return leftOneTouchButton[i];
        }

        protected JButton createRightOneTouchButton() {
            rightOneTouchButton[i] = super.createRightOneTouchButton();
            return rightOneTouchButton[i];
        }
    }
}
