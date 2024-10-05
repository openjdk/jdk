/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.CountDownLatch;

/*
 * @test
 * @bug 5097243
 * @summary Tests that FileDialog can be closed by ESC any time
 * @key headful
 * @run main DoubleActionESC
 */

public class DoubleActionESC {
    private static Frame f;
    private static Button showBtn;
    private static FileDialog fd;
    private static Robot robot;
    private static volatile Point p;
    private static volatile Dimension d;
    private static volatile CountDownLatch latch;
    private static final int REPEAT_COUNT = 2;

    public static void main(String[] args) throws Exception {
        latch = new CountDownLatch(1);

        robot = new Robot();
        robot.setAutoDelay(100);
        try {
            EventQueue.invokeAndWait(() -> {
                createAndShowUI();
            });

            robot.delay(1000);
            EventQueue.invokeAndWait(() -> {
                p = showBtn.getLocationOnScreen();
                d = showBtn.getSize();
            });

            for (int i = 0; i < REPEAT_COUNT; ++i) {
                Thread thread = new Thread(() -> {
                    robot.mouseMove(p.x + d.width / 2, p.y + d.height / 2);
                    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                });
                thread.start();
                robot.delay(3000);

                Thread thread1 = new Thread(() -> {
                    robot.keyPress(KeyEvent.VK_ESCAPE);
                    robot.keyRelease(KeyEvent.VK_ESCAPE);
                    robot.waitForIdle();
                });
                thread1.start();
                robot.delay(3000);
            }

            latch.await();
            if (fd.isVisible()) {
                throw new RuntimeException("File Dialog is not closed");
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (f != null) {
                    f.dispose();
                }
            });
        }
    }

    public static void createAndShowUI() {
        f = new Frame("DoubleActionESC Test");
        showBtn = new Button("Show File Dialog");
        fd = new FileDialog(f);
        showBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (e.getSource() == showBtn) {
                    fd.setSize(200, 200);
                    fd.setLocation(200, 200);
                    fd.setVisible(true);
                    latch.countDown();
                }
            }
        });
        f.add(showBtn);
        f.setSize(300, 200);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }
}
