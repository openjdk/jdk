/*
 * Copyright (c) 2010, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/*
 * @test
 * @key headful
 * @bug 7184401
 * @summary Verify that events are not lost when EDT is interrupted.
 * @modules java.desktop/sun.awt
 * @run main InterruptEDTTest
 */

public class InterruptEDTTest {

    private static Frame frame = null;
    private static volatile Thread edt = null;
    private static volatile Robot robot = null;
    private static volatile int xLocation;
    private static volatile int yLocation;
    private static volatile int width;
    private static volatile int height;

    public static void main(String args[]) throws Exception {
        try {
            robot = new Robot();
            EventQueue.invokeAndWait(() -> {
                edt = Thread.currentThread();
                frame = new Frame("Frame");
                frame.addMouseListener(new MouseAdapter() {
                    public void mouseClicked(MouseEvent me) {
                        System.out.println("Mouse clicked Event: " + me);
                    }
                });
                frame.setBounds(350, 50, 400, 400);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });
            ((sun.awt.SunToolkit) (Toolkit.getDefaultToolkit())).realSync();
            EventQueue.invokeAndWait(() -> {
                xLocation = frame.getX();
                yLocation = frame.getY();
                width = frame.getWidth();
                height = frame.getHeight();
            });
            ((sun.awt.SunToolkit) (Toolkit.getDefaultToolkit())).realSync();
            EventQueue.invokeLater(() -> {
                robot.mouseMove(xLocation + width / 2, yLocation + height / 2);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            });
        } catch (Exception e) {
            e.printStackTrace();
        }

        edt.interrupt();
        ((sun.awt.SunToolkit) (Toolkit.getDefaultToolkit())).realSync();
        try {
            robot.mouseMove(xLocation + width / 3, yLocation + height / 3);
        } catch (Exception exx) {
            exx.printStackTrace();
        }
        System.out.println("Test passed.");
        EventQueue.invokeAndWait(() -> disposeFrame());
    }

    private static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
            frame = null;
        }
    }
}

