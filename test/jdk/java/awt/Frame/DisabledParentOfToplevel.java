/*
 * Copyright (c) 2004, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/*
 * @test
 * @bug 5062118
 * @key headful
 * @summary Disabling of a parent should not disable Window.
 * @run main DisabledParentOfToplevel
 */

public class DisabledParentOfToplevel {
    private static Button okBtn;
    private static Window ww;
    private static Frame parentFrame;
    private static volatile Point p;
    private static volatile Dimension d;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(100);
        try {
            EventQueue.invokeAndWait(() -> {
                createAndShowUI();
            });
            robot.delay(1000);
            EventQueue.invokeAndWait(() -> {
                p = okBtn.getLocationOnScreen();
                d = okBtn.getSize();
            });
            robot.mouseMove(p.x + d.width / 2, p.x + d.height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(500);
            if (ww.isVisible()) {
                throw new RuntimeException("Window is visible but should be hidden: failure.");
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (parentFrame != null) {
                    parentFrame.dispose();
                }
            });
        }
    }

    private static void createAndShowUI() {
        parentFrame = new Frame("parentFrame");
        parentFrame.setSize(100, 100);
        parentFrame.setEnabled(false);
        ww = new Window(parentFrame);
        ww.setLayout(new BorderLayout());
        okBtn = new Button("Click to Close Me");
        ww.add(okBtn);
        ww.setSize(250, 250);
        ww.setLocation(110, 110);
        okBtn.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent me) {
                System.out.println("Pressed: close");
                ww.setVisible(false);
            }
        });
        parentFrame.setVisible(true);
        ww.setVisible(true);
        okBtn.requestFocus();
    }
}
