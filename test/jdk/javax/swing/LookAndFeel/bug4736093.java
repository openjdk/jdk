/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4736093 8155030
 * @requires (os.family == "windows")
 * @summary REGRESSION: Menu and controls shortcuts are not visible in Win L&F in jdk1.4.1
 * @modules java.desktop/sun.swing
 * @key headful
 */

import java.awt.Robot;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import sun.swing.MnemonicHandler;

public class bug4736093 {
    static volatile boolean passed = true;
    static volatile boolean done = false;
    static volatile boolean winlaf = true;
    static JFrame mainFrame = null;
    static Robot robo;

    public static void main(String args[]) throws Exception {
        try {
            robo = new Robot();

            SwingUtilities.invokeAndWait(() -> {
                try {
                    UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
                } catch (Exception ex) {
                    winlaf = false;
                }

                if (winlaf && MnemonicHandler.isMnemonicHidden()) {
                    mainFrame = new JFrame("Bug 4736093");
                    mainFrame.addWindowListener(new TestStateListener());
                    mainFrame.setSize(200, 400);
                    mainFrame.setLocationRelativeTo(null);
                    mainFrame.setVisible(true);
                } else {
                    System.out.println("Test is not for this system. Passed.");
                }
            });

            robo.waitForIdle();
            robo.delay(1000);

            if (!passed) {
                throw new RuntimeException("Test failed.");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (mainFrame != null) {
                        mainFrame.dispose();
                }
            });
        }
    }

    public static void addMenuBar() {
        JMenuBar mbar = new JMenuBar();
        JMenu menu = new JMenu("File");
        for (int i = 1; i < 5; i++) {
            menu.add(new JMenuItem("Menu Item " + i));
        }
        mbar.add(menu);
        mainFrame.setJMenuBar(mbar);
    }


    public static void checkForMnemonics(boolean expected) {
        if (expected != MnemonicHandler.isMnemonicHidden()) {
            passed = false;
        }
    }

    public static class TestStateListener extends WindowAdapter {
        public void windowOpened(WindowEvent ev) {
            try {
                new Thread(new RobotThread()).start();
            } catch (Exception ex) {
                throw new RuntimeException("Thread Exception");
            }
        }
    }

    public static class RobotThread implements Runnable {
        public void run() {
            MenuElement[] path;
            int altKey = java.awt.event.KeyEvent.VK_ALT;
            robo.setAutoDelay(3000); // 3 seconds delay
            robo.waitForIdle();

            robo.keyPress(altKey);
            robo.delay(1000);

            checkForMnemonics(false); // mnemonics should appear on press
            robo.keyRelease(altKey);
            robo.delay(1000);

            checkForMnemonics(true); // and disappear on release
            robo.keyPress(java.awt.event.KeyEvent.VK_ESCAPE);
            robo.keyRelease(java.awt.event.KeyEvent.VK_ESCAPE);
            robo.delay(1000);

            addMenuBar();
            robo.delay(1000);

            robo.keyPress(altKey);
            robo.delay(1000);

            checkForMnemonics(false); // mnemonics should appear on press
            robo.keyRelease(altKey);
            robo.delay(1000);

            checkForMnemonics(false); // and stay appeared in selected menu
            path = MenuSelectionManager.defaultManager().getSelectedPath();
            if (path.length == 0) {
                passed = false; // menu should be selected
            }
            robo.delay(1000);

            robo.keyPress(altKey);
            robo.delay(1000);

            checkForMnemonics(true); // and only now disappear
            path = MenuSelectionManager.defaultManager().getSelectedPath();
            if (path.length != 0) {
                passed = false; // menu should be deselected
            }
            robo.keyRelease(altKey);
            done = true;
            robo.delay(1000);
        }
    }
}
