/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6471949
 * @summary JMenu should stay selected after escape is pressed
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug6471949
*/

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class bug6471949 {

    private static final String INSTRUCTIONS = """
        Test the menu and its submenus for different LaF:

        Click on "Menu" and then click on "Inner" submenu
        and then click on "One more" submenu.

        For Metal, Nimbus and Aqua Laf the Escape key hides the last open submenu,
        Press Esc till the last menu "Inner" is closed.
        If the last menu is closed then the menu button (in menubar) gets unselected.

        For Windows Laf the Escape key hides the last open submenu
        if the last menu is closed then the menu button remains selected,
        until the Escape key is pressed again or any other key letter pressed.

        For GTK and Motif menu, all open submenus must hide when the Escape key is pressed.

        If everything works as described, the test passes and fails otherwise.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("bug6471949 Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(bug6471949::createTestUI)
                .logArea()
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {

        PassFailJFrame.log("Menu.cancelMode = " +
                           UIManager.getString("Menu.cancelMode"));
        PassFailJFrame.log("Menu.preserveTopLevelSelection = " +
                            UIManager.getBoolean("Menu.preserveTopLevelSelection"));
        PassFailJFrame.log("");

        JFrame frame = new JFrame("bug6471949");
        JMenuBar bar = new JMenuBar();
        JMenu menu = new JMenu("Menu");
        menu.setMnemonic('m');

        JMenuItem item = new JMenuItem("Item");
        menu.add(item);
        JMenu inner = new JMenu("Inner");
        inner.add(new JMenuItem("Test"));
        JMenu oneMore = new JMenu("One more");
        oneMore.add(new JMenuItem("Lala"));
        inner.add(oneMore);
        menu.add(inner);

        JMenu lafMenu = new JMenu("Change LaF");

        UIManager.LookAndFeelInfo[] lafs = UIManager.getInstalledLookAndFeels();
        for (final UIManager.LookAndFeelInfo lafInfo : lafs) {
            JMenuItem lafItem = new JMenuItem(lafInfo.getName());
            lafItem.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    setLaf(frame, lafInfo.getClassName());
                }
            });
            lafMenu.add(lafItem);
        }

        frame.setJMenuBar(bar);
        bar.add(menu);
        bar.add(lafMenu);

        JTextArea field = new JTextArea();
        frame.add(field);
        field.requestFocusInWindow();
        frame.pack();
        return frame;
    }

    private static void setLaf(JFrame frame, String laf) {
        try {
            UIManager.setLookAndFeel(laf);
            SwingUtilities.updateComponentTreeUI(frame);
            PassFailJFrame.log("Menu.cancelMode = " +
                               UIManager.getString("Menu.cancelMode"));
            PassFailJFrame.log("Menu.preserveTopLevelSelection = " +
                               UIManager.getBoolean("Menu.preserveTopLevelSelection"));
            PassFailJFrame.log("");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
