/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4265784 4267291
 * @summary Tests work of TAB key in JTextArea
 * @key headful
 * @run main bug4265784
 */

import javax.swing.JFrame;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class bug4265784 {
    static JFrame frame;
    static JTextArea ta;
    static volatile Point p;
    static volatile int pos;
    static volatile int pos1;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(100);
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame("bug4265784");
                ta = new JTextArea();
                frame.getContentPane().add(ta);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                p = ta.getLocationOnScreen();
            });
            robot.mouseMove(p.x + 10, p.y + 10);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            SwingUtilities.invokeAndWait(() -> {
                pos = ta.getCaretPosition();
            });
            System.out.println(pos);
            robot.keyPress(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_TAB);
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                pos1 = ta.getCaretPosition();
            });
            System.out.println(pos1);
            if (pos == pos1) {
                throw new RuntimeException("TAB ignored");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
