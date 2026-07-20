/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4410477
 * @summary Tests that menu does not crash during simultaneous drawing
 *          and removal of items.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MenuAddRemoveCrash
 */

import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;

public class MenuAddRemoveCrash {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. Move and resize the frame.
                2. If the test crashes the test is FAILED.
                   Otherwise it is PASSED.
                    """;
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(MenuAddRemoveCrash::initialize)
                .build()
                .awaitAndCheck();
    }

    public static Frame initialize() {
        final TestGui myTestGui = new TestGui();
        Thread test = new Thread() {
            public void run() {
                while (!Thread.interrupted()) {
                    myTestGui.changeMenuItems();
                }
            }
        };
        test.setDaemon(true);
        test.start();
        return myTestGui;
    }
}

class TestGui extends Frame {
    Menu myMenu1;
    Menu myMenu2;

    public TestGui() {
        this.setTitle("Try to resize this frame!");

        this.setSize(300, 300);
        this.setVisible(true);

        MenuBar myMenuBar = new MenuBar();
        myMenu1 = new Menu("DemoMenu1");
        myMenu2 = new Menu("DemoMenu2");

        myMenuBar.add(myMenu1);
        myMenuBar.add(myMenu2);

        this.setMenuBar(myMenuBar);
    }

    public void changeMenuItems() {
        myMenu1.removeAll();

        for (int i = 0; i < 10; i++) {
            MenuItem myMenuItem1 = new MenuItem("DemoMenuItem" + i);
            myMenu1.add(myMenuItem1);
        }
        try {
            Thread.sleep(100);
        } catch (Exception e) {
            throw new RuntimeException("Failed :" + e);
        }
    }
}
