/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4169461
 * @summary Test for Motif Scrollbar unit increment
 * @key headful
 * @run main UnitIncrementTest
 */

import javax.swing.UIManager;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Scrollbar;
import java.awt.event.AdjustmentEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

public class UnitIncrementTest {
    private static Frame frame;
    private static Scrollbar scrollbar;
    private static final java.util.List<AdjustmentEvent> eventsList = new ArrayList<>();
    private static final int UNIT_INCREMENT_VALUE = 5;
    private static final int INCREMENTS_COUNT = 10;
    private static volatile Rectangle scrollbarBounds;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("com.sun.java.swing.plaf.motif.MotifLookAndFeel");

        try {
            EventQueue.invokeAndWait(UnitIncrementTest::createAndShowUI);
            test();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void createAndShowUI() {
        frame = new Frame("UnitIncrementTest");

        scrollbar = new Scrollbar(Scrollbar.HORIZONTAL);

        scrollbar.setUnitIncrement(UNIT_INCREMENT_VALUE);
        scrollbar.setBlockIncrement(20);

        scrollbar.addAdjustmentListener(e -> {
            eventsList.add(e);
            System.out.println(e);
        });

        frame.add(scrollbar);

        frame.setSize(300, 100);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void test() throws Exception {
        Robot robot = new Robot();
        robot.waitForIdle();
        robot.setAutoDelay(25);
        robot.delay(500);

        EventQueue.invokeAndWait(() -> {
            Point locationOnScreen = scrollbar.getLocationOnScreen();
            Dimension size = scrollbar.getSize();
            scrollbarBounds = new Rectangle(locationOnScreen, size);
        });

        robot.mouseMove(scrollbarBounds.x + scrollbarBounds.width - 10,
                scrollbarBounds.y + scrollbarBounds.height / 2);

        for (int i = 0; i < INCREMENTS_COUNT; i++) {
            robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
            robot.delay(150);
        }

        robot.waitForIdle();
        robot.delay(250);

        if (eventsList.size() != INCREMENTS_COUNT) {
            throw new RuntimeException("Wrong number of events: " + eventsList.size());
        }

        int oldValue = 0;
        for (AdjustmentEvent event : eventsList) {
            System.out.println("\nChecking event " + event);

            int diff = event.getValue() - oldValue;
            System.out.printf("diff: %d - %d = %d\n", event.getValue(), oldValue, diff);

            if (diff != UNIT_INCREMENT_VALUE) {
                throw new RuntimeException("Unexpected adjustment value: %d".formatted(diff));
            }

            oldValue = event.getValue();
        }
    }
}
