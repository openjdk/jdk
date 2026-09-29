/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4094620
 * @summary MenuItem.enableEvents does not work
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MenuActionEventTest
 */

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.event.ActionEvent;

public class MenuActionEventTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. Click on the Menu and then on Menuitem on the frame.
                2. If you find the following message being printed in
                   the test log area:,
                   _MenuItem: action event",
                   click PASS, else click FAIL"
                 """;
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(MenuActionEventTest::initialize)
                .logArea()
                .build()
                .awaitAndCheck();
    }

    static Frame initialize() {
        Frame f = new Frame("Menu Action Event Test");
        f.setLayout(new BorderLayout());
        f.setMenuBar(new MenuBar());
        Menu m = new _Menu("Menu");
        MenuBar mb = f.getMenuBar();
        mb.add(m);
        MenuItem mi = new _MenuItem("Menuitem");
        m.add(mi);
        f.setBounds(204, 152, 396, 300);
        return f;
    }

    static class _Menu extends Menu {
        public _Menu(String text) {
            super(text);
            enableEvents(AWTEvent.ACTION_EVENT_MASK);
        }

        @Override
        protected void processActionEvent(ActionEvent e) {
            PassFailJFrame.log("_Menu: action event");
            super.processActionEvent(e);
        }
    }

    static class _MenuItem extends MenuItem {
        public _MenuItem(String text) {
            super(text);
            enableEvents(AWTEvent.ACTION_EVENT_MASK);
        }

        @Override
        protected void processActionEvent(ActionEvent e) {
            PassFailJFrame.log("_MenuItem: action event");
            super.processActionEvent(e);
        }
    }

}
