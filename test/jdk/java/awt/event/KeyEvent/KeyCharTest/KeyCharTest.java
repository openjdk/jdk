/*
 * Copyright (c) 2004, 2022, Oracle and/or its affiliates. All rights reserved.
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
  @bug 5013984
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
    HashMap<Integer, Character> transMap = new HashMap();

    public void keyTyped(KeyEvent e){
    }

    public void keyPressed(KeyEvent e){
        transMap.put(e.getKeyCode(), e.getKeyChar());
    }

    public void keyReleased(KeyEvent e){
        Object value = transMap.get(e.getKeyCode());
        if (value != null && e.getKeyChar() != ((Character)value).charValue()) {
            throw new RuntimeException("Wrong KeyChar on KEY_RELEASED "+
                KeyEvent.getKeyText(e.getKeyCode()));
        }
    }

    public void start () {
        addKeyListener(this);
        setLocationRelativeTo(null);
        setSize(200, 200);
        setVisible(true);
        requestFocus();

        try {
            Robot robot = new Robot();
            robot.setAutoDelay(10);
            robot.setAutoWaitForIdle(true);
            robot.delay(100);
            robot.mouseMove(getLocationOnScreen().x + getWidth()/2,
                            getLocationOnScreen().y + getHeight()/2);

            robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);

            for(int vkey = 0x20; vkey < 0x7F; vkey++) {
                try {
                    robot.keyPress(vkey);
                    robot.keyRelease(vkey);
                    System.out.println(KeyEvent.getKeyText(vkey) + " " + vkey);
                } catch (RuntimeException e) {
                }
            }
            robot.delay(100);
        } catch(Exception e){
            e.printStackTrace();
            throw new RuntimeException("Exception while performing Robot actions.");
        }
   }

    public static void main(String[] args) {
        KeyCharTest test = new KeyCharTest();
        try {
            test.start();
        } finally {
            test.setVisible(false);
            test.dispose();
        }
    }
}

