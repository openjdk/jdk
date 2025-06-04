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
 * @bug 6185057
 * @summary Disabling a frame does not disable the menus on the frame, on
 *      solaris/linux
 * @requires os.family != "mac"
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MenuBarOnDisabledFrame
 */

import java.awt.Button;
import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;

public class MenuBarOnDisabledFrame {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                Check if MenuBar is disabled on 'Disabled frame'
                Press pass if menu bar is disabled, fail otherwise
                """;

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(MenuBarOnDisabledFrame::createUI)
                .build()
                .awaitAndCheck();
    }

    public static Frame createUI() {
        Frame f = new Frame("Disabled frame");
        MenuBar mb = new MenuBar();
        Menu m1 = new Menu("Disabled Menu 1");
        Menu m2 = new Menu("Disabled Menu 2");
        MenuItem m11 = new MenuItem("MenuItem 1.1");
        MenuItem m21 = new MenuItem("MenuItem 2.1");
        Button b = new Button("Disabled button");

        m1.add(m11);
        m2.add(m21);
        mb.add(m1);
        mb.add(m2);
        f.setMenuBar(mb);
        f.add(b);
        f.setEnabled(false);
        f.setSize(300, 300);
        return f;
    }
}
