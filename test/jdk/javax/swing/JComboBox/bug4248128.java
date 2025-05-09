/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Robot;
import java.awt.event.KeyEvent;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/*
 * @test
 * @bug 4248128 7148092
 * @summary Tests that alt+down arrow pulls down JComboBox popup
 * @key headful
 * @run main bug4248128
 */

public class bug4248128 {
    static JFrame frame;
    static volatile JComboBox combo;

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(250);
            SwingUtilities.invokeAndWait(() -> createTestUI());
            robot.waitForIdle();

            robot.keyPress(KeyEvent.VK_ALT);
            robot.keyPress(KeyEvent.VK_DOWN);
            robot.keyRelease(KeyEvent.VK_DOWN);
            robot.keyRelease(KeyEvent.VK_ALT);
            robot.waitForIdle();

            SwingUtilities.invokeAndWait(() -> {
                if (!combo.isPopupVisible()) {
                    throw new RuntimeException("Popup did not appear.");
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
        frame = new JFrame("4248128 Test");
        Object[] fruits = {"Banana", "Pear", "Apple"};
        combo = new JComboBox(fruits);
        frame.setLayout(new BoxLayout(frame.getContentPane(), BoxLayout.X_AXIS));
        frame.add(combo);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
