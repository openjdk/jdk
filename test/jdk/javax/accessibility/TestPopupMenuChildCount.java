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

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/*
 * @test
 * @bug 8341311
 * @summary Verifies that VoiceOver announces correct number of child for PopupMenu on macOS
 * @requires os.family == "mac"
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TestPopupMenuChildCount
 */

public class TestPopupMenuChildCount {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                This test is applicable only on macOS.

                Test UI contains an empty JFrame. On press of left/right mouse button,
                a PopupMenu will be visible.

                Follow these steps to test the behaviour:

                1. Start the VoiceOver (Press Command + F5) application
                2. Press Left/Right mouse button inside test frame window to open
                   the PopupMenu
                3. VO should announce "Menu" with number of child items of the Popupmenu
                4. Press Up/Down arrow to traverse popupmenu child items
                5. Press Right arrow key to open submenu
                6. VO should announce "Menu" with correct number of child items
                   for the submenu (For e.g. When Submenu-1 is open, VO should announce
                   "Menu 4 items")
                7. Repeat the process for other submenus
                8. Press Pass if you are able to hear correct announcements
                   else Fail""";

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(45)
                .testUI(TestPopupMenuChildCount::createUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createUI() {
        JFrame frame = new JFrame("Test Frame");

        JPopupMenu popupmenu = new JPopupMenu();
        JMenuItem mi1 = new JMenuItem("MenuItem-1");
        JMenuItem mi2 = new JMenuItem("MenuItem-2");
        JMenuItem mi3 = new JMenuItem("MenuItem-3");
        popupmenu.add(mi1);
        popupmenu.add(mi2);
        popupmenu.add(mi3);

        JMenu submenu1 = new JMenu("Submenu-1");
        submenu1.add("subOne");
        submenu1.add("subTwo");
        submenu1.add("subThree");

        JMenu submenu2 = new JMenu("Submenu-2");
        submenu2.add("subOne");
        submenu2.add("subTwo");

        JMenu submenu3 = new JMenu ("Submenu-3");
        submenu3.add("subOne");
        submenu1.add(submenu3);

        popupmenu.add(submenu1);
        popupmenu.add(submenu2);

        frame.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                popupmenu.show(e.getComponent(), e.getX(), e.getY());
            }
        });
        frame.setSize(300, 300);
        return frame;
    }
}
