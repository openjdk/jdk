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
  @bug 4256692
  @summary Showing a non modal dialog after a modal dialog allows both to receive events
  @key headful
  @run main NonModalDialogReceiveEventsAfterModalTest
*/

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

public class NonModalDialogReceiveEventsAfterModalTest implements Runnable
{
    Frame modalParentFrame, nonModalParentFrame;
    Dialog modalDialog, nonModalDialog;

    volatile public static boolean passed = true;
    volatile public static String errorMessage = null;

    Robot r = null;
    volatile Point loc = null;

    public static void main(String args[]) throws Exception {
        NonModalDialogReceiveEventsAfterModalTest test = new NonModalDialogReceiveEventsAfterModalTest();
        test.start();
    }

    public void start() throws Exception {

        // create an independent top level frame to be the
        // parent of the modal dialog and show it
        try {
            r = new Robot();
            EventQueue.invokeAndWait(() -> {
                modalParentFrame = new Frame("Parent of modal dialog");
                modalParentFrame.setBounds(100, 100, 200, 200);
                modalParentFrame.setLayout(new BorderLayout());
                modalParentFrame.setVisible(true);

                // create an independent top level frame to be the
                // parent of the non-modal dialog and show it
                nonModalParentFrame = new Frame("Parent of non-modal dialog");
                nonModalParentFrame.setBounds(400, 100, 200, 200);
                nonModalParentFrame.setLayout(new BorderLayout());
                nonModalParentFrame.setVisible(true);

                // create the non-modal dialog and kick off a
                // thread to show it in 1 second
                nonModalDialog = new Dialog(nonModalParentFrame, "Non modal", false);
                nonModalDialog.setBounds(400, 150, 100, 100);
                nonModalDialog.addMouseMotionListener(new TestMouseMotionAdapter());
                nonModalDialog.addFocusListener(new TestFocusAdapter());
                new Thread(this).start();

                // create the modal dialog and show it from this thread
                modalDialog = new Dialog(modalParentFrame, "Modal", true);
                modalDialog.setBounds(100, 400, 100, 100);
                modalDialog.setVisible(true);
            });
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (modalParentFrame != null) {
                    modalParentFrame.dispose();
                }
                if (nonModalParentFrame != null) {
                    nonModalParentFrame.dispose();
                }
                if (modalDialog != null) {
                    modalDialog.dispose();
                }
                if (nonModalDialog != null) {
                    nonModalDialog.dispose();
                }
            });
        }

    }

    // This is the implementation of Runnable and is
    // used to show the non-modal dialog in 1 second
    public void run() {
        r.delay(1000);
        r.waitForIdle();
        //show the non modal dialog
        nonModalDialog.setVisible(true);

        r.delay(1000);
        r.waitForIdle();
        test();
    }

    private void test() {

        // mouse, focus, activate events triggering
        r.delay(500);
        loc = nonModalDialog.getLocationOnScreen();
        r.delay(500);

        r.mouseMove(loc.x + (int) (nonModalDialog.getWidth() / 2), loc.y + (int) (nonModalDialog.getHeight() / 2));
        r.delay(100);
        r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        r.delay(100);
        r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        r.delay(100);
        r.mouseMove(loc.x - 100, loc.y - 100);

        r.waitForIdle();

        // dispose modal window in order to finish test
        modalDialog.dispose();

        // check test result
        if (!passed) {
            throw new RuntimeException("test failed: " + errorMessage);
        }
    }

    public static void testFailed(String message) {
        passed = false;
        errorMessage = message;
    }
}

class TestMouseMotionAdapter extends MouseMotionAdapter {

    public void mouseClicked(MouseEvent e){
        NonModalDialogReceiveEventsAfterModalTest.testFailed("mouseClicked");
    }

    public void mouseEntered(MouseEvent e){
        NonModalDialogReceiveEventsAfterModalTest.testFailed("mouseEntered");
    }

    public void mouseExited(MouseEvent e){
        NonModalDialogReceiveEventsAfterModalTest.testFailed("mouseExited");
    }

    public void mousePressed(MouseEvent e){
        NonModalDialogReceiveEventsAfterModalTest.testFailed("mousePressed");
    }

    public void mouseReleased(MouseEvent e){
        NonModalDialogReceiveEventsAfterModalTest.testFailed("mouseReleased");
    }
}

class TestFocusAdapter extends FocusAdapter {
    public void focusGained(FocusEvent e){
        NonModalDialogReceiveEventsAfterModalTest.testFailed("focusGained");
    }

    public void focusLost(FocusEvent e){
        NonModalDialogReceiveEventsAfterModalTest.testFailed("focusLost");
    }
}
