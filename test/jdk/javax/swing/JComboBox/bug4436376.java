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

import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.event.InputEvent;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/*
 * @test
 * @bug 4436376
 * @key headful
 * @summary Tests that ComboBox items can't be deselected with Ctrl+click
 * @run main bug4436376
 */

public class bug4436376 {
    static JFrame frame;
    static volatile Point p;
    static volatile JComboBox combo;

    final static int SELECTED_INDEX = 2;

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(250);
            SwingUtilities.invokeAndWait(() -> createTestUI());
            robot.waitForIdle();

            SwingUtilities.invokeAndWait(() -> p = combo.getLocationOnScreen());
            robot.waitForIdle();

            robot.mouseMove(p.x + 10, p.y + 10);
            robot.waitForIdle();

            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.keyRelease(KeyEvent.VK_CONTROL);
            robot.waitForIdle();

            SwingUtilities.invokeAndWait(() -> {
                if (combo.getSelectedIndex() != SELECTED_INDEX) {
                    throw new RuntimeException("Failed: selected index has been changed");
                }
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    public static void createTestUI() {
        frame = new JFrame("bug4436376");
        String[] items = new String[]{"One", "Two", "Three", "Four"};
        combo = new JComboBox(items);
        combo.setSelectedIndex(SELECTED_INDEX);

        frame.setLayout(new FlowLayout());
        frame.add(combo);
        frame.setLocationRelativeTo(null);
        frame.pack();
        frame.setVisible(true);
    }
}
