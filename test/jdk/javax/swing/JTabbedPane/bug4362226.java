/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4362226
 * @summary JTabbedPane's HTML title should have proper offsets
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4362226
*/

import java.awt.BorderLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;

public class bug4362226 {

    static final String PLAIN = "Label";
    static final String HTML = "<html>Label</html>";

    static final String INSTRUCTIONS = """
        The test window contains a JTabbedPane with two tabs.
        The titles for both tabs should look similar and drawn with the same fonts.
        The text of the tabs should start in a position that is offset from the left
        boundary of the tab, so there is clear space between them.
        If there is no space, then the test FAILS.
    """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
            .title("bug4362226 Test Instructions")
            .instructions(INSTRUCTIONS)
            .columns(60)
            .testUI(bug4362226::createUI)
            .build()
            .awaitAndCheck();
    }

    static JFrame createUI() {
        try {
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (Exception e) {
        }
        JFrame frame = new JFrame("bug4362226");
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(PLAIN, new JPanel());
        tabs.addTab(HTML, new JPanel());
        frame.add(tabs, BorderLayout.CENTER);
        frame.setSize(500, 300);
        return frame;
    }
}
