/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4174399
 * @summary Check that modifier values are set on a KeyPressed event
 *          when a modifier key is pressed.
 * @key headful
 * @run main KeyPressedModifiers
 */

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Robot;
import java.awt.TextField;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;

public class KeyPressedModifiers extends Frame implements KeyListener {
    static AtomicBoolean shiftDown = new AtomicBoolean(false);
    static AtomicBoolean controlDown = new AtomicBoolean(false);
    static AtomicBoolean altDown = new AtomicBoolean(false);

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException {
        KeyPressedModifiers test = new KeyPressedModifiers();
        try {
            EventQueue.invokeAndWait(test::initUI);
            Robot robot = new Robot();
            robot.setAutoDelay(100);
            robot.delay(500);
            robot.waitForIdle();
            robot.keyPress(KeyEvent.VK_SHIFT);
            robot.keyRelease(KeyEvent.VK_SHIFT);
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyRelease(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_ALT);
            robot.keyRelease(KeyEvent.VK_ALT);
            robot.delay(500);
            robot.waitForIdle();
            if (!shiftDown.get() || !controlDown.get() || !altDown.get()) {
                String error = "Following key modifiers were not registered:" +
                        (shiftDown.get() ? "" : " SHIFT") +
                        (controlDown.get() ? "" : " CONTROL") +
                        (altDown.get() ? "" : " ALT");
                throw new RuntimeException(error);
            }
        } finally {
            EventQueue.invokeAndWait(test::dispose);
        }
    }

    public void initUI() {
        setLayout(new BorderLayout());
        TextField tf = new TextField(30);
        tf.addKeyListener(this);
        add(tf, BorderLayout.CENTER);
        setSize(350, 100);
        setVisible(true);
        tf.requestFocus();
    }

    public void keyTyped(KeyEvent ignore) {
    }

    public void keyReleased(KeyEvent ignore) {
    }

    public void keyPressed(KeyEvent e) {
        System.out.println(e);
        switch (e.getKeyCode()) {
            case KeyEvent.VK_SHIFT:
                shiftDown.set(e.isShiftDown());
                break;
            case KeyEvent.VK_CONTROL:
                controlDown.set(e.isControlDown());
                break;
            case KeyEvent.VK_ALT:
                altDown.set(e.isAltDown());
                break;
        }
    }
}
