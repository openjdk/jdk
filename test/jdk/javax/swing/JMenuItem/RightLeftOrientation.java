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
 * @bug 4211052
 * @requires (os.family == "windows")
 * @summary
 *     This test checks if menu items lay out correctly when their
 *     ComponentOrientation property is set to RIGHT_TO_LEFT.
 *     The tester is asked to compare left-to-right and
 *     right-to-left menus and judge whether they are mirror images of each
 *     other.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual RightLeftOrientation
 */

import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.Icon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import javax.swing.LookAndFeel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

public class RightLeftOrientation {

    private static final String INSTRUCTIONS = """
        A menu bar is shown containing a menu for each look and feel.
        A disabled menu means that the look and feel is not available for
        testing in this environment.
        Every effort should be made to run this test
        in an environment that covers all look and feels.

        Each menu is divided into two halves. The upper half is oriented
        left-to-right and the lower half is oriented right-to-left.
        For each menu, ensure that the lower half mirrors the upper half.

        Note that when checking the positioning of the sub-menus, it
        helps to position the frame away from the screen edges.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("RightLeftOrientation Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(RightLeftOrientation::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {
        JFrame frame = new JFrame("RightLeftOrientation");
        JMenuBar menuBar = new JMenuBar();

        menuBar.add(createMenu("javax.swing.plaf.metal.MetalLookAndFeel",
                                "Metal"));
        menuBar.add(createMenu("com.sun.java.swing.plaf.motif.MotifLookAndFeel",
                                "Motif"));
        menuBar.add(createMenu("com.sun.java.swing.plaf.windows.WindowsLookAndFeel",
                                "Windows"));

        frame.setJMenuBar(menuBar);
        frame.pack();
        return frame;
    }


    static JMenu createMenu(String laf, String name) {
        JMenu menu = new JMenu(name);
        try {
            LookAndFeel save = UIManager.getLookAndFeel();
            UIManager.setLookAndFeel(laf);
            addMenuItems(menu, ComponentOrientation.LEFT_TO_RIGHT);
            menu.addSeparator();
            addMenuItems(menu, ComponentOrientation.RIGHT_TO_LEFT);
            UIManager.setLookAndFeel(save);
        } catch (Exception e) {
            menu = new JMenu(name);
            menu.setEnabled(false);
        }
        return menu;
    }

    static void addMenuItems(JMenu menu, ComponentOrientation o) {

        JMenuItem menuItem;

        menuItem = new JMenuItem("Menu Item");
        menuItem.setComponentOrientation(o);
        menu.add(menuItem);

        menuItem = new JMenuItem("Text trails icon", new MyMenuItemIcon());
        menuItem.setComponentOrientation(o);
        menu.add(menuItem);

        menuItem = new JMenuItem("Text leads icon", new MyMenuItemIcon());
        menuItem.setComponentOrientation(o);
        menuItem.setHorizontalTextPosition(SwingConstants.LEADING);
        menu.add(menuItem);

        menuItem = new JRadioButtonMenuItem("Radio Button Menu Item");
        menuItem.setComponentOrientation(o);
        menuItem.setSelected(true);
        menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_1, ActionEvent.ALT_MASK));
        menu.add(menuItem);

        menuItem = new JCheckBoxMenuItem("Check box Menu Item");
        menuItem.setComponentOrientation(o);
        menuItem.setSelected(true);
        menuItem.setFont( new Font("Serif",Font.PLAIN, 24) );
        menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_2, ActionEvent.ALT_MASK));
        menu.add(menuItem);

        menuItem = new JMenu("Sub Menu");
        menuItem.setComponentOrientation(o);
        menuItem.add(new JMenuItem("Sub Menu One")).setComponentOrientation(o);
        menuItem.add(new JMenuItem("Sub Menu Two")).setComponentOrientation(o);
        menu.add(menuItem);
    }


    private static class MyMenuItemIcon implements Icon {
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Color oldColor = g.getColor();
            g.setColor(Color.orange);
            g.fill3DRect(x, y, getIconWidth(), getIconHeight(), true);
            g.setColor(oldColor);
        }
        public int getIconWidth() { return 15; }
        public int getIconHeight() { return 15; }
    }
}
