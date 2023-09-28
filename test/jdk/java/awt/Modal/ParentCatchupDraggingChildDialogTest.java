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
  @bug 5083555
  @summary Parent Windows of mouse events catchup while dragging child dialog window
  @key headful
  @run main ParentCatchupDraggingChildDialogTest
*/

import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Robot;

import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;


public class ParentCatchupDraggingChildDialogTest {
    JFrame frame = null;
    JDialog dialog = null;
    DialogThread thread = null;
    JButton trigger = new JButton("trigger");
    JButton show = new JButton("show");
    Robot r = null;
    volatile Point locTrigger, locDialog;

    volatile boolean passed = true;

    public static void main(String args[]) throws Exception {
        ParentCatchupDraggingChildDialogTest test = new ParentCatchupDraggingChildDialogTest();
        test.start();
    }

    public void start () throws Exception {
        try {
            EventQueue.invokeAndWait(() -> {
                frame = new JFrame("Parent frame");
                frame.setBounds(20, 20, 300, 300);
                frame.setLayout(new FlowLayout());
                frame.add(trigger);
                frame.add(show);
                frame.setVisible(true);

                dialog = new JDialog(frame, "Dialog", true);
                dialog.setBounds(100, 100, 300, 300);

                trigger.addMouseListener(new MouseAdapter() {
                    public void mouseEntered(MouseEvent e) {
                        System.out.println("Trigger button event: " + e);
                        passed = false;
                    }
                });
            });

            thread = new DialogThread(dialog);
            thread.start();

            test();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
                if (dialog != null) {
                    dialog.dispose();
                }
            });
        }
    }

    /* Test scenario:
     * 1) dragging mouse over the 'Trigger' button in order to be sure that the events don't occured for non modal window
     * 2) checking
     * 3) close dialog in order to finish test
     */
    private void test() throws Exception {
        try {
            r = new Robot();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        r.delay(500);
        EventQueue.invokeAndWait(() -> {
            locTrigger = trigger.getLocationOnScreen();
        });

        r.delay(500);
        EventQueue.invokeAndWait(() -> {
            locDialog = dialog.getLocationOnScreen();
        });
        r.delay(500);

        r.mouseMove(locDialog.x + dialog.getWidth() / 2, locDialog.y + dialog.getHeight() / 2);
        r.delay(500);
        r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        r.delay(500);
        r.mouseMove(locTrigger.x + trigger.getWidth() / 2, locTrigger.y + trigger.getHeight() / 2);
        r.delay(500);
        r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        r.delay(500);

        if (!passed) {
            throw new RuntimeException("Test failed. Triggering occured.");
        }

        EventQueue.invokeAndWait(() -> {
            dialog.dispose();
        });
    }
}

class DialogThread extends Thread {
    JDialog dialog = null;

    public DialogThread(JDialog dialog){
        this.dialog = dialog;
    }

    public void run(){
        dialog.setVisible(true);
    }
}
