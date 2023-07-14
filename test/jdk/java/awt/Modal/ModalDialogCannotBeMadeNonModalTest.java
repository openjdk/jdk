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
  @bug 4272629
  @summary Modal dialog cannot be made non-modal
  @key headful
  @run main ModalDialogCannotBeMadeNonModalTest
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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class ModalDialogCannotBeMadeNonModalTest {
    Frame frame = null;
    Button button = null;
    Dialog dialog = null;
    Robot r = null;
    volatile Point loc = null;

    volatile private boolean buttonPressed = false;

    public static void main(String args[]) throws Exception {
        ModalDialogCannotBeMadeNonModalTest test = new ModalDialogCannotBeMadeNonModalTest();
        test.start();
    }

    public void start() throws Exception {
        try {
            r = new Robot();
            EventQueue.invokeAndWait(() -> {
                frame = new Frame("Parent frame");
                frame.setLayout(new BorderLayout());
                frame.setBounds(200, 200, 200, 200);
                frame.setVisible(true);

                button = new Button("Trigger");
                button.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        buttonPressed = true;
                    }
                });
                frame.add(button);
                frame.setVisible(true);

                dialog = new Dialog(frame, "Dialog");
                dialog.setBounds(0, 0, 100, 100);
                dialog.addWindowListener(new WindowAdapter() {
                    public void windowClosing(WindowEvent we) {
                        we.getWindow().setVisible(false);
                    }
                });
            });

            r.delay(500);
            r.waitForIdle();
            EventQueue.invokeAndWait(() -> {
                loc = button.getLocationOnScreen();
            });
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
    public void test() throws Exception {

        // 1-visibility, 2-modality
        System.out.println("1 create visible, modal ... ");
        EventQueue.invokeAndWait(() -> {
            dialog.setModal(true);
            setDialogVisible(true);
        });
        r.delay(1000);
        r.waitForIdle();

        System.out.println("2 set non visible, modal ... ");
        EventQueue.invokeAndWait(() -> {
            dialog.setVisible(false);
            dialog.setModal(false);
        });
        r.delay(1000);
        r.waitForIdle();

        System.out.println("3 set visible, non modal ... ");
        EventQueue.invokeAndWait(() -> {
            setDialogVisible(true);
        });
        r.delay(1000);
        r.waitForIdle();

        System.out.println("4 checking ... ");
        check();
        r.delay(1000);
        r.waitForIdle();
        System.out.println("5 exit ");
    }

    public void check() throws Exception {
        r.delay(500);
        r.mouseMove(loc.x + button.getWidth()/2, loc.y + button.getHeight()/2);
        r.delay(500);
        r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        r.delay(500);
        r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        r.delay(500);

        if (!buttonPressed) {
            throw new RuntimeException("Test failed");
        }
    }

    public void setDialogVisible(boolean visibility) {
        if (visibility) {
            new Thread(new Runnable() {
                public void run() {
                    dialog.setVisible(true);
                }
            }).start();
        } else {
            dialog.setVisible(false);
        }
    }
}
