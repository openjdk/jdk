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
  @bug 4504665
  @summary MerlinBeta2 - vetoing a focus change causes infinite loop
  @key headful
  @run main RestoreFocusInfiniteLoopTest
*/

import java.awt.AWTException;
import java.awt.Button;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Robot;
import java.awt.TextArea;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;

public class RestoreFocusInfiniteLoopTest {
    static final int TEST_TIMEOUT = 1000;
    static final int DELAY = 100;
    static Button b1;
    static Frame frame;
    static Object b1Monitor;
    static Point origin;
    static Dimension dim;
    static MonitoredFocusListener monitorer;

    public static void main(String[] args) throws Exception {
        try {
            EventQueue.invokeAndWait(() -> {

                b1Monitor = new Object();
                frame = new Frame();
                b1 = new Button("1");
                Button b2 = new Button("2");
                b1.setName("b1");
                b2.setName("b2");

                frame.setLayout(new FlowLayout());
                frame.add(b1);
                frame.add(b2);
                frame.pack();
                frame.setSize(100, 100);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
                FocusVetoableChangeListener vetoer = new FocusVetoableChangeListener(b2);
                KeyboardFocusManager.getCurrentKeyboardFocusManager().
                    addVetoableChangeListener("focusOwner", vetoer);

            });
            Robot robot = new Robot();
            robot.setAutoDelay(DELAY);
            robot.setAutoWaitForIdle(true);
            robot.delay(1000);
            EventQueue.invokeAndWait(() -> {
                monitorer = new MonitoredFocusListener(b1Monitor);
                b1.addFocusListener(monitorer);
                origin = b1.getLocationOnScreen();
                dim = b1.getSize();
            });
            robot.mouseMove((int)origin.getX() + (int)dim.getWidth()/2,
                            (int)origin.getY() + (int)dim.getHeight()/2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            if (!b1.isFocusOwner()) {
                synchronized (b1Monitor) {
                    b1Monitor.wait(TEST_TIMEOUT);
                }
            }

            monitorer.resetFocusLost();
            robot.keyPress(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_TAB);

            if (!monitorer.isFocusLostReceived() || !b1.isFocusOwner()) {
               synchronized (b1Monitor) {
                    b1Monitor.wait(TEST_TIMEOUT);
                }
            }
            if (!b1.isFocusOwner()) {
                throw new RuntimeException("Test is FAILED");
            } else {
                System.out.println("Test is PASSED");
            }
        } finally {
            if (frame != null) {
                frame.dispose();
            }
        }
    }

 }// class RestoreFocusInfiniteLoopTest

class FocusVetoableChangeListener implements VetoableChangeListener {
    Component vetoedComponent;
    public FocusVetoableChangeListener(Component vetoedComponent) {
        this.vetoedComponent = vetoedComponent;
    }
    public void vetoableChange(PropertyChangeEvent evt)
        throws PropertyVetoException
    {
        Component oldComp = (Component)evt.getOldValue();
        Component newComp = (Component)evt.getNewValue();

        boolean vetoFocusChange = (newComp == vetoedComponent);
        process(evt.getPropertyName(), oldComp, newComp);

        if (vetoFocusChange) {
            throw new PropertyVetoException("message", evt);
        }
    }
    boolean process(String propName, Component o1, Component o2) {
        System.out.println(propName +
                           " old=" + (o1 != null ? o1.getName() : "null") +
                           " new=" + (o2 != null ? o2.getName() : "null"));
            return true;
        }
    }

class MonitoredFocusListener extends FocusAdapter {
    Object monitor;
    boolean focuslost = false;

    public void resetFocusLost() {
        focuslost = false;
    }
    public boolean isFocusLostReceived() {
        return focuslost;
    }
    public MonitoredFocusListener(Object monitor) {
        this.monitor = monitor;
    }

    public void focusLost(FocusEvent fe) {
        System.out.println(fe.toString());
        focuslost = true;
    }
    public void focusGained(FocusEvent fe) {
        System.out.println(fe.toString());
        synchronized (monitor) {
            monitor.notify();
        }
    }
}
