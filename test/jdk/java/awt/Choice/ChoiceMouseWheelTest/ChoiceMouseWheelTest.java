/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @key headful
 * @bug 7050935
 * @summary closed/java/awt/Choice/WheelEventsConsumed/WheelEventsConsumed.html fails on win32
 * @library /java/awt/regtesthelpers
 * @build Util
 * @run main ChoiceMouseWheelTest
 */

import test.java.awt.regtesthelpers.Util;

import java.awt.Choice;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class ChoiceMouseWheelTest extends Frame {

    private volatile boolean itemChanged = false;
    private volatile boolean wheelMoved = false;
    private volatile boolean frameExited = false;
    private final Choice choice;

    public static void main(String[] args) throws Exception {
        ChoiceMouseWheelTest test = Util.invokeOnEDT(ChoiceMouseWheelTest::new);
        try {
            test.test();
        } finally {
            EventQueue.invokeAndWait(test::dispose);
        }
    }

    ChoiceMouseWheelTest() {
        super("ChoiceMouseWheelTest");
        setLayout(new FlowLayout());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                e.getWindow().dispose();
            }
        });

        choice = new Choice();
        for(int i = 0; i < 50; i++) {
            choice.add(Integer.toString(i));
        }

        choice.addItemListener(e -> itemChanged = true);
        choice.addMouseWheelListener(e -> wheelMoved = true);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                frameExited = true;
            }
        });

        add(choice);
        setSize(200, 300);
        setLocationRelativeTo(null);
        setVisible(true);
        toFront();
    }

    private void test() throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(20);
        robot.waitForIdle();
        robot.delay(500);

        Rectangle choiceBounds = Util.invokeOnEDT(() -> {
            Point pt = choice.getLocationOnScreen();
            Dimension size = choice.getSize();
            return new Rectangle(pt, size);
        });

        int x = choiceBounds.x + choiceBounds.width / 3;
        robot.mouseMove(x, choiceBounds.y + choiceBounds.height / 2);

        // Test mouse wheel over the choice
        String name = Toolkit.getDefaultToolkit().getClass().getName();
        boolean isXtoolkit = name.equals("sun.awt.X11.XToolkit");
        boolean isLWCToolkit = name.equals("sun.lwawt.macosx.LWCToolkit");

        // mouse wheel doesn't work for the choice on X11 and Mac, so skip it
        if (!isXtoolkit && !isLWCToolkit) {
            robot.mouseWheel(1);
            robot.waitForIdle();

            if (!wheelMoved || !itemChanged) {
                throw new RuntimeException("Mouse Wheel over the choice failed!");
            }
        }

        // Test mouse wheel over the drop-down list
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();

        frameExited = false;

        Rectangle frameBounds = Util.invokeOnEDT(this::getBounds);

        int y = frameBounds.y + frameBounds.height;
        while (!frameExited && y >= frameBounds.y) { // move to the bottom of drop-down list
            robot.mouseMove(x, --y);
            robot.waitForIdle();
        }

        if (y < frameBounds.y) {
            throw new RuntimeException("Could not enter drop-down list!");
        }

        y -= choiceBounds.height / 2;
        robot.mouseMove(x, y); // move to the last visible item in the drop-down list
        robot.waitForIdle();

        int scrollDirection = isLWCToolkit ? -1 : 1;
        // wheel to the last item
        robot.mouseWheel(scrollDirection * choice.getItemCount());
        robot.waitForIdle();

        // click the last item
        itemChanged = false;
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();

        if (!itemChanged || choice.getSelectedIndex() != choice.getItemCount() - 1) {
            throw new RuntimeException("Mouse Wheel scroll position error!");
        }
    }
}
