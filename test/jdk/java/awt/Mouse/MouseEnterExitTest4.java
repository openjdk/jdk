/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Robot;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/*
 * @test
 * @bug 4431868
 * @key headful
 * @summary Tests that window totally obscured by its child doesn't receive
 *          enter/exit events when located over another frame
 * @run main MouseEnterExitTest4
 */

public class MouseEnterExitTest4 {
    static Button button = new Button("Button");
    static Frame frame = new Frame("Mouse Enter/Exit test");
    static Window window = new Window(frame);
    static MouseListener listener = new MouseAdapter() {
        public void mouseEntered(MouseEvent e) {
            throw new RuntimeException("Test failed due to Mouse Enter event");
        }

        public void mouseExited(MouseEvent e) {
            throw new RuntimeException("Test failed due to Mouse Exit event");
        }
    };

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();

        robot.setAutoDelay(100);
        try {
            EventQueue.invokeAndWait(() -> {
                button.setBackground(Color.red);
                window.add(button);
                frame.setBounds(100, 100, 300, 300);
                window.setBounds(200, 200, 100, 100);
                window.addMouseListener(listener);
                window.setVisible(true);
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(200);
            EventQueue.invokeAndWait(() -> robot.mouseMove(
                    frame.getLocationOnScreen().x + frame.getSize().width / 2,
                    frame.getLocationOnScreen().y + frame.getSize().height / 2));
            robot.waitForIdle();
            robot.delay(200);
            EventQueue.invokeAndWait(() -> robot.mouseMove(
                    window.getLocationOnScreen().x + window.getSize().width * 2,
                    window.getLocationOnScreen().y + window.getSize().height / 2));
            robot.waitForIdle();
            robot.delay(500);
            System.out.println("Test Passed");

        } finally {
            EventQueue.invokeAndWait(() -> {
               if (frame != null) {
                   frame.dispose();
               }
               if (window != null) {
                   window.dispose();
               }
            });
        }
    }
}
