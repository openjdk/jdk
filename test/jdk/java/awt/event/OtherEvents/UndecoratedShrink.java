/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Robot;

/*
 * @test
 * @bug 4418155
 * @key headful
 * @summary Checks Undecorated Frame repaints when shrinking
 */

public class UndecoratedShrink extends Frame {
    private static boolean passed = false;
    private static UndecoratedShrink frame;

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            EventQueue.invokeAndWait(() -> {
                frame = new UndecoratedShrink();
                frame.setUndecorated(true);
                frame.setSize(100, 100);
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);

            EventQueue.invokeAndWait(() -> {
                frame.setSize(50, 50);
                frame.repaint();
            });
            robot.waitForIdle();
            robot.delay(500);

            if (!passed) {
                throw new RuntimeException("Test Fails." +
                        " Frame does not get repainted");
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    @Override
    public void paint(Graphics g) {
        passed = true;
    }
}
