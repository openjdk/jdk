/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/*
 * @test
 * @bug 4839739
 * @key headful
 * @summary Tests if JEditorPane works correctly with HTML comments.
 */

public class bug4839739 {

    private static JFrame jFrame;
    private static JEditorPane jep;
    private static volatile Point p;

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            robot.setAutoWaitForIdle(true);
            robot.delay(50);

            SwingUtilities.invokeAndWait(bug4839739::createAndShowUI);
            robot.waitForIdle();
            robot.delay(500);

            SwingUtilities.invokeAndWait(() -> p = jep.getLocationOnScreen());
            robot.delay(200);

            robot.mouseMove(p.x + 20, p.y + 20);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.keyPress(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_TAB);
            robot.delay(300);

            Component comp = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (!(comp instanceof JEditorPane)) {
                throw new RuntimeException("Test failed." +
                        " JEditorPane doesn't work as expected with HTML comments");
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
        String text = "<html><head><body><!-- some comment -->" +
                "some always visible text</body></html>";

        jFrame = new JFrame("JEditorPane With HTML");
        jep = new JEditorPane();
        jep.setEditorKit(new HTMLEditorKit());
        jep.setEditable(false);

        jep.setText(text);
        jFrame.getContentPane().add(jep);
        jFrame.setSize(200,200);
        jFrame.setLocationRelativeTo(null);
        jFrame.setVisible(true);
    }
}
