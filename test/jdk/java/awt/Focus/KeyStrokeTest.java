/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4845868
 * @summary REGRESSION: First keystroke after JDialog is closed is lost
 * @key headful
 * @run main KeyStrokeTest
 */

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Robot;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class KeyStrokeTest {
    static boolean keyTyped;
    static Frame frame;

    public static void main(String[] args) throws Exception {
        try {
            KeyStrokeTest test = new KeyStrokeTest();
            test.doTest();
        } finally {
            if (frame != null) {
                frame.dispose();
            }
        }
    }

    private static void doTest() throws Exception {
        final Object monitor = new Object();
        frame = new Frame();
        TextField textField = new TextField() {
                public void transferFocus() {
                    System.err.println("transferFocus()");
                    final Dialog dialog = new Dialog(frame, true);
                    Button btn = new Button("Close It");
                    btn.addActionListener(new ActionListener() {
                            public void actionPerformed(ActionEvent e) {
                                System.err.println("action performed");
                                dialog.setVisible(false);
                            }
                        });
                    dialog.add(btn);
                    dialog.setSize(200, 200);
                    dialog.setVisible(true);
                }
            };

        textField.addKeyListener(new KeyAdapter() {
                public void keyTyped(KeyEvent e) {
                    System.err.println(e);
                    if (e.getKeyChar() == 'a') {
                        keyTyped = true;
                    }

                    synchronized (monitor) {
                        monitor.notifyAll();
                    }
                }
            });
        frame.add(textField);
        frame.setSize(400, 400);
        frame.setVisible(true);

        Robot robot = new Robot();
        robot.waitForIdle();
        robot.delay(1000);
        robot.keyPress(KeyEvent.VK_TAB);
        robot.keyRelease(KeyEvent.VK_TAB);

        robot.delay(1000);
        robot.keyPress(KeyEvent.VK_SPACE);
        robot.keyRelease(KeyEvent.VK_SPACE);

        robot.delay(1000);
        synchronized (monitor) {
            robot.keyPress(KeyEvent.VK_A);
            robot.keyRelease(KeyEvent.VK_A);
            monitor.wait(3000);
        }

        if (!keyTyped) {
            throw new RuntimeException("TEST FAILED");
        }

        System.out.println("Test passed");
    }

}
