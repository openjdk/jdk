/*
 * Copyright (c) 2002, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @key headful
 * @bug 4424517
 * @summary Verify the mapping of various KeyEvents with their KeyLocations
 * is as expected.
 * @run main KeyEventLocationTest
 */

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;

import javax.swing.SwingUtilities;

public class KeyEventLocationTest {

    private static volatile Frame frame;
    private static volatile boolean keyPressed;
    private static volatile boolean keyReleased;
    private static volatile boolean keyTyped;
    private static volatile Robot robot;
    private static volatile int xLocation;
    private static volatile int yLocation;
    private static volatile int width;
    private static volatile int height;
    private static volatile Label label = new Label();
    private static volatile String currentString = "";

    private static int[] keyEvents = { KeyEvent.VK_0, KeyEvent.VK_1,
        KeyEvent.VK_2, KeyEvent.VK_3, KeyEvent.VK_4, KeyEvent.VK_5,
        KeyEvent.VK_6, KeyEvent.VK_7, KeyEvent.VK_8, KeyEvent.VK_9,
        KeyEvent.VK_A, KeyEvent.VK_B, KeyEvent.VK_C, KeyEvent.VK_D,
        KeyEvent.VK_E, KeyEvent.VK_F, KeyEvent.VK_G, KeyEvent.VK_H,
        KeyEvent.VK_I, KeyEvent.VK_J, KeyEvent.VK_K, KeyEvent.VK_L,
        KeyEvent.VK_M, KeyEvent.VK_N, KeyEvent.VK_O, KeyEvent.VK_P,
        KeyEvent.VK_Q, KeyEvent.VK_R, KeyEvent.VK_S, KeyEvent.VK_T,
        KeyEvent.VK_U, KeyEvent.VK_V, KeyEvent.VK_W, KeyEvent.VK_X,
        KeyEvent.VK_Y, KeyEvent.VK_Z, KeyEvent.VK_BACK_QUOTE,
        KeyEvent.VK_BACK_SLASH, KeyEvent.VK_BACK_SPACE,
        KeyEvent.VK_CLOSE_BRACKET, KeyEvent.VK_COMMA, KeyEvent.VK_EQUALS,
        KeyEvent.VK_ESCAPE, KeyEvent.VK_MINUS, KeyEvent.VK_OPEN_BRACKET,
        KeyEvent.VK_PERIOD, KeyEvent.VK_QUOTE, KeyEvent.VK_SEMICOLON,
        KeyEvent.VK_SLASH, KeyEvent.VK_SPACE };

    private static int specialKeyEvents[] = { KeyEvent.VK_F1, KeyEvent.VK_F2,
        KeyEvent.VK_F3, KeyEvent.VK_F4, KeyEvent.VK_F5, KeyEvent.VK_F6,
        KeyEvent.VK_F7, KeyEvent.VK_F8, KeyEvent.VK_F9, KeyEvent.VK_F10 };

    private static void createGUI() {
        frame = new Frame("Test frame");
        frame.setLayout(new BorderLayout());
        frame.setAlwaysOnTop(true);

        frame.addKeyListener(new KeyListener() {
            public void keyPressed(KeyEvent event) {
                try {
                    handleEvent("keyPressed", event);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                keyPressed = true;
            }

            public void keyReleased(KeyEvent event) {
                try {
                    handleEvent("keyReleased", event);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                keyReleased = true;
            }

            public void keyTyped(KeyEvent event) {
                try {
                    handleEvent("keyTyped", event);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                keyTyped = true;
            }

            private void handleEvent(String eventString, KeyEvent event)
                throws Exception {
                label.setText(eventString + " triggered for " + event);
                if ((event.getID() == KeyEvent.KEY_TYPED
                    && event.getKeyLocation() != KeyEvent.KEY_LOCATION_UNKNOWN)
                    || ((event.getID() == KeyEvent.KEY_PRESSED
                    || event.getID() == KeyEvent.KEY_PRESSED)
                    && event.getKeyLocation()
                    != KeyEvent.KEY_LOCATION_STANDARD)) {
                    throw new Exception("FAIL: Incorrect KeyLocation: "
                        + event.getKeyLocation() + " returned when "
                        + eventString + " triggered for " + event.getKeyChar());
                }
            }
        });
        label.setText("Current Event: ");
        frame.add(label, BorderLayout.SOUTH);
        frame.setSize(600, 300);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.toFront();
    }

    private static void doTest() throws Exception {
        robot = new Robot();
        robot.setAutoWaitForIdle(true);
        robot.setAutoDelay(100);

        SwingUtilities.invokeAndWait(() -> {
            xLocation = frame.getLocationOnScreen().x;
            yLocation = frame.getLocationOnScreen().y;
            width = frame.getWidth();
            height = frame.getHeight();
        });

        robot.mouseMove(xLocation + width / 2, yLocation + height / 2);
        robot.mousePress(MouseEvent.BUTTON1_MASK);
        robot.mouseRelease(MouseEvent.BUTTON1_MASK);

        for (int i = 0; i < keyEvents.length; i++) {
            resetValues();
            robot.keyPress(keyEvents[i]);
            robot.delay(200);
            if (!keyPressed) {
                throw new Exception(
                    "FAIL: keyPressed did not get triggered for "
                        + KeyEvent.getKeyText(keyEvents[i]));
            }
            robot.keyRelease(keyEvents[i]);
            robot.delay(200);
            if (!keyReleased) {
                throw new Exception(
                    "FAIL: keyReleased did not get triggered for "
                        + KeyEvent.getKeyText(keyEvents[i]));
            }
            robot.delay(200);
            if (!keyTyped) {
                throw new Exception("FAIL: keyTyped did not get triggered for "
                    + KeyEvent.getKeyText(keyEvents[i]));
            }
        }

        for (int i = 0; i < specialKeyEvents.length; i++) {
            resetValues();
            robot.keyPress(specialKeyEvents[i]);
            robot.delay(200);
            if (!keyPressed) {
                throw new Exception("FAIL: keyPressed did not get triggered"
                    + " for " + KeyEvent.getKeyText(specialKeyEvents[i]));
            }
            robot.keyRelease(specialKeyEvents[i]);
            robot.delay(200);
            if (!keyReleased) {
                throw new Exception("FAIL: keyReleased got triggered for "
                    + KeyEvent.getKeyText(specialKeyEvents[i]));
            }
            robot.delay(200);
            if (keyTyped) {
                throw new Exception("FAIL: keyTyped got triggered for "
                    + KeyEvent.getKeyText(specialKeyEvents[i]));
            }
        }
    }

    private static void resetValues() {
        keyPressed = false;
        keyReleased = false;
        keyTyped = false;
    }

    public static void main(String[] args) throws Exception {
        try {
            EventQueue.invokeAndWait(() -> createGUI());
            doTest();
            System.out.println("Test Passed");
        } finally {
            if (frame != null)
                EventQueue.invokeAndWait(() -> frame.dispose());
        }
    }
}

