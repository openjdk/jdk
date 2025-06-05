/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4309079
 * @summary Tests that when a JInternalFrame is activated,
            focused JTextField shows cursor.
 * @key headful
 * @run main bug4309079
 */

import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import javax.swing.JFrame;
import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class bug4309079 {

    private static JFrame f;
    private static JTextField tf;
    private static JDesktopPane desktop;
    private static JInternalFrame f1;
    private static JInternalFrame f2;
    private static volatile boolean passed = true;
    private static volatile Point p;

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(100);
            SwingUtilities.invokeAndWait(() -> {
                f = new JFrame();
                f.setSize(500, 300);
                tf = new JTextField(10);
                tf.addFocusListener(new FocusListener() {
                    public void focusGained(FocusEvent e) {
                        passed = tf.getCaret().isVisible();
                    }
                    public void focusLost(FocusEvent e) {
                    }
                });
                tf.requestFocus();
                f1 = AddFrame(new JTextField(10));
                f2 = AddFrame(tf);
                f.getContentPane().add(desktop);
                f.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(500);

            SwingUtilities.invokeAndWait(() -> {
                f1.toFront();
                f2.toFront();
                p = tf.getLocationOnScreen();
            });
            robot.mouseMove(p.x, p.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK );
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK );

            if (!passed) {
                throw new RuntimeException("Test failed.");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (f != null) {
                    f.dispose();
                }
            });
        }
    }

    private static JInternalFrame AddFrame(JTextField tf) {
        JInternalFrame frame = new JInternalFrame();
        desktop = new JDesktopPane();
        desktop.add(frame);
        frame.getContentPane().setLayout(new FlowLayout());
        frame.getContentPane().add(tf);
        frame.setSize(300, 200);
        frame.setVisible(true);
        return frame;
    }
}
