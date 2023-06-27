/*
 * Copyright (c) 2002, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Robot;
import java.awt.TextField;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

/*
 * @test
 * @bug 4724007
 * @key headful
 * @summary Tests that KeyTyped events are fired for the Delete key
 *          and that no extraneous characters are entered as a result.
 */

public class DeleteKeyTyped {
    private static Frame frame;
    private static TextField tf;

    private static boolean deleteKeyTypedReceived = false;
    private static final String ORIGINAL = "0123456789";
    private static final String SUCCESS = "123456789";

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            robot.setAutoWaitForIdle(true);
            robot.setAutoDelay(100);

            EventQueue.invokeAndWait(DeleteKeyTyped::createTestUI);
            robot.waitForIdle();
            robot.delay(1000);

            // Move cursor to start of TextField
            robot.keyPress(KeyEvent.VK_HOME);
            robot.keyRelease(KeyEvent.VK_HOME);
            robot.waitForIdle();
            robot.delay(50);

            // Press and release Delete
            robot.keyPress(KeyEvent.VK_DELETE);
            robot.keyRelease(KeyEvent.VK_DELETE);
            robot.waitForIdle();
            robot.delay(50);

            EventQueue.invokeAndWait(DeleteKeyTyped::testDeleteKeyEvent);
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void createTestUI() {
        frame = new Frame();
        tf = new TextField(ORIGINAL, 20);
        frame.add(tf);
        frame.setSize(300, 100);
        frame.setVisible(true);
        tf.requestFocusInWindow();

        tf.addKeyListener(new KeyListener() {
            @Override
            public void keyPressed(KeyEvent evt) {
                printKey(evt);
            }

            @Override
            public void keyTyped(KeyEvent evt) {
                printKey(evt);
                int keychar = evt.getKeyChar();
                if (keychar == 127) { // Delete character is 127 or \u007F
                    deleteKeyTypedReceived = true;
                }
            }

            @Override
            public void keyReleased(KeyEvent evt) {
                printKey(evt);
            }

            private void printKey(KeyEvent evt) {
                switch(evt.getID()) {
                    case KeyEvent.KEY_TYPED:
                    case KeyEvent.KEY_PRESSED:
                    case KeyEvent.KEY_RELEASED:
                        break;
                    default:
                        System.out.println("Other Event");
                        return;
                }

                System.out.println("params= " + evt.paramString() + "  \n" +
                        "KeyChar: " + evt.getKeyChar() + " = " + (int) evt.getKeyChar() +
                        "   KeyCode: " + evt.getKeyCode() +
                        "   Modifiers: " + evt.getModifiersEx());

                if (evt.isActionKey()) {
                    System.out.println("Action Key");
                }

                System.out.println("keyText= " + KeyEvent.getKeyText(evt.getKeyCode()) + "\n");
            }
        });
    }

    private static void testDeleteKeyEvent() {
        if (deleteKeyTypedReceived) {
            if (tf.getText().equals(SUCCESS)) {
                System.out.println("Test PASSED");
            } else {
                System.out.println("Test FAILED: wrong string");
                throw new RuntimeException("The test failed: wrong string:  " +
                        tf.getText());
            }
        }
    }
}
