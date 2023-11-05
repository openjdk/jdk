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
  @bug 4525962
  @summary Opposite component calculated inaccurately
  @key headful
  @run main ClearMostRecentFocusOwnerTest
*/

import java.awt.AWTEvent;
import java.awt.AWTException;
import java.awt.Button;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;

import java.awt.event.AWTEventListener;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClearMostRecentFocusOwnerTest implements AWTEventListener {
    final static int ROBOT_DELAY = 50;
    volatile Frame firstFrame;
    volatile Frame secondFrame;
    volatile Button actionButton;
    volatile Button btnToRemove;
    volatile Button btnToHide;
    volatile Button btnToDisable;
    volatile Button btnToNonFocusable;
    volatile Panel pnlToHide;
    volatile Button btnInPanel;

    Robot robot;

    volatile Component opposite = null;
    volatile Component focusOwner = null;
    volatile Object monitor = null;

    public void init() throws InterruptedException,
            InvocationTargetException {
        try {
            robot = new Robot();
        } catch (AWTException e) {
            throw new RuntimeException("Can not create awt-robot.");
        }
        EventQueue.invokeAndWait(() -> {
            firstFrame = new Frame("The First Frame");
            firstFrame.setName("\"1st Frame\"");
            secondFrame = new Frame("The Second Frame");
            secondFrame.setName("\"2nd Frame\"");
            pnlToHide = new Panel();
            pnlToHide.setName("Panel");
            actionButton = new Button("Action Button");
            actionButton.setName("\"" + actionButton.getLabel() + "\"");
            btnToRemove = new Button("To Remove");
            btnToRemove.setName("\"" + btnToRemove.getLabel() + "\"");
            btnToHide = new Button("ToHide");
            btnToHide.setName("\"" + btnToHide.getLabel() + "\"");
            btnToDisable = new Button("To Disable");
            btnToDisable.setName("\"" + btnToDisable.getLabel() + "\"");
            btnToNonFocusable = new Button("To setFocusable(false)");
            btnToNonFocusable.setName("\"" + btnToNonFocusable.getLabel() + "\"");
            btnInPanel = new Button("Int Panel");
            btnInPanel.setName("\"" + btnInPanel.getLabel() + "\"");

            firstFrame.add(actionButton);

            secondFrame.setLayout(new FlowLayout());
            secondFrame.add(btnToRemove);
            secondFrame.add(btnToHide);
            secondFrame.add(btnToDisable);
            secondFrame.add(btnToNonFocusable);
            secondFrame.add(pnlToHide);
            pnlToHide.add(btnInPanel);

            firstFrame.pack();
            firstFrame.setVisible(true);
            secondFrame.pack();
            secondFrame.setLocation(0, firstFrame.getHeight() + 50);
            secondFrame.setVisible(true);
        });
    }

    public void start() throws InterruptedException, InvocationTargetException {
        try {
            Toolkit.getDefaultToolkit().
                    addAWTEventListener(this,
                            AWTEvent.FOCUS_EVENT_MASK);

            makeFocusOwner(btnToRemove);
            EventQueue.invokeAndWait(() -> {
                secondFrame.setVisible(false);
                secondFrame.remove(btnToRemove);
            });
            makeFocusOwner(actionButton);
            opposite = null;
            EventQueue.invokeAndWait(() -> {
                secondFrame.setVisible(true);
            });
            makeActiveFrame(secondFrame);
            if (opposite != btnToHide) {
                System.out.println("opposite = " + opposite);
                throw new RuntimeException("Test FAILED: wrong opposite after Component.remove().");
            }

            makeFocusOwner(btnToHide);
            EventQueue.invokeAndWait(() -> {
                secondFrame.setVisible(false);
                btnToHide.setVisible(false);
            });
            makeFocusOwner(actionButton);
            opposite = null;
            EventQueue.invokeAndWait(() -> {
                secondFrame.setVisible(true);
            });
            makeActiveFrame(secondFrame);
            if (opposite != btnToDisable) {
                System.out.println("opposite = " + opposite);
                throw new RuntimeException("Test FAILED: wrong opposite after Component.setVisible(false).");
            }

            makeFocusOwner(btnToDisable);
            EventQueue.invokeAndWait(() -> {
                secondFrame.setVisible(false);
                btnToDisable.setEnabled(false);
            });
            makeFocusOwner(actionButton);
            opposite = null;
            EventQueue.invokeAndWait(() -> {
                secondFrame.setVisible(true);
            });
            makeActiveFrame(secondFrame);
            if (opposite != btnToNonFocusable) {
                System.out.println("opposite = " + opposite);
                throw new RuntimeException("Test FAILED: wrong opposite after Component.rsetEnabled(false).");
            }

            makeFocusOwner(btnToNonFocusable);
            EventQueue.invokeAndWait(() -> {
                secondFrame.setVisible(false);
                btnToNonFocusable.setFocusable(false);
            });
            makeFocusOwner(actionButton);
            opposite = null;
            EventQueue.invokeAndWait(() -> {
                secondFrame.setVisible(true);
            });
            makeActiveFrame(secondFrame);
            if (opposite != btnInPanel) {
                System.out.println("opposite = " + opposite);
                throw new RuntimeException("Test FAILED: wrong opposite after Component.setFocusable(false).");
            }

            makeFocusOwner(btnInPanel);
            EventQueue.invokeAndWait(() -> {
                secondFrame.setVisible(false);
                pnlToHide.setVisible(false);
            });
            makeFocusOwner(actionButton);
            opposite = null;
            EventQueue.invokeAndWait(() -> {
                secondFrame.setVisible(true);
            });
            makeActiveFrame(secondFrame);
            if (opposite == btnInPanel) {
                System.out.println("opposite = " + opposite);
                throw new RuntimeException("Test FAILED: wrong opposite after Container.setVisible(false).");
            }
        } finally {
            if (firstFrame != null) {
                EventQueue.invokeAndWait(firstFrame::dispose);
            }
            if (secondFrame != null) {
                EventQueue.invokeAndWait(secondFrame::dispose);
            }

        }
    }

    public void eventDispatched(AWTEvent event) {
        switch (event.getID()) {
            case FocusEvent.FOCUS_GAINED:
                if (focusOwner == ((FocusEvent) event).getComponent()
                        && monitor != null) {
                    synchronized (monitor) {
                        monitor.notify();
                    }
                }
                break;
            case FocusEvent.FOCUS_LOST:
                opposite = ((FocusEvent) event).getOppositeComponent();
                break;
        }
        System.out.println(event);
    }

    void clickOnComponent(Component comp) throws InterruptedException,
            InvocationTargetException {
        System.err.println("clickOnComopnent " + comp.getName());
        robot.delay(3000);
        int[] point = new int[2];
        EventQueue.invokeAndWait(() -> {
            Point origin = comp.getLocationOnScreen();
            Dimension dim = comp.getSize();
            point[0] = origin.x + (int) dim.getWidth() / 2;
            point[1] = origin.y + (int) dim.getHeight() / 2;
        });
        robot.mouseMove(point[0], point[1]);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(ROBOT_DELAY);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    void makeFocusOwner(Component comp) throws InterruptedException,
            InvocationTargetException {
        AtomicBoolean isOwner = new AtomicBoolean(false);
        EventQueue.invokeAndWait(() -> {
            isOwner.set(comp.isFocusOwner());
        });
        if (!isOwner.get()) {
            clickOnComponent(comp);
            try {
                EventQueue.invokeAndWait(() -> {
                    isOwner.set(comp.isFocusOwner());
                });
                if (!isOwner.get()) {
                    monitor = new Object();
                    focusOwner = comp;
                    synchronized (monitor) {
                        monitor.wait(3000);
                    }
                }
            } catch (InterruptedException ie) {
                throw new RuntimeException("Test was interrupted.");
            }
        }
        EventQueue.invokeAndWait(() -> {
            isOwner.set(comp.isFocusOwner());
        });
        if (!isOwner.get()) {
            throw new RuntimeException("Test can not make "
                    + comp.getName() + " a focus owner.");
        }
    }

    void makeActiveFrame(Frame frame) throws InvocationTargetException,
            InterruptedException {
        robot.delay(3000);
        if (!frame.isActive()) {
            System.err.println("frame is not active");
            int[] point = new int[2];
            EventQueue.invokeAndWait(() -> {
                Point origin = frame.getLocationOnScreen();
                Insets ins = frame.getInsets();
                point[0] = origin.x + frame.getWidth() / 2;
                point[1] = origin.y + ins.top / 2;
            });
            robot.mouseMove(point[0], point[1]);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(ROBOT_DELAY);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        }
        robot.delay(3000);
        EventQueue.invokeAndWait(() -> {
            if (!frame.isActive()) {
                throw new RuntimeException("Test can not activate " + frame.getName() + ".");
            }
        });
    }

    public static void main(String[] args) throws InterruptedException, InvocationTargetException {
        ClearMostRecentFocusOwnerTest test = new ClearMostRecentFocusOwnerTest();
        test.init();
        test.start();
    }
}
