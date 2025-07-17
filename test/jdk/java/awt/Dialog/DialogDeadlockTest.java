/*
 * Copyright (c) 2004, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 5006427
  @summary Shows many modal dialog and checks if there is a deadlock or thread race.
  @key headful
  @run main DialogDeadlockTest
*/

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Dialog;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Window;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;
import java.util.List;

public class DialogDeadlockTest {
    public static final int MAX_COUNT = 200;
    private static Dialog lastDialog;
    private static Runnable r;
    private static volatile int count;
    private static volatile int cumul;

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        DialogDeadlockTest ddt = new DialogDeadlockTest();
        ddt.start();
    }

    public void start() {
        final Frame frame = new Frame("abc");
        final List<Window> toDispose = new LinkedList<>();

        try {
            frame.setLocation(300, 0);
            frame.add(new Button("def"));
            frame.pack();
            frame.setVisible(true);
            cumul = 0;

            r = new Runnable() {
                public void run() {
                    count++;
                    if (count < 10) {
                        Dialog xlastDialog = lastDialog;
                        cumul += count;
                        Dialog d = new Dialog(frame, "Dialog "
                                + cumul, true);
                        d.setLayout(new BorderLayout());
                        d.add(new Button("button " + count), BorderLayout.CENTER);
                        d.pack();
                        toDispose.add(d);
                        lastDialog = d;
                        EventQueue.invokeLater(r);
                        d.setVisible(true);
                        if (xlastDialog != null) {
                            xlastDialog.setVisible(false);
                        } else {
                            if (cumul < MAX_COUNT) {
                                count = 0;
                                lastDialog = null;
                                EventQueue.invokeLater(r);
                            }
                        }
                    } else {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ignore) {
                        }
                        lastDialog.setVisible(false);
                        lastDialog = null;
                    }
                }
            };
            try {
                EventQueue.invokeAndWait(r);
            } catch (InterruptedException ignore) {
            } catch (Exception e) {
                throw new RuntimeException("Unexpected exception: "
                        + e.getLocalizedMessage());
            }
            while (cumul < MAX_COUNT - 1) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignore) {}
            }
            System.out.println("Test PASSED");
        } finally {
            try {
                EventQueue.invokeAndWait(() -> {
                    frame.setVisible(false);
                    frame.dispose();
                    for (Window w: toDispose) {
                        w.dispose();
                    }
                });
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
