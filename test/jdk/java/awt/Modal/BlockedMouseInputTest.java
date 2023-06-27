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
  @bug 4957639
  @summary REGRESSION: blocked mouse input in a special case on win32
  @key headful
  @run main BlockedMouseInputTest
*/

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;

import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.JComboBox;
import javax.swing.JDialog;
/*
 * Threads:
 * 0) Main - running others, checking
 * 1) First - opening first dialog
 * 2) Second - opening second dialog, generating item state changed events
 * We need 1 and 2 thread in order to don't block main thread
 */

public class BlockedMouseInputTest implements ItemListener {
    Frame frame = null;

    ThreadDialog thread1 = null;
    ThreadDialog thread2 = null;

    // If we recreate dialogs in the Threads classes then the test works fine
    JComboBox<String> cb = null;
    JDialog dialog1 = null;
    JDialog dialog2 = null;

    Robot r = null;
    volatile Point loc = null;
    volatile int cbWidth;
    volatile int cbHeight;

    volatile int selected;

    volatile boolean passed = false;

    public static void main(String[] args) throws Exception {
        BlockedMouseInputTest test = new BlockedMouseInputTest();
        test.start();
    }

    public void start() throws Exception {
        try {
            r = new Robot();
            EventQueue.invokeAndWait(() -> {
                frame = new Frame("Parent frame");
                frame.setSize(200, 200);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });
            test();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
                if (dialog1 != null) {
                    dialog1.dispose();
                }
                if (dialog2 != null) {
                    dialog2.dispose();
                }
            });
        }
    }

    private void test() throws Exception {

        // The state of the combobox should stay the same to new iteration of the cycle
        // We couldn't run the thread twice
        EventQueue.invokeAndWait(() -> {
            cb = new JComboBox<String>(new String[]{"entry a", "entry b",
                    "entry c", "entry d", "entry e"});
            dialog1 = new JDialog(frame, "dialog1", true);
            dialog2 = new JDialog(frame, "dialog2", true);
            dialog2.getContentPane().add(cb);
            cb.addItemListener(this);

            dialog1.setLocation(20, 20);
            dialog1.setSize(new Dimension(150, 50));
            dialog2.setLocation(120, 120);
            dialog2.setSize(new Dimension(150, 50));
        });

        for (int i = 0; i < 2; i++) {
            passed = false;
            tryGenerateEvent();
            if (!passed && i != 0) {
                throw new RuntimeException("Test failed: triggering not occurred, iteration - " + i);
            }
        }
    }

    private void tryGenerateEvent() throws Exception {
        EventQueue.invokeAndWait(() -> {
            thread1 = new ThreadDialog(dialog1);
            thread2 = new ThreadDialog(dialog2);
        });

        thread1.start();
        r.delay(500);
        r.waitForIdle();
        thread2.start();
        r.delay(500);
        r.waitForIdle();

        doRobotAction();

        EventQueue.invokeAndWait(() -> {
            dialog2.setVisible(false);
            dialog1.setVisible(false);
        });
    }

    public void itemStateChanged(ItemEvent ie) {
        passed = true;
        System.out.println("event: "+ie);
    }

    public void doRobotAction() throws Exception {
        EventQueue.invokeAndWait(() -> {
            loc = cb.getLocationOnScreen();
            cbWidth = cb.getWidth();
            cbHeight = cb.getHeight();
        });

        r.mouseMove(loc.x + cbWidth / 2, loc.y + cbHeight / 2);
        r.delay(500);
        r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        r.delay(500);
        r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        r.delay(500);

        EventQueue.invokeAndWait(() -> {
            selected = cb.getSelectedIndex();
        });

        r.mouseMove(loc.x + cbWidth / 2, loc.y + cbHeight * ((selected == 0) ? 2 : 1) + 10);
        r.delay(500);
        r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        r.delay(500);
        r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        r.delay(500);

        r.waitForIdle();
    }
}

class ThreadDialog extends Thread {

    JDialog dialog = null;

    public ThreadDialog(JDialog dialog){
        this.dialog = dialog;
    }

    public void run() {
        dialog.setVisible(true);
    }
}
