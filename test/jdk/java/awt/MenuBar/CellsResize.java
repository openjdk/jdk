/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6502052
 * @summary Menu cells must resize if font changes (XToolkit)
 * @requires os.family == "linux"
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual CellsResize
 */

import java.awt.Button;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuComponent;
import java.awt.MenuItem;
import java.awt.Panel;
import java.awt.PopupMenu;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class CellsResize {
    private static Frame frame;
    private static MenuBar menuBar;
    private static PopupMenu popupMenu;
    private static Menu barSubMenu;
    private static Menu popupSubMenu;
    private static boolean fontMultiplied = false;

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. Open all nested menus in menu bar.
                2. Click on "popup-menu" button to show popup-menus.
                3. Open all nested menus in popup-menu.
                4. Click on "big-font" button (to make all menus have a
                    bigger font).
                5. Open all nested menus again (as described in 1, 2, 3).
                6. If all menu items use a bigger font now and their labels fit
                into menu-item size, press "pass", otherwise press "fail".
                """;

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(CellsResize::createUI)
                .logArea(5)
                .build()
                .awaitAndCheck();
    }

    public static Frame createUI () {
        if (!checkToolkit()) {
            new RuntimeException("Toolkit check failed.");
        }
        frame = new Frame("MenuBar Cell Resize Test");

        popupMenu = new PopupMenu();
        popupMenu.add(createMenu(false));

        frame.add(popupMenu);

        menuBar = new MenuBar();
        menuBar.add(createMenu(true));

        frame.setMenuBar(menuBar);

        Button bp = new Button("popup-menu");
        bp.addMouseListener(new MouseAdapter() {
            public void mouseReleased(MouseEvent e) {
                popupMenu.show(e.getComponent(), e.getX(), e.getY());
            }
        });

        Button bf = new Button("big-font");
        bf.addMouseListener(new MouseAdapter() {
            public void mouseReleased(MouseEvent e) {
                bigFont();
            }
        });

        Panel panel = new Panel();
        panel.setLayout(new GridLayout(2, 1));
        panel.add(bp);
        panel.add(bf);

        frame.add(panel);
        frame.setSize(300, 300);
        return frame;
    }

    static boolean checkToolkit() {
        String toolkitName = Toolkit.getDefaultToolkit().getClass().getName();
        return toolkitName.equals("sun.awt.X11.XToolkit");
    }

    static Menu createMenu(boolean bar) {
        Menu menu1 = new Menu("Menu-1");
        Menu menu11 = new Menu("Menu-11");
        menu1.add(menu11);
        if (bar) {
            barSubMenu = menu11;
        } else {
            popupSubMenu = menu11;
        }
        menu11.add(new MenuItem("MenuItem"));
        return menu1;
    }

    static void bigFont() {
        if (fontMultiplied) {
            return;
        } else {
            fontMultiplied = true;
        }

        multiplyFont(barSubMenu, 7);
        multiplyFont(popupSubMenu, 7);

        // NOTE: if previous two are moved below following
        // two, they get their font multiplied twice.

        multiplyFont(menuBar, 5);
        multiplyFont(popupMenu, 5);
    }

    static void multiplyFont(MenuComponent comp, int times) {
        Font font = comp.getFont();
        float size = font.getSize() * times;
        comp.setFont(font.deriveFont(size));
    }
}
