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
  @bug 4943277
  @requires (os.family == "linux")
  @summary XAWT: Scrollbar can't be controlled by keyboard
  @key headful
*/

import java.awt.AWTException;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Robot;
import java.awt.Scrollbar;
import java.awt.Toolkit;

import java.awt.event.AdjustmentListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class ScrollbarKeyControlTest implements AdjustmentListener, KeyListener {
    Scrollbar scrollbarV;
    Scrollbar scrollbarH;
    volatile int changesTotal = 0;
    Robot robot;
    Object LOCK = new Object();
    Frame frame;

    public static void main(String[] args) throws Exception {
        if (!System.getProperty("os.name").startsWith("Linux")) {
            System.out.println("This test is for XAWT only.");
            return;
        }
        ScrollbarKeyControlTest scrollbarKeyControlTest = new ScrollbarKeyControlTest();
        scrollbarKeyControlTest.init();
    }

    public void init() throws Exception {
        try {
            EventQueue.invokeAndWait(() -> {
                frame = new Frame("Scrollbar Test");

                scrollbarV = new Scrollbar(Scrollbar.VERTICAL, 0, 1, 0, 255);
                scrollbarH = new Scrollbar(Scrollbar.HORIZONTAL, 0, 60, 0, 300);

                scrollbarH.addAdjustmentListener(this);
                scrollbarH.addKeyListener(this);
                scrollbarV.addAdjustmentListener(this);
                scrollbarV.addKeyListener(this);

                frame.add("South", scrollbarH);
                frame.add("East", scrollbarV);

                frame.setSize(200, 200);
                frame.setVisible(true);
                frame.validate();
                frame.toFront();
            });
            robot = new Robot();
            robot.delay(1000);
            robot.waitForIdle();

            testOneScrollbar(scrollbarV);
            if (changesTotal != 9) { //one by mouse click and six by keys
                throw new RuntimeException("Test failed.  Not all adjustment " +
                        "events received by vertical scrollbar (" + changesTotal + " of 9)");
            }
            changesTotal = 0;
            testOneScrollbar(scrollbarH);
            if (changesTotal != 9) { //one by mouse click and six by keys
                throw new RuntimeException("Test failed.  Not all adjustment " +
                        "events received by horizontal scrollbar (" + changesTotal + " of 9)");
            }
            System.out.println("Test passed. Adjustment Event called  "
                    + changesTotal + " times for each scrollbar");

        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    public void testOneScrollbar(Scrollbar sb) {
        robot.waitForIdle();
        robot.mouseMove(sb.getLocationOnScreen().x + sb.getWidth() / 2,
                sb.getLocationOnScreen().y + sb.getHeight() / 2);
        try {
            synchronized (LOCK) {
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                LOCK.wait(2000);
                robot.keyPress(KeyEvent.VK_DOWN);
                robot.keyRelease(KeyEvent.VK_DOWN);
                LOCK.wait(2000);
                robot.keyPress(KeyEvent.VK_PAGE_DOWN);
                robot.keyRelease(KeyEvent.VK_PAGE_DOWN);
                LOCK.wait(2000);
                robot.keyPress(KeyEvent.VK_UP);
                robot.keyRelease(KeyEvent.VK_UP);
                LOCK.wait(2000);
                robot.keyPress(KeyEvent.VK_PAGE_UP);
                robot.keyRelease(KeyEvent.VK_PAGE_UP);
                LOCK.wait(2000);
                robot.keyPress(KeyEvent.VK_RIGHT);
                robot.keyRelease(KeyEvent.VK_RIGHT);
                LOCK.wait(2000);
                robot.keyPress(KeyEvent.VK_LEFT);
                robot.keyRelease(KeyEvent.VK_LEFT);
                LOCK.wait(2000);
                robot.keyPress(KeyEvent.VK_HOME);
                robot.keyRelease(KeyEvent.VK_HOME);
                LOCK.wait(2000);
                robot.keyPress(KeyEvent.VK_END);
                robot.keyRelease(KeyEvent.VK_END);
                LOCK.wait(2000);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Test interrupted while keys being pressed.", e);
        }
    }

    public void adjustmentValueChanged(AdjustmentEvent e) {
        changesTotal++;
        synchronized (LOCK) {
            LOCK.notify();
        }
        System.out.println("Adjustment Event called ");
    }

    public void keyPressed(KeyEvent e) {
        System.out.println("KeyPressed called");
    }

    public void keyReleased(KeyEvent e) {
        System.out.println("in keyReleased");
    }

    public void keyTyped(KeyEvent e) {
        System.out.println("in keyTyped");
    }
}
