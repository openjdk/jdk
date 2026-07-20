/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @key headful
 * @bug 8065861
 * @summary Test to check pressing Escape key sets 'canceled' property of ProgressMonitor
 * @run main ProgressMonitorEscapeKeyPress
 */


import javax.swing.JFrame;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ProgressMonitorEscapeKeyPress {

    static volatile int counter = 0;
    static ProgressMonitor monitor;
    static TestThread testThread;
    static JFrame frame;
    static CountDownLatch progressLatch;
    static Robot robot;


    public static void main(String[] args) throws Exception {
        try {
            progressLatch = new CountDownLatch(20);
            createTestUI();
            robot = new Robot();
            robot.setAutoDelay(50);
            robot.setAutoWaitForIdle(true);
            testThread = new TestThread();
            testThread.start();
            Thread.sleep(100);
            if (progressLatch.await(15, TimeUnit.SECONDS)) {
                System.out.println("Progress monitor completed 20%, lets press Esc...");
                robot.keyPress(KeyEvent.VK_ESCAPE);
                robot.keyRelease(KeyEvent.VK_ESCAPE);
                System.out.println("ESC pressed....");
            } else {
                System.out.println("Failure : No status available from Progress monitor...");
                throw new RuntimeException(
                        "Can't get the status from Progress monitor even after waiting too long..");
            }

            if (counter >= monitor.getMaximum()) {
                throw new RuntimeException("Escape key did not cancel the ProgressMonitor");
            }
            System.out.println("Test Passed...");
        } finally {
            disposeTestUI();
        }
    }

    private static void createTestUI() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            frame = new JFrame("Test");
            frame.setSize(300, 300);
            monitor = new ProgressMonitor(frame, "Progress", "1", 0, 100);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setLocationByPlatform(true);
        });
    }


     private static void disposeTestUI() throws Exception {
           SwingUtilities.invokeAndWait(() -> {
               frame.dispose();
           });
       }
}


class TestThread extends Thread {
    @Override
    public void run() {
        System.out.println("TestThread started.........");
        for (ProgressMonitorEscapeKeyPress.counter = 0;
             ProgressMonitorEscapeKeyPress.counter <= 100;
             ProgressMonitorEscapeKeyPress.counter += 1) {
            ProgressMonitorEscapeKeyPress.robot.delay(100);
            ProgressMonitor monitor = ProgressMonitorEscapeKeyPress.monitor;
            if (!monitor.isCanceled()) {
                monitor.setNote("" + ProgressMonitorEscapeKeyPress.counter);
                monitor.setProgress(ProgressMonitorEscapeKeyPress.counter);
                ProgressMonitorEscapeKeyPress.progressLatch.countDown();
                System.out.println("Progress bar is in progress....."
                        + ProgressMonitorEscapeKeyPress.counter + "%");
            }
            if (monitor.isCanceled()) {
                System.out.println("$$$$$$$$$$$$$$$ Monitor canceled");
                break;
            }
        }
    }
}

