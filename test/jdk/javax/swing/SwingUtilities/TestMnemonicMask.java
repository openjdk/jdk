/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8067449
 * @key headful
 * @requires (os.family != "mac")
 * @summary Test SwingUtilities.getSystemMnemonicKeyMask()
 * @run main TestMnemonicMask
 */

import java.awt.Robot;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

public class TestMnemonicMask {
    private static JFrame f;
    private static String[] data = {"one", "two", "three", "four"};
    private static final JList<String> list = new JList<String>(data);
    private static boolean menuSelected;
    private static volatile boolean failed;
    private static CountDownLatch listGainedFocusLatch = new CountDownLatch(1);

    public static void main(String[] args) throws Exception {
        try {
            createUI();
            runTest();
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (f != null) {
                    f.dispose();
                }
            });
        }
    }

    private static void createUI() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            f = new JFrame("TestMnemonicMask");
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

            list.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    listGainedFocusLatch.countDown();
                }
            });
            f.add(list);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setAlwaysOnTop(true);
            f.setVisible(true);
        });
    }

    private static void runTest() throws Exception {
        Robot robot = new Robot();
        robot.waitForIdle();
        robot.delay(1000);
        if (!listGainedFocusLatch.await(1, TimeUnit.SECONDS)) {
            throw new RuntimeException("Waited too long, but can't gain" +
                " focus for list");
        }
        int keyMask = SwingUtilities.getSystemMnemonicKeyMask();
        robot.keyPress(KeyEvent.VK_O);
        robot.keyRelease(KeyEvent.VK_O);
        robot.waitForIdle();
        robot.delay(500);
        robot.keyPress(getModKeyCode(keyMask));
        robot.keyPress(KeyEvent.VK_F);
        robot.keyRelease(KeyEvent.VK_F);
        robot.keyRelease(getModKeyCode(keyMask));
        robot.waitForIdle();
        robot.delay(500);

        if (!menuSelected) {
            throw new RuntimeException("Mnemonics mask not working");
        }
    }

    private static int getModKeyCode(int mod) {
        if ((mod & (InputEvent.ALT_DOWN_MASK | InputEvent.ALT_MASK)) != 0) {
            return KeyEvent.VK_ALT;
        }

        return 0;
    }
}
