/*
 * Copyright (c) 2025, Oracle and/or its affiliates.
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

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import jdk.test.lib.Platform;

/*
 * @test
 * @key headful
 * @bug 8319880
 * @summary verify that JTextField text selection stop if ended during loss of window focus
 * @library /test/lib
 * @build jdk.test.lib.Platform
 * @run main TextSelectionFocusLoss
 */

public class TextSelectionFocusLoss {
    private static JFrame frame;
    private static JTextField textField;
    private static Robot robot;

    public static void main(String[] args) throws Exception {
        try {
            robot = new Robot();
            robot.setAutoDelay(50);
            robot.setAutoWaitForIdle(true);

            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame("TextSelectionFocusLoss");

                textField = new JTextField("This is an example");
                textField.setBounds(10, 10, 260, 30);

                frame.setLayout(null);
                frame.add(textField);
                frame.setSize(350, 120);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });

            robot.delay(500);

            Point location = textField.getLocationOnScreen();
            Dimension size = textField.getSize();

            int y = location.y + size.height / 2;
            int startX = location.x + size.width - 30;

            robot.mouseMove(startX, y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);

            for (int dx = startX; dx > location.x - 30; dx -= 10) {
                robot.mouseMove(dx, y);
            }

            if (Platform.isOSX()) {
                robot.keyPress(KeyEvent.VK_META);
                robot.keyPress(KeyEvent.VK_TAB);
                robot.keyRelease(KeyEvent.VK_TAB);
                robot.keyRelease(KeyEvent.VK_META);
            } else {
                robot.keyPress(KeyEvent.VK_ALT);
                robot.keyPress(KeyEvent.VK_TAB);
                robot.keyRelease(KeyEvent.VK_TAB);
                robot.keyRelease(KeyEvent.VK_ALT);
            }

            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            if (Platform.isOSX()) {
                robot.keyPress(KeyEvent.VK_META);
                robot.keyPress(KeyEvent.VK_TAB);
                robot.keyRelease(KeyEvent.VK_TAB);
                robot.keyRelease(KeyEvent.VK_META);
            } else {
                robot.keyPress(KeyEvent.VK_ALT);
                robot.keyPress(KeyEvent.VK_TAB);
                robot.keyRelease(KeyEvent.VK_TAB);
                robot.keyRelease(KeyEvent.VK_ALT);
            }

            String expected = "hihititi";
            typeString(expected);
            if (!textField.getText().equals(expected)) {
                throw new RuntimeException("Test failed: text is  " + textField.getText());
            }
            System.out.println("Test succeeded");
        } finally {
            SwingUtilities.invokeAndWait(()-> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void typeString(String s) {
        for (char c : s.toCharArray()) {
            int code = KeyEvent.getExtendedKeyCodeForChar(c);
            if (code == KeyEvent.VK_UNDEFINED) continue;
            robot.keyPress(code);
            robot.keyRelease(code);
        }
    }
}
