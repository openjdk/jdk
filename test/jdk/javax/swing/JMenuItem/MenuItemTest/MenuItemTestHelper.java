/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import javax.swing.UIManager;

class MenuItemTestHelper {

    public static JFrame getMenuItemTestFrame(boolean isLeft, String lafName, int frameY) {
        boolean applyLookAndFeel = lafName != null;

        if (applyLookAndFeel) {
            try {
                UIManager.setLookAndFeel(lafName);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Icon myIcon = new Icon() {
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Color color = g.getColor();
                g.setColor(Color.RED);
                g.fillRect(x, y, 10, 10);
                g.setColor(color);
            }

            public int getIconWidth() { return 10; }
            public int getIconHeight() { return 10; }
        };

        Icon myIcon2 = new Icon() {
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Color color = g.getColor();
                g.setColor(Color.GREEN);
                g.fillRect(x, y, 15, 10);
                g.setColor(color);
            }

            public int getIconWidth() { return 15; }
            public int getIconHeight() { return 10; }
        };

        JMenuBar menuBar = new JMenuBar();
        menuBar.add(createViewMenu(myIcon, myIcon2));
        menuBar.add(createNoNothingMenu());
        menuBar.add(createSomeIconsMenu(myIcon, myIcon2));

        String title = "Menu Item Test " + (isLeft ? "(Left-to-right)" : "(Right-to-left)");
        JFrame frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setJMenuBar(menuBar);
        frame.applyComponentOrientation(isLeft ? ComponentOrientation.LEFT_TO_RIGHT : ComponentOrientation.RIGHT_TO_LEFT);

        if (applyLookAndFeel) {
            String shortName = lafName.substring(lafName.lastIndexOf('.') + 1);
            JLabel label = new JLabel("<HTML><H2>" + shortName + "</H2></HTML>");
            frame.setLayout(new BorderLayout());
            frame.add(label, BorderLayout.CENTER);
        }

        frame.setSize(300, 300);
        frame.setLocation(isLeft ? 0 : 600, frameY);
        frame.setVisible(true);
        return frame;
    }

    public static JFrame getMenuItemTestFrame(boolean isLeft) {
        return getMenuItemTestFrame(isLeft, null, 20);
    }

    private static JMenu createViewMenu(Icon myIcon, Icon myIcon2) {
        JMenu menu = new JMenu("View");
        menu.setMnemonic('V');

        menu.add(new JMenuItem("Refresh"));
        menu.add(new JMenuItem("Customize..."));
        menu.add(new JCheckBoxMenuItem("Show Toolbar"));
        menu.addSeparator();
        menu.add(new JRadioButtonMenuItem("List"));
        menu.add(new JRadioButtonMenuItem("Icons"));

        JRadioButtonMenuItem rm2 = new JRadioButtonMenuItem("And icon.");
        rm2.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1, KeyEvent.SHIFT_MASK));
        rm2.setIcon(myIcon2);
        menu.add(rm2);

        JRadioButtonMenuItem mi3 = new JRadioButtonMenuItem("Radio w/icon");
        mi3.setIcon(myIcon);
        menu.add(mi3);

        menu.add(new JMenuItem(myIcon2));

        JMenuItem mi4 = new JMenuItem("Item with icon");
        mi4.setIcon(myIcon);
        menu.addSeparator();
        menu.add(mi4);

        return menu;
    }

    private static JMenu createNoNothingMenu() {
        final JMenu menu2 = new JMenu("No nothing");

        for (String label : new String[]{"One", "Two", "Threeee"}) {
            JMenuItem item = new JMenuItem(label);
            item.addActionListener(new AbstractAction() {
                public void actionPerformed(ActionEvent e) {
                    int width = menu2.getPopupMenu().getWidth();
                    PassFailJFrame.log("menu.width = " + width);
                }
            });
            menu2.add(item);
        }

        return menu2;
    }

    private static JMenu createSomeIconsMenu(Icon myIcon, Icon myIcon2) {
        JMenu someIcons = new JMenu("Some icons");

        JMenuItem imi1 = new JMenuItem("Icon!");
        imi1.setIcon(myIcon);
        someIcons.add(imi1);

        JMenuItem imi2 = new JMenuItem("Wide icon!");
        imi2.setIcon(myIcon2);
        someIcons.add(imi2);

        someIcons.add(new JCheckBoxMenuItem("CheckBox"));
        someIcons.add(new JRadioButtonMenuItem("RadioButton"));

        return someIcons;
    }
}
