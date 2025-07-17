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
 * @test id=metal
 * @bug 4211052
 * @requires (os.family == "windows")
 * @summary Verifies if menu items lay out correctly when their
 *     ComponentOrientation property is set to RIGHT_TO_LEFT.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual RightLeftOrientation metal
 */

/*
 * @test id=motif
 * @bug 4211052
 * @requires (os.family == "windows")
 * @summary Verifies if menu items lay out correctly when their
 *     ComponentOrientation property is set to RIGHT_TO_LEFT.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual RightLeftOrientation motif
 */

/*
 * @test id=windows
 * @bug 4211052
 * @requires (os.family == "windows")
 * @summary Verifies if menu items lay out correctly when their
 *     ComponentOrientation property is set to RIGHT_TO_LEFT.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual RightLeftOrientation windows
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
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class RightLeftOrientation {

    private static final String INSTRUCTIONS = """
        A menu bar is shown with a menu.

        The menu is divided into two halves. The upper half is oriented
        left-to-right and the lower half is oriented right-to-left.
        Ensure that the lower half mirrors the upper half.

        Note that when checking the positioning of the sub-menus, it
        helps to position the frame away from the screen edges.""";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Look-and-Feel keyword is required");
        }

        final String lafClassName;
        switch (args[0]) {
            case "metal" -> lafClassName = UIManager.getCrossPlatformLookAndFeelClassName();
            case "motif" -> lafClassName = "com.sun.java.swing.plaf.motif.MotifLookAndFeel";
            case "windows" -> lafClassName = "com.sun.java.swing.plaf.windows.WindowsLookAndFeel";
            default -> throw new IllegalArgumentException(
                           "Unsupported Look-and-Feel keyword for this test: " + args[0]);
        }

        SwingUtilities.invokeAndWait(() -> {
            try {
                UIManager.setLookAndFeel(lafClassName);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        System.out.println("Test for LookAndFeel " + lafClassName);

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(RightLeftOrientation::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {
        JFrame frame = new JFrame("RightLeftOrientation");
        JMenuBar menuBar = new JMenuBar();

        menuBar.add(createMenu());

        frame.setJMenuBar(menuBar);
        frame.setSize(250, 70);
        return frame;
    }


    static JMenu createMenu() {
        JMenu menu = new JMenu(UIManager.getLookAndFeel().getID());
        addMenuItems(menu, ComponentOrientation.LEFT_TO_RIGHT);
        menu.addSeparator();
        addMenuItems(menu, ComponentOrientation.RIGHT_TO_LEFT);
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
