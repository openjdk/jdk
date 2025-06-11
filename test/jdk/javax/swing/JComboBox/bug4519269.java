/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/*
 * @test
 * @bug 4519269
 * @summary Tests that DefaultKeySelectionManager doesn't throw NPE
 * @key headful
 * @run main bug4519269
 */

public class bug4519269 {
    static JFrame frame;
    static JComboBox combo;
    static Point p;
    static Object[] data = {new CustomString("Item 1"), new CustomString("Item 2"),
            new CustomString("Item 3"), new CustomString("Item 4")};

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(250);

            SwingUtilities.invokeAndWait(() -> createTestUI());
            robot.waitForIdle();
            robot.delay(1000);

            SwingUtilities.invokeAndWait(() -> p = combo.getLocationOnScreen());
            robot.mouseMove(p.x + 5, p.y + 5);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();

            robot.keyPress(KeyEvent.VK_SHIFT);
            robot.keyRelease(KeyEvent.VK_SHIFT);
            robot.waitForIdle();
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    public static void createTestUI() {
        frame = new JFrame("bug4519269");
        combo = new JComboBox(data);
        frame.getContentPane().add(combo);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    static class CustomString {
        String string;

        public CustomString(String s) {
            string = s;
        }

        public String toString() {
            return null;
        }
    }
}
