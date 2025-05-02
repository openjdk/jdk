/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Font;
import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;

/*
 * @test
 * @bug 4066657 8009454
 * @requires os.family != "mac"
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Tests that setting a font on the Menu with MenuItem takes effect.
 * @run main/manual MenuSetFontTest
 */

public class MenuSetFontTest {

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                    Look at the menu in the upper left corner of the 'SetFont Test' frame.
                    Click on the "File" menu. You will see "menu item" item.
                    Press Pass if menu item is displayed using bold and large font,
                    otherwise press Fail.
                    If you do not see menu at all, press Fail.""";

        PassFailJFrame.builder()
                .title("MenuSetFontTest")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(40)
                .testUI(MenuSetFontTest::createAndShowUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createAndShowUI() {
        Frame frame = new Frame("SetFont Test");
        MenuBar menuBar = new MenuBar();
        Menu menu = new Menu("File");
        MenuItem item = new MenuItem("menu item");
        menu.add(item);
        menuBar.add(menu);
        menuBar.setFont(new Font(Font.MONOSPACED, Font.BOLD, 24));
        frame.setMenuBar(menuBar);
        frame.setSize(300, 200);
        return frame;
    }
}
