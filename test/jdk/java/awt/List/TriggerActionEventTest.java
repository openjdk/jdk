/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 6190746
  @summary Tests that list trigger ActionEvent when double clicking a programmatically selected item, XToolkit
  @key headful
  @run main TriggerActionEventTest
*/

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.List;
import java.awt.Point;
import java.awt.Robot;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;

import java.lang.reflect.InvocationTargetException;

public class TriggerActionEventTest implements ActionListener {
    final Object LOCK = new Object();
    final int ACTION_TIMEOUT = 1000;

    List list;
    Frame frame;
    volatile Point loc;
    private volatile boolean passed = false;

    public static void main(String[] args) throws Exception {
        TriggerActionEventTest TrgrActnEvntTest = new TriggerActionEventTest();
        TrgrActnEvntTest.test(new TestState(0));
        TrgrActnEvntTest.test(new TestState(3));
    }

    private void test(TestState currentState) throws Exception {

        synchronized (LOCK) {
            System.out.println("begin test for: " + currentState);

            EventQueue.invokeAndWait(() -> {
                list = new List();

                list.clear();
                list.add("0");
                list.add("1");
                list.add("2");
                list.add("3");
                list.addActionListener(this);

                int index = currentState.getSelectedIndex();

                list.select(index);

                frame = new Frame("TriggerActionEventTest");
                frame.setLayout(new BorderLayout());
                frame.add(BorderLayout.SOUTH, list);
                frame.setSize(200, 200);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });


            Robot r = new Robot();
            r.delay(500);
            EventQueue.invokeAndWait(() -> {
                loc = list.getLocationOnScreen();
            });

            r.mouseMove(loc.x + 10, loc.y + 10);
            r.mousePress(InputEvent.BUTTON1_MASK);
            r.delay(10);
            r.mouseRelease(InputEvent.BUTTON1_MASK);
            r.mousePress(InputEvent.BUTTON1_MASK);
            r.delay(10);
            r.mouseRelease(InputEvent.BUTTON1_MASK);
            r.delay(10);


            LOCK.wait(ACTION_TIMEOUT);

            System.out.println(currentState);
            if (!passed) {
                throw new RuntimeException("Test failed");
            }
            this.passed = false;

            EventQueue.invokeAndWait(() -> {
                list.removeActionListener(this);
                frame.remove(list);
                frame.setVisible(false);
            });

        }
    }

    public void actionPerformed (ActionEvent ae) {
        synchronized (LOCK) {
            System.out.println(ae);
            passed = true;
            LOCK.notifyAll();
        }
    }

}

class TestState {
    private final int selectedIndex;

    public TestState(int selectedIndex) {
        this.selectedIndex = selectedIndex;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public String toString() {
        return ""+selectedIndex;
    }

}
