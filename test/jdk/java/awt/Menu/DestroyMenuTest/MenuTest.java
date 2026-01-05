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

import java.awt.Canvas;
import java.awt.CardLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Label;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.Panel;

public class MenuTest extends Frame {
    private MenuItem quitItem;
    private final Panel cards;
    private final CardLayout layout;

    public MenuTest(String s) {
        super(s);
        MenuBar mbar = new MenuBar();
        createMenus(mbar);
        setMenuBar(mbar);

        cards = new Panel();
        layout = new CardLayout();
        cards.setLayout(layout);

        cards.add(new MyPanelOne("Options"), "Options");
        cards.add(new MyRectCanvas(), "MyRectCanvas");
        cards.add(new MycircleCanvas(), "MyCircleCanvas");

        add(cards, "Center");
    }

    public void createMenus(MenuBar mbar) {
        mbar.add(createFileMenu());
        mbar.add(createEditMenu());
        mbar.add(createOptionMenu1());
        mbar.add(createOptionMenu2());
        mbar.add(createOptionMenu3());
        mbar.add(createOptionMenu4());
    }

    private Menu createFileMenu() {
        Menu fileMenu = new Menu("File");
        fileMenu.add(quitItem = new MenuItem("Quit"));

        quitItem.addActionListener(event -> {
            MenuItem item = (MenuItem) event.getSource();
            if (item == quitItem) {
                dispose();
            }
        });
        return fileMenu;
    }

    private Menu createEditMenu() {
        Menu editMenu = new Menu("Edit");

        editMenu.add("Cut");
        editMenu.add("Copy");
        editMenu.add("Paste");
        editMenu.addSeparator();
        editMenu.add("Select all");
        editMenu.addSeparator();
        editMenu.add("Find");
        editMenu.add("Find again");

        return editMenu;
    }

    private Menu createOptionMenu1() {
        Menu optionMenu1 = new Menu("Option1");
        MenuItem item1, item2, item3;
        optionMenu1.add(item1 = new MenuItem("Item1"));
        optionMenu1.add(item2 = new MenuItem("Item2"));
        optionMenu1.add(item3 = new MenuItem("Item3"));

        item1.addActionListener(event -> {
            MenuItem mItem = (MenuItem) event.getSource();
            if (mItem == item1) {
                layout.show(cards, "Options");
            }
        });
        item2.addActionListener(event -> {
            MenuItem mItem = (MenuItem) event.getSource();
            if (mItem == item2) {
                layout.show(cards, "MyRectCanvas");
            }
        });
        item3.addActionListener(event -> {
            MenuItem mItem = (MenuItem) event.getSource();
            if (mItem == item3) {
                layout.show(cards, "MyCircleCanvas");
            }
        });
        return optionMenu1;
    }

    private Menu createOptionMenu2() {
        Menu optionMenu2 = new Menu("Option2");

        optionMenu2.add("Item1");
        optionMenu2.add("Item2");

        return optionMenu2;
    }

    private Menu createOptionMenu3() {
        Menu optionMenu3 = new Menu("Option3");

        optionMenu3.add("Item1");
        optionMenu3.add("Item2");
        optionMenu3.add("Item3");
        optionMenu3.add("Item4");

        return optionMenu3;
    }

    private Menu createOptionMenu4() {
        Menu optionMenu4 = new Menu("Option3");

        optionMenu4.add("Item1");
        optionMenu4.add("Item2");
        optionMenu4.add("Item3");

        return optionMenu4;
    }
}

class MyRectCanvas extends Canvas {
    @Override
    public void paint(Graphics g) {
        g.drawRect(0, 0, 100, 100);
    }
}

class MyPanelOne extends Panel {
    MyPanelOne(String name) {
        add(new Label(name + " panel goes here"));
    }
}

class MycircleCanvas extends Canvas {
    @Override
    public void paint(Graphics g) {
        g.drawOval(0, 0, 100, 100);
        g.drawOval(2, 2, 100, 100);
        g.drawOval(4, 4, 100, 100);
    }
}
