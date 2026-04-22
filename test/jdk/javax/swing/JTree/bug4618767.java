/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4618767
 * @summary First letter navigation in JTree interferes with mnemonics
 * @key headful
 * @run main bug4618767
 */

import java.awt.Robot;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

public class bug4618767 {
    private static JFrame f;
    private static final JTree tree = new
            JTree(new String[] {"one", "two", "three", "four"});
    private static boolean menuSelected;
    private static CountDownLatch listGainedFocusLatch = new CountDownLatch(1);

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                f = new JFrame("bug4618767 Test");
                JMenu menu = new JMenu("File");
                menu.setMnemonic('F');
                JMenuItem menuItem = new JMenuItem("item");
                menu.add(menuItem);
                JMenuBar menuBar = new JMenuBar();
                menuBar.add(menu);
                f.setJMenuBar(menuBar);

                menu.addMenuListener(new MenuListener() {
                    public void menuCanceled(MenuEvent e) {}
                    public void menuDeselected(MenuEvent e) {}
                    public void menuSelected(MenuEvent e) {
                        menuSelected = true;
                    }
                });

                tree.addFocusListener(new FocusAdapter() {
                    @Override
                    public void focusGained(FocusEvent e) {
                        listGainedFocusLatch.countDown();
                    }
                });
                f.add(tree);
                f.pack();
                f.setLocationRelativeTo(null);
                f.setAlwaysOnTop(true);
                f.setVisible(true);
            });
            runTest();
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (f != null) {
                    f.dispose();
                }
            });
        }
    }

    private static void runTest() throws Exception {
        if (!listGainedFocusLatch.await(3, TimeUnit.SECONDS)) {
            throw new RuntimeException("Waited too long, but can't gain" +
                    " focus for list");
        }
        Robot robot = new Robot();
        robot.setAutoDelay(200);
        robot.waitForIdle();
        robot.keyPress(KeyEvent.VK_O);
        robot.keyRelease(KeyEvent.VK_O);
        robot.waitForIdle();
        robot.keyPress(KeyEvent.VK_ALT);
        robot.keyPress(KeyEvent.VK_F);
        robot.keyRelease(KeyEvent.VK_F);
        robot.keyRelease(KeyEvent.VK_ALT);

        SwingUtilities.invokeAndWait(() -> {
            if (menuSelected && !tree.getSelectionPath()
                    .getLastPathComponent().toString().equals("one")) {
                throw new RuntimeException("Mnemonics interferes with JTree" +
                        " item selection using KeyEvent");
            }
        });
    }
}
