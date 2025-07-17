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

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Robot;
import java.awt.event.KeyEvent;

/*
 * @test
 * @key headful
 * @bug 4965227
 * @requires (os.family == "linux")
 * @summary tests that Shift+F10 during Window show doesn't cause deadlock- Linux only
 */

public class ShiftF10Test {
    private static Frame frame;

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(10);

            EventQueue.invokeLater(() -> {
                frame = new Frame("Deadlocking one");
                frame.setSize(100, 100);
                frame.setVisible(true);
            });

            for (int i = 0; i < 250; i++) {
                robot.keyPress(KeyEvent.VK_SHIFT);
                robot.keyPress(KeyEvent.VK_F10);
                robot.keyRelease(KeyEvent.VK_F10);
                robot.keyRelease(KeyEvent.VK_SHIFT);
                robot.delay(10);
            }
        } catch (Exception e) {
            throw new RuntimeException("Test Failed due to following error: ", e);
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
