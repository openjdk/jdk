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
/*
 * @test
 * @bug 4474893
 * @summary Component.nextFocusHelper should search for first visible focus cycle root ancst
 * @key headful
 * @run main NextFocusHelperTest
*/

import java.awt.Button;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.FlowLayout;
import java.awt.KeyboardFocusManager;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;

public class NextFocusHelperTest {
    static Panel panel;
    static Frame frame;
    static Button btn1;
    static Button btn3;
    static Button hideButton;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(100);
        try {
            EventQueue.invokeAndWait(() -> createTestUI());
            robot.waitForIdle();
            robot.delay(1000);

            Point loc = btn1.getLocationOnScreen();
            Dimension dim = btn1.getSize();
            robot.mouseMove(loc.x + dim.width/2, loc.y + dim.height/2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(500);

            Point loc1 = hideButton.getLocationOnScreen();
            Dimension dim1 = hideButton.getSize();
            robot.mouseMove(loc1.x + dim1.width/2, loc1.y + dim1.height/2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();

            if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner()
                    instanceof Button btn) {
                if (!btn.getLabel().equals("Button 3")) {
                    throw new RuntimeException("Wrong button has focus");
                }
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void createTestUI() {
        frame = new Frame("NextFocusHelperTest Frame");
        frame.setLayout(new FlowLayout());

        panel = new Panel();
        panel.setFocusCycleRoot(true);
        btn1 = new Button("Button In Panel");
        panel.add(btn1);

        hideButton = new Button("Hide Panel");
        hideButton.setFocusable(false);
        hideButton.addActionListener(e -> {
            panel.setVisible(false);
        });

        frame.add(new Button("Button 1"));
        frame.add(panel);
        frame.add(new Button("Button 3"));
        frame.add(hideButton);
        frame.setSize(200, 200);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}

