/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5021949
 * @key headful
 * @summary  Verifies JSplitPane setEnabled(false) disables one touch expandable clicks
 * @run main TestSplitPaneEnableTest
 */

import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JSplitPane;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public class TestSplitPaneEnableTest {
    private static JButton leftOneTouchButton;
    private static JButton rightOneTouchButton;
    private static JFrame frame;
    private static JSplitPane jsp;
    private static volatile Point loc;
    private static volatile int prevDivLoc;
    private static volatile int curDivLoc;

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

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        for (UIManager.LookAndFeelInfo laf : UIManager.getInstalledLookAndFeels()) {
            System.out.println("Testing LAF : " + laf.getClassName());
            SwingUtilities.invokeAndWait(() -> setLookAndFeel(laf));

            try {
                SwingUtilities.invokeAndWait(() -> {
                    frame = new JFrame("SplitPaneTest");
                    jsp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                         new JButton("Left"),
                                         new JButton("Right"));

                    frame.getContentPane().add(jsp);
                    jsp.setUI(new TestSplitPaneUI());
                    jsp.setOneTouchExpandable(true);

                    jsp.setEnabled(false);

                    frame.setSize(400, 200);
                    frame.setLocationRelativeTo(null);
                    frame.setVisible(true);
                });

                robot.waitForIdle();
                robot.delay(1000);

                SwingUtilities.invokeAndWait(() -> {
                    loc = leftOneTouchButton.getLocationOnScreen();
                    prevDivLoc = jsp.getDividerLocation();
                });
                robot.mouseMove(loc.x, loc.y);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

                robot.waitForIdle();
                robot.delay(1000);

                SwingUtilities.invokeAndWait(() -> {
                    curDivLoc = jsp.getDividerLocation();
                });
                System.out.println("current Divider location " + curDivLoc);
                if (curDivLoc != prevDivLoc) {
                    throw new RuntimeException("Divider position changed on disabled oneTouchExpandable arrow click");
                }
            } finally {
                SwingUtilities.invokeAndWait(() -> {
                    if (frame != null) {
                        frame.dispose();
                    }
                });
            }
        }
    }

    static class TestSplitPaneUI extends BasicSplitPaneUI {

        public TestSplitPaneUI() {
            super();
        }

        public BasicSplitPaneDivider createDefaultDivider() {
            return new TestSplitPaneDivider(this);
        }
    }

    static class TestSplitPaneDivider extends BasicSplitPaneDivider {

        public TestSplitPaneDivider(BasicSplitPaneUI ui) {
            super(ui);
        }

        protected JButton createLeftOneTouchButton() {
            leftOneTouchButton = super.createLeftOneTouchButton();
            return leftOneTouchButton;
        }

        protected JButton createRightOneTouchButton() {
            rightOneTouchButton = super.createRightOneTouchButton();
            return rightOneTouchButton;
        }
    }
}

