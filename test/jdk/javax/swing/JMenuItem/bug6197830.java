/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6197830
 * @requires (os.family == "linux")
 * @summary Fix for 4729669 does not work on Motif and GTK look and feels
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug6197830
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;
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

public class bug6197830 {

    private static final String INSTRUCTIONS = """
        Four windows should appear: Left-to-right and Right-to-left for
        the two different Look and Feels (Motif and GTK).
        Check that text on all the menu items of all menus is properly
        vertically aligned.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("bug6197830 Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(bug6197830::createTestUI)
                .position(PassFailJFrame.Position.TOP_LEFT_CORNER)
                .build()
                .awaitAndCheck();
    }

    private static List<JFrame> createTestUI() {
        JFrame frame1 = getMenuItemTestFrame(true,
                            "com.sun.java.swing.plaf.motif.MotifLookAndFeel",
                             20);
        frame1.setLocation(300, 300);
        JFrame frame2 = getMenuItemTestFrame(false,
                            "com.sun.java.swing.plaf.motif.MotifLookAndFeel",
                             20);
        frame2.setLocation((int)(frame1.getLocation().getX() + frame1.getWidth()
                            + 100), 300);
        JFrame frame3 = getMenuItemTestFrame(true,
                             "com.sun.java.swing.plaf.gtk.GTKLookAndFeel", 420);
        frame3.setLocation(300, (int)(frame1.getLocation().getY()
                                 + frame1.getHeight() + 100));
        JFrame frame4 = getMenuItemTestFrame(false,
                             "com.sun.java.swing.plaf.gtk.GTKLookAndFeel", 420);
        frame4.setLocation((int)(frame3.getLocation().getX() + frame3.getWidth()
                            + 100),
                           (int)frame3.getLocation().getY());
        return List.of(frame1, frame2, frame3, frame4);
    }

        public static JFrame getMenuItemTestFrame(boolean isLeft, String lafName, int frameY) {
            try {
                UIManager.setLookAndFeel(lafName);
            } catch (Exception e) {
                e.printStackTrace();
            }

            JMenu menu = new JMenu("View");
            menu.setMnemonic('V');

            menu.add(new JMenuItem("Refresh"));
            menu.add(new JMenuItem("Customize..."));
            menu.add(new JCheckBoxMenuItem("Show Toolbar"));
            menu.addSeparator();
            menu.add(new JRadioButtonMenuItem("List"));
            menu.add(new JRadioButtonMenuItem("Icons"));
            JRadioButtonMenuItem rm2 = new JRadioButtonMenuItem("And icon.");
            rm2.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1,
                    KeyEvent.SHIFT_MASK));
            menu.add(rm2);
            JRadioButtonMenuItem mi3 = new JRadioButtonMenuItem("Radio w/icon");

            Icon myIcon = new Icon() { // 10 pixel red
                public void paintIcon(Component c, Graphics g, int x, int y) {
                    Color color = g.getColor();
                    g.setColor(Color.RED);
                    g.fillRect(x, y, 10, 10);
                    g.setColor(color);
                }

                public int getIconWidth() {
                    return 10;
                }

                public int getIconHeight() {
                    return 10;
                }
            };

            Icon myIcon2 = new Icon() { // 15 pixel green
                @Override
                public void paintIcon(Component c, Graphics g, int x, int y) {
                    Color color = g.getColor();
                    g.setColor(Color.GREEN);
                    g.fillRect(x, y, 15, 10);
                    g.setColor(color);
                }

                public int getIconWidth() {
                    return 15;
                }

                public int getIconHeight() {
                    return 10;
                }
            };

            rm2.setIcon(myIcon2);

            mi3.setIcon(myIcon);
            menu.add(mi3);
            menu.add(new JMenuItem(myIcon2));

            final JMenu menu2 = new JMenu("No nothing");
            menu2.add("One").addActionListener(new AbstractAction() {
                public void actionPerformed(ActionEvent e) {
                    System.out.println("menu.width = "+menu2.getPopupMenu().getWidth());
                }
            });
            menu2.add("Two").addActionListener(new AbstractAction() {
                public void actionPerformed(ActionEvent e) {
                    System.out.println("menu.width = "+menu2.getPopupMenu().getWidth());
                }
            });
            menu2.add("Threeee").addActionListener(new AbstractAction() {
                public void actionPerformed(ActionEvent e) {
                    System.out.println("menu.width = "+menu2.getPopupMenu().getWidth());
                }
            });

            JMenuItem mi4 = new JMenuItem("Item with icon");
            mi4.setIcon(myIcon);
            menu.addSeparator();
            menu.add(mi4);

            String title = "Menu Item Test " + (isLeft ? "(Left-to-right)" : "(Right-to-left)");
            JFrame frame = new JFrame(title);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

            JMenuBar menuBar = new JMenuBar();
            menuBar.add(menu);
            menuBar.add(menu2);

            JMenu someIcons = new JMenu("Some icons");
            JMenuItem imi1 = new JMenuItem("Icon!");
            imi1.setIcon(myIcon);
            someIcons.add(imi1);
            JMenuItem imi2 = new JMenuItem("Wide icon!");
            imi2.setIcon(myIcon2);
            someIcons.add(imi2);
            someIcons.add(new JCheckBoxMenuItem("CheckBox"));
            someIcons.add(new JRadioButtonMenuItem("RadioButton"));
            menuBar.add(someIcons);
            frame.setJMenuBar(menuBar);
            ComponentOrientation co = (isLeft ?
                    ComponentOrientation.LEFT_TO_RIGHT :
                    ComponentOrientation.RIGHT_TO_LEFT);
            frame.applyComponentOrientation(co);
            String shortName = lafName.substring(lafName.lastIndexOf(".")+1);
            JLabel label = new JLabel("<HTML><H2>"+shortName+"</H2></HTML>");
            frame.setLayout(new BorderLayout());
            frame.add(label, BorderLayout.CENTER);
            frame.setSize(300, 300);
            int frameX = isLeft ? 0 : 600;
            frame.setLocation(frameX, frameY);
            frame.setVisible(true);
            return frame;
        }
}
