/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4128659
 * @summary Tests whether a focus request will work on a focus lost event.
 * @key headful
 * @run main FocusKeepTest
 */

import java.awt.BorderLayout;
import java.awt.KeyboardFocusManager;
import java.awt.Robot;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class FocusKeepTest {

    static JFrame frame;
    static JTextField tf;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(100);
        try {
            SwingUtilities.invokeAndWait(() -> createTestUI());
            robot.waitForIdle();
            robot.delay(1000);
            robot.keyPress(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_TAB);
            if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() instanceof JTextField tf1) {
                if (!tf1.getText().equals("TextField 1")) {
                    throw new RuntimeException("Focus on wrong textfield");
                }
            } else {
                throw new RuntimeException("Focus not on correct component");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void createTestUI() {
        frame = new JFrame("FocusKeepTest");
        tf = new JTextField("TextField 1");
        tf.addFocusListener(new MyFocusAdapter("TextField 1"));
        frame.add(tf, BorderLayout.NORTH);

        tf = new JTextField("TextField 2");
        tf.addFocusListener(new MyFocusAdapter("TextField 2"));
        frame.add(tf, BorderLayout.SOUTH);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    static class MyFocusAdapter extends FocusAdapter {
        private String myName;

        public MyFocusAdapter (String name) {
            myName = name;
        }

        public void focusLost (FocusEvent e) {
            if (myName.equals ("TextField 1")) {
                e.getComponent().requestFocus ();
            }
        }

        public void focusGained (FocusEvent e) {
        }
    }
}
