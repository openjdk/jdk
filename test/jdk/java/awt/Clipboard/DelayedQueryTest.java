/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4085183 8000630
  @summary tests that clipboard contents is retrieved even if the app didn't
           receive native events for a long time.
  @requires (os.family != "mac")
  @key headful
  @run main DelayedQueryTest
*/

import java.awt.AWTException;
import java.awt.Button;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.InputEvent;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;

public class DelayedQueryTest implements ClipboardOwner, Runnable {
    int returnCode = Child.CHILD_RETURN_CODE_NOT_READY;

    Process childProcess = null;
    Frame frame;

    public static void main(String[] args)
            throws InterruptedException, InvocationTargetException {
        String osName = System.getProperty("os.name");
        if (osName.toLowerCase().contains("os x")) {
            System.out.println("This test is not for MacOS, considered passed.");
            return;
        }
        DelayedQueryTest delayedQueryTest = new DelayedQueryTest();
        EventQueue.invokeAndWait(delayedQueryTest::initAndShowGui);
        try {
            delayedQueryTest.start();
        } finally {
            EventQueue.invokeAndWait(() -> delayedQueryTest.frame.dispose());
        }
    }

    public void initAndShowGui(){
        frame = new Frame("DelayedQueryTest");
        frame.add(new Panel());
        frame.setBounds(200,200, 200, 200);
        frame.setVisible(true);
    }

    public void start() {
        try {
            Robot robot = new Robot();
            // Some mouse activity to update the Xt time stamp at
            // the parent process.
            robot.delay(1000);
            robot.waitForIdle();

            Point p = frame.getLocationOnScreen();
            robot.mouseMove(p.x + 100, p.y + 100);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        } catch (AWTException e) {
            e.printStackTrace();
            throw new RuntimeException("The test failed.");
        }
        Child.sysClipboard.setContents(Child.transferable, this);

        String javaPath = System.getProperty("java.home", "");
        String[] command = {
                javaPath + File.separator + "bin" + File.separator + "java",
                "-cp", System.getProperty("test.classes", "."),
                "Child"
        };

        try {
            Process process = Runtime.getRuntime().exec(command);
            childProcess = process;
            returnCode = process.waitFor();
            childProcess = null;

            InputStream errorStream = process.getErrorStream();
            int count = errorStream.available();
            if (count > 0) {
                byte[] b = new byte[count];
                errorStream.read(b);
                System.err.println("========= Child VM System.err ========");
                System.err.print(new String(b));
                System.err.println("======================================");
            }
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException("The test failed.");
        }
        if (returnCode != Child.CHILD_RETURN_CODE_OK) {
            System.err.println("Child VM: returned " + returnCode);
            throw new RuntimeException("The test failed.");
        }
    } // start()

    public void lostOwnership(Clipboard clipboard,
                              Transferable contents) {
        // At this moment the child process has definitely started.
        // So we can try to retrieve the clipboard contents set
        // by the child process.
        new Thread(this).start();
    }

    public void run() {
        // We are going to check if it is possible to retrieve the data
        // after the child process has set the clipboard contents twice,
        // since after the first setting the retrieval is always successful.
        // So we wait to let the child process set the clipboard contents
        // twice.
        try {
            Thread.sleep(Child.CHILD_SELECTION_CHANGE_TIMEOUT);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
            String s = (String)Child.sysClipboard
                    .getContents(null)
                    .getTransferData(DataFlavor.stringFlavor);
            if (!"String".equals(s)) {
                System.err.println("Data retrieved: " + s);
                throw new RuntimeException("Retrieved data is incorrect.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (childProcess != null) {
                childProcess.destroy();
                childProcess = null;
            }
            throw new RuntimeException("Failed to retrieve the data.");
        }
        Child.sysClipboard.setContents(Child.transferable, null);
    }
}

class Child {
    static final Clipboard sysClipboard =
            Toolkit.getDefaultToolkit().getSystemClipboard();
    static final Transferable transferable = new StringSelection("String");

    /*
     * Timeouts.
     */
    static final int FRAME_ACTIVATION_TIMEOUT = 1000;
    static final int PARENT_TIME_STAMP_TIMEOUT = 1000;
    static final int CHILD_SELECTION_CHANGE_TIMEOUT =
            FRAME_ACTIVATION_TIMEOUT + PARENT_TIME_STAMP_TIMEOUT + 5000;
    static final int PARENT_RETRIEVE_DATA_TIMEOUT = 10000;

    /*
     * Child process return codes.
     */
    static final int CHILD_RETURN_CODE_NOT_READY            = -1;
    static final int CHILD_RETURN_CODE_OK                   = 0;
    static final int CHILD_RETURN_CODE_UNEXPECTED_EXCEPTION = 1;
    static final int CHILD_RETURN_CODE_OTHER_FAILURE        = 2;
    static Button button;

    static void initAndShowGui() {
        final Frame frame = new Frame();
        button = new Button("button");
        frame.add(button);
        frame.pack();
        frame.setLocation(100, 100);
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        sysClipboard.setContents(
                new StringSelection("First String"), null);

        // Some mouse activity to update the Xt time stamp at
        // the child process.
        try {
            EventQueue.invokeAndWait(Child::initAndShowGui);
            try {
                Thread.sleep(FRAME_ACTIVATION_TIMEOUT);
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.exit(CHILD_RETURN_CODE_UNEXPECTED_EXCEPTION);
            }

            Robot robot = new Robot();
            robot.waitForIdle();

            Point p = button.getLocationOnScreen();
            robot.mouseMove(p.x + 10, p.y + 10);
            // Wait to let the Xt time stamp become out-of-date.
            try {
                Thread.sleep(PARENT_TIME_STAMP_TIMEOUT);
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.exit(CHILD_RETURN_CODE_UNEXPECTED_EXCEPTION);
            }
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(CHILD_RETURN_CODE_UNEXPECTED_EXCEPTION);
        }

        sysClipboard.setContents(transferable, new ClipboardOwner() {
            public void lostOwnership(Clipboard clipboard,
                                      Transferable contents) {
                System.exit(CHILD_RETURN_CODE_OK);
            }
        });
        // Wait to let the parent process retrieve the data.
        try {
            Thread.sleep(PARENT_RETRIEVE_DATA_TIMEOUT);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // Parent failed to set clipboard contents, so we signal test failure
        System.exit(CHILD_RETURN_CODE_OTHER_FAILURE);
    }
}
