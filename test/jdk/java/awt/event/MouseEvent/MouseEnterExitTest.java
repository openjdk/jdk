/*
 * Copyright (c) 2007, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.List;
import java.awt.Point;
import java.awt.Robot;
import java.awt.TextArea;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/*
 * @test
 * @key headful
 * @bug 4454304
 * @summary On Solaris, TextArea triggers MouseEntered when the mouse is inside the component
 * @run main MouseEnterExitTest
 */
public class MouseEnterExitTest {

    private static Frame frame;

    private volatile static boolean entered = false;
    private volatile static boolean exited = false;
    private volatile static boolean passed = true;

    private volatile static Point compAt;
    private volatile static Dimension compSize;

    private static final MouseListener mouseListener = new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent e) {
            System.out.println(
                "MouseEntered component " + e.getSource().getClass().getName());
            if (entered) {
                passed = false;
            }
            entered = true;
            exited = false;
        }

        @Override
        public void mouseExited(MouseEvent e) {
            System.out.println(
                "MouseExited component " + e.getSource().getClass().getName());
            if (exited) {
                passed = false;
            }
            entered = false;
            exited = true;
        }
    };

    private static void initializeGUI() {
        frame = new Frame("MouseEnterExitTest");
        frame.setLayout(new FlowLayout());
        List list = new List(4);
        for (int i = 0; i < 10; i++) {
            list.add("item " + i);
        }
        list.addMouseListener(mouseListener);
        frame.add(list);

        TextArea textArea = new TextArea("TextArea", 10, 20);
        textArea.addMouseListener(mouseListener);
        frame.add(textArea);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(100);
            robot.setAutoWaitForIdle(true);

            EventQueue.invokeAndWait(MouseEnterExitTest::initializeGUI);
            robot.waitForIdle();

            EventQueue.invokeAndWait(() -> {
                compAt = frame.getLocationOnScreen();
                compSize = frame.getSize();
            });
            compAt.y += compSize.getHeight() / 2;
            int xr = compAt.x + compSize.width + 1;
            for (int i = compAt.x - 5; (i < xr) && passed; i++) {
                robot.mouseMove(i, compAt.y);
            }

            if (!passed || entered || !exited) {
                throw new RuntimeException(
                    "MouseEnterExitTest FAILED. MouseEntered/MouseExited "
                        + "not properly triggered. Please see the log");
            }
            System.out.println("Test PASSED");
        } finally {
            EventQueue.invokeAndWait(MouseEnterExitTest::disposeFrame);
        }
    }

    private static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
        }
    }
}
