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
  @bug 6291736
  @summary ITEM_STATE_CHANGED triggered after List.removeAll(), XToolkit
  @key headful
  @run main ISCAfterRemoveAllTest
*/

import java.awt.AWTException;
import java.awt.FlowLayout;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.List;
import java.awt.Point;
import java.awt.Robot;

import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

public class ISCAfterRemoveAllTest implements ItemListener {
    List list;
    Frame frame;
    volatile boolean passed = true;

    public static void main(String[] args) throws Exception {
        ISCAfterRemoveAllTest test = new ISCAfterRemoveAllTest();
        test.start();
    }

    public void start () throws Exception {
        try {
            EventQueue.invokeAndWait(() -> {
                list = new List(4, false);
                frame = new Frame("ISCAfterRemoveAllTest");
                list.add("000");
                list.add("111");
                list.add("222");
                list.add("333");
                list.add("444");
                list.add("555");
                list.add("666");
                list.add("777");
                list.add("888");
                list.add("999");

                frame.add(list);
                frame.setLayout(new FlowLayout());
                frame.setSize(300, 200);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });
            test();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private void test() throws Exception {
        Robot r = new Robot();
        r.delay(1000);
        r.waitForIdle();
        EventQueue.invokeAndWait(() -> {
            Point loc = list.getLocationOnScreen();
            r.mouseMove(loc.x + list.getWidth() / 2, loc.y + list.getHeight() / 2);
        });
        r.delay(100);
        r.mousePress(InputEvent.BUTTON1_MASK);
        r.delay(10);
        r.mouseRelease(InputEvent.BUTTON1_MASK);
        r.delay(100);

        EventQueue.invokeAndWait(() -> {
            list.removeAll();

            // The interesting events are generated after removing
            list.addItemListener(this);
            r.delay(100);

            list.requestFocusInWindow();
            r.delay(100);
            if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() != list) {
                throw new RuntimeException("Test failed - list isn't focus owner.");
            }
        });

        r.delay(10);
        r.keyPress(KeyEvent.VK_UP);
        r.delay(10);
        r.keyRelease(KeyEvent.VK_UP);
        r.delay(100);

        // This is the test case for the 6299853 issue
        r.delay(10);
        r.keyPress(KeyEvent.VK_SPACE);
        r.delay(10);
        r.keyRelease(KeyEvent.VK_SPACE);
        r.delay(100);

        r.waitForIdle();

        if (!passed) {
            throw new RuntimeException("Test failed.");
        }
    }

    public void itemStateChanged(ItemEvent ie) {
        System.out.println(ie);
        // We shouldn't generate any events since the list is empty
        passed = false;
    }

}
