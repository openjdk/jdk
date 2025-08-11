/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4187996
 * @summary Tests that Metal submenus overlap menu
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4187996
 */

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.UIManager;

public class bug4187996 {

    private static final String INSTRUCTIONS = """
        Open the menu "Menu", then "Submenu".
        The submenu should be top-aligned with the menu,
        and slightly overlap it horizontally. Otherwise test fails.""";

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        PassFailJFrame.builder()
                .title("bug4187996 Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(bug4187996::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {
        JFrame frame = new JFrame("bug4187996");
        JMenu submenu = new JMenu("Submenu");
        submenu.add(new JMenuItem("Sub 1"));
        submenu.add(new JMenuItem("Sub 2"));

        JMenu menu = new JMenu("Menu");
        menu.add(submenu);
        menu.add(new JMenuItem("Item 1"));
        menu.add(new JMenuItem("Item 2"));

        JMenuBar mbar = new JMenuBar();
        mbar.add(menu);
        frame.setJMenuBar(mbar);
        frame.setSize(300, 100);
        return frame;
    }
}
