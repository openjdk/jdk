/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
  @key headful
  @bug 8272602
  @summary Ctrl+Space should generate a KeyTyped event on macOS
  @run main CtrlSpace
 */

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class CtrlSpace extends Frame implements KeyListener {

    static volatile boolean testPassed = false;
    static volatile Robot robot;

    public static void main(String[] args) throws Exception {

        robot = new Robot();
        robot.setAutoWaitForIdle(true);
        robot.setAutoDelay(100);

        Frame frame = createAndShowGUI(robot);

        test(robot);
        robot.waitForIdle();
        Thread.sleep(2000);
        frame.setVisible(false);
        frame.dispose();
        if (!testPassed) {
            throw new RuntimeException("No KeyTyped event");
        }
    }


   static Frame createAndShowGUI(Robot robot) {
        CtrlSpace win = new CtrlSpace();
        win.setSize(300, 300);
        Panel panel = new Panel(new BorderLayout());
        TextField textField = new TextField("abcdefghijk");
        textField.addKeyListener(win);
        panel.add(textField, BorderLayout.CENTER);
        win.add(panel);
        win.setVisible(true);
        robot.waitForIdle();
        textField.requestFocusInWindow();
        robot.waitForIdle();
        return win;
    }

    public static void test(Robot robot) {
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyPress(KeyEvent.VK_SPACE);
        robot.keyRelease(KeyEvent.VK_SPACE);
        robot.keyRelease(KeyEvent.VK_CONTROL);
        robot.delay(200);
    }

    public void keyPressed(KeyEvent evt) {
        System.out.println("Pressed " + evt);
    }

    public void keyReleased(KeyEvent evt) {
        System.out.println("Released " + evt);
    }

    public void keyTyped(KeyEvent evt) {
        System.out.println("Typed " + evt);
        testPassed = true;
    }

}
