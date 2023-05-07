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
  @bug 4597455
  @summary setFocusable(false) is not moving the focus to next Focusable Component
  @key headful
  @run main SetFocusableTest
*/

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.TextArea;
import java.awt.TextField;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class SetFocusableTest implements KeyListener {
    static Object buttonMonitor;
    Object tfMonitor;
    static final int TEST_TIMEOUT = 5000;
    Button button;
    Frame frame;
    TextField textfield;

    public static void main(String[] args) throws Exception {
        SetFocusableTest test = new SetFocusableTest();
        test.start();
    }

    public void start() throws Exception {
        try {
            EventQueue.invokeAndWait(() -> {
                buttonMonitor = new Object();
                tfMonitor = new Object();
                frame = new Frame();
                frame.setTitle("Test Frame");
                frame.setLocation(100, 100);
                frame.setLayout(new FlowLayout());

                button = new Button("BUTTON");
                textfield = new TextField("First");

                button.addKeyListener(this);
                textfield.addKeyListener(this);

                frame.add(button);
                frame.add(textfield);

                frame.setBackground(Color.red);
                frame.setSize(500,200);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
                frame.toFront();
                button.addFocusListener(new MonitoredFocusListener(buttonMonitor));
                textfield.addFocusListener(new MonitoredFocusListener(tfMonitor));
            });

            Robot robot;
            robot = new Robot();
            robot.setAutoDelay(100);
            robot.setAutoWaitForIdle(true);
            robot.delay(1000);

            Point buttonOrigin = button.getLocationOnScreen();
            Dimension buttonSize = button.getSize();
            robot.mouseMove(
                (int)buttonOrigin.getX() + (int)buttonSize.getWidth() / 2,
                (int)buttonOrigin.getY() + (int)buttonSize.getHeight() / 2);

            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            if (!button.isFocusOwner()) {
                synchronized (buttonMonitor) {
                    buttonMonitor.wait(TEST_TIMEOUT);
                }
            }
            System.out.println("\n\nBefore calling the method button.setFocusable(false)");
            System.out.println("====================================================");
            System.out.println("Button is Focusable(button.isFocusable()) :"+button.isFocusable());
            System.out.println("Button is Focus owner(button.isFocusOwner()) :"+button.isFocusOwner());
            System.out.println("Button has Focus (button.hasFocus) :"+button.hasFocus());
            System.out.println("====================================================");

            button.setFocusable(false);

            if (!textfield.isFocusOwner()) {
                synchronized (tfMonitor) {
                    tfMonitor.wait(TEST_TIMEOUT);
                }
            }

            System.out.println("\nAfter Calling button.setFocusable(false)");
            System.out.println("====================================================");
            System.out.println("Button is Focusable(button.isFocusable()) :"+button.isFocusable());
            System.out.println("Button is Focus owner(button.isFocusOwner()) :"+button.isFocusOwner());
            System.out.println("Button has Focus (button.hasFocus()) :"+button.hasFocus());
            System.out.println("TextField is Focusable(textfield.isFocusable()) :"+textfield.isFocusable());
            System.out.println("TextField is Focus owner(textfield.isFocusOwner()) :"+textfield.isFocusOwner());
            System.out.println("TextField has Focus (textfield.hasFocus()) :"+textfield.hasFocus());
            System.out.println("====================================================n\n\n\n");

            if (!button.hasFocus() && !button.isFocusOwner() &&
                textfield.hasFocus() && textfield.isFocusOwner()){
                System.out.println("\n\n\nASSERTION :PASSED");
                System.out.println("=========================");
                System.out.println("Textfield is having the Focus.Transfer of Focus has happend.");
            } else {
                System.out.println("\n\n\nASSERTION :FAILED");
                System.out.println("==========================");
                System.out.println("Button is still having the Focus instead of TextField");
                throw new RuntimeException("Test FIALED");
            }
        } finally {
            if (frame != null) {
                frame.dispose();
            }
        }
    }// start()

    public void keyPressed(KeyEvent e) {
        System.out.println("Key Pressed ");
    }
    public void keyReleased(KeyEvent ke) {
        System.out.println("keyReleased called ");
    }
    public void keyTyped(KeyEvent ke) {
        System.out.println("keyTyped called ");
    }
}// class SetFocusableTest

class MonitoredFocusListener extends FocusAdapter {
    Object monitor;
    public MonitoredFocusListener(Object monitor) {
        this.monitor = monitor;
    }

    public void focusGained(FocusEvent fe) {
        System.out.println(fe.toString());
        synchronized (monitor) {
            monitor.notify();
        }
    }
}
