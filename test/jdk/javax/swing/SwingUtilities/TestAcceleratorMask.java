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
 * @run main TestAcceleratorMask
 */

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.InputEvent;
import java.awt.Robot;
import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

public class TestAcceleratorMask {

    static JFrame frame;
    static Robot robot;
    static boolean passed;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        int keyMask = SwingUtilities.getSystemMnemonicKeyMask();
        int keyCode = KeyEvent.VK_S;
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame("SetAccelerator Example");
                JMenuBar menuBar = new JMenuBar();
                JMenu menu = new JMenu("File");
                JMenuItem saveItem = new JMenuItem("Save");

                saveItem.setAccelerator(KeyStroke.getKeyStroke(
                                        keyCode, keyMask));

                saveItem.addActionListener(e -> passed = true);

                menu.add(saveItem);
                menuBar.add(menu);
                frame.setJMenuBar(menuBar);
                frame.setSize(300, 200);
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
            robot.keyPress(getModKeyCode(keyMask));
            robot.keyPress(keyCode);
            robot.keyRelease(keyCode);
            robot.keyRelease(getModKeyCode(keyMask));
            robot.waitForIdle();
            robot.delay(1000);
            if (!passed) {
                throw new RuntimeException("Accelerator mask not working");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static int getModKeyCode(int mod) {
        if ((mod & (InputEvent.ALT_DOWN_MASK | InputEvent.ALT_MASK)) != 0) {
            return KeyEvent.VK_ALT;
        }

        return 0;
    }
}
