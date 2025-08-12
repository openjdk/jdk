/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.event.ActionEvent;
import java.awt.GridLayout;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/*
 * @test
 * @bug 8225220
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @requires (os.family == "linux")
 * @summary JTabbedPane arrow should point to left or right direction
 *          when tab layout policy is set to SCROLL_TAB_LAYOUT and tab
 *          placement is set to either TOP or BOTTOM
 * @run main/manual TestJTabbedPaneArrowDirection
 */

public class TestJTabbedPaneArrowDirection {
    private static JFrame frame;
    private static JTabbedPane tabPane;
    private static final String INSTRUCTIONS =
            "1. Observe the arrows are pointing to left and right direction\n" +
               " for tab placement set to TOP. Default tab placement is TOP.\n\n" +
            "2. Press BOTTOM to change the tab placement to bottom.\n\n" +
            "3. Observe arrows are pointing to the left and right direction.\n\n" +
            "4. If the behaviour is correct, press Pass else Fail.";

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
        PassFailJFrame passFailJFrame = PassFailJFrame.builder()
                .title("JTabbedPane Arrow Direction Test Instructions")
                .instructions(INSTRUCTIONS)
                .testTimeOut(5)
                .rows(12)
                .columns(40)
                .screenCapture()
                .build();
        SwingUtilities.invokeAndWait(
                TestJTabbedPaneArrowDirection::createAndShowUI);
        passFailJFrame.awaitAndCheck();
    }

    private static void createAndShowUI() {
        int NUM_TABS = 15;
        frame = new JFrame("Test JTabbedPane Arrow Direction");
        JTabbedPane tabPane = new JTabbedPane();
        tabPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabPane.setTabPlacement(JTabbedPane.TOP);
        PassFailJFrame.addTestWindow(frame);
        PassFailJFrame.positionTestWindow(
                frame, PassFailJFrame.Position.HORIZONTAL);
        for( int i = 0; i < NUM_TABS; ++i) {
            tabPane.addTab("Tab " + i , new JLabel("Content Area"));
        }
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(tabPane, BorderLayout.CENTER);
        JButton topButton = new JButton(new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                tabPane.setTabPlacement(JTabbedPane.TOP);
            }
        });
        topButton.setText("TOP");
        JButton bottomButton = new JButton(new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                tabPane.setTabPlacement(JTabbedPane.BOTTOM);
            }
        });
        bottomButton.setText("BOTTOM");
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2));
        buttonPanel.add(topButton);
        buttonPanel.add(bottomButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        frame.add(panel);
        frame.setSize(500, 500);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}
