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
 * @bug 4685843
 * @requires (os.family == "windows")
 * @summary Tests that disabled JCheckBoxMenuItem's are drawn properly in
 * Windows LAF
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4685843
 */

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.UIManager;

public class bug4685843 {
    public static void main(String[] args) throws Exception {
        try {
            UIManager.setLookAndFeel (
                "com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        } catch (Exception e) {
            throw new RuntimeException("Failed to set Windows LAF");
        }

        String INSTRUCTIONS = """
            In the window named "bug4685843" open File menu.
            If all three disabled items are drawn properly press "Pass".
            Otherwise press "Fail".
            """;
        PassFailJFrame.builder()
            .instructions(INSTRUCTIONS)
            .columns(35)
            .testUI(bug4685843::initialize)
            .build()
            .awaitAndCheck();
    }

    private static JFrame initialize() {
        JMenuBar jMenuBar = new JMenuBar();
        JMenu jMenu = new JMenu("File");
        JMenuItem jMenuItem = new JMenuItem("JMenuItem");
        JCheckBoxMenuItem jCheckBoxMenuItem =
            new JCheckBoxMenuItem("JCheckBoxMenuItem");
        JRadioButtonMenuItem jRadioButtonMenuItem =
            new JRadioButtonMenuItem("JRadioButtonMenuItem");

        jMenuItem.setEnabled(false);
        jMenu.add(jMenuItem);
        jCheckBoxMenuItem.setEnabled(false);
        jMenu.add(jCheckBoxMenuItem);
        jRadioButtonMenuItem.setEnabled(false);
        jMenu.add(jRadioButtonMenuItem);
        jMenuBar.add(jMenu);

        JFrame mainFrame = new JFrame("bug4685843");
        mainFrame.setJMenuBar(jMenuBar);
        mainFrame.setSize(200, 200);
        return mainFrame;
    }
}
