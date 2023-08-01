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
  @test
  @bug 4531849
  @summary Test that double action event no longer sent
  @key headful
*/

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

public class DoubleActionEventTest implements ActionListener, WindowListener {

    static class Lock {
        boolean go = false;
        public synchronized boolean getGo() {return go;}
        public synchronized void setGo(boolean newGo) {go = newGo;}
    }

    static volatile Frame f;
    static volatile int numActionEvents = 0;
    static volatile Lock lock = new Lock();

    public static void main(String[] args) throws Exception {
        try {
            DoubleActionEventTest test = new DoubleActionEventTest();
            EventQueue.invokeAndWait(() -> test.createUI());
            runTest();
        } finally {
            if (f != null) {
                f.dispose();
            }
        }
    }

    public void createUI() {
        f = new Frame("DoubleActionEventTest");
        f.setLayout (new BorderLayout());
        f.addWindowListener(this);
        Button b = new Button("Action Listening Button");
        b.addActionListener(this);
        f.add(b);
        f.setBounds(100, 100, 200, 200);
        f.setVisible(true);
    }

    static void runTest() throws Exception {

        Robot robot = new Robot();
        robot.setAutoDelay(250);
        robot.setAutoWaitForIdle(true);
        robot.mouseMove(200, 200);

        while (!lock.getGo()) {}

        robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);

        if (numActionEvents != 1) {
            System.out.println("Wrong number of ActionEvents.  Test FAILS.");
            throw new RuntimeException("TEST FAILS");
        }
    }

    public void actionPerformed(ActionEvent e) {
        numActionEvents++;
        System.out.println("Number of ActionEvents: " + numActionEvents);
    }

    public void windowActivated(WindowEvent e) {
        lock.setGo(true);
    }
    public void windowClosed(WindowEvent e) {}
    public void windowClosing(WindowEvent e) {}
    public void windowDeactivated(WindowEvent e) {}
    public void windowDeiconified(WindowEvent e) {}
    public void windowIconified(WindowEvent e) {}
    public void windowOpened(WindowEvent e) {}

}
