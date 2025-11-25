/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Dialog;
import java.awt.EventQueue;
import java.awt.Frame;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

/*
 * @test
 * @bug 6278150
 * @key headful
 * @summary Initially modal blocked window causes modal dialog to lose focus
 * @run main DialogLosesFocusTest
 */

public class DialogLosesFocusTest {
    private static Frame parent;
    private static Dialog dialog;
    private static Frame blocked;
    private static volatile boolean failed;

    public static void main(String[] args) throws Exception {
        try {
            createAndShowUI();

            sleepForMsecs(10000);

            if (failed) {
               throw new RuntimeException("Test Failed");
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (parent != null) {
                    parent.dispose();
                }
                if (dialog != null) {
                    dialog.dispose();
                }
                if (blocked != null) {
                    blocked.dispose();
                }
            });
        }
    }

    public static void createAndShowUI() throws Exception {
        EventQueue.invokeAndWait(() -> {
            parent = new Frame("Parent frame");
            parent.setBounds(0, 0, 300, 100);
            parent.setVisible(true);
        });

        sleepForMsecs(1000);

        EventQueue.invokeLater(() -> {
            dialog = new Dialog(parent, "Modal dialog", Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setBounds(100, 120, 300, 100);
            dialog.setVisible(true);
        });

        sleepForMsecs(1000);

        EventQueue.invokeAndWait(() -> {
            blocked = new Frame("Blocked frame");
            blocked.setBounds(200, 240, 300, 100);
            blocked.addWindowListener(new WindowAdapter() {
                @Override
                public void windowActivated(WindowEvent we) {
                    if (dialog.isVisible()) {
                        failed = true;
                    }
                }
            });
            blocked.setVisible(true);
        });
    }

    private static void sleepForMsecs(int t) {
        try {
            Thread.sleep(t);
        } catch (Exception z) {}
    }
}
