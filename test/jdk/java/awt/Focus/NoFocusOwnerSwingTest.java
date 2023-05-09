/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @bug 4390019
  @summary REGRESSION: Alt-F4 keybinding no longer shuts down java application on Windows
  @key headful
  @requires (os.family == "windows")
  @run main NoFocusOwnerSwingTest
*/

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.Label;
import java.awt.MenuBar;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.MenuShortcut;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowAdapter;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class NoFocusOwnerSwingTest {
    static boolean closingWindowCalled = false;
    static JFrame frame;

    public static void main(String[] args) throws Exception {
        try {
            if (!System.getProperty("os.name").startsWith("Windows")) {
                // this test is Win32 test only
                return;
            }

            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame("No Focus Owner Swing Test");
                JButton btn;
                frame.getContentPane().add(btn = new JButton("press"));
                frame.addWindowListener(new WindowAdapter() {
                    public void windowClosing(WindowEvent e) {
                        System.out.println("windowClosing is called");
                        closingWindowCalled = true;
                    }
                });
                frame.pack();
                frame.toFront();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });

            Robot robot = new Robot();
            robot.setAutoDelay(100);
            robot.delay(1000);
            robot.keyPress(KeyEvent.VK_ALT);
            robot.keyPress(KeyEvent.VK_F4);
            robot.waitForIdle();
            robot.keyRelease(KeyEvent.VK_F4);
            robot.keyRelease(KeyEvent.VK_ALT);
            robot.waitForIdle();

            if (!closingWindowCalled) {
                throw new RuntimeException("Test FAILED(closingWindowCalled=" +
                                         closingWindowCalled + ")");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}// class NoFocusOwnerSwingTest
