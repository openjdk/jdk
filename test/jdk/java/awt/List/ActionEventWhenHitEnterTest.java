/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 4234245
 * @summary the actionEvent is not invoked when hit enter on list.
 * @key headful
 * @run main ActionEventWhenHitEnterTest
 */

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.IllegalComponentStateException;
import java.awt.List;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;

public class ActionEventWhenHitEnterTest
        implements ActionListener, ItemListener {

    volatile boolean passed1;
    volatile boolean passed2;
    volatile Point pt;
    List list;
    Frame frame;

    public static void main(final String[] args) throws Exception {
        ActionEventWhenHitEnterTest app = new ActionEventWhenHitEnterTest();
        app.doTest();
    }

    public ActionEventWhenHitEnterTest() {
        list = new List(7);
        for (int i = 0; i < 10; i++) {
            list.add("Item " + i);
        }
        list.addItemListener(this);
        list.addActionListener(this);
    }

    public void actionPerformed(ActionEvent ae) {
        passed1 = true;
        System.out.println("--> Action event invoked: " + ae.getSource());
    }

    public void itemStateChanged(ItemEvent ie) {
        passed2 = true;
        System.out.println("--> Item state changed:" + ie.getSource());
    }

    public void doTest() throws Exception {
        EventQueue.invokeAndWait(() -> {
            frame = new Frame("ActionEventWhenHitEnterTest");
            frame.add(list);
            frame.setSize(200, 200);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });

        try {
            Robot robot = new Robot();
            robot.setAutoDelay(100);
            robot.waitForIdle();
            robot.delay(1000);

            EventQueue.invokeAndWait(() -> {
                pt = list.getLocationOnScreen();
            });

            if (pt.x != 0 || pt.y != 0) {
                robot.mouseMove(pt.x + list.getWidth() / 2,
                                pt.y + list.getHeight() / 2);
                robot.waitForIdle();
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                robot.waitForIdle();

                robot.keyPress(KeyEvent.VK_ENTER);
                robot.keyRelease(KeyEvent.VK_ENTER);
            }

            robot.waitForIdle();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }

        if (!passed1 || !passed2) {
            throw new RuntimeException("ActionEventWhenHitEnterTest FAILED");
        }
        System.out.println("Test PASSED");

    }

}
