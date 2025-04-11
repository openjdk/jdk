/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4530303
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Tests JPopupMenu.pack()
 * @run main/manual bug4530303
 */

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

public class bug4530303 {

    static final String INSTRUCTIONS = """
        The test window has a menu bar.
        Open the menu "Menu" and place the mouse pointer over the first menu item, "Point here".
        The second menu item, "Ghost", should be replaced with another item, "Fixed!".
        If the item just disappears and no new item appears in the empty space, the test FAILS.
    """;

    static volatile JMenu menu;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
            .instructions(INSTRUCTIONS)
            .columns(60)
            .testUI(bug4530303::createUI)
            .build()
            .awaitAndCheck();
    }

    static JFrame createUI() {
        JFrame frame = new JFrame("bug4530303");
        menu = new JMenu("Menu");
        JMenuItem item = new JMenuItem("Point here");
        item.addMouseListener(new MenuBuilder());
        menu.add(item);
        menu.add(new JMenuItem("Ghost"));

        JMenuBar mbar = new JMenuBar();
        mbar.add(menu);
        frame.setJMenuBar(mbar);
        frame.setSize(300, 300);
        return frame;
    }

    static class MenuBuilder extends MouseAdapter {
        public void mouseEntered(MouseEvent ev) {
            menu.remove(1);
            menu.add(new JMenuItem("Fixed!"));

            JPopupMenu pm = menu.getPopupMenu();
            pm.pack();
            pm.paintImmediately(pm.getBounds());
        }
    }
}
