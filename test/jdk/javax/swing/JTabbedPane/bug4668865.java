/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4668865
 * @summary Tests if JTabbedPane's setEnabledAt properly renders bounds of Tabs
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4668865
*/

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;

public class bug4668865 {

    static final String INSTRUCTIONS = """
        This tests that tooltips are shown for all tabs in all orientations,
        when it is necessary to scroll to see all the tabs.
        Use the buttons to select each orientation (top/bottom/left/right) in turn.
        Scroll through the 8 tabs - using the navigation arrows as needed.
        Move the mouse over each tab in turn and verify that the matching tooltip is shown
        after sufficient hover time.
        The test PASSES if the tooltips are shown for all cases, and FAILS otherwise.
    """;

    static JTabbedPane tabPane;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
            .title("bug4668865 Test Instructions")
            .instructions(INSTRUCTIONS)
            .columns(50)
            .testUI(bug4668865::createUI)
            .build()
            .awaitAndCheck();
    }

    static JFrame createUI() {
        JFrame frame = new JFrame("bug4668865");

        tabPane = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
        for(int i = 1; i < 9; i++) {
            tabPane.addTab("Tab" + i, null, new JTextField("Tab" + i), "Tab" + i);
        }
        frame.add(tabPane, BorderLayout.CENTER);

        JButton top = new JButton(new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                tabPane.setTabPlacement(JTabbedPane.TOP);
            }
        });
        top.setText("Top");
        frame.add(top, BorderLayout.NORTH);

        JButton bottom = new JButton(new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                tabPane.setTabPlacement(JTabbedPane.BOTTOM);
            }
        });
        bottom.setText("Bottom");
        frame.add(bottom, BorderLayout.SOUTH);

        JButton left = new JButton(new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                tabPane.setTabPlacement(JTabbedPane.LEFT);
            }
        });

        left.setText("Left");
        frame.add(left, BorderLayout.WEST);

        JButton right = new JButton(new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                tabPane.setTabPlacement(JTabbedPane.RIGHT);
            }
        });

        right.setText("Right");
        frame.add(right, BorderLayout.EAST);

        frame.setSize(400, 400);
        return frame;
    }

}
