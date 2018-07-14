/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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
  @key headful
  @bug 4980161 7158623 8204860
  @summary Setting focusable window state to false makes the maximized frame resizable
  @compile UnfocusableMaximizedFrameResizablity.java
  @run main UnfocusableMaximizedFrameResizablity
*/

import java.awt.Toolkit;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.AWTException;
import java.awt.event.InputEvent;
import java.awt.Robot;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class UnfocusableMaximizedFrameResizablity {

    private static Frame frame;
    private static JFrame jframe;
    private static Robot robot;
    private static boolean isProgInterruption = false;
    private static Thread mainThread = null;
    private static int sleepTime = 300000;

    private static void createAndShowFrame() throws Exception {

        //The MAXIMIZED_BOTH state is not supported by the toolkit. Nothing to test.
        if (!Toolkit.getDefaultToolkit().isFrameStateSupported(Frame.MAXIMIZED_BOTH)) {
            return;
        }

        //Case 1: Setting frame resizable to true followed by focusable to false
        frame = createFrame("Resizable Unfocusable frame");
        frame.setResizable(true);
        frame.setFocusableWindowState(false);
        tryToResizeFrame(frame);

        //Case 2: Setting frame focusable to false followed by resizable to true
        frame = createFrame("Unfocusable Resizable frame");
        frame.setFocusableWindowState(false);
        frame.setResizable(true);
        tryToResizeFrame(frame);

        //Case 3: Testing JFrame fullscreen behaviour only on Mac OS
        if (System.getProperty("os.name").toLowerCase().startsWith("mac")) {
            SwingUtilities.invokeAndWait(new Runnable() {

                Override
                public void run() {
                    jframe = createJFrame("Unfocusable Resizable JFrame");
                    jframe.setFocusableWindowState(false);
                    jframe.setResizable(true);
                    Object prop1 = jframe.getRootPane().getClientProperty("apple.awt.fullscreenable");
                    jframe.setVisible(false);
                    jframe.setVisible(true);
                    Object prop2 = jframe.getRootPane().getClientProperty("apple.awt.fullscreenable");

                    if((prop1 != null && prop2 != null) && (!prop1.equals(prop2))) {
                        jframe.dispose();
                        cleanup();
                        throw new RuntimeException("Non-focusable resizable JFrame is fullscreenable!!");
                    }
                }
            });
        }

        cleanup();
    }

    private static JFrame createJFrame(String title) {
        JFrame jframe = new JFrame(title);
        jframe.setMaximizedBounds(new Rectangle(0, 0, 300, 300));
        jframe.setSize(200, 200);
        jframe.setVisible(true);
        jframe.setExtendedState(Frame.MAXIMIZED_BOTH);

        return jframe;
    }

    private static Frame createFrame(String title) {
        Frame frame = new Frame(title);
        frame.setMaximizedBounds(new Rectangle(0, 0, 300, 300));
        frame.setSize(200, 200);
        frame.setVisible(true);
        frame.setExtendedState(Frame.MAXIMIZED_BOTH);

        return frame;
    }

    private static void tryToResizeFrame(Frame frame) {
        try {
            robot = new Robot();
        } catch (AWTException e) {
            throw new RuntimeException("Robot creation failed");
        }
        robot.delay(2000);

        // The initial bounds of the frame
        final Rectangle bounds = frame.getBounds();

        // Let's move the mouse pointer to the bottom-right coner of the frame (the "size-grip")
        robot.mouseMove(bounds.x + bounds.width - 2, bounds.y + bounds.height - 2);
        robot.waitForIdle();

        // ... and start resizing
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        robot.mouseMove(bounds.x + bounds.width + 20, bounds.y + bounds.height + 15);
        robot.waitForIdle();

        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();

        // The bounds of the frame after the attempt of resizing is made
        final Rectangle finalBounds = frame.getBounds();

        if (!finalBounds.equals(bounds)) {
            cleanup();
            throw new RuntimeException("The maximized unfocusable frame can be resized.");
        }

        frame.dispose();
    }

    private static void cleanup() {
        isProgInterruption = true;
        mainThread.interrupt();
    }

    public static void main(String args[]) throws Exception {

        mainThread = Thread.currentThread();

        try {
            createAndShowFrame();
            mainThread.sleep(sleepTime);
        } catch (InterruptedException e) {
            if (!isProgInterruption) {
                throw e;
            }
        }

        if (!isProgInterruption) {
            throw new RuntimeException("Timed out after " + sleepTime / 1000
                    + " seconds");
        }
    }
}

