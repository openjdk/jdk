/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 6190768
  @summary Tests that pressing pg-up / pg-down on AWT list doesn't selects the items, on XToolkit
  @key headful
  @run main PageUPSelectsItemsTest
*/

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.Label;
import java.awt.List;
import java.awt.Point;
import java.awt.Robot;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import java.lang.reflect.InvocationTargetException;

public class PageUPSelectsItemsTest implements FocusListener, KeyListener {

    List list = new List(6, true);
    Label label = new Label("for focus");

    Frame frame;

    final Object LOCK = new Object();
    final int ACTION_TIMEOUT = 500;

    public static void main(String[] args) throws Exception {
        PageUPSelectsItemsTest test = new PageUPSelectsItemsTest();
        test.start();
    }

    public void start() throws Exception {
        try {
            EventQueue.invokeAndWait(() -> {
                list.add("0");
                list.add("1");
                list.add("2");
                list.add("3");
                list.add("4");
                list.add("5");
                list.add("6");
                list.add("7");
                list.add("8");
                list.add("9");
                list.add("10");
                list.add("11");
                list.add("12");

                list.select(8);

                list.addFocusListener(this);
                list.addKeyListener(this);
                frame = new Frame("PageUPSelectsItemsTest");
                frame.setLayout(new BorderLayout());
                frame.add(BorderLayout.SOUTH, list);
                frame.add(BorderLayout.CENTER, label);
                frame.setSize(300, 200);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });
            test();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private void test() throws Exception {
        synchronized (LOCK) {

            Robot r = new Robot();
            r.delay(500);

            Point loc = label.getLocationOnScreen();
            r.mouseMove(loc.x + (int) (label.getWidth() / 2), loc.y + (int) (label.getHeight() / 2));
            r.mousePress(InputEvent.BUTTON1_MASK);
            r.delay(10);
            r.mouseRelease(InputEvent.BUTTON1_MASK);
            r.delay(500);

            list.requestFocusInWindow();
            LOCK.wait(ACTION_TIMEOUT);
            if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() != list) {
                throw new RuntimeException("Test failed - list isn't focus owner.");
            }

            r.delay(10);
            loc = list.getLocationOnScreen();

            r.delay(50);
            r.keyPress(KeyEvent.VK_PAGE_UP);
            r.delay(50);
            r.keyRelease(KeyEvent.VK_PAGE_UP);
            r.delay(50);

            r.keyPress(KeyEvent.VK_PAGE_DOWN);
            r.delay(50);
            r.keyRelease(KeyEvent.VK_PAGE_DOWN);
            r.delay(50);

            r.waitForIdle();
            EventQueue.invokeAndWait(new Runnable() {
                public void run() {
                    System.out.println("Dummy block");
                }
            });

            System.err.println("Selected objects: " + list.getSelectedItems().length);

            if (list.getSelectedItems().length > 1) {
                throw new RuntimeException("Test failed");
            }
        }
    }

    public void focusGained(FocusEvent e) {

        synchronized (LOCK) {
            LOCK.notifyAll();
        }

    }

    public void focusLost(FocusEvent e) {
    }

    public void keyPressed(KeyEvent e){
        System.out.println("keyPressed-"+e);
    }

    public void keyReleased(KeyEvent e){
        System.out.println("keyReleased-"+e);
    }

    public void keyTyped(KeyEvent e){
        System.out.println("keyTyped-"+e);
    }
}
