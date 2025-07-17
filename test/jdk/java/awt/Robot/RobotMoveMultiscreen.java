/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4288230
  @summary Tests that Robot can move mouse to another screen
  @key headful
*/

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;

import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

public class RobotMoveMultiscreen {
    static volatile int x_dest = 20;
    static volatile int y_dest = 20;
    static Frame frame;
    static volatile Boolean testCondition = false;

    public static void main(String[] args) throws Exception {
        GraphicsDevice[] devs =
                GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();

        if (devs.length <= 1) {
            System.out.println("Minimum 2 display screens are required" +
                    " for the test, Found " + devs.length);
            return;
        }
        try {
            EventQueue.invokeAndWait(() -> {
                GraphicsDevice workDev = devs[devs.length - 1];
                GraphicsConfiguration config = workDev.getDefaultConfiguration();
                Rectangle bounds = config.getBounds();
                x_dest = bounds.x + bounds.width / 2;
                y_dest = bounds.y + bounds.height / 2;
                frame = new Frame("Listening frame");
                frame.addMouseMotionListener(new MouseMotionAdapter() {
                    public void mouseMoved(MouseEvent e) {
                        testCondition = true;
                    }
                });
                frame.setLocation(x_dest,y_dest);
                frame.setSize(100,100);
                frame.setVisible(true);
            });

            Robot robot = new Robot();
            robot.delay(1000);
            robot.waitForIdle();
            robot.mouseMove(x_dest+50, y_dest+50);
            robot.waitForIdle();
            EventQueue.invokeAndWait(() -> {
                if (testCondition == false) {
                    throw new RuntimeException("Can't move to another display");
                }
            });

            System.out.println("Test Pass!!");
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
