/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4100671
  @summary Tests that after removing/adding a component can be still access.
  @key headful
*/

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.ScrollPane;
import java.awt.Toolkit;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;

public class ScrollPaneRemoveAdd {
    Button button;
    ScrollPane pane;
    Semaphore actionSema;
    Frame frame;
    Robot robot;

    public static void main(String[] args) throws Exception {
        ScrollPaneRemoveAdd scrollTest = new ScrollPaneRemoveAdd();
        scrollTest.init();
        scrollTest.start();
    }

    public void init() throws Exception {
        EventQueue.invokeAndWait(() -> {
            frame = new Frame("Scroll pane Add/Remove");

            pane = new ScrollPane(ScrollPane.SCROLLBARS_ALWAYS);
            button = new Button("press");
            pane.add(button);
            frame.add(pane);
            actionSema = new Semaphore();
            button.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    actionSema.raise();
                }
            });
        });
        robot = new Robot();
    }

    public void start() throws Exception {
        try {
            EventQueue.invokeAndWait(() -> {
                    frame.pack();
                    frame.setLocationRelativeTo(null);
                    frame.setVisible(true);
                    pane.remove(0);
                    pane.add(button);
            });

            Point buttonLoc = button.getLocationOnScreen();
            Dimension buttonSize = button.getSize();
            robot.mouseMove(buttonLoc.x + buttonSize.width / 2, buttonLoc.y + buttonSize.height / 2);
            robot.delay(50);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(50);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            Toolkit.getDefaultToolkit().sync();
            robot.delay(50);
            robot.waitForIdle();
            try {
                actionSema.doWait(1000);
            } catch (Exception ie) {
                throw new RuntimeException("My sleep was interrupted");
            }
            robot.delay(50);
            robot.waitForIdle();
            if (!actionSema.getState()) {
                throw new RuntimeException("ScrollPane doesn't handle correctly add after remove");
            }
        } finally {
            EventQueue.invokeLater(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
