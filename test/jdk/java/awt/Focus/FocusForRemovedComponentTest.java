/*
 * Copyright (c) 2004, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4722671
  @summary Accessibility problem in JRE Finder
  @key headful
  @run main FocusForRemovedComponentTest
*/

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;

public class FocusForRemovedComponentTest
        implements ActionListener {
    static int ACTIVATION_TIMEOUT = 2000;
    static long WAIT_TIMEOUT = 3000;
    volatile Frame frame;
    volatile Button btnFirst;
    volatile Button btnSecond;
    volatile Button btnThird;

    public void start() throws InterruptedException, InvocationTargetException {
        try {
            EventQueue.invokeAndWait(() -> {
                frame = new Frame("FocusForRemovedComponentTest");
                btnFirst = new Button("First Button");
                btnSecond = new Button("Second Button");
                btnThird = new Button("Third Button");
                frame.add(btnFirst, BorderLayout.NORTH);
                frame.add(btnSecond, BorderLayout.CENTER);
                btnFirst.addActionListener(this);
                btnFirst.requestFocusInWindow();
                frame.pack();
                frame.setVisible(true);
            });

            try {
                Robot robot = new Robot();
                robot.delay(ACTIVATION_TIMEOUT);
                int[] location = new int[2];
                EventQueue.invokeAndWait(() -> {
                    Point button_location = btnFirst.getLocationOnScreen();
                    Dimension button_size = btnFirst.getSize();
                    location[0] = button_location.x + button_size.width / 2;
                    location[1] = button_location.y + button_size.height / 2;
                });
                robot.mouseMove(location[0], location[1]);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

                Object monitor = new Object();
                final MonitoredFocusListener monitorer = new MonitoredFocusListener(monitor);
                AtomicBoolean isFocused = new AtomicBoolean(false);
                synchronized (monitor) {
                    EventQueue.invokeAndWait(() -> {
                        btnThird.addFocusListener(monitorer);
                        isFocused.set(btnThird.isFocusOwner());
                    });

                    if (!isFocused.get()) {
                        monitor.wait(WAIT_TIMEOUT);
                        EventQueue.invokeAndWait(() -> {
                            isFocused.set(btnThird.isFocusOwner());
                        });
                    }
                }

                if (!isFocused.get()) {
                    throw new RuntimeException("TEST FAILED. The third button is not focus owner.");
                } else {
                    System.out.println("TEST PASSED.");
                }
            } catch (AWTException e) {
                e.printStackTrace();
                throw new RuntimeException("Some AWTException occurred.");
            } catch (InterruptedException e) {
                e.printStackTrace();
                throw new RuntimeException("Test was interrupted.");
            }
        } finally {
            if (frame != null) {
                EventQueue.invokeAndWait(frame::dispose);
            }
        }
    }

    public void actionPerformed(ActionEvent e) {
        if (btnSecond.isVisible()) {
            btnFirst.setEnabled(false);
            frame.remove(btnSecond);
            frame.add(btnThird, BorderLayout.CENTER);
            btnThird.requestFocusInWindow();
            btnFirst.setEnabled(true);
            frame.validate();
            frame.repaint();
        }
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        FocusForRemovedComponentTest test = new FocusForRemovedComponentTest();
        test.start();
    }
}

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
