/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/*
 * @test
 * @bug 8361283
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @requires (os.family == "mac")
 * @summary VO shouldn't announce the tab items as RadioButton
 * @run main/manual AccessibleTabbedPaneRoleTest
 */

public class AccessibleTabbedPaneRoleTest {

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                This test is applicable only on macOS.

                Test UI contains a JFrame containing JTabbedPane with multiple tabs.

                Follow these steps to test the behaviour:

                1. Start the VoiceOver (Press Command + F5) application.
                2. Test Frame should have focus. If not, then bring focus to test frame.
                3. Press Left / Right arrow key to move to next and prevoius tab.
                4. VO should announce "Tab" in stead of "RadioButton" for tab items.
                   (For e.g. When Tab 1 is selected, VO should announce "Tab 1, selected,
                   tab, group).
                5. Press Pass if you are able to hear correct announcements
                   else Fail.""";

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(45)
                .testUI(AccessibleTabbedPaneRoleTest::createUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createUI() {
        int NUM_TABS = 6;
        JFrame frame = new JFrame("Test Frame");
        JTabbedPane tabPane = new JTabbedPane();
        tabPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabPane.setTabPlacement(JTabbedPane.TOP);
        for (int i = 0; i < NUM_TABS; ++i) {
            tabPane.addTab("Tab " + i , new JLabel("Content Area"));
        }
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(tabPane, BorderLayout.CENTER);
        frame.add(panel);
        frame.setSize(400, 100);
        return frame;
    }
}
