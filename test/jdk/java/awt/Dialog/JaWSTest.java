/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4690465
  @summary Tests that after dialog is hidden on another EDT, owning EDT gets notified.
  @modules java.desktop/sun.awt
  @key headful
  @run main JaWSTest
*/

import java.awt.Button;
import java.awt.Dialog;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import sun.awt.SunToolkit;
import sun.awt.AppContext;

public class JaWSTest implements ActionListener, Runnable {

    static volatile Frame frame;
    static volatile JaWSTest worker;
    static volatile Dialog dummyDialog;
    static final Object signalObject = new Object();
    static volatile AppContext appContextObject = null;
    static volatile Button button = null;
    static final CountDownLatch dialogFinished = new CountDownLatch(1);

    public static void main(String[] args) throws Exception {
        try {
            EventQueue.invokeAndWait(JaWSTest::createUI);
            Robot robot = new Robot();
            robot.waitForIdle();
            robot.delay(1000);
            Point buttonLocation = button.getLocationOnScreen();
            robot.mouseMove(buttonLocation.x + button.getWidth()/2,
                            buttonLocation.y + button.getHeight()/2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            if (!dialogFinished.await(5, TimeUnit.SECONDS)) {
                throw new RuntimeException("Dialog thread is blocked");
            }
        } finally {
            if (frame != null) {
                EventQueue.invokeAndWait(frame::dispose);
            }
        }
    }

    static void createUI() {
        worker = new JaWSTest();
        frame = new Frame("JaWSTest Main User Frame");
        button = new Button("Press To Save");
        button.addActionListener(worker);
        frame.add(button);
        frame.pack();
        frame.setVisible(true);
    }

    public void actionPerformed(ActionEvent ae) {
        System.err.println("Action Performed");
        synchronized (signalObject) {
            ThreadGroup askUser = new ThreadGroup("askUser");
            final Thread handler = new Thread(askUser, worker, "userDialog");

            dummyDialog = new Dialog(frame, "Dummy Modal Dialog", true);
            dummyDialog.setBounds(200, 200, 100, 100);
            dummyDialog.addWindowListener(new WindowAdapter() {
                    public void windowOpened(WindowEvent we) {
                        System.err.println("handler is started");
                        handler.start();
                    }
                    public void windowClosing(WindowEvent e) {
                        dummyDialog.setVisible(false);
                    }
                });
            dummyDialog.setResizable(false);
            dummyDialog.toBack();
            System.err.println("Before First Modal");
            dummyDialog.setVisible(true);
            System.err.println("After First Modal");
            try {
                signalObject.wait();
            } catch (Exception e) {
                e.printStackTrace();
                dummyDialog.setVisible(false);
            }
            if (appContextObject != null) {
                appContextObject = null;
            }
            dummyDialog.dispose();
        }
        System.err.println("Show Something");
        dialogFinished.countDown();
    }

    public void run() {
        System.err.println("Running");
        try {
            appContextObject = SunToolkit.createNewAppContext();
       } finally {
           try {
               Thread.sleep(1000);
           } catch (InterruptedException ie) {
               ie.printStackTrace();
           }
           System.err.println("Before Hiding 1");
           dummyDialog.setVisible(false);
           System.err.println("Before Synchronized");
           synchronized (signalObject) {
               System.err.println("In Synchronized");
               signalObject.notify();
               System.err.println("After Notify");
           }
        }
        System.err.println("Stop Running");
    }
}
