/*
 * Copyright (c) 2001, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 4411368 8381565
 * @key headful
 * @summary tests the app doesn't crash when a drop target registered
 *           on a file dialog is unregistered
 * @run main/othervm/timeout=120 -Dsun.awt.disableGtkFileDialogs=true FileDialogDropTargetTest
 */

import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Robot;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;

public class FileDialogDropTargetTest {
    private static Robot robot;
    private static Frame frame;

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        frame = new Frame();

        try {
            test(10);
            test(100);
        } finally {
            EventQueue.invokeAndWait(frame::dispose);
        }

        System.out.println("Test passed");
    }

    private static void test(int delay) {
        System.out.printf("\nTesting FileDialogDropTarget with %d ms delay\n", delay);
        for (int i = 0; i < 100; i++) {
            final FileDialog fileDialog = new FileDialog(frame);

            fileDialog.setDropTarget(new DropTarget(fileDialog,
                    new DropTargetAdapter() {
                        public void drop(DropTargetDropEvent dtde) {
                        }
                    }));
            fileDialog.pack();

            new Thread(() -> {
                robot.delay(delay);
                fileDialog.dispose();
            }).start();

            fileDialog.setVisible(true);
        }

        robot.waitForIdle();
        robot.delay(1000);
    }
}
