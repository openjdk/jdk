/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8348760 8370945
 * @summary Verify if RadioButtonMenuItem bullet and
 *          JCheckboxMenuItem checkmark is shown if
 *          JRadioButtonMenuItem and JCheckboxMenuItem
 *          is rendered with ImageIcon in WindowsLookAndFeel
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TestRadioAndCheckMenuItemWithIcon
 */

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import javax.swing.UIManager;

public class TestRadioAndCheckMenuItemWithIcon {

    private static final String INSTRUCTIONS1 = """
        This test, although can be run in any L&F, is primarily for
        WindowsLookAndFeel.
        A window is displayed which has a "Menu" on the menu bar.""";

    private static final String INSTRUCTIONS2 = """
        A window is displayed which has 2 Menu on the menu bar.
        One is "Menu" and another "Menu2".""";

    private static final String INSTRUCTIONS3 = """
        \nClicking on the "Menu" will show a
            JRadioButtonMenuItem group with 3 radiobutton menuitems
            and a JCheckBoxMenuItem group with 3 checkbox menuitems
            and 2 JMenuItems.

            First radiobutton menuitem is selected with image icon of a red square.
            Second radiobutton menuitem is unselected with image icon.
            Third radiobutton menuItem is unselected without image icon.

            First checkbox menuitem is selected with image icon.
            Second checkbox menuitem is unselected with image icon.
            Third checkbox menuItem is unselected without image icon.

            Verify that for first JRadioButtonMenuItem with image icon,
            a bullet is shown alongside the image icon and
            for first JCheckBoxMenuItem with image icon
            a checkmark is shown alongside the image icon.

            Also, verify the JMenuItem "MenuItem2" red square image icon location.
            The red square images are not all the same size but
            their left edges should  all be aligned.

        If bullet and checkmark is shown in "Menu"
        and "MenuItem2" image icon location is aligned, test passes else fails.""";

    private static final String INSTRUCTIONS4 = """
        \nClicking on the "Menu2" will show
            2 JMenuitems with only text
            1 JMenuItem with an image icon
            1 JCheckBoxMenuItem menuitem with selected check mark
            1 JRadioButtonMenuItem with a radio bullet

            The red square image, check mark and radio bullet
            should all be rendered in the 1st column
            and the menu text of all the menuitems should be aligned.


        If bullet and checkmark is shown in "Menu"
        and "MenuItem2" image icon location and
        and "Menu2" rendering is as described, test passes else fails.""";

    public static void main(String[] args) throws Exception {
        if ((UIManager.getLookAndFeel().getID()).equals("Aqua")) {
            return;
        }

        System.out.println(UIManager.getLookAndFeel().getName());
        String instructions = null;
        int colWidth = 0;
        if ((UIManager.getLookAndFeel().getName()).equals("Windows")) {
            instructions = INSTRUCTIONS2 + INSTRUCTIONS3 + INSTRUCTIONS4;
            colWidth = 75;
        } else {
            instructions = INSTRUCTIONS1 + INSTRUCTIONS3;
            colWidth = 45;
        }
        PassFailJFrame.builder()
                .title("JRadioButtonMenuItem Instructions")
                .instructions(instructions)
                .columns(colWidth)
                .testUI(TestRadioAndCheckMenuItemWithIcon::doTest)
                .build()
                .awaitAndCheck();
    }

    public static JFrame doTest() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics g = img.getGraphics();
        g.setColor(Color.red);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.dispose();

        BufferedImage img2 = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics g2 = img2.getGraphics();
        g2.setColor(Color.red);
        g2.fillRect(0, 0, img2.getWidth(), img2.getHeight());
        g2.dispose();

        JFrame frame = new JFrame("RadioButtonWithImageIcon");
        ImageIcon imageIcon1 = new ImageIcon(img);
        ImageIcon imageIcon2 = new ImageIcon(img2);
        AbstractButton button1;
        JRadioButtonMenuItem m1 = new JRadioButtonMenuItem("JRadioButtonMenuItem 1",
                imageIcon1);
        m1.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4, ActionEvent.ALT_MASK|ActionEvent.CTRL_MASK|ActionEvent.SHIFT_MASK));
        button1 = m1;
        button1.setSelected(true);
        AbstractButton button2 = new JRadioButtonMenuItem("JRadioButtonMenuItem 2", imageIcon2);
        AbstractButton button3 = new JRadioButtonMenuItem("JRadioButtonMenuItem 3");

        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(button1);
        buttonGroup.add(button2);
        buttonGroup.add(button3);

        AbstractButton check1 = new JCheckBoxMenuItem("JCheckBoxMenuItem 1",
                imageIcon1);
        check1.setSelected(true);
        AbstractButton check2 = new JCheckBoxMenuItem("JCheckBoxMenuItem 2", imageIcon1);
        JCheckBoxMenuItem c3;
        AbstractButton check3 = c3 = new JCheckBoxMenuItem("JCheckBoxMenuItem 3");
        c3.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, ActionEvent.ALT_MASK|ActionEvent.CTRL_MASK|ActionEvent.SHIFT_MASK));

        JMenu topLevel = new JMenu("Menu");

        topLevel.add(button1);
        topLevel.add(button2);
        topLevel.add(button3);

        topLevel.addSeparator();

        topLevel.add(check1);
        topLevel.add(check2);
        topLevel.add(check3);

        AbstractButton menuitem1 = new JMenuItem("MenuItem1");
        AbstractButton menuitem2 = new JMenuItem("MenuItem2", imageIcon1);
        topLevel.addSeparator();
        topLevel.add(menuitem1);
        topLevel.add(menuitem2);

        JMenuBar menuBar = new JMenuBar();
        menuBar.add(topLevel);
        if ((UIManager.getLookAndFeel().getName()).equals("Windows")) {
            JMenu menu2 = new JMenu("Menu2");
            JMenuItem menuItem1 = new JMenuItem("text-only menu item1");
            JMenuItem menuItem2 = new JMenuItem("text-only menu item2");
            JMenuItem menuItem3 = new JMenuItem("text-only menu item2", imageIcon1);
            JCheckBoxMenuItem cbMenuItem = new JCheckBoxMenuItem("text-check menu item4");
            cbMenuItem.setSelected(true);
            JRadioButtonMenuItem radioMenuItem = new JRadioButtonMenuItem("text-radio menu item5");
            radioMenuItem.setSelected(true);
            menu2.add(menuItem1);
            menu2.add(menuItem2);
            menu2.add(menuItem3);
            menu2.add(cbMenuItem);
            menu2.add(radioMenuItem);
            menuBar.add(menu2);
        }

        frame.setJMenuBar(menuBar);
        frame.setSize(300, 300);
        return frame;

    }
}
