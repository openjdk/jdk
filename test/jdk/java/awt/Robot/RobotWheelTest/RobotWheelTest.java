/*
 * Copyright (c) 1998, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Button;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Robot;
import java.util.concurrent.atomic.AtomicInteger;

import jdk.test.lib.Platform;

/*
 * @test 1.2 98/08/05
 * @key headful
 * @bug 4373478 8079255
 * @summary Test mouse wheel functionality of Robot
 * @author bchristi: area=Robot
 * @library /test/lib
 * @build jdk.test.lib.Platform
 * @run main RobotWheelTest
 */
public class RobotWheelTest {

    private static final int NUMTESTS = 20;

    private static AtomicInteger wheelRotation = new AtomicInteger();
    private static int wheelSign = Platform.isOSX() ? -1 : 1;

    static Robot robot;

    static void waitTillSuccess(int i) {
        boolean success = false;

        for (int t = 0; t < 5; t++) {
            if (i == wheelSign * wheelRotation.get()) {
                success = true;
                break;
            }
            System.out.printf(
                    "attempt #%d failed. wheelRotation = %d, expected value = %d\n",
                    t, wheelRotation.get(), i
            );
            robot.delay(100);
        }

        if (!success) {
            throw new RuntimeException("wheelRotation = " + wheelRotation.get()
                    + ", expected value = " + i);
        }
    }

    public static void main(String[] args) throws Exception {
        robot = new Robot();

        Frame frame = null;
        try {
            frame = new Frame();
            frame.setSize(200, 200);
            Button button = new Button("WheelButton");
            button.addMouseWheelListener(e -> wheelRotation.addAndGet(e.getWheelRotation()));
            frame.add(button);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            robot.setAutoDelay(100);
            robot.waitForIdle();

            Rectangle bounds = frame.getBounds();
            int centerX = bounds.x + bounds.width / 2;
            int centerY = bounds.y + bounds.height / 2;
            robot.mouseMove(centerX, centerY);
            robot.waitForIdle();

            for (int i = -NUMTESTS; i <= NUMTESTS; i++) {
                if (i == 0) {
                    continue;
                }

                wheelRotation.set(0);

                robot.mouseWheel(i);
                robot.waitForIdle();

                waitTillSuccess(i);
            }
        } finally {
            if (frame != null) {
                frame.dispose();
            }
        }
    }
}
