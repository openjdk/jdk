/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;

/*
 * @test
 * @bug 4099695
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary menu items with Unicode labels treated as separators
 * @run main/manual UnicodeMenuItemTest
 */

public class UnicodeMenuItemTest {

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                Click on the "Menu" on the top-left corner of frame.

                The menu should have four entries:
                1) a row of five unicode characters: \u00c4\u00cb\u00cf\u00d6\u00dc
                2) a menu separator
                3) a unicode character:  \u012d
                4) a unicode character:  \u022d

                If the menu items look like the list above, the test passes.
                It is okay if the unicode characters look like empty boxes
                or something - as long as they are not separators.

                If either of the last two menu items show up as separators,
                the test FAILS.

                Press 'Pass' if above instructions hold good else press 'Fail'.""";

        PassFailJFrame.builder()
                .title("UnicodeMenuItemTest")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(40)
                .testUI(UnicodeMenuItemTest::createAndShowUI)
                .build()
                .awaitAndCheck();
    }
    private static Frame createAndShowUI() {
        Frame frame = new Frame("Unicode MenuItem Test");
        MenuBar mb = new MenuBar();
        Menu m = new Menu("Menu");

        MenuItem mi1 = new MenuItem("\u00c4\u00cb\u00cf\u00d6\u00dc");
        m.add(mi1);

        MenuItem separator = new MenuItem("-");
        m.add(separator);

        MenuItem mi2 = new MenuItem("\u012d");
        m.add(mi2);

        MenuItem mi3 = new MenuItem("\u022d");
        m.add(mi3);

        mb.add(m);

        frame.setMenuBar(mb);
        frame.setSize(450, 150);
        return frame;
    }
}
