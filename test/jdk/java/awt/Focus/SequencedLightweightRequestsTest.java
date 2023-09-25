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
  @bug 4648816
  @summary Sometimes focus requests on LW components are delayed
  @key headful
  @run main SequencedLightweightRequestsTest
*/

import java.awt.AWTException;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.TextArea;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class SequencedLightweightRequestsTest implements FocusListener {
    final int WAIT_TIME = 5000;

    JFrame testFrame;
    JButton testButton1;
    JButton testButton2;
    JTextField testField;

    public void focusGained(FocusEvent fe) {
        System.err.println("FocusGained on " + fe.getComponent().getName());
    }

    public void focusLost(FocusEvent fe) {
        System.err.println("FocusLost on " + fe.getComponent().getName());
    }

    public static void main(String[] args) throws Exception {
        SequencedLightweightRequestsTest test =
            new SequencedLightweightRequestsTest();
        test.start();
    }

    public void start() throws Exception {
        try {

            SwingUtilities.invokeAndWait(() -> {
                testFrame = new JFrame("See my components!");
                testButton1 = new JButton("Click me!");
                testButton2 = new JButton("Do I have focus?");
                testField = new JTextField("Do I have focus?");
                testFrame.getContentPane().setLayout(new FlowLayout());
                testFrame.getContentPane().add(testButton1);
                testFrame.getContentPane().add(testField);
                testFrame.getContentPane().add(testButton2);

                testButton1.setName("Button1");
                testButton2.setName("Button2");
                testField.setName("textField");
                testButton1.addFocusListener(this);
                testButton2.addFocusListener(this);
                testField.addFocusListener(this);
                testFrame.addFocusListener(this);

                testFrame.setSize(300, 100);
                testFrame.setLocationRelativeTo(null);
                testFrame.setVisible(true);
            });

            Robot robot = new Robot();
            robot.setAutoDelay(100);
            robot.setAutoWaitForIdle(true);

            // wait to give to frame time for showing
            robot.delay(1000);

            // make sure that first button has focus
            Object monitor = new Object();
            MonitoredFocusListener monitorer =
                          new MonitoredFocusListener(monitor);
            Point origin = testButton1.getLocationOnScreen();
            Dimension dim = testButton1.getSize();
            robot.mouseMove((int)origin.getX() + (int)dim.getWidth()/2,
                            (int)origin.getY() + (int)dim.getHeight()/2);
            robot.mousePress(InputEvent.BUTTON1_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_MASK);

            if (!testButton1.isFocusOwner()) {
                synchronized (monitor) {
                    testButton1.addFocusListener(monitorer);
                    monitor.wait(WAIT_TIME);
                    testButton1.removeFocusListener(monitorer);
                }
            }

            // if first button still doesn't have focus, test fails
            if (!testButton1.isFocusOwner()) {
                throw new RuntimeException("First button doesn't receive focus");
            }

            // two lightweight requests
            java.awt.EventQueue.invokeAndWait(new Runnable() {
                public void run() {
                    testButton2.requestFocus();
                    testField.requestFocus();
                }
            });

            // make sure third button receives focus
            if (!testField.isFocusOwner()) {
                synchronized (monitor) {
                    testField.addFocusListener(monitorer);
                    monitor.wait(WAIT_TIME);
                    testField.removeFocusListener(monitorer);
                }
            }

            // if the text field still doesn't have focus, test fails
            if (!testField.isFocusOwner()) {
                throw new RuntimeException("Text field doesn't receive focus");
            }
            System.out.println("Test PASSED");
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (testFrame != null) {
                    testFrame.dispose();
                }
            });
        }
    }
}// class SequencedLightweightRequestsTest

class MonitoredFocusListener extends FocusAdapter {
    Object monitor;

    public MonitoredFocusListener(Object monitor) {
        this.monitor = monitor;
    }

    public void focusGained(FocusEvent fe) {
        synchronized (monitor) {
            monitor.notify();
        }
    }
}
