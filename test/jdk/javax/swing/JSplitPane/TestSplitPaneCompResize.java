/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4765299
 * @key headful
 * @summary Verifies componentResized() is called with nested JSplitPanes
 * @run main TestSplitPaneCompResize
 */

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.SwingUtilities;

public class TestSplitPaneCompResize {

    private static JFrame frame;
    private JSplitPane outer;
    private static JButton leftOneTouchButton;
    private static volatile Point leftBtnPos;
    private static volatile boolean resized;

    public TestSplitPaneCompResize() {

        // set up a simple list embedded inside a scroll pane
        String[] listItems = {"Item1", "Item2"};
        JList list = new JList<>(listItems);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);

        JScrollPane comp = new JScrollPane(list);
        JSplitPane inner = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                                          comp, new JPanel());
        JPanel rightPanel = new JPanel();

        outer = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                               inner, rightPanel);
        outer.setDividerLocation(150);

        //Provide minimum sizes for the two components in the split pane
        Dimension minimumSize = new Dimension(100, 50);
        comp.setMinimumSize(minimumSize);
        inner.setMinimumSize(minimumSize);
        rightPanel.setMinimumSize(minimumSize);

        //Provide a preferred size for the split pane
        outer.setPreferredSize(new Dimension(400, 200));
        inner.addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                System.out.println("inner resized");
            }
        });
        comp.addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                resized = true;
                System.out.println("comp resized");
            }
        });
    }

    public JSplitPane getSplitPane() {
        return outer;
    }


    public static void main(String[] s) throws Exception {
        Robot robot = new Robot();
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame("SplitPaneDemo");

                TestSplitPaneCompResize sp = new TestSplitPaneCompResize();
                JSplitPane jsp = sp.getSplitPane();
                frame.getContentPane().add(jsp);
                jsp.setUI(new MySplitPaneUI());
                jsp.setOneTouchExpandable(true);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });

            robot.waitForIdle();
            robot.delay(1000);

            SwingUtilities.invokeAndWait(() -> {
                leftBtnPos = leftOneTouchButton.getLocationOnScreen();
                leftBtnPos.x += leftOneTouchButton.getWidth() / 2;
                leftBtnPos.y += leftOneTouchButton.getHeight() / 2;
            });

            resized = false;
            robot.mouseMove(leftBtnPos.x, leftBtnPos.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(1000);

            if (!resized) {
                throw new RuntimeException("ComponentResized not called");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }


    static class MySplitPaneUI extends BasicSplitPaneUI {

        public MySplitPaneUI() {
            super();
        }

        public BasicSplitPaneDivider createDefaultDivider() {
            return new MySplitPaneDivider(this);
        }
    }

    static class MySplitPaneDivider extends BasicSplitPaneDivider {

        public MySplitPaneDivider(BasicSplitPaneUI ui) {
            super(ui);
        }

        protected JButton createLeftOneTouchButton() {
            leftOneTouchButton = super.createLeftOneTouchButton();
            return leftOneTouchButton;
        }

        protected JButton createRightOneTouchButton() {
            JButton rightOneTouchButton = super.createRightOneTouchButton();
            return rightOneTouchButton;
        }
    }
}
