/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6259533
 * @requires (os.family == "windows")
 * @summary Win L&F : JTabbedPane should move upwards the tabComponent of the selected tab.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug6259533
*/

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;

public class bug6259533 {

    static final String INSTRUCTIONS = """
        This test is for the Windows LaF only.

        You should see a JTabbedPane with two tabs.
        The first tab uses a string and the second tab has a JLabel as a tabComponent

        Select each tab and notice that on selection the tab title
        is moved upwards slightly in comparison with the unselected tab

        If that is the observed behaviour, press PASS, press FAIL otherwise.

    """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
            .instructions(INSTRUCTIONS)
            .columns(40)
            .testUI(bug6259533::createUI)
            .build()
            .awaitAndCheck();
    }

    static JFrame createUI() {
        try {
            UIManager.setLookAndFeel(
                "com.sun.java.swing.plaf.windows.WindowsClassicLookAndFeel");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        JFrame frame = new JFrame("bug6259533");
        JTabbedPane pane = new JTabbedPane();
        pane.add("String Tab", null);
        pane.add("Tab 2", null);
        JLabel label = new JLabel("JLabel Tab");
        pane.setTabComponentAt(1, label);
        frame.add(pane);
        frame.setSize(400, 200);
        return frame;
    }

}
