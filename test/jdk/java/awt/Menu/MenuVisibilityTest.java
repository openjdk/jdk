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
 * @bug 5046491 6423258
 * @summary CheckboxMenuItem: menu text is missing from test frame
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MenuVisibilityTest
*/

import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;

public class MenuVisibilityTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. Press on a MenuBar with a long name.
                2. Select "First item" in an opened menu.
                   If you see that "First menu item was pressed" in
                   the test log area, press PASS
                   Otherwise press FAIL"
                 """;
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(MenuVisibilityTest::initialize)
                .logArea()
                .build()
                .awaitAndCheck();
    }

    public static Frame initialize() {
        Frame frame = new Frame("Menu visibility test");
        String menuTitle = "I_have_never_seen_so_long_Menu_Title_" +
                "!_ehe-eha-ehu-ehi_ugu-gu!!!_;)_BANG_BANG...";
        MenuBar menubar = new MenuBar();
        Menu menu = new Menu(menuTitle);
        MenuItem menuItem = new MenuItem("First item");
        menuItem.addActionListener(e ->
                PassFailJFrame.log("First menu item was pressed."));
        menu.add(menuItem);
        menubar.add(menu);
        frame.setMenuBar(menubar);
        frame.setSize(100, 200);
        return frame;
    }
}
