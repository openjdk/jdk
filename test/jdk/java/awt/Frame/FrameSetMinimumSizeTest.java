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

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Robot;

/*
 * @test
 * @bug 4320050
 * @key headful
 * @summary Minimum size for java.awt.Frame is not being enforced.
 * @run main FrameSetMinimumSizeTest
 */

public class FrameSetMinimumSizeTest {
    private static Frame f;
    private static Robot robot;
    public static void main(String[] args) throws Exception {
        robot = new Robot();
        try {
            EventQueue.invokeAndWait(FrameSetMinimumSizeTest::createAndShowUI);
            robot.waitForIdle();
            robot.delay(500);

            test(
                new Dimension(200, 200),
                new Dimension(300, 300)
            );

            test(
                new Dimension(200, 400),
                new Dimension(300, 400)
            );

            test(
                new Dimension(400, 200),
                new Dimension(400, 300)
            );

            EventQueue.invokeAndWait(() -> f.setMinimumSize(null));
            test(
                new Dimension(200, 200),
                new Dimension(200, 200)
            );
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (f != null) {
                    f.dispose();
                }
            });
        }
    }

    private static void test(Dimension size, Dimension expected) throws Exception {
        robot.waitForIdle();
        robot.delay(250);
        EventQueue.invokeAndWait(() -> f.setSize(size));
        robot.waitForIdle();
        EventQueue.invokeAndWait(() -> verifyFrameSize(expected));
    }

    private static void createAndShowUI() {
        f = new Frame("Minimum Size Test");
        f.setSize(300, 300);
        f.setMinimumSize(new Dimension(300, 300));
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }

    private static void verifyFrameSize(Dimension expected) {
        if (f.getSize().width != expected.width || f.getSize().height != expected.height) {
            String message =
                    "Frame's setMinimumSize not honoured for the frame size: %s. Expected %s"
                            .formatted(f.getSize(), expected);
            throw new RuntimeException(message);
        }
    }
}
