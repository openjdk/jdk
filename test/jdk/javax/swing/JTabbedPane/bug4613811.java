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
 * @bug 4613811
 * @summary Scrollable Buttons of JTabbedPane don't
 *          get enabled or disabled on selecting tab
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4613811
 */

import java.awt.BorderLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;

public class bug4613811 {
    private static final String INSTRUCTIONS = """
            Select different tabs and check that the scrollable
            buttons are correctly enabled and disabled.

            When the very first tab (Tab 1) is fully visible
            On macOS:
            the left arrow button should NOT be visible.

            On other platforms:
            the left arrow button should be disabled.

            If the last tab (Tab 5) is fully visible
            On macOS:
            the right arrow button should NOT be visible.

            On other platforms:
            the right arrow button should be disabled.

            If the above is true press PASS else FAIL.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(30)
                .testUI(bug4613811::createAndShowUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createAndShowUI() {
        JFrame frame = new JFrame("bug4613811 - JTabbedPane Test");
        final JTabbedPane tabPane = new JTabbedPane(JTabbedPane.TOP,
                                                    JTabbedPane.SCROLL_TAB_LAYOUT);
        for (int i = 1; i <= 5; i++) {
            tabPane.addTab("TabbedPane: Tab " + i, null, new JLabel("Tab " + i));
        }
        frame.add(tabPane, BorderLayout.CENTER);
        frame.setResizable(false);
        frame.setSize(400, 200);
        return frame;
    }
}
