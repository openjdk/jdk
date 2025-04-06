/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4700350
 * @requires os.family != "mac"
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Tests menu item font is big
 * @run main/manual GiantFontTest
 */

public class GiantFontTest {

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                        A frame with one menu will appear.
                        On Linux, the menu's (present on menu bar) font should
                        be quite large (48 point).
                        If not, test fails.

                        On Windows, the menu's (present on menu bar) font
                        should be normal size.
                        If the menu text is clipped by the title bar, or is painted over
                        the title bar or client area, the test fails.

                        On both Windows and Linux, the menu items in the popup
                        menu should be large.

                        If so, test passes.""";

        PassFailJFrame.builder()
                .title("GiantFontTest")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(40)
                .testUI(GiantFontTest::createAndShowUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createAndShowUI() {
        Font giantFont = new Font("Dialog", Font.PLAIN, 48);
        Frame f = new Frame("GiantFontTest");
        MenuBar mb = new MenuBar();
        Menu m = new Menu("My font is too big!");
        m.setFont(giantFont);
        for (int i = 0; i < 5; i++) {
            m.add(new MenuItem("Some MenuItems"));
        }
        mb.add(m);
        f.setMenuBar(mb);
        f.setSize(450, 400);
        return f;
    }
}
