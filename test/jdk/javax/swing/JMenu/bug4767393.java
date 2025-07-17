/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4767393
 * @summary Disabled JMenu is selectable via mnemonic
 * @key headful
 * @run main bug4767393
 */

import java.awt.Robot;
import java.awt.event.KeyEvent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.SwingUtilities;

public class bug4767393 {

    public static JFrame mainFrame;
    public static JMenuBar menuBar;
    public static JMenu menu;
    public static JMenu disabled;
    public static volatile boolean disabledMenuSelected = true;

    public static void main(String[] args) throws Exception {
        try {
            Robot robo = new Robot();
            robo.setAutoDelay(100);
            SwingUtilities.invokeAndWait(() -> {
                mainFrame = new JFrame("Bug4767393");
                menuBar = new JMenuBar();
                menu = new JMenu("File");
                disabled = new JMenu("Disabled");
                menuBar.add(menu);
                menu.add("Menu Item 1");
                menu.add("Menu Item 2");
                disabled.setEnabled(false);
                disabled.setMnemonic('D');
                disabled.add("Dummy menu item");
                menu.add(disabled);
                menu.add("Menu Item 3");
                menu.add("Menu Item 4");
                mainFrame.setJMenuBar(menuBar);

                mainFrame.setSize(200, 200);
                mainFrame.setLocationRelativeTo(null);
                mainFrame.setVisible(true);
            });
            robo.waitForIdle();
            robo.delay(500);

            robo.keyPress(KeyEvent.VK_F10);
            robo.keyRelease(KeyEvent.VK_F10);
            robo.keyPress(KeyEvent.VK_DOWN);
            robo.keyRelease(KeyEvent.VK_DOWN);
            robo.delay(500);
            robo.keyPress(KeyEvent.VK_D);
            robo.keyRelease(KeyEvent.VK_D);
            robo.delay(100);

            SwingUtilities.invokeAndWait(() -> {
                disabledMenuSelected = disabled.isSelected();
            });

            if (disabledMenuSelected) {
                throw new RuntimeException("Disabled JMenu is selected" +
                        " by the mnemonic. Test failed.");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (mainFrame != null) {
                    mainFrame.dispose();
                }
            });
        }
    }
}
