/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;

/* @test
 * @bug 4666224
 * @summary Tests that focus indicator is painted properly in JTabbedPane
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4666224
 */

public class bug4666224 {
    private static JTabbedPane tabPane;
    private static JFrame frame;

    private static final String INSTRUCTIONS = """
                ON ALL PLATFORMS
                    1. Click on any of the tabs, focus indicator is visible.
                    2. Lose focus on the window by clicking on some other window.
                    3. Focus indicator should disappear
                    4. Regain focus on the window by pressing the tab,
                        the focus indicator should reappear.
                    5. If focus doesn't behave as above,
                        press 'Fail' else press 'Pass'.
                    6. Type 'C' to change the tab layout to WRAP_TAB_LAYOUT
                        and repeat from step 1 to 5.
                    7. Type 'R' to align the tabs to the right side and repeat
                        from step 1 to 5.
                    8. Type 'B' to align the tabs to the bottom side and repeat
                        from step 1 to 5.
                    9. Type 'L' to align the tabs to the left side and repeat
                        from step 1 to 5.
                    10. Type 'T' to align the tabs to the top side and repeat
                         from step 1 to 5.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("JTabbedPane Instructions")
                .instructions(INSTRUCTIONS)
                .rows(20)
                .columns(40)
                .testUI(bug4666224::createAndShowUI)
                .build()
                .awaitAndCheck();
    }

    public static JFrame createAndShowUI() {
        frame = new JFrame("Test JTabbedPane");
        tabPane = new JTabbedPane();
        tabPane.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                Point pt = e.getPoint();
                System.out.println("Index at location: "
                        + tabPane.indexAtLocation(pt.x, pt.y));
            }
        });
        InputMap inputMap = createInputMap();
        SwingUtilities.replaceUIInputMap(frame.getRootPane(), JComponent.WHEN_IN_FOCUSED_WINDOW, inputMap);
        ActionMap actionMap = createActionMap();
        SwingUtilities.replaceUIActionMap(frame.getRootPane(), actionMap);

        tabPane.setTabPlacement(JTabbedPane.TOP);
        tabPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

        for (int i = 0; i <= 10; i++) {
            JPanel panel = new JPanel();
            panel.setPreferredSize(new Dimension(500, 200));
            tabPane.addTab("Tab " + i, panel);
        }
        JPanel mainPanel = new JPanel();
        mainPanel.add(tabPane);

        frame.setSize(600, 300);
        frame.getContentPane().add(mainPanel);
        tabPane.requestFocus();
        return frame;
    }

    protected static InputMap createInputMap() {
        return LookAndFeel.makeComponentInputMap(frame.getRootPane(), new Object[] {
                "R", "right",
                "L", "left",
                "T", "top",
                "B", "bottom",
                "C", "changeLayout",
                "D", "dump"
        });
    }

    protected static ActionMap createActionMap() {
        ActionMap map = new ActionMap();
        map.put("right", new RotateAction(JTabbedPane.RIGHT));
        map.put("left", new RotateAction(JTabbedPane.LEFT));
        map.put("top", new RotateAction(JTabbedPane.TOP));
        map.put("bottom", new RotateAction(JTabbedPane.BOTTOM));
        map.put("changeLayout", new ChangeLayoutAction());
        map.put("dump", new DumpAction());
        return map;
    }

    private static class RotateAction extends AbstractAction {
        private int placement;
        public RotateAction(int placement) {
            this.placement = placement;
        }

        public void actionPerformed(ActionEvent e) {
            tabPane.setTabPlacement(placement);
        }
    }

    private static class ChangeLayoutAction extends AbstractAction {
        private boolean a = true;
        public void actionPerformed(ActionEvent e) {
            if (a) {
                tabPane.setTabLayoutPolicy(JTabbedPane.WRAP_TAB_LAYOUT);
                a = false;
            } else {
                tabPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
                a = true;
            }
        }
    }

    private static class DumpAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            for (int i = 0; i < tabPane.getTabCount(); i++) {
                System.out.println("Tab: " + i + " "
                        + tabPane.getUI().getTabBounds(tabPane, i));
            }
        }
    }
}
