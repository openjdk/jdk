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

/*
 * @test
 * @bug 4275848
 * @summary Tests that MenuBar is painted correctly after its submenu is removed
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MenuBarRemoveMenuTest
 */

import java.awt.Button;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class MenuBarRemoveMenuTest implements ActionListener {
    private static MenuBar menubar;
    private static Button removeButton;
    private static Button addButton;

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                Press "Remove menu" button. If you see that both menus
                disappeared, the test failed. Otherwise try to add and remove
                menu several times to verify that the test passed. Every time
                you press "Remove menu" button only one menu should go away.
                """;

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(MenuBarRemoveMenuTest::createUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createUI() {
        Frame frame = new Frame();
        menubar = new MenuBar();
        removeButton = new Button("Remove menu");
        addButton = new Button("Add menu");
        removeButton.addActionListener(new MenuBarRemoveMenuTest());
        addButton.addActionListener(new MenuBarRemoveMenuTest());
        addButton.setEnabled(false);
        menubar.add(new Menu("menu"));
        menubar.add(new Menu("menu"));
        frame.setMenuBar(menubar);
        frame.setLayout(new GridLayout(1, 2));
        frame.add(removeButton);
        frame.add(addButton);
        frame.pack();
        return frame;
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == removeButton) {
            menubar.remove(0);
            removeButton.setEnabled(false);
            addButton.setEnabled(true);
        } else {
            menubar.add(new Menu("menu"));
            removeButton.setEnabled(true);
            addButton.setEnabled(false);
        }
    }
}
