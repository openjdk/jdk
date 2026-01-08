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
* @bug 6513492
* @summary Escape key needs to be pressed twice to remove focus from an empty/diabled Menu.
* @library /java/awt/regtesthelpers
* @build PassFailJFrame
* @run main/manual bug6513492
*/

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class bug6513492 {

    private static final String INSTRUCTIONS = """
        Test Menu for different LaF:

        * For Windows Laf:
            Click the editor
            Click EmpyMenu, press Escape -> focus must go to the editor
            Click EmpyMenu, press right arrow button, press Escape -> focus must go to the editor
            Click SubMenuTest, highlight the first disabled submenu, press Escape
                -> focus must stay at the topLevelMenu

        * For Metal, Nimbus and Aqua Laf
            Click the editor
            Click SubMenuTest, highlight the EmptySubmenu, press Escape -> focus must go to the editor
            Click SubMenuTest, highlight the EnabledItem, press Escape -> focus must go to the editor

        * For GTK and Motif
            Click the editor
            Open any menu or submenu, press Escape -> focus must go to the editor.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("bug6513492 Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(bug6513492::createTestUI)
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

        JFrame frame = new JFrame("bug6513492");
        JMenuBar bar = new JMenuBar();
        bar.add(new JMenu("EmptyMenu"));

        JMenu disabledMenu = new JMenu("NotEmpyButDisabled");
        disabledMenu.add(new JMenuItem("item"));
        disabledMenu.setEnabled(false);
        bar.add(disabledMenu);

        JMenu menu = new JMenu("SubMenuTest");
        JMenu disabledSubmenu = new JMenu("Submenu");
        disabledSubmenu.add(new JMenuItem("item"));
        disabledSubmenu.setEnabled(false);
        menu.add(disabledSubmenu);

        JMenu enabledSubmenu = new JMenu("Submenu");
        enabledSubmenu.add(new JMenuItem("item"));
        menu.add(enabledSubmenu);

        JMenu emptySubmenu = new JMenu("EmptySubmenu");
        menu.add(emptySubmenu);

        menu.add(new JMenuItem("EnabledItem"));
        JMenuItem item = new JMenuItem("DisabledItem");
        item.setEnabled(false);
        menu.add(item);
        bar.add(menu);

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

        JTextArea field = new JTextArea("The editor");
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
