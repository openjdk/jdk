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

import java.awt.Button;
import java.awt.CheckboxMenuItem;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.TextField;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

/*
 * @test
 * @bug 4133279
 * @summary  Clicking in menu in inactive frame crashes application
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MenuCrash
 */
public class MenuCrash {

    private static final String INSTRUCTIONS = """
            Two frames will appear, alternate between frames by clicking on the
            menubar of the currently deactivated frame and verify no crash occurs.

            Try mousing around the menus and choosing various items to see the menu
            item name reflected in the text field. Note that CheckBoxMenuItems do
            not fire action events so the check menu item (Item 03) will not change
            the field.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("MenuCrash Instructions")
                .instructions(INSTRUCTIONS)
                .columns(45)
                .testUI(MenuCrash::createAndShowUI)
                .positionTestUIRightRow()
                .build()
                .awaitAndCheck();
    }


    private static List<Window> createAndShowUI() {
        Frame frame1 = new MenuFrame("Frame 1 MenuCrash");
        Frame frame2 = new MenuFrame("Frame 2 MenuCrash");

        frame1.setSize(300, 200);
        frame2.setSize(300, 200);

        frame1.validate();
        frame2.validate();

        return List.of(frame1, frame2);
    }

    static class MenuFrame extends Frame {
        private final TextField  field;

        MenuFrame(String name) {
            super(name);
            setLayout(new FlowLayout());

            Button removeMenus = new Button("Remove Menus");
            removeMenus.addActionListener(ev -> remove(getMenuBar()));

            Button addMenus = new Button("Add Menus");
            addMenus.addActionListener(ev -> setupMenus());

            add(removeMenus);
            add(addMenus);
            field = new TextField(20);
            add(field);

            addWindowListener(
                    new WindowAdapter() {
                        public void windowActivated(WindowEvent e) {
                            setupMenus();
                        }
                    }
            );

            addComponentListener(
                    new ComponentAdapter() {
                        public void componentResized(ComponentEvent e) {
                            System.out.println(MenuFrame.this);
                        }
                    }
            );

            pack();
        }

        private void addMenuListeners() {
            MenuBar menuBar = getMenuBar();

            for (int nMenu = 0; nMenu < menuBar.getMenuCount(); nMenu++) {
                Menu menu = menuBar.getMenu(nMenu);
                for (int nMenuItem = 0; nMenuItem < menu.getItemCount(); nMenuItem++) {
                    MenuItem item = menu.getItem(nMenuItem);
                    item.addActionListener(ev -> field.setText(ev.getActionCommand()));
                }
            }
        }

        private void setupMenus() {
            MenuItem miSetLabel = new MenuItem("Item 01");
            MenuItem miSetEnabled = new MenuItem("Item 02");
            CheckboxMenuItem miSetState = new CheckboxMenuItem("Item 03");
            MenuItem miAdded = new MenuItem("Item 04 Added");

            MenuBar menuBar = new MenuBar();
            Menu menu1 = new Menu("Menu 01");
            menu1.add(miSetLabel);
            menu1.add(miSetEnabled);
            menu1.add(miSetState);
            menuBar.add(menu1);
            setMenuBar(menuBar);

            // now that the peers are created, screw
            // around with the menu items
            miSetLabel.setLabel("Menu 01 - SetLabel");
            miSetEnabled.setEnabled(false);
            miSetState.setState(true);
            menu1.add(miAdded);
            menu1.remove(miAdded);
            menu1.addSeparator();
            menu1.add(miAdded);

            Menu menu2 = new Menu("Menu 02");
            menuBar.add(menu2);
            menuBar.remove(menu2);
            menuBar.add(menu2);
            menu2.add(new MenuItem("Foo"));
            menu1.setLabel("Menu Number 1");
            menu2.setLabel("Menu Number 2");

            addMenuListeners();
        }
    }
}
