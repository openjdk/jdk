/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4028130 4112308
 * @summary Test for location of Frame/MenuBar when MenuBar is re-added
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MenuBarAddRemoveTest
 */

import java.awt.Button;
import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;

public class MenuBarAddRemoveTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. Click the left mouse button on the "Re-Add MenuBar"
                button several times.
                3. The Frame/MenuBar may repaint or flash, but the location
                of its upper left corner should never change.
                """;

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(MenuBarAddRemoveTest::createUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createUI() {
        Frame f = new Frame("Re-Add MenuBar Test Frame");
        Button b = new Button("Re-Add MenuBar");
        b.addActionListener(e -> f.setMenuBar(createMenuBar()));
        f.setMenuBar(createMenuBar());
        f.add(b);
        f.pack();
        return f;
    }

    private static MenuBar createMenuBar() {
        MenuBar bar = new MenuBar();
        bar.add(new Menu("foo"));
        return bar;
    }
}
