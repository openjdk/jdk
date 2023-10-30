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
  @bug 4902933
  @summary Test that dragging off an unfurled Choice prevents selecting a new item
  @key headful
*/

import java.awt.Choice;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

public class DragOffNoSelectTest implements WindowListener, Runnable {

    static volatile DragOffNoSelectTest testInstance;
    static volatile Frame frame;
    static volatile Choice theChoice;
    static volatile Robot robot;
    static final String firstItem = new String("First Choice Item");

    static volatile Object lock = new Object();

    public static void main(String[] args) throws Exception {
        testInstance = new DragOffNoSelectTest();
        robot = new Robot();
        robot.setAutoDelay(500);
        try {
            EventQueue.invokeAndWait(() -> createUI());
            runTest();
        } finally {
            if (frame != null) {
               EventQueue.invokeAndWait(() -> frame.dispose());
            }
        }
    }

    static void createUI() {
        frame = new Frame("DragOffNoSelectTest");
        theChoice = new Choice();
        theChoice.add(firstItem);
        for (int i = 0; i < 10; i++) {
            theChoice.add(new String("Choice Item " + i));
        }
        frame.add(theChoice);
        frame.addWindowListener(testInstance);
        frame.pack();
        frame.setLocationRelativeTo(null);

        frame.setVisible(true);
        frame.validate();
    }

    static void runTest() throws Exception {
        robot.mouseMove(10, 30);
        synchronized (lock) {
            try {
                lock.wait(120000);
            }
            catch (InterruptedException e) {}
        }
        robot.waitForIdle();

        if (!firstItem.equals(theChoice.getSelectedItem())) {
            throw new RuntimeException("TEST FAILED - new item was selected");
        }
    }

    public void run() {
        robot.delay(1000);
        // get loc of Choice on screen
        Point loc = theChoice.getLocationOnScreen();
        // get bounds of Choice
        Dimension size = theChoice.getSize();
        robot.mouseMove(loc.x + size.width - 10, loc.y + size.height / 2);

        robot.setAutoDelay(500);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);

        robot.mouseMove(loc.x + size.width / 2, loc.y + size.height + size.height / 2);
        robot.mouseMove(loc.x + size.width / 2, loc.y + 2*size.height + size.height / 2);
        robot.mouseMove(loc.x + size.width / 2, loc.y + 3*size.height + size.height / 2);
        robot.mouseMove(loc.x + size.width / 2, loc.y + 4*size.height + size.height / 2);
        robot.mouseMove(loc.x + size.width, loc.y + 4*size.height + size.height / 2);
        robot.mouseMove(loc.x + 2*size.width, loc.y + 4*size.height + size.height / 2);
        robot.mouseMove(loc.x + 3*size.width, loc.y + 4*size.height + size.height / 2);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        synchronized(lock) {
            lock.notify();
        }
    }

    public void windowOpened(WindowEvent e) {
        System.out.println("windowActivated()");
        Thread testThread = new Thread(testInstance);
        testThread.start();
    }
    public void windowActivated(WindowEvent e) { }
    public void windowDeactivated(WindowEvent e) {}
    public void windowClosed(WindowEvent e) {}
    public void windowClosing(WindowEvent e) {}
    public void windowIconified(WindowEvent e) {}
    public void windowDeiconified(WindowEvent e) {}

}

