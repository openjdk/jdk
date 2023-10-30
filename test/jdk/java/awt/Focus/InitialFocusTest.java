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
  @bug 4150021
  @summary if user requests focus on some component, it must become a focus owner after activation
  @key headful
  @run main InitialFocusTest
*/

import java.awt.AWTException;
import java.awt.Button;
import java.awt.DefaultKeyboardFocusManager;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;

public class InitialFocusTest implements PropertyChangeListener {
    //Declare things used in the test, like buttons and labels here
    final static String FOCUSED_WINDOW_PROP = "focusedWindow";
    final static int ACTION_TIMEOUT = 2000;

    volatile Frame frame;
    volatile Button btn1;
    volatile Button btn2;

    public void start() throws InterruptedException, InvocationTargetException {
        DefaultKeyboardFocusManager.getCurrentKeyboardFocusManager().
                addPropertyChangeListener(FOCUSED_WINDOW_PROP, this);
        try {
            EventQueue.invokeAndWait(() -> {
                frame = new Frame("InitialFocusTest");
                frame.setLayout(new FlowLayout());
                btn1 = new Button("First Button");
                frame.add(btn1);
                btn2 = new Button("Second Button");
                frame.add(btn2);
                frame.setLocationRelativeTo(null);
                frame.pack();
                frame.setVisible(true);
            });
            try {
                Robot robot = new Robot();
                robot.delay(ACTION_TIMEOUT);
                if (!activateFrame(frame, robot, ACTION_TIMEOUT)) {
                    throw new RuntimeException("Frame was not activated.");
                }
                robot.delay(ACTION_TIMEOUT);
                AtomicBoolean isFocused = new AtomicBoolean(false);
                EventQueue.invokeAndWait(() -> {
                    isFocused.set(frame.isFocused());
                });
                if (!isFocused.get()) {
                    throw new RuntimeException("Frame didn't become focused.");
                }
                EventQueue.invokeAndWait(() -> {
                    isFocused.set(btn2.isFocusOwner());
                });
                if (!isFocused.get()) {
                    throw new RuntimeException("Btn2 didn't receive focus.");
                }
            } catch (AWTException e) {
                e.printStackTrace();
            }
            System.out.printf("Test passed.");
        } finally {
            if (frame != null) {
                EventQueue.invokeAndWait(frame::dispose);
            }
        }
    }

    public void propertyChange(PropertyChangeEvent pce) {
        if (FOCUSED_WINDOW_PROP.equals(pce.getPropertyName())) {
            if (pce.getNewValue() == frame) {
                System.out.println("requesting focus on btn2");
                btn2.requestFocusInWindow();
            }
        }
    }

    boolean activateFrame(Frame frame, Robot robot, int timeout)
            throws InterruptedException, InvocationTargetException {
        AtomicBoolean isActive = new AtomicBoolean(false);
        EventQueue.invokeAndWait(() -> {
            isActive.set(frame.isActive());
        });
        if (!isActive.get()) {
            int[] point = new int[2];
            EventQueue.invokeAndWait(() -> {
                Point origin = frame.getLocationOnScreen();
                Dimension dim = frame.getSize();
                Insets insets = frame.getInsets();
                point[0] = origin.x + dim.width / 2;
                point[1] = origin.y + insets.top / 2;
            });
            robot.mouseMove(point[0], point[1]);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(timeout);
            EventQueue.invokeAndWait(() -> {
                isActive.set(frame.isActive());
            });
        }
        return frame.isActive();
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        InitialFocusTest test = new InitialFocusTest();
        test.start();
    }
}
