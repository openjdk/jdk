/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @bug 4261935
  @summary Menu display problem when changing the text of the menu(window 98)
  @key headful
  @run main MenuSetLabelTest
*/

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.Robot;

public class MenuSetLabelTest {
    Menu1 f;

    public static void main(String[] args) throws Exception {
        MenuSetLabelTest test = new MenuSetLabelTest();
        test.start();
    }

    public void start() throws Exception {
        try {
            EventQueue.invokeAndWait(() -> {
                f = new Menu1();
                f.setTitle("MenuSetLabelTest");
                f.setSize(300, 200);
                f.setLocationRelativeTo(null);
                f.setVisible(true);
            });
            Robot robot = new Robot();
            robot.delay(1000);
            robot.waitForIdle();
            EventQueue.invokeAndWait(() -> {
                f.changeMenuLabel();
            });
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (f != null) {
                    f.dispose();
                }
            });
        }
    }

}

class Menu1 extends Frame {

    String s1 = new String("short");
    String s2 = new String("This is a long string");
    String s3 = new String("Menu Item string");

    MenuBar mb1 = new MenuBar();
    Menu f = new Menu(s1);
    Menu m = new Menu(s1);
    boolean flag = true;

    public Menu1()
    {
        for (int i = 0; i < 5; i++) {
            m.add(new MenuItem(s3));
        }
        for (int i = 0; i < 10; i++) {
            f.add(new MenuItem(s3));
        }
        mb1.add(f);
        mb1.add(m);
        setMenuBar(mb1);
    }

    public void changeMenuLabel() {
        MenuBar mb = getMenuBar();
        Menu m0 = mb.getMenu(0);
        Menu m1 = mb.getMenu(1);

        if (flag) {
            m0.setLabel(s2);
            m1.setLabel(s2);
        } else {
            m0.setLabel(s1);
            m1.setLabel(s1);
        }
        flag = !flag;
    }
}
