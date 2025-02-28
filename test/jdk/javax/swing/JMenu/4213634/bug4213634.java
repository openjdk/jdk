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

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/*
 * @test
 * @key headful
 * @bug 4213634 8017187
 * @library ../../regtesthelpers
 * @build Util
 * @run main bug4213634
 */

public class bug4213634 {

    private static JMenu menu;
    private static JFrame frame;
    private static Robot robot;

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        SwingUtilities.invokeAndWait(() -> {
            createAndShowGUI();
        });

        robot.waitForIdle();
        robot.delay(1000);
        test();
    }

    public static void createAndShowGUI() {
        frame = new JFrame("TEST");
        JMenuBar mb = new JMenuBar();
        menu = mb.add(createMenu("1 - First Menu", true));
        mb.add(createMenu("2 - Second Menu", false));
        frame.setJMenuBar(mb);
        JTextArea ta = new JTextArea("This test dedicated to Nancy and Kathleen, testers and bowlers extraordinaire\n\n\nNo exception means pass.");
        frame.getContentPane().add("Center", ta);
        JButton button = new JButton("Test");
        frame.getContentPane().add("South", button);
        frame.setSize(400, 400);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        button.requestFocusInWindow();
    }

    private static void test() throws AWTException, InterruptedException, InvocationTargetException {

        Util.hitMnemonics(robot, KeyEvent.VK_1);
        robot.waitForIdle();
        robot.delay(100);

        SwingUtilities.invokeAndWait(() -> {
            frame.dispose();
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

        for(int i = 0; i < 10; i ++) {
            menuitem = new JMenuItem("JMenuItem" + i);
            menuitem.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    throw new RuntimeException(
                        "Failed: Mnemonic activated");
                }
            });
            if (bFlag) {
                menuitem.setMnemonic('0' + i);
            }
            menu.add(menuitem);
        }
        return menu;
    }
}
