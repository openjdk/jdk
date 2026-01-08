/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4203208
 * @summary setShortcut method does not display proper text on Menu component
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual SetShortCutTest
 */

import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.MenuShortcut;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

import static java.awt.event.KeyEvent.VK_META;
import static java.awt.event.KeyEvent.VK_SHIFT;

public class SetShortCutTest {
    public static void main(String[] args) throws Exception {
        boolean isMac = System.getProperty("os.name").startsWith("Mac");
        String shortcut = "Ctrl+Shift+";
        if (isMac) {
            shortcut = KeyEvent.getKeyText(VK_SHIFT) + "+" + KeyEvent.getKeyText(VK_META);
        }

        String INSTRUCTIONS = """
                1. Select menuitem 'Stuff -> Second' once to remove 'File -> First'.
                2. Select menuitem 'Stuff -> Second' again to add 'File -> First'.
                3. If menuitem 'File -> First' reads First """ + shortcut + """
                       'C', press PASS. Otherwise press FAIL.
                """;
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(SetShortCutTest::initialize)
                .build()
                .awaitAndCheck();
    }

    static Frame initialize() {
        return new TestMenuShortCut();
    }

    static class TestMenuShortCut extends Frame implements ActionListener {
        Menu menu1;
        MenuItem item1;
        MenuItem item2;
        boolean beenHere;

        public TestMenuShortCut() {
            setTitle("Set ShortCut test");
            beenHere = false;
            MenuBar mTopMenu = buildMenu();
            setSize(300, 300);
            this.setMenuBar(mTopMenu);
        }

        public MenuBar buildMenu() {
            MenuBar bar;
            bar = new MenuBar();
            menu1 = new Menu("File");
            item1 = new MenuItem("First");
            menu1.add(item1);
            item1.setShortcut(new MenuShortcut(KeyEvent.VK_C, true));
            bar.add(menu1);

            // Stuff menu
            item2 = new MenuItem("Second");
            Menu menu2 = new Menu("Stuff");
            menu2.add(item2);
            item2.setShortcut(new MenuShortcut(KeyEvent.VK_C, false));
            bar.add(menu2);

            item1.addActionListener(this);
            item2.addActionListener(this);
            return bar;
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            if (event.getSource() == item1) {
                Frame temp = new Frame("Accelerator key is working for 'First'");
                temp.setSize(300, 50);
                temp.setVisible(true);
            }

            // Click on the "Stuff" menu to remove the "first" menu item
            else if (event.getSource() == item2) {
                // If the item has not been removed from the menu,
                // then remove "First" from the "File" menu
                if (beenHere == false) {
                    item1.removeActionListener(this);
                    menu1.remove(item1);
                    beenHere = true;
                } else {
                    item1 = new MenuItem("First");
                    menu1.add(item1);
                    item1.addActionListener(this);
                    item1.setShortcut(new MenuShortcut(KeyEvent.VK_C, true));
                    beenHere = false;
                }
            }
        }
    }
}
