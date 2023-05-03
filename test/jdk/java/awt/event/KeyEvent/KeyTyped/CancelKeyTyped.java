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

/*
 * @test
 * @bug 4754155
 * @summary Tests that KeyTyped events are fired for the Cancel key
 *          and that no extraneous characters are entered as a result
 * @key headful
 * @run main CancelKeyTyped
 */

import java.awt.AWTException;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Robot;
import java.awt.TextField;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.lang.reflect.InvocationTargetException;


public class CancelKeyTyped {
    static volatile boolean cancelKeyTypedReceived = false;
    static Frame frame;
    static TextField tf;
    static final String ORIGINAL = "0123456789";

    public static void main(String[] args) throws AWTException,
            InterruptedException, InvocationTargetException {
        try {
            Robot robot = new Robot();
            robot.setAutoWaitForIdle(true);
            robot.setAutoDelay(100);

            EventQueue.invokeAndWait(CancelKeyTyped::init);
            robot.waitForIdle();
            robot.delay(1000);

            // Press and release Cancel
            robot.keyPress(KeyEvent.VK_CANCEL);
            robot.keyRelease(KeyEvent.VK_CANCEL);

            if (cancelKeyTypedReceived) {
                if (tf.getText().equals(ORIGINAL)) {
                    System.out.println("Test PASSED");
                } else {
                    System.out.println("Test FAILED: wrong string");
                    throw new RuntimeException("The test failed: wrong string: "
                            + tf.getText());
                }
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    public static void init() {
        frame = new Frame("CancelKeyTyped");
        tf = new TextField(ORIGINAL, 20);
        frame.add(tf);
        frame.setSize(300, 100);
        frame.setVisible(true);
        frame.validate();

        tf.requestFocusInWindow();

        tf.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent evt) {
                printKey(evt);

                int keychar = evt.getKeyChar();
                if (keychar == 24) { // Cancel character is 24 or \u0018
                    cancelKeyTypedReceived = true;
                }
            }

            @Override
            public void keyPressed(KeyEvent evt) {
                printKey(evt);
            }

            @Override
            public void keyReleased(KeyEvent evt) {
                printKey(evt);
            }

            protected void printKey(KeyEvent evt) {
                switch (evt.getID()) {
                    case KeyEvent.KEY_TYPED:
                    case KeyEvent.KEY_PRESSED:
                    case KeyEvent.KEY_RELEASED:
                        break;
                    default:
                        return;
                }

                System.out.println("params= " + evt.paramString() + "  \n" +
                        "KeyChar: " + evt.getKeyChar() + " = " +
                        (int) evt.getKeyChar() +
                        "   KeyCode: " + evt.getKeyCode() +
                        "   Modifiers: " + evt.getModifiersEx());

                if (evt.isActionKey()) {
                    System.out.println("Action Key");
                }

                System.out.println("keyText= " +
                        evt.getKeyText(evt.getKeyCode()) + "\n");
            }
        });
    }
}
