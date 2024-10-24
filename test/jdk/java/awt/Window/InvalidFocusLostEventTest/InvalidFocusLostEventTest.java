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
 * @bug 4397883
 * @summary Tests that non-focusable Window doesn't grab focus
 * @key headful
 * @run main InvalidFocusLostEventTest
 */

import java.awt.Button;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;

public class InvalidFocusLostEventTest implements ActionListener {
    private static Frame f;
    private static Button b;
    private static KeyboardFocusManager fm;
    private static volatile Point bp;
    private static volatile int width, height;
    private static Robot robot;

    public static void main(String[] args) throws Exception {
        try {
            InvalidFocusLostEventTest test = new InvalidFocusLostEventTest();
            EventQueue.invokeAndWait(() -> test.createUI());
            runTest();
            // we should check focus after all events are processed,
            // since focus transfers are asynchronous
            robot.waitForIdle();
            if (fm.getFocusOwner() != b) {
                throw new RuntimeException("Failed: focus was lost");
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (f != null) {
                    f.dispose();
                }
            });
        }
    }

    private void createUI() {
        f = new Frame("InvalidFocusLostEventTest");
        b = new Button("Press me");
        fm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        b.addActionListener(this);
        f.add(b);
        f.pack();
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }

    private static void runTest() throws Exception {
        robot = new Robot();
        robot.setAutoDelay(100);
        robot.setAutoWaitForIdle(true);
        EventQueue.invokeAndWait(() -> {
            bp = b.getLocationOnScreen();
            width = b.getWidth();
            height = b.getHeight();
        });
        robot.mouseMove(bp.x + width / 2, bp.y + height / 2 );
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    public void actionPerformed(ActionEvent ev) {
        // pop up a non-focusable window
        Window win = new Window(f);
        win.setFocusableWindowState(false);
    }
}
