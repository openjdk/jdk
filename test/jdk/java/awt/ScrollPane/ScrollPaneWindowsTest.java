/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4452612
  @requires os.family=="windows"
  @summary The popup menu of the scroll bar doesn't work properly in Window2000.
  @key headful
*/

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.Panel;
import java.awt.Robot;
import java.awt.ScrollPane;
import java.awt.ScrollPaneAdjustable;

import java.awt.event.AdjustmentListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class ScrollPaneWindowsTest implements AdjustmentListener {
    ScrollPane sp;
    Panel p;
    Robot robot;
    Frame frame;
    Insets paneInsets;
    public static final Object LOCK = new Object();
    ScrollPaneAdjustable vScroll;
    ScrollPaneAdjustable hScroll;
    boolean notifyReceived = false;
    volatile int xPos = 0;
    volatile int yPos = 0;

    public static void main(String[] args) throws Exception {
        ScrollPaneWindowsTest scrollTest = new ScrollPaneWindowsTest();
        scrollTest.init();
        scrollTest.start();
    }

    public void init() throws Exception {
        EventQueue.invokeAndWait(() -> {
            frame = new Frame("ScrollPaneWindowsTest");
            frame.setLayout(new BorderLayout(1, 1));
            p = new Panel();
            p.setLayout(null);
            p.setSize(new Dimension(800, 800));
            sp = new ScrollPane(ScrollPane.SCROLLBARS_ALWAYS);
            vScroll = (ScrollPaneAdjustable) sp.getVAdjustable();
            hScroll = (ScrollPaneAdjustable) sp.getHAdjustable();
            vScroll.addAdjustmentListener(ScrollPaneWindowsTest.this);
            hScroll.addAdjustmentListener(ScrollPaneWindowsTest.this);
            sp.add(p);
            frame.add(sp);
            frame.pack();
            frame.setSize(400, 400);
            frame.setLocationRelativeTo(null);
            frame.setAlwaysOnTop(true);
            frame.setVisible(true);
        });
    }

    public void start() throws Exception {
        try {
            EventQueue.invokeAndWait(() -> {
                paneInsets = sp.getInsets();
                System.out.println("Insets: right = " + paneInsets.right + " bottom =  " + paneInsets.bottom);
            });

            robot = new Robot();
            robot.waitForIdle();
            robot.delay(100);

            EventQueue.invokeAndWait(() -> {
                xPos = sp.getLocationOnScreen().x + sp.getWidth() - paneInsets.right / 2;
                yPos = sp.getLocationOnScreen().y + sp.getHeight() / 2;
            });

            robot.mouseMove(xPos, yPos);
            testOneScrollbar(vScroll);

            robot.waitForIdle();
            robot.delay(100);

            EventQueue.invokeAndWait(() -> {
                xPos = sp.getLocationOnScreen().x + sp.getWidth() / 2;
                yPos = sp.getLocationOnScreen().y + sp.getHeight() - paneInsets.bottom / 2;
            });

            robot.mouseMove(xPos, yPos);
            testOneScrollbar(hScroll);
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
        System.out.println("Test passed. ");
    }

    public void testOneScrollbar(ScrollPaneAdjustable scroll) throws Exception {
        //to Bottom  - right
        robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
        robot.waitForIdle();
        robot.delay(2000);

        synchronized (LOCK) {
            notifyReceived = false;
            for (int i = 0; i < 3; i++) {
                robot.keyPress(KeyEvent.VK_DOWN);
                robot.keyRelease(KeyEvent.VK_DOWN);
            }
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);
            if (!notifyReceived) {
                System.out.println("we are waiting 1");
                LOCK.wait(2000);
            }
            if (scroll.getValue() + scroll.getVisibleAmount() != scroll.getMaximum()) {
                System.out.println("scroll.getValue() = " + scroll.getValue());
                System.out.println("scroll.getVisibleAmount() = " + scroll.getVisibleAmount());
                System.out.println("scroll.getMaximum() = " + scroll.getMaximum());
                throw new RuntimeException("Test Failed. Position of scrollbar is incorrect.");
            } else {
                System.out.println("Test stage 1 passed.");
            }
            notifyReceived = false;
        }

        //to top-left
        robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
        robot.waitForIdle();
        robot.delay(2000);

        synchronized (LOCK) {
            for (int i = 0; i < 2; i++) {
                robot.keyPress(KeyEvent.VK_DOWN);
                robot.keyRelease(KeyEvent.VK_DOWN);
            }
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);
            if (!notifyReceived) {
                System.out.println("we are waiting 2");
                LOCK.wait(2000);
            }
            if (scroll.getValue() != 0) {
                System.out.println("scroll.getValue() = " + scroll.getValue());
                throw new RuntimeException("Test Failed. Position of scrollbar is incorrect.");
            } else {
                System.out.println("Test stage 2 passed.");
            }
        }
    }

    @Override
    public void adjustmentValueChanged(AdjustmentEvent adjustmentEvent) {
        synchronized (ScrollPaneWindowsTest.LOCK) {
            notifyReceived = true;
            ScrollPaneWindowsTest.LOCK.notify();
        }
        System.out.println("Adjustment Event called ");
    }
}
