/*
 * Copyright (c) 2004, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5024051
 * @summary Tests if menu is repainted in enabling/disabling it and
 *          changing its label while it is visible, either on MenuBar
 *          or in other Menu. Menu items are covered as well
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual OnFlyRepaintMenuTest
 */

import java.awt.Button;
import java.awt.CheckboxMenuItem;
import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;

public class OnFlyRepaintMenuTest {
    static boolean menuEnabled = true;

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. Click the button 'Change state' and wait for 5 secs.
                2. If menu is repainted correctly after its setLabel()
                   and setEnabled() methods called test PASSED, else FAILED.
                   (During a 5 secs delay you may select the menu to see
                   the effect for menu items and submenu)
                         """;
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(OnFlyRepaintMenuTest::initialize)
                .build()
                .awaitAndCheck();
    }

    static Frame initialize() {
        Frame f = new Frame("OnFly Menu Repaint Test");

        f.setSize(200, 100);

        MenuBar mb = new MenuBar();
        Menu menu = new Menu("Menu");
        MenuItem menuItem = new MenuItem("MenuItem");
        menu.add(menuItem);
        Menu submenu = new Menu("SubMenu");
        MenuItem submenuItem = new MenuItem("SubmenuItem");
        submenu.add(submenuItem);
        CheckboxMenuItem checkMenuItem = new CheckboxMenuItem("CheckboxmenuItem");
        checkMenuItem.setState(true);
        menu.add(checkMenuItem);
        menu.add(submenu);
        mb.add(menu);
        f.setMenuBar(mb);

        Button b = new Button("Change state");
        b.addActionListener(ev -> new Thread(() -> {
            try {
                Thread.sleep(5000);
            } catch (Exception e) {
            }
            menuEnabled = !menuEnabled;
            String label = menuEnabled ? "Enabled" : "Disabled";
            menu.setLabel(label);
            menuItem.setLabel(label);
            submenu.setLabel(label);
            submenuItem.setLabel(label);
            checkMenuItem.setLabel(label);
            checkMenuItem.setEnabled(menuEnabled);
            checkMenuItem.setState(menuEnabled);
            submenuItem.setEnabled(menuEnabled);
            submenu.setEnabled(menuEnabled);
            menuItem.setEnabled(menuEnabled);
            menu.setEnabled(menuEnabled);
        }).start());
        f.add(b);
        return f;
    }
}
