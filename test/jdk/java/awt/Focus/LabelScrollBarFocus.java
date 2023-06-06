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
  @bug 4027897
  @summary Test that Label can't be made focused by the mouse, while ScrollBar should become focused.
  @key headful
  @run main LabelScrollBarFocus
*/

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Scrollbar;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;

public class LabelScrollBarFocus extends Panel {
    static final Semaphore sema = new Semaphore();
    Label lab;
    Scrollbar scr;
    static Frame frame;

    public void init() {
        this.setLayout(new FlowLayout());
        FocusAdapter fa = new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                sema.raise();
            }
        };

        lab = new Label("Label");
        scr = new Scrollbar(Scrollbar.HORIZONTAL);
        lab.addFocusListener(fa);
        scr.addFocusListener(fa);
        add(lab);
        add(scr);
        setSize(200, 200);
        validate();
        setVisible(true);
    }

    public void start() throws InterruptedException,
            InvocationTargetException {
        Robot robot = null;
        try {
            robot = new Robot();
        } catch (Exception e) {
            throw new RuntimeException("Can't create robot instance");
        }
        int[] point = new int[2];
        EventQueue.invokeAndWait(() -> {
            Point labLoc = lab.getLocationOnScreen();
            point[0] = labLoc.x + 5;
            point[1] = labLoc.y + 5;
        });
        robot.mouseMove(point[0], point[1]);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(1000);
        robot.waitForIdle();
        try {
            sema.doWait(2000);
        } catch (InterruptedException ie) {
            throw new RuntimeException("Interrupted");
        }

        AtomicBoolean isFocusOwner = new AtomicBoolean(false);
        EventQueue.invokeAndWait(() -> {
            isFocusOwner.set(lab.isFocusOwner());
        });
        if (isFocusOwner.get()) {
            throw new RuntimeException("Label is focused");
        }

        EventQueue.invokeAndWait(() -> {
            Point scrLoc = scr.getLocationOnScreen();
            point[0] = scrLoc.x + 20;
            point[1] = scrLoc.y + 5;
        });
        robot.mouseMove(point[0], point[1]);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(1000);
        robot.waitForIdle();
        try {
            sema.doWait(2000);
        } catch (InterruptedException ie) {
            throw new RuntimeException("Interrupted");
        }

        EventQueue.invokeAndWait(() -> {
            isFocusOwner.set(scr.isFocusOwner());
        });
        if (!isFocusOwner.get()) {
            throw new RuntimeException("Scroll bar is not focused");
        }
        System.out.println("Test passed");
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        try {
            LabelScrollBarFocus test = new LabelScrollBarFocus();
            EventQueue.invokeAndWait(() -> {
                frame = new Frame("LabelScrollBarFocus");
                test.init();
                frame.setLayout(new BorderLayout());
                frame.add(test, BorderLayout.CENTER);
                frame.setLocationRelativeTo(null);
                frame.pack();
                frame.setVisible(true);
            });
            test.start();
        } finally {
            if (frame != null) {
                EventQueue.invokeAndWait(frame::dispose);
            }
        }
    }
}

class Semaphore {
    boolean state = false;
    Object lock = new Object();
    int waiting = 0;

    public Semaphore() {
    }

    public void doWait(int timeout) throws InterruptedException {
        synchronized (lock) {
            waiting++;
            synchronized (this) {
                wait(timeout);
            }
            waiting--;
        }
    }

    public void raise() {
        synchronized (lock) {
            state = true;
            if (waiting > 0) {
                synchronized (this) {
                    notifyAll();
                }
            }
        }
    }
}
