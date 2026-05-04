/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4464723
 * @summary Tests simple KeyAdapter / KeyListener on an empty, focusable window
 * @key headful
 * @run main EmptyWindowKeyTest
*/

import java.awt.AWTEvent;
import java.awt.Frame;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.Robot;

public class EmptyWindowKeyTest {

    static volatile boolean passed1, passed2;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(100);
        MainFrame mainFrame = new MainFrame();
        mainFrame.setSize(50,50);
        mainFrame.addKeyListener(new KeyboardTracker());
        robot.waitForIdle();
        robot.delay(1000);
        robot.keyPress(KeyEvent.VK_A);
        robot.keyRelease(KeyEvent.VK_A);
        robot.waitForIdle();
        robot.delay(1000);
        if (!passed1 || !passed2) {
            throw new RuntimeException("KeyPress/keyRelease not seen," +
                       "passed1 " + passed1 + " passed2 " + passed2);
        }
    }

    static public class KeyboardTracker extends KeyAdapter {
        public KeyboardTracker() { }
        public void keyTyped(KeyEvent e) {}

        public void keyPressed(KeyEvent e) {
            if (e.getKeyText(e.getKeyCode()).equals("A")) {
                passed1 = true;
            }
        }
        public void keyReleased(KeyEvent e) {
            if (e.getKeyText(e.getKeyCode()).equals("A")) {
                passed2 = true;
            }
        }
    }

    static public class MainFrame extends Frame {

        public MainFrame() {
            super();
            enableEvents(AWTEvent.KEY_EVENT_MASK);
            setVisible(true);
        }

    }

}

