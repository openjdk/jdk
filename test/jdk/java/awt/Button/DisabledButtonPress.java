/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 5044469
  @summary REG: Disabled component gains focus and receives keyevents on win32
  @key headful
*/

import java.awt.AWTException;
import java.awt.Button;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;

public class DisabledButtonPress implements ActionListener, FocusListener {

    public static void main(String[] args) throws Exception {
        try {
            DisabledButtonPress test = new DisabledButtonPress();
            EventQueue.invokeAndWait(() -> test.createUI());
            runTest();
        } finally {
            if (f != null) {
               f.dispose();
            }
        }
        if (!testPassed) {
            throw new RuntimeException("Test Failed.");
        }
    }

    final static Object FOCUS_LOCK = new Object();
    final static Object ACTION_LOCK = new Object();
    static volatile Frame f;
    static volatile Button b2;
    static volatile boolean testPassed = true;

    public void createUI() {
        f = new Frame("DisabledButtonPress");
        b2 = new Button("Click Me");
        b2.addActionListener(this);
        b2.addFocusListener(this);
        f.add(b2);
        f.pack();
        f.setVisible(true);
    }

    static void runTest() throws Exception {

        Robot robot = new Robot();
        robot.delay(500);
        System.out.println("Requesting focus");
        System.out.println(" b2.requestFocusInWindow()="+  b2.requestFocusInWindow());
        b2.setEnabled(false);
        synchronized(FOCUS_LOCK) {
            FOCUS_LOCK.wait(3000);
        }
        if (!b2.isFocusOwner()) {
            throw new RuntimeException("Test failed. Button doesn't have a focus.");
        }
        System.out.println("Button disabling");
        robot.delay(1000);
        robot.keyPress(KeyEvent.VK_SPACE);
        robot.delay(10);
        robot.keyRelease(KeyEvent.VK_SPACE);
        synchronized(ACTION_LOCK) {
            ACTION_LOCK.wait(2000); //give time to handle
                                    // ACTION_PERFORMED event from the Button if it was generated
        }
    }

    public void focusGained(FocusEvent ae) {
        System.out.println("Button got focus");
        synchronized(FOCUS_LOCK) {
            FOCUS_LOCK.notify();
        }
    }

    public void focusLost(FocusEvent ae) {}

    public void actionPerformed(ActionEvent evt) {
        System.out.println("Button: " + evt.getActionCommand() + " Clicked. Event is " +evt);
        if (evt.getSource() == b2) {
            testPassed = false;
            synchronized(ACTION_LOCK) {
                ACTION_LOCK.notify();
            }
        }
    }
}
