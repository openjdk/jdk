/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Robot;
import java.awt.event.KeyEvent;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

/*
 * @test
 * @key headful
 * @bug 4213634 8017187
 * @library ../../regtesthelpers
 * @build Util
 * @summary Verifies if Alt+mnemonic char works when
 *          menu & menuitem have same mnemonic char
 * @run main bug4213634
 */

public class bug4213634 {

    private static JMenu menu;
    private static JFrame frame;
    private static Robot robot;

    public static void main(String[] args) throws Exception {
        try {
            robot = new Robot();
            SwingUtilities.invokeAndWait(bug4213634::createAndShowGUI);

            robot.waitForIdle();
            robot.delay(1000);
            test();
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    public static void createAndShowGUI() {
        frame = new JFrame("bug4213634");
        JMenuBar mb = new JMenuBar();
        menu = mb.add(createMenu("1 - First Menu", true));
        mb.add(createMenu("2 - Second Menu", false));
        frame.setJMenuBar(mb);
        JButton button = new JButton("Test");
        frame.getContentPane().add("South", button);
        frame.setSize(300, 300);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        button.requestFocusInWindow();
    }

    private static void test() throws Exception {
        Util.hitMnemonics(robot, KeyEvent.VK_1);
        robot.waitForIdle();
        robot.delay(100);

        SwingUtilities.invokeAndWait(() -> {
            if (!menu.isSelected()) {
                throw new RuntimeException(
                    "Failed: Menu didn't remain posted at end of test");
            } else {
                System.out.println("Test passed!");
            }
        });
    }

    private static JMenu createMenu(String str, boolean bFlag) {
        JMenuItem menuitem;
        JMenu menu = new JMenu(str);
        menu.setMnemonic(str.charAt(0));

        for (int i = 0; i < 10; i++) {
            menuitem = new JMenuItem("JMenuItem" + i);
            menuitem.addActionListener(e -> {
                throw new RuntimeException("Failed: Mnemonic activated");
            });
            if (bFlag) {
                menuitem.setMnemonic('0' + i);
            }
            menu.add(menuitem);
        }
        return menu;
    }
}
