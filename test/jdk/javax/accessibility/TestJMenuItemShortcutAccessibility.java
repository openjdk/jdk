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

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

/*
 * @test
 * @bug 8339728
 * @summary Tests that JAWS announce the shortcuts for JMenuItems.
 * @requires os.family == "windows"
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TestJMenuItemShortcutAccessibility
 */

public class TestJMenuItemShortcutAccessibility {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. Start the JAWS application
                2. Press Alt + M to open application Menu
                3. Navigate the Menu Items by using UP / DOWN arrow key
                4. Press Pass if you are able to hear correct JAWS announcements
                   (JAWS should read full shortcut text and not only the 1st
                   character of shortcut text for each menu item) else Fail
                """;

        PassFailJFrame.builder()
                .title("TestJMenuItemShortcutAccessibility Instruction")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(TestJMenuItemShortcutAccessibility::createUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createUI() {
        JFrame frame = new JFrame("A Frame with Menu");

        JMenuBar menuBar = new JMenuBar();
        JMenu menu = new JMenu("Menu with shortcuts");
        menu.setMnemonic(KeyEvent.VK_M);
        menuBar.add(menu);

        KeyStroke keyStroke1 = KeyStroke.getKeyStroke(KeyEvent.VK_F,
                InputEvent.CTRL_DOWN_MASK);
        KeyStroke keyStroke2 = KeyStroke.getKeyStroke(KeyEvent.VK_2,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
        KeyStroke keyStroke3 = KeyStroke.getKeyStroke(KeyEvent.VK_F1,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
        KeyStroke keyStroke4 = KeyStroke.getKeyStroke(KeyEvent.VK_COMMA,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
        KeyStroke keyStroke5 = KeyStroke.getKeyStroke(KeyEvent.VK_PERIOD,
                InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK);
        KeyStroke keyStroke6 = KeyStroke.getKeyStroke(KeyEvent.VK_TAB,
                InputEvent.CTRL_DOWN_MASK);
        KeyStroke keyStroke7 = KeyStroke.getKeyStroke(KeyEvent.VK_SPACE,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);

        JMenuItem menuItem1 = new JMenuItem("First Menu Item");
        menuItem1.setAccelerator(keyStroke1);
        JMenuItem menuItem2 = new JMenuItem("Second Menu Item");
        menuItem2.setAccelerator(keyStroke2);
        JMenuItem menuItem3 = new JMenuItem("Third Menu Item");
        menuItem3.setAccelerator(keyStroke3);
        JMenuItem menuItem4 = new JMenuItem("Fourth Menu Item");
        menuItem4.setAccelerator(keyStroke4);
        JMenuItem menuItem5 = new JMenuItem("Fifth Menu Item");
        menuItem5.setAccelerator(keyStroke5);
        JMenuItem menuItem6 = new JMenuItem("Sixth Menu Item");
        menuItem6.setAccelerator(keyStroke6);
        JMenuItem menuItem7 = new JMenuItem("Seventh Menu Item");
        menuItem7.setAccelerator(keyStroke7);

        menu.add(menuItem1);
        menu.add(menuItem2);
        menu.add(menuItem3);
        menu.add(menuItem4);
        menu.add(menuItem5);
        menu.add(menuItem6);
        menu.add(menuItem7);

        frame.setJMenuBar(menuBar);
        frame.setSize(300, 200);
        return frame;
    }
}
