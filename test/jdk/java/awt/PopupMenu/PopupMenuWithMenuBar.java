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
import java.awt.MenuItem;
import java.awt.PopupMenu;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/*
 * @test
 * @bug 4038140
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Test for functionality of PopupMenuWithMenuBar
 * @run main/manual PopupMenuWithMenuBar
 */

public class PopupMenuWithMenuBar {
    public static void main(String[] args) throws Exception {
        PopupMenuWithMenuBar obj = new PopupMenuWithMenuBar();
        String INSTRUCTIONS = """
                There was a bug that prevented the popup menu from appearing properly
                (if even at all) for a frame window when there is also a menu bar.

                Right click inside the frame window to display the popup window. If
                the popup menu appears normally, then the test is successful and the
                bug has been fixed.""";

        PassFailJFrame.builder()
                .title("PopupMenuWithMenuBar Instruction")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(obj::createUI)
                .build()
                .awaitAndCheck();
    }

    private Frame createUI() {
        Frame f = new Frame("PopupMenuWithMenuBar Test");
        f.setBounds(10, 10, 300, 250);
        MenuBar menuBar = new MenuBar();
        Menu fileMenu = createFileMenu();
        menuBar.add(fileMenu);
        f.setMenuBar(menuBar);
        PopupMenu popupMenu = createPopupMenu();
        f.add(popupMenu);
        f.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                popupMenu.show(f, e.getX(), e.getY());
            }
        });
        return f;
    }

    private Menu createFileMenu() {
        String[] menu1Labels = new String[]
                {"Save As", "Save As", "Quit"};
        MenuItem menuItem;
        Menu returnMenu = new Menu("File");
        for (int menu1Index = 0; menu1Index < menu1Labels.length; menu1Index++) {
            menuItem = new MenuItem(menu1Labels[menu1Index]);
            returnMenu.add(menuItem);
        }
        return returnMenu;
    }

    private PopupMenu createPopupMenu() {
        String[] popupLabels = new String[]
                {"Popup 1", "Popup 2", "Quit"};
        MenuItem menuItem;
        PopupMenu returnMenu = new PopupMenu("Popups");
        for (int popupIndex = 0; popupIndex < popupLabels.length; popupIndex++) {
            menuItem = new MenuItem(popupLabels[popupIndex]);
            returnMenu.add(menuItem);
        }
        return returnMenu;
    }
}
