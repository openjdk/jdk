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
  @bug 6274378
  @summary Test for 6274378: Blocked mouse and keyboard input after hiding modal dialog
  @key headful
  @run main BlockedMouseInputTest3
*/

import java.awt.Button;
import java.awt.Dialog;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Robot;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;


public class BlockedMouseInputTest3 {
    Frame frame;

    Dialog dlg1; // application-modal
    Dialog dlg2; // application-modal
    Dialog d; // toolkit-modal

    Button b1; // in dlg1
    Button b2; // in dlg2

    Robot r = null;

    volatile boolean b1pressed, b2pressed;
    volatile boolean dlg1activated, dlg2activated;
    volatile int b1Width, b1Height;
    volatile int b2Width, b2Height;
    volatile Point p1, p2;

    public static void main(String args[]) throws Exception {
        BlockedMouseInputTest3 test = new BlockedMouseInputTest3();
        test.start();
    }

    public void start() throws Exception {
        try {
            r = new Robot();
            EventQueue.invokeAndWait(() -> {
                frame = new Frame("Parent frame");
                frame.setBounds(0, 0, 200, 100);
                frame.setVisible(true);

                // create d and set it visible
                d = new Dialog(frame, "Toolkit-modal", Dialog.ModalityType.TOOLKIT_MODAL);
                d.setBounds(250, 0, 200, 100);
            });
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    d.setVisible(true);
                }
            });

            r.delay(1000);
            r.waitForIdle();

            // create dlg1 and set it visible
            // dlg1 is blocked by d

            EventQueue.invokeAndWait(() -> {
                dlg1 = new Dialog(frame, "Application-modal 1", Dialog.ModalityType.APPLICATION_MODAL);
                dlg1.setBounds(0, 150, 200, 100);
                dlg1.addWindowListener(new WindowAdapter() {
                    public void windowActivated(WindowEvent e) {
                        dlg1activated = true;
                    }
                });
                b1 = new Button("B1");
                b1.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        b1pressed = true;
                    }
                });
                dlg1.add(b1);
            });
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    dlg1.setVisible(true);
                }
            });

            r.delay(1000);
            r.waitForIdle();

            // create dlg2 and set it visible
            // dlg2 is blocked by d
            EventQueue.invokeAndWait(() -> {
                dlg2 = new Dialog(frame, "Application-modal 2", Dialog.ModalityType.APPLICATION_MODAL);
                dlg2.setBounds(0, 300, 200, 100);
                dlg2.addWindowListener(new WindowAdapter() {
                    public void windowActivated(WindowEvent e) {
                        dlg2activated = true;
                    }
                });
                b2 = new Button("B2");
                b2.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        b2pressed = true;
                    }
                });
                dlg2.add(b2);
            });
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    dlg2.setVisible(true);
                }
            });

            r.delay(1000);
            r.waitForIdle();


            // hide d
            // dlg2 is unblocked and dlg1 is blocked by dlg2
            EventQueue.invokeAndWait(() -> {
                d.setVisible(false);
            });

            r.delay(1000);
            r.waitForIdle();

            // values to check
            b1pressed = false;
            b2pressed = false;
            dlg1activated = false;
            dlg2activated = false;
            System.err.println(KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow());

            // check mouse events and activation
            EventQueue.invokeAndWait(() -> {
                p1 = b1.getLocationOnScreen();
                b1Width = b1.getWidth();
                b1Height = b1.getHeight();
            });
            clickPoint(r, p1.x + b1Width / 2, p1.y + b1Height / 2);

            EventQueue.invokeAndWait(() -> {
                dlg1activated = (KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() == dlg1);
            });

            EventQueue.invokeAndWait(() -> {
                p2 = b2.getLocationOnScreen();
                b2Width = b2.getWidth();
                b2Height = b2.getHeight();
            });

            clickPoint(r, p2.x + b2Width / 2, p2.y + b2Height / 2);

            EventQueue.invokeAndWait(() -> {
                dlg2activated = (KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() == dlg2);
            });

            if (dlg1activated || b1pressed || !dlg2activated || !b2pressed) {
                throw new RuntimeException("Test is FAILED");
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
                if (d != null) {
                    d.dispose();
                }
            });
        }
    }

    private static void clickPoint(Robot r, int x, int y) {
        r.mouseMove(x, y);
        r.delay(500);
        r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        r.delay(500);
        r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        r.delay(500);
    }
}
