/*
 * Copyright (c) 2002, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4636311 4645035
  @summary Modal dialog shown on EDT after modal dialog on EDT doesn't receive mouse events
  @key headful
  @run main ModalDialogOnNonEdt
*/

import java.awt.Dialog;
import java.awt.Frame;
import java.awt.EventQueue;
import java.awt.Robot;
import java.awt.Point;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.AWTException;
import java.awt.event.MouseEvent;
import java.awt.event.MouseAdapter;
import java.util.ArrayList;
import java.util.List;

public class ModalDialogOnNonEdt {

    public void start () {
        ShowModalDialog showModalDialog = new ShowModalDialog();

        try {
            EventQueue.invokeLater(showModalDialog);
            Robot robot = new Robot();
            robot.delay(2000);

            Point origin = ShowModalDialog.lastShownDialog.getLocationOnScreen();
            Dimension dim = ShowModalDialog.lastShownDialog.getSize();
            robot.mouseMove((int)origin.getX() + (int)dim.getWidth()/2,
                            (int)origin.getY() + (int)dim.getHeight()/2);
            robot.mousePress(InputEvent.BUTTON1_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_MASK);

            robot.delay(2000);
            if (ShowModalDialog.count < 2) {
                throw new RuntimeException("TEST FAILED: second modal dialog was not shown");
            }

            /* click on second modal dialog to verify if it receives mouse events */
            synchronized (ShowModalDialog.monitor) {
                origin = ShowModalDialog.lastShownDialog.getLocationOnScreen();
                dim = ShowModalDialog.lastShownDialog.getSize();
                robot.mouseMove((int)origin.getX() + (int)dim.getWidth()/2,
                                (int)origin.getY() + (int)dim.getHeight()/2);
                robot.mousePress(InputEvent.BUTTON1_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_MASK);

                ShowModalDialog.monitor.wait(2000);
            }

            if (ShowModalDialog.count < 3) {
                throw new RuntimeException("TEST FAILED: second modal dialog didn't receive mouse events");
            }

        } catch (AWTException e) {
            e.printStackTrace();
            throw new RuntimeException("Some AWTException occurred");
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException("Test was interrupted");
        } finally {
            for (Window w : ShowModalDialog.toDispose) {
                w.setVisible(false);
                w.dispose();
            }
        }

        System.out.println("TEST PASSED");
    }

    public static void main(String[] args) {
        new ModalDialogOnNonEdt().start();
    }
}

class ShowModalDialog implements Runnable {
    static volatile int count = 0;
    static Object monitor = new Object();
    static Dialog lastShownDialog;
    static List<Window> toDispose = new ArrayList<>();

    public void run() {
        count++;
        Frame frame = new Frame("Frame #" + count);
        toDispose.add(frame);
        Dialog dialog = new Dialog(frame, "Modal Dialog #" + count, true);
        dialog.setSize(100, 100);
        dialog.setLocation(100, 100*count);
        dialog.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent me) {
                    System.out.println(me.toString());
                    if (ShowModalDialog.count < 2) {
                        Runnable runner = new ShowModalDialog();
                        new Thread(runner).start();
                    } else {
                        synchronized (monitor) {
                            ShowModalDialog.count++;
                            monitor.notifyAll();
                        }
                    }
                }
            });
        lastShownDialog = dialog;
        toDispose.add(dialog);
        dialog.setVisible(true);
    }
}
