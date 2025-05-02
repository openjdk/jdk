/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.event.KeyEvent;
import java.awt.event.KeyAdapter;

/*
 * @test
 * @key headful
 * @bug 8007156 8025126
 * @summary Extended key code is not set for a key event
 * @run main ExtendedKeyCodeTest
 */

public class ExtendedKeyCodeTest {
    private static Frame frame;
    private static volatile boolean setExtendedKeyCode;
    private static volatile int eventsCount;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(50);

        try {
            EventQueue.invokeAndWait(() -> createTestGUI());
            robot.waitForIdle();
            robot.delay(1000);

            robot.keyPress(KeyEvent.VK_D);
            robot.keyRelease(KeyEvent.VK_D);
            robot.waitForIdle();

            if (!setExtendedKeyCode) {
                throw new RuntimeException("Wrong extended key code!");
            }
        } finally {
            EventQueue.invokeAndWait(() -> frame.dispose());
        }

        try {
            EventQueue.invokeAndWait(() -> createTestGUI2());
            robot.waitForIdle();
            robot.delay(1000);

            robot.keyPress(KeyEvent.VK_LEFT);
            robot.keyRelease(KeyEvent.VK_LEFT);
            robot.waitForIdle();

            if (eventsCount != 2 || !setExtendedKeyCode) {
                throw new RuntimeException("Wrong extended key code\n" +
                        "eventsCount: " + eventsCount);
            }
        } finally {
            EventQueue.invokeAndWait(() -> frame.dispose());
        }
    }

    public static void createTestGUI() {
        eventsCount = 0;
        setExtendedKeyCode = true;
        frame = new Frame("ExtendedKeyCodeTest1");
        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                eventsCount++;
                int keyCode = KeyEvent.getExtendedKeyCodeForChar(e.getKeyChar());
                setExtendedKeyCode = setExtendedKeyCode && (e.getExtendedKeyCode() == keyCode);
                System.out.println("Test 1 keyPressed " + keyCode);
            }

            @Override
            public void keyReleased(KeyEvent e) {
                eventsCount++;
                int keyCode = KeyEvent.getExtendedKeyCodeForChar(e.getKeyChar());
                setExtendedKeyCode = setExtendedKeyCode && (e.getExtendedKeyCode() == keyCode);
                System.out.println("Test 1 keyReleased " + keyCode);
            }
        });

        frame.setLocationRelativeTo(null);
        frame.setSize(300, 300);
        frame.setVisible(true);
    }

    public static void createTestGUI2() {
        setExtendedKeyCode = false;
        frame = new Frame("ExtendedKeyCodeTest2");
        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int keyCode = e.getExtendedKeyCode();
                setExtendedKeyCode = keyCode == KeyEvent.VK_LEFT;
                System.out.println("Test 2 keyPressed " + keyCode);
            }
        });

        frame.setLocationRelativeTo(null);
        frame.setSize(300, 300);
        frame.setVisible(true);
    }
}
