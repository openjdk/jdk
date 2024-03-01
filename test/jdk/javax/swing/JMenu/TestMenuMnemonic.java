/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8326458
 * @key headful
 * @requires (os.family == "windows")
 * @modules java.desktop/com.sun.java.swing.plaf.windows
 * @summary Verifies if menu mnemonics toggle between show or hide
 *          on F10 press in windows LAF.
 * @run main TestMenuMnemonic
 */

import java.awt.event.KeyEvent;
import java.awt.Robot;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JMenuBar;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import com.sun.java.swing.plaf.windows.WindowsLookAndFeel;

public class TestMenuMnemonic {

    private static JFrame frame;
    private static JMenuBar menuBar;
    private static JMenu fileMenu;
    private static JMenu editMenu;
    private static JMenuItem item1;
    private static JMenuItem item2;
    private static int expectedMnemonicShowHideCount = 5;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        Robot robot = new Robot();
        robot.setAutoDelay(100);
        int mnemonicHideCount = 0;
        int mnemonicShowCount = 0;
        try {
            SwingUtilities.invokeAndWait(() -> {
                createAndShowUI();
            });

            robot.waitForIdle();
            robot.delay(1000);

            for (int i = 0; i < 10; i++) {
                robot.keyPress(KeyEvent.VK_F10);
                robot.waitForIdle();
                robot.delay(50);
                robot.keyRelease(KeyEvent.VK_F10);
                MenuSelectionManager msm =
                        MenuSelectionManager.defaultManager();
                MenuElement[] selectedPath = msm.getSelectedPath();
                if (WindowsLookAndFeel.isMnemonicHidden()) {
                    mnemonicHideCount++;
                    // check if selection is cleared when mnemonics are hidden
                    if (selectedPath.length != 0) {
                        throw new RuntimeException("Menubar is active even after" +
                                " mnemonics are hidden");
                    }
                } else {
                    mnemonicShowCount++;
                    if (selectedPath.length == 0 &&
                        (selectedPath[0] != menuBar || selectedPath[1] != fileMenu)) {
                        throw new RuntimeException("No Menu and Menubar is active when" +
                                " mnemonics are shown");
                    }
                }
            }
            robot.waitForIdle();
            robot.delay(1000);
            if (mnemonicShowCount != expectedMnemonicShowHideCount
                && mnemonicHideCount != expectedMnemonicShowHideCount) {
                throw new RuntimeException("Mismatch in Mnemonic show/hide on F10 press");
            }


        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void createAndShowUI() {
        frame = new JFrame("Test Menu Mnemonic Show/Hide");
        menuBar  = new JMenuBar();
        fileMenu = new JMenu("File");
        editMenu = new JMenu("Edit");
        fileMenu.setMnemonic(KeyEvent.VK_F);
        editMenu.setMnemonic(KeyEvent.VK_E);
        item1 = new JMenuItem("Item 1");
        item2 = new JMenuItem("Item 2");
        fileMenu.add(item1);
        fileMenu.add(item2);
        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        frame.setJMenuBar(menuBar);
        frame.pack();
        frame.setSize(250, 200);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}

