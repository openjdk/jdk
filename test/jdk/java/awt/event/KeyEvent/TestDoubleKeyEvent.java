/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4495473
 * @summary Tests that when you press key on canvas-type heavyweight only one key event arrives
 * @key headful
 * @run main TestDoubleKeyEvent
 */

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JFrame;
import javax.swing.JWindow;
import javax.swing.JTextField;

public class TestDoubleKeyEvent extends JFrame {
    JWindow w;
    JTextField tf;

    public void initUI() {
        setLayout(new BorderLayout());
        setTitle("Double Key Event Test");
        setLocationRelativeTo(null);
        setVisible(true);
        w = new JWindow(this);
        w.setLayout(new FlowLayout());
        tf = new JTextField(20);
        w.add(tf);
        w.pack();
        w.setLocationRelativeTo(null);
        w.setVisible(true);
        tf.requestFocus();
    }

    public void testAndClean() {
        String str = tf.getText();
        w.dispose();
        dispose();
        if (str.length() != str.chars().distinct().count()) {
            throw new RuntimeException("Duplicate characters found!");
        }
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException {
        TestDoubleKeyEvent test = new TestDoubleKeyEvent();
        EventQueue.invokeAndWait(test::initUI);
        Robot robot = new Robot();
        robot.setAutoDelay(50);
        robot.waitForIdle();
        robot.delay(1000);
        for (int i = 0; i < 15; i++) {
            robot.keyPress(KeyEvent.VK_A + i);
            robot.keyRelease(KeyEvent.VK_A + i);
            robot.waitForIdle();
        }
        robot.delay(1000);
        EventQueue.invokeAndWait(test::testAndClean);
    }
}
