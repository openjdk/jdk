/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

/* @test
   @bug 8267374
   @key headful
   @requires (os.family == "mac")
   @summary Verifies ALT+Up/ALT+Down keypress move cursor to start/end of textline.
   @run main TestAltUpDown
 */

import java.awt.Robot;
import java.awt.event.KeyEvent;
import javax.swing.JFrame;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Utilities;

public class TestAltUpDown {
    private static JFrame frame;
    private static JTextArea textArea;
    volatile static int caretPosition;
    volatile static int rowEnd;
    volatile static int rowStart;

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                textArea = new JTextArea(20, 40);
                textArea.setLineWrap(true);
                textArea.setText("This is first line\n" +
                        "This is second line\n" +
                        "This is thrid line");
                textArea.setCaretPosition(0);
                frame = new JFrame("Alt-Arrow Bug");
                frame.add(textArea);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });

            Robot robot = new Robot();
            robot.waitForIdle();
            robot.delay(1000);
            robot.keyPress(KeyEvent.VK_ALT);
            robot.keyPress(KeyEvent.VK_DOWN);
            robot.keyRelease(KeyEvent.VK_DOWN);
            robot.keyRelease(KeyEvent.VK_ALT);

            SwingUtilities.invokeAndWait(() -> {
                caretPosition = textArea.getCaretPosition();
                try {
                    rowEnd = Utilities.getRowEnd(textArea, caretPosition);
                } catch (BadLocationException ex) {
                    throw new RuntimeException(ex);
                }
                System.out.println("caretPosition" + caretPosition + " rowEnd " + rowEnd);
            });
            if (caretPosition != rowEnd) {
                throw new RuntimeException("Option+Down doesn't move the cursor");
            }

            robot.delay(1000);
            robot.keyPress(KeyEvent.VK_ALT);
            robot.keyPress(KeyEvent.VK_DOWN);
            robot.keyRelease(KeyEvent.VK_DOWN);
            robot.keyRelease(KeyEvent.VK_ALT);
            robot.delay(1000);
            robot.keyPress(KeyEvent.VK_ALT);
            robot.keyPress(KeyEvent.VK_UP);
            robot.keyRelease(KeyEvent.VK_UP);
            robot.keyRelease(KeyEvent.VK_ALT);
            robot.delay(1000);
            robot.keyPress(KeyEvent.VK_ALT);
            robot.keyPress(KeyEvent.VK_UP);
            robot.keyRelease(KeyEvent.VK_UP);
            robot.keyRelease(KeyEvent.VK_ALT);

            caretPosition = textArea.getCaretPosition();
            try {
                rowStart = Utilities.getRowStart(textArea, caretPosition);
            } catch (BadLocationException bex) {
                throw new RuntimeException(bex);
            }
            System.out.println("caretPosition" + caretPosition + " rowStart " + rowStart);
            if (caretPosition != 0) {
                throw new RuntimeException("Option+Up doesn't move the cursor");
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
