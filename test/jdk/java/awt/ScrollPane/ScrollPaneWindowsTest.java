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

import java.util.Properties;

public class ScrollPaneWindowsTest implements AdjustmentListener {
    ScrollPane sp;
    Panel p;
    Robot robot;
    Frame frame;
    Insets paneInsets;
    public static Object LOCK = new Object();
    ScrollPaneAdjustable vScroll;
    ScrollPaneAdjustable hScroll;
    boolean notifyReceived = false;

    public static void main(String[] args) throws Exception {
        Properties prop = System.getProperties();
        String os = prop.getProperty("os.name", "").toUpperCase();
        System.out.println("OS= " + os);
        if (!os.equals("WINDOWS 2000") && !os.equals("WINDOWS 2003") &&
                !os.equals("WINDOWS XP")) {
            System.out.println("This test is for Windows 2000/2003/XP only.");
            return;
        }
        ScrollPaneWindowsTest scrollTest = new ScrollPaneWindowsTest();

        scrollTest.init();
        scrollTest.start();
    }

    public void init() throws Exception {
        EventQueue.invokeAndWait(() -> {
            frame = new Frame();
            frame.setLayout(new BorderLayout(1, 1));
            p = new Panel();
            p.setLayout(null);
            p.setSize(new Dimension(800, 800));
        });
        robot = new Robot();
        sp = new ScrollPane(ScrollPane.SCROLLBARS_ALWAYS);
        vScroll = (ScrollPaneAdjustable) sp.getVAdjustable();
        hScroll = (ScrollPaneAdjustable) sp.getHAdjustable();
        vScroll.addAdjustmentListener(this);
        hScroll.addAdjustmentListener(this);

    }

    public void start() throws Exception {
        EventQueue.invokeAndWait(() -> {
            sp.add(p);
            frame.add(sp);

            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            paneInsets = sp.getInsets();
            System.out.println("Insets: right = " + paneInsets.right + " bottom =  " + paneInsets.bottom);
        });
        robot.wait(100);
        robot.waitForIdle();

        robot.mouseMove(sp.getLocationOnScreen().x + sp.getWidth() - paneInsets.right / 2,
                sp.getLocationOnScreen().y + sp.getHeight() / 2);
        testOneScrollbar(vScroll);
        robot.mouseMove(sp.getLocationOnScreen().x + sp.getWidth() / 2,
                sp.getLocationOnScreen().y + sp.getHeight() - paneInsets.bottom / 2);
        testOneScrollbar(hScroll);

        System.out.println("Test passed. ");
    }

    public void testOneScrollbar(ScrollPaneAdjustable scroll) throws Exception{
        try {
            //to Bottom  - right
            robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
            robot.delay(2000);

            notifyReceived = false;
            synchronized (LOCK) {
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
                    System.out.println(" scroll.getValue() = " + scroll.getValue());
                    System.out.println(" scroll.getVisibleAmount() =  " + scroll.getVisibleAmount());
                    System.out.println(" scroll.getMaximum() = " + scroll.getMaximum());
                    throw new RuntimeException("Test Failed. Position of scrollbar is incorrect.");
                } else {
                    System.out.println("Test stage 1 passed.");
                }
            }

            //top-left
            notifyReceived = false;
            robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
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
        } catch (InterruptedException e) {
            throw new RuntimeException("Test interrupted while keys being pressed.", e);
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    public void adjustmentValueChanged(AdjustmentEvent e) {
        notifyReceived = true;
        synchronized (ScrollPaneWindowsTest.LOCK) {
            ScrollPaneWindowsTest.LOCK.notify();
        }
        System.out.println("Adjustment Event called ");
    }
}
