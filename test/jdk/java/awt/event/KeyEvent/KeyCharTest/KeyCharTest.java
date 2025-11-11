/*
 * Copyright (c) 2004, 2025, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @bug 5013984 8360647
  @summary Tests KEY_PRESSED has the same KeyChar as KEY_RELEASED
  @key headful
  @run main KeyCharTest
*/

import java.awt.Frame;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.util.HashMap;

public class KeyCharTest extends Frame implements KeyListener {
    HashMap<Integer, Character> transMap = new HashMap<>();

    public void keyTyped(KeyEvent e){
    }

    public void keyPressed(KeyEvent e){
        transMap.put(e.getKeyCode(), e.getKeyChar());
    }

    public void keyReleased(KeyEvent e){
        Character value = transMap.get(e.getKeyCode());
        if (value != null && e.getKeyChar() != value) {
            throw new RuntimeException("Wrong KeyChar on KEY_RELEASED "+
                KeyEvent.getKeyText(e.getKeyCode()));
        }
    }

    private void testKeyRange(Robot robot, int start, int end) {
        System.out.printf("\nTesting range on %d to %d\n", start, end);
        for(int vkey = start; vkey <= end; vkey++) {
            try {
                robot.keyPress(vkey);
                robot.keyRelease(vkey);
                System.out.println(KeyEvent.getKeyText(vkey) + " " + vkey);
            } catch (RuntimeException ignored) {}
        }
        robot.delay(100);
    }

    public void start() throws Exception {
        Robot robot = new Robot();
        addKeyListener(this);
        setLocationRelativeTo(null);
        setSize(200, 200);
        setVisible(true);
        requestFocus();

        boolean wasNumlockPressed = false;
        try {
            robot.setAutoDelay(10);
            robot.setAutoWaitForIdle(true);
            robot.delay(100);
            robot.mouseMove(getLocationOnScreen().x + getWidth()/2,
                            getLocationOnScreen().y + getHeight()/2);

            robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);

            testKeyRange(robot, 0x20, 0x7E);

            // Try again with a different numpad state.
            robot.keyPress(KeyEvent.VK_NUM_LOCK);
            robot.keyRelease(KeyEvent.VK_NUM_LOCK);
            wasNumlockPressed = true;

            testKeyRange(robot, KeyEvent.VK_NUMPAD0, KeyEvent.VK_DIVIDE);
        } catch(Exception e){
            throw new RuntimeException("Exception while performing Robot actions.");
        } finally {
            if (wasNumlockPressed) {
                robot.keyPress(KeyEvent.VK_NUM_LOCK);
                robot.keyRelease(KeyEvent.VK_NUM_LOCK);
            }
        }
   }

    public static void main(String[] args) throws Exception {
        KeyCharTest test = new KeyCharTest();
        try {
            test.start();
        } finally {
            test.setVisible(false);
            test.dispose();
        }
    }
}

