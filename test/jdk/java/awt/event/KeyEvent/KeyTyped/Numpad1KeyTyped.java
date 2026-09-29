/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Frame;
import java.awt.Robot;
import java.awt.TextField;
import java.awt.Toolkit;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.concurrent.CountDownLatch;

import jdk.test.lib.Platform;

import static java.util.concurrent.TimeUnit.SECONDS;

/*
 * @test
 * @bug 4724007
 * @key headful
 * @summary Tests that KeyTyped events are fired for the Numpad1 key
 * @library /test/lib
 * @build jdk.test.lib.Platform
 * @run main Numpad1KeyTyped
 */
public final class Numpad1KeyTyped extends FocusAdapter implements KeyListener {

    private static final String ORIGINAL = "0123456789";
    private static final String EXPECTED = "10123456789";

    private final CountDownLatch typedNum1 = new CountDownLatch(1);
    private final CountDownLatch focusGained = new CountDownLatch(1);

    public static void main(String[] args) throws Exception {
        Numpad1KeyTyped test = new Numpad1KeyTyped();
        test.start();
    }

    private void start() throws Exception {
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Boolean oldState = null;

        Robot robot = new Robot();
        robot.setAutoDelay(100);

        Frame frame = new Frame("Numpad1KeyTyped");
        TextField tf = new TextField(ORIGINAL, 20);
        frame.add(tf);
        tf.addKeyListener(this);

        tf.addFocusListener(this);

        frame.setSize(300, 100);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        tf.requestFocusInWindow();

        if (!focusGained.await(2, SECONDS)) {
            throw new RuntimeException("TextField didn't receive focus");
        }
        robot.waitForIdle();

        try {
            // Move cursor to start of TextField
            robot.keyPress(KeyEvent.VK_HOME);
            robot.keyRelease(KeyEvent.VK_HOME);
            robot.waitForIdle();

            if (Platform.isLinux()) {
                // Press but don't release NumLock
                robot.keyPress(KeyEvent.VK_NUM_LOCK);
            }
            if (Platform.isWindows()) {
                oldState = toolkit.getLockingKeyState(KeyEvent.VK_NUM_LOCK);
                toolkit.setLockingKeyState(KeyEvent.VK_NUM_LOCK, true);
            }

            // Press and release Numpad-1
            robot.keyPress(KeyEvent.VK_NUMPAD1);
            robot.keyRelease(KeyEvent.VK_NUMPAD1);

            if (!typedNum1.await(2, SECONDS)) {
                throw new RuntimeException("TextField didn't receive keyTyped('1') - too slow");
            }

            final String text = tf.getText();
            if (!text.equals(EXPECTED)) {
                throw new RuntimeException("Test FAILED: wrong string '"
                                           + text + "' vs "
                                           + "expected '" + EXPECTED + "'");
            }
            System.out.println("Test PASSED");
        } finally {
            if (Platform.isLinux()) {
                // "release" + "press and release" NumLock to disable numlock
                robot.keyRelease(KeyEvent.VK_NUM_LOCK);
                robot.keyPress(KeyEvent.VK_NUM_LOCK);
                robot.keyRelease(KeyEvent.VK_NUM_LOCK);
            }
            if (oldState != null) {
                toolkit.setLockingKeyState(KeyEvent.VK_NUM_LOCK, oldState);
            }

            frame.dispose();
        }
    }

    @Override
    public void focusGained(FocusEvent e) {
        System.out.println("tf.focusGained");
        focusGained.countDown();
    }

    @Override
    public void keyPressed(KeyEvent evt) {
        printKey(evt);
    }

    @Override
    public void keyTyped(KeyEvent evt) {
        printKey(evt);

        int keychar = evt.getKeyChar();
        if (keychar == '1') {
            typedNum1.countDown();
        }
    }

    @Override
    public void keyReleased(KeyEvent evt) {
        printKey(evt);
        System.out.println();
    }

    private static void printKey(KeyEvent evt) {
        int id = evt.getID();
        if (id != KeyEvent.KEY_TYPED
            && id != KeyEvent.KEY_PRESSED
            && id != KeyEvent.KEY_RELEASED) {

            System.out.println("Other Event");
            return;
        }

        System.out.println("params= " + evt.paramString() + "  \n" +
          "KeyChar: " + evt.getKeyChar() + " = " + (int) evt.getKeyChar() +
          "   KeyCode: " + evt.getKeyCode() +
          "   Modifiers: " + evt.getModifiersEx());

        if (evt.isActionKey()) {
            System.out.println("   Action Key");
        }

        System.out.println("keyText= " + KeyEvent.getKeyText(evt.getKeyCode()) + "\n");
    }

}
