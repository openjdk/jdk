/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6180416
 * @summary Tests MenuBar and drop down menu visuals
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MenuBarVisuals
 */

import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.MenuShortcut;
import java.awt.event.KeyEvent;

public class MenuBarVisuals {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                Look at the MenuBar and traverse the menus using mouse and
                keyboard. Then check if following is showing correctly:
                1. Mnemonic label Ctrl+A is NOT drawn for Menu 1/Submenu 1.1
                2. Mnemonic label Ctrl+B is drawn for
                    Menu 1/Submenu 1.1/Item 1.1.1
                3. Mnemonic label Ctrl+C is drawn for Menu1/Item 1.2
                Press PASS if Menu is drawing correctly, FAIL otherwise.
                """;

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(MenuBarVisuals::createUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createUI() {
        Frame f = new Frame("MenuBar Visuals Test");
        MenuBar mb = new MenuBar();
        Menu menu1 = new Menu("Menu 1");
        Menu submenu11 = new Menu("Submenu 1.1");
        MenuItem item111 = new MenuItem("Item 1.1.1");
        MenuItem item112 = new MenuItem("Item 1.1.2");
        MenuItem item12 = new MenuItem("Item 1.2");
        Menu menu2 = new Menu("Menu 2");
        MenuItem item21 = new MenuItem("Item 2.1");
        MenuItem item22 = new MenuItem("Item 2.2");
        item111.setShortcut(new MenuShortcut(KeyEvent.VK_B, false));
        submenu11.add(item111);
        submenu11.add(item112);
        submenu11.setShortcut(new MenuShortcut(KeyEvent.VK_A, false));
        menu1.add(submenu11);
        item12.setShortcut(new MenuShortcut(KeyEvent.VK_C, false));
        menu1.add(item12);
        mb.add(menu1);
        menu2.add(item21);
        menu2.add(item22);
        mb.add(menu2);
        f.setMenuBar(mb);
        f.setSize(300, 300);
        return f;
    }
}
