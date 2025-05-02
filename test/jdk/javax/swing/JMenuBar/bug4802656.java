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
 * @bug 4802656
 * @summary Problem with keyboard navigation in JMenus JMenuItems if setVisible(false)
 * @key headful
 * @run main bug4802656
 */

import java.awt.Robot;
import java.awt.event.KeyEvent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.SwingUtilities;

public class bug4802656 {

    public static JFrame mainFrame;
    public static JMenu menu2;
    public static volatile boolean menu2Selected = true;

    public static void main(String[] args) throws Exception {
        Robot robo = new Robot();
        robo.setAutoDelay(100);
        try {
            SwingUtilities.invokeAndWait(() -> {
                mainFrame = new JFrame("Bug4802656");
                JMenuBar menuBar = new JMenuBar();
                JMenu menu1 = new JMenu("File");
                menu2 = new JMenu("Hidden");
                JMenu menu3 = new JMenu("Help");
                menuBar.add(menu1);
                menuBar.add(menu2);
                menuBar.add(menu3);
                menu2.setVisible(false);
                mainFrame.setJMenuBar(menuBar);
                mainFrame.setSize(200, 200);
                mainFrame.setLocationRelativeTo(null);
                mainFrame.setVisible(true);
            });
            robo.waitForIdle();
            robo.delay(1000);
            robo.keyPress(KeyEvent.VK_F10);
            robo.keyRelease(KeyEvent.VK_F10);
            robo.keyPress(KeyEvent.VK_RIGHT);
            robo.keyRelease(KeyEvent.VK_RIGHT);
            robo.delay(500);

            SwingUtilities.invokeAndWait(() -> {
                menu2Selected = menu2.isSelected();
            });

            if (menu2Selected) {
                throw new RuntimeException("Test failed");
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
