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
 * @bug 4080225
 * @summary A replaced menu shortcut does not draw in the menu.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MenuItemShortcutReplaceTest
 */

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.MenuShortcut;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

/*
 * Manual test because visual verification of the shortcut being painted is required.
 */

public class MenuItemShortcutReplaceTest implements ActionListener {

    static boolean isMac = System.getProperty("os.name").startsWith("Mac");
    static String shortcut = (isMac) ? "Cmd" : "Ctrl";
    static String instructions =
         "1. On the frame 'MenuItem Shortcut Replace Test' click on the Menu 'Click here'.\n" +
         "   You will see a MenuItem 'MenuItem1' with the shortcut key displayed as" +
         " '" + shortcut + "+M'.\n" +
         "2. Click the 'Change Shortcutkey' button.\n" +
         "3. Now click on the Menu again to see the MenuItem.\n" +
         "4. If the shortcut key displayed near the MenuItem is changed to " +
         "'" + shortcut + "+C', press 'Pass' else press 'Fail'";

    public static void main(String[] args) throws Exception {
       PassFailJFrame.builder()
                .title("MenuItem Shortcut Replace Test Instructions")
                .instructions(instructions)
                .columns(60)
                .logArea()
                .testUI(MenuItemShortcutReplaceTest::createUI)
                .build()
                .awaitAndCheck();

    }

    static volatile Button change;
    static volatile MenuItem mi;
    static volatile MenuShortcut ms;

    static Frame createUI() {
        Frame frame = new Frame("MenuItem Shortcut Replace Test");
        MenuBar mb = new MenuBar();
        change = new Button("Change ShortcutKey");
        Panel p = new Panel();
        p.add(change);
        MenuItemShortcutReplaceTest misrt = new MenuItemShortcutReplaceTest();
        change.addActionListener(misrt);
        Menu m = new Menu("Click here");
        mb.add(m);
        mi = new MenuItem("MenuItem1");
        m.add(mi);
        mi.addActionListener(misrt);
        frame.setMenuBar(mb);
        //Set the shortcut key for the menuitem
        ms = new MenuShortcut(KeyEvent.VK_M);
        mi.setShortcut(ms);
        frame.add(p, BorderLayout.SOUTH);
        frame.setSize(300, 300);
        return frame;
    }

    public void actionPerformed(ActionEvent e) {
        //change the shortcut key
        if (e.getSource() == change) {
            ms = new MenuShortcut(KeyEvent.VK_C);
            mi.setShortcut(ms);
            PassFailJFrame.log("Shortcut key set to "+shortcut+"C");
        }
        if (e.getSource() == mi) {
            PassFailJFrame.log("MenuItem Selected");
        }
    }
}
