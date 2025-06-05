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
/*
 * @test
 * @bug 6180261
 * @summary Test that auto-transfer doesn't happen when there are pending focus requests
 * @key headful
 * @run main TestDisabledAutoTransfer
*/

import java.awt.Button;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.util.concurrent.atomic.AtomicBoolean;

public class TestDisabledAutoTransfer {
    static Frame frame;
    static Robot robot;
    Button b1;
    Button desired;
    AtomicBoolean focused = new AtomicBoolean();
    ActionListener mover;
    volatile Point loc;
    volatile Dimension dim;

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        try {
            TestDisabledAutoTransfer test = new TestDisabledAutoTransfer();
            test.createTestUI();
            robot.waitForIdle();
            robot.delay(1000);
            test.doTest();
        } finally {
            if (frame != null) {
                frame.dispose();
            }
        }
    }

    public void createTestUI() {
        frame = new Frame("TestDisabledAutoTransfer");
        frame.setLayout(new FlowLayout());
        desired = new Button("Desired");
        FocusAdapter watcher = new FocusAdapter() {
                public void focusGained(FocusEvent e) {
                    synchronized(focused) {
                        focused.set(true);
                    }
                }
            };
        b1 = new Button("Press to disable");
        mover = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                desired.requestFocus();
                    ((Component)e.getSource()).setEnabled(false);
                }
            };
        b1.addFocusListener(watcher);
        desired.addFocusListener(watcher);
        frame.add(b1);
        Button misc = new Button("Next");
        frame.add(misc);
        misc.addFocusListener(watcher);
        frame.add(desired);
        frame.setSize(200, 200);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.validate();

    }

    public void doTest() {

        loc = b1.getLocationOnScreen();
        dim = b1.getSize();
        robot.mouseMove(loc.x + dim.width / 2, loc.y + dim.height / 2);
        robot.waitForIdle();
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        b1.requestFocus();

        try {
            synchronized(focused) {
                if (!focused.get()) {
                    focused.wait(1000);
                }
            }
        } catch (InterruptedException ie) {
            throw new RuntimeException("Test was interrupted");
        }

        if (!focused.get()) {
            throw new RuntimeException("b1 didn't get focus");
        }
        focused.set(false);

        b1.addActionListener(mover);
        robot.mouseMove(loc.x + dim.width / 2, loc.y + dim.height / 2);
        robot.waitForIdle();
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();

        try {
            synchronized(focused) {
                if (!focused.get()) {
                    focused.wait(1000);
                }
            }
        } catch (InterruptedException ie) {
            throw new RuntimeException("Test was interrupted");
        }

        if (!focused.get()) {
            throw new RuntimeException("none got focus");
        }

        if (!desired.isFocusOwner()) {
            throw new RuntimeException("desired didn't get focus");
        }
    }

}

