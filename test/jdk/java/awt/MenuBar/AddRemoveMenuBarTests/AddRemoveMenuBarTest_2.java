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

import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/*
 * @test
 * @bug 4028130
 * @key headful
 * @summary Test dynamically adding and removing a menu bar
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual AddRemoveMenuBarTest_2
 */

public class AddRemoveMenuBarTest_2 {
    private static final String INSTRUCTIONS = """
            A frame with a menu bar appears.

            Click anywhere in the frame to replace the menu bar with
            another one.

            Each menu bar has one (empty) menu, 'foo'.

            After the menu bar replacement, the containing frame
            should not be resized nor repositioned on the screen.

            Upon test completion, click Pass or Fail appropriately.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("AddRemoveMenuBarTest_2 Instructions")
                .instructions(INSTRUCTIONS)
                .testTimeOut(5)
                .rows(15)
                .columns(45)
                .testUI(AddRemoveMenuBar_2::new)
                .build()
                .awaitAndCheck();
    }
}

class AddRemoveMenuBar_2 extends Frame {
    AddRemoveMenuBar_2() {
        super("AddRemoveMenuBar_2");
        setSize(200, 200);
        setMenuBar();
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                setMenuBar();
            }
        });
    }

    int count = 0;

    void setMenuBar() {
        MenuBar bar = new MenuBar();
        bar.add(new Menu("foo " + count++));
        super.setMenuBar(bar);
    }
}
