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
  @bug 6271546
  @summary REG. Mouse input blocked on a window which is a child of a modal dialog
  @key headful
  @run main BlockedMouseInputTest2
*/

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Dialog;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;

public class BlockedMouseInputTest2 {
    Frame frame;
    Dialog dlg1;
    Dialog dlg2;
    Button b;
    Robot r = null;
    volatile boolean passed = false;
    volatile Point p;
    volatile int btnWidth;
    volatile int btnHeight;

    public static void main(String args[]) throws Exception {
        BlockedMouseInputTest2 test = new BlockedMouseInputTest2();
        test.start();
    }

    public void start() throws Exception {
        try {
            r = new Robot();
            EventQueue.invokeAndWait(() -> {
                frame = new Frame("Parent frame");
                frame.setBounds(100, 100, 200, 100);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);

                dlg1 = new Dialog(frame, "Dialog 1", true);
                dlg1.setBounds(200, 200, 200, 100);

            new Thread(new Runnable() {
                public void run() {
                    dlg1.setVisible(true);
                }
            }).start();
            });

            r.delay(1000);
            r.waitForIdle();

            EventQueue.invokeAndWait(() -> {
                dlg2 = new Dialog(frame, "Dialog 2", true);
                dlg2.setBounds(300, 300, 200, 100);
            });
            new Thread(new Runnable() {
                public void run() {
                    dlg2.setVisible(true);
                }
            }).start();

            r.delay(1000);
            r.waitForIdle();

            EventQueue.invokeAndWait(() -> {
                Dialog d = new Dialog(dlg2, "D", false);
                d.setBounds(400, 400, 200, 100);
                d.setLayout(new BorderLayout());
                b = new Button("Test me");
                b.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        passed = true;
                    }
                });
                d.add(b, BorderLayout.CENTER);
                d.setVisible(true);
            });

            r.delay(1000);
            r.waitForIdle();

            EventQueue.invokeAndWait(() -> {
                p = b.getLocationOnScreen();
                btnWidth = b.getSize().width;
                btnHeight = b.getSize().height;
            });
            r.mouseMove(p.x + btnWidth / 2, p.y + btnHeight / 2);
            r.delay(500);
            r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            r.delay(500);
            r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            r.delay(500);

            if (!passed) {
                throw new RuntimeException("Test is FAILED: button is not pressed");
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
                if (dlg1 != null) {
                    dlg1.dispose();
                }
                if (dlg2 != null) {
                    dlg2.dispose();
                }
            });
        }
    }
}
