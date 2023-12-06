/*
 * Copyright (c) 2004, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 5072554
  @summary Tests that vetoing focus doesn't generate extra PropertyChange notification.
  @key headful
  @run main ExtraPropChangeNotifVetoingTest
*/

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.Panel;
import java.awt.Robot;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.lang.reflect.InvocationTargetException;

public class ExtraPropChangeNotifVetoingTest extends Panel {
    Button one = new Button("One");
    Button two = new Button("Two");
    Robot robot;
    static Frame frame;

    int i = 0;

    public void init() {
        try {
            robot = new Robot();
        } catch (AWTException e) {
            throw new RuntimeException("Error: unable to create robot", e);
        }

        setLayout(new FlowLayout());
        add(one);
        add(two);

        KeyboardFocusManager.getCurrentKeyboardFocusManager().
                addVetoableChangeListener("permanentFocusOwner",
                        new VetoableChangeListener() {
                    public void vetoableChange(PropertyChangeEvent e) throws PropertyVetoException {
                        System.out.println((i++) + ". Old=" + e.getOldValue() + ", New=" + e.getNewValue());

                        if (e.getOldValue() == e.getNewValue()) {
                            throw new RuntimeException("Test failed!");
                        }

                        if (e.getNewValue() == two) {
                            System.out.println("VETOING");
                            throw new PropertyVetoException("vetoed", e);
                        }
                    }
                });
        setVisible(true);
    }

    public void start() throws InterruptedException, InvocationTargetException {
        EventQueue.invokeAndWait(one::requestFocusInWindow);
        robot.waitForIdle();
        robot.delay(200);
        EventQueue.invokeAndWait(two::requestFocusInWindow);
        robot.waitForIdle();
        robot.delay(200);
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        ExtraPropChangeNotifVetoingTest test = new ExtraPropChangeNotifVetoingTest();
        try {
            EventQueue.invokeAndWait(() -> {
                frame = new Frame("ExtraPropChangeNotifVetoingTest");
                frame.setLayout(new BorderLayout());
                frame.add(test, BorderLayout.CENTER);
                test.init();
                frame.setLocationRelativeTo(null);
                frame.pack();
                frame.setVisible(true);
            });
            test.start();
        } finally {
            if (frame != null) {
                EventQueue.invokeAndWait(frame::dispose);
            }
        }
    }
}
