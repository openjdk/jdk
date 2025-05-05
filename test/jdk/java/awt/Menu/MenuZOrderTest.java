/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6267182
 * @summary Menu is not visible after showing and disposing a file dialog.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MenuZOrderTest
 */

import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class MenuZOrderTest {
    static class Listener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            Frame f = new Frame("Menu Z order test frame");
            f.setBounds(200, 200, 200, 200);
            f.setVisible(true);
        }
    }

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. Choose Menu 1 --> Menu Item 1 several times.
                2. If menu window is shown correctly and each click
                   creates new frame - press PASS.
                3. If menu window is obscured by frame - press FAIL.
                    """;
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(MenuZOrderTest::initialize)
                .build()
                .awaitAndCheck();
    }

    static Frame initialize() {
        Frame mf = new Frame("Menu Z order test");
        Listener l = new Listener();
        MenuBar mb = new MenuBar();
        Menu m1 = new Menu("Menu 1");
        MenuItem mi1 = new MenuItem("Menu Item 1");

        mf.setSize(200, 200);
        mi1.addActionListener(l);
        m1.add(mi1);
        mb.add(m1);
        mf.setMenuBar(mb);
        mf.setVisible(true);
        return mf;
    }
}
