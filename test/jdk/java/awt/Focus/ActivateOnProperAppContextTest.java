/*
 * Copyright (c) 2006, 2024, Oracle and/or its affiliates. All rights reserved.
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
* @bug 6385277
* @key headful
* @summary   Tests that activation happens on correct AppContext.
* @modules java.desktop/sun.awt
* @run main ActivateOnProperAppContextTest
*/

import sun.awt.AppContext;
import sun.awt.SunToolkit;

import java.awt.Button;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.util.concurrent.atomic.AtomicBoolean;

public class ActivateOnProperAppContextTest {
    static Robot robot;
    SunToolkit toolkit;

    ThreadGroup threadGroup = new ThreadGroup("Test_Thread_Group");
    AppContext appContext;
    Frame frame;
    volatile boolean passed = true;
    AtomicBoolean cond = new AtomicBoolean(false);

    public static void main(String[] args) throws Exception {
        ActivateOnProperAppContextTest app = new ActivateOnProperAppContextTest();
        robot = new Robot();
        app.start();
    }

    public void start() {
        toolkit = (SunToolkit)Toolkit.getDefaultToolkit();

        Runnable runnable = new Runnable() {
                public void run() {
                    test();

                    synchronized (cond) {
                        cond.set(true);
                        cond.notifyAll();
                    }
                }
            };

        Thread thread = new Thread(threadGroup, runnable, "Test Thread");

        synchronized (cond) {

            thread.start();

            while (!cond.get()) {
                try {
                    cond.wait();
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        }

        if (passed) {
            System.out.println("Test passed.");
        } else {
            throw new TestFailedException("Test failed!");
        }
    }

    void test() {
        appContext = SunToolkit.createNewAppContext();
        System.out.println("Created new AppContext: " + appContext);

        frame = new Frame("ActivateOnProperAppContextTest Frame") {
                public boolean isActive() {
                    verifyAppContext("Frame.isActive()");
                    return super.isActive();
                }
                public boolean isFocused() {
                    verifyAppContext("Frame.isFocused()");
                    return super.isFocused();
                }
                public boolean isFocusable() {
                    verifyAppContext("Frame.isFocusable()");
                    return super.isFocusable();
                }
                public Window getOwner() {
                    verifyAppContext("Frame.getOwner()");
                    return super.getOwner();
                }
                public boolean isEnabled() {
                    verifyAppContext("Frame.isEnabled()");
                    return super.isEnabled();
                }
                public boolean isVisible() {
                    verifyAppContext("Frame.isVisible()");
                    return super.isVisible();
                }
                public Container getParent() {
                    verifyAppContext("Frame.getParent()");
                    return super.getParent();
                }
                public Cursor getCursor() {
                    verifyAppContext("Frame.getCursor()");
                    return super.getCursor();
                }
                public Point getLocation() {
                    verifyAppContext("Frame.getLocation()");
                    return super.getLocation();
                }
                public Point getLocationOnScreen() {
                    verifyAppContext("Frame.getLocationOnScreen()");
                    return super.getLocationOnScreen();
                }
            };
        Window window = new Window(frame) {
                public boolean isFocused() {
                    verifyAppContext("Window.isFocused()");
                    return super.isFocused();
                }
                public boolean isFocusable() {
                    verifyAppContext("Window.isFocusable()");
                    return super.isFocusable();
                }
                public Window getOwner() {
                    verifyAppContext("Window.getOwner()");
                    return super.getOwner();
                }
                public boolean isEnabled() {
                    verifyAppContext("Window.isEnabled()");
                    return super.isEnabled();
                }
                public boolean isVisible() {
                    verifyAppContext("Window.isVisible()");
                    return super.isVisible();
                }
                public Container getParent() {
                    verifyAppContext("Window.getParent()");
                    return super.getParent();
                }
                public Cursor getCursor() {
                    verifyAppContext("Window.getCursor()");
                    return super.getCursor();
                }
                public Point getLocation() {
                    verifyAppContext("Window.getLocation()");
                    return super.getLocation();
                }
                public Point getLocationOnScreen() {
                    verifyAppContext("Window.getLocationOnScreen()");
                    return super.getLocationOnScreen();
                }
            };
        Button button = new Button("button");
        Label label = new Label("label");

        window.setLayout(new FlowLayout());
        window.add(button);
        window.add(label);
        window.setLocation(800, 0);
        window.pack();
        window.setVisible(true);

        frame.setBounds(800, 100, 100, 50);
        frame.setVisible(true);

        toolkit.realSync();

        /*
         * When the label is clicked in the window some of
         * the owner's public method get called.
         */
        clickOn(label);
    }

    void verifyAppContext(String methodName) {
        AppContext ac = AppContext.getAppContext();
        println(methodName + " called on AppContext: " + ac);

        if (ac != appContext) {
            passed = false;
            System.err.println("Test failed: " + methodName + " is called on wrong AppContext!");
            Thread.dumpStack();
        }
    }

    void clickOn(Component c) {
        Point p = c.getLocationOnScreen();
        Dimension d = c.getSize();

        robot.mouseMove(p.x + (int)(d.getWidth()/2), p.y + (int)(d.getHeight()/2));

        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(20);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        toolkit.realSync();
    }

    void println(final String msg) {
        SunToolkit.executeOnEventHandlerThread(frame, new Runnable() {
                public void run() {
                    System.out.println(msg);
                }
            });
    }
}

class TestFailedException extends RuntimeException {
    TestFailedException(String msg) {
        super(msg);
    }
}
