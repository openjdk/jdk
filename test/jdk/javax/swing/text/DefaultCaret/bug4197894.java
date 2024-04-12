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

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.JFrame;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/*
 * @test
 * @bug 4197894
 * @key headful
 * @summary Tests if shift-click adjusts selection in text areas.
 */

public class bug4197894 {
    private static JFrame jFrame;
    private static JTextArea ta;

    private static volatile Point point = null;
    private static volatile Rectangle bounds;

    private static volatile boolean passed = true;

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(50);
            robot.setAutoWaitForIdle(true);

            SwingUtilities.invokeAndWait(bug4197894::createAndShowUI);
            robot.waitForIdle();
            robot.delay(1000);

            SwingUtilities.invokeAndWait(() -> {
                point = ta.getLocationOnScreen();
                bounds = ta.getBounds();
            });
            robot.waitForIdle();
            robot.delay(300);

            robot.mouseMove((point.x + bounds.width / 4),
                    (point.y + bounds.height / 4));

            robot.keyPress(KeyEvent.VK_SHIFT);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.keyRelease(KeyEvent.VK_SHIFT);
            robot.delay(300);

            if (!passed) {
                throw new RuntimeException("Test failed." +
                        " Shift-Click Text Selection does not work!");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (jFrame != null) {
                    jFrame.dispose();
                }
            });
        }
    }

    private static void createAndShowUI() {
        jFrame = new JFrame("Shift-Click Text Selection");
        ta = new JTextArea();
        ta.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
                String selText = ta.getSelectedText();
                passed = !(selText == null || (selText.length() == 0));
            }
        });
        ta.setText("12345\n12345\n12345\n12345\n12345\n12345\n12345");
        ta.setCaretPosition(ta.getDocument().getLength());
        jFrame.getContentPane().add(ta);
        jFrame.pack();
        jFrame.setLocationRelativeTo(null);
        jFrame.setAlwaysOnTop(true);
        jFrame.setVisible(true);
    }
}
