/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4075950
 * @summary Test for functionality of Control Click on Scrollbar
 * @key headful
 * @run main ScrollbarCtrlClickTest
 */

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Scrollbar;
import java.awt.TextArea;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ScrollbarCtrlClickTest {
    private static Frame frame;
    private static TextArea ta;
    private static Scrollbar scrollbar;
    private static final CountDownLatch latch = new CountDownLatch(1);
    private static volatile Rectangle sbBounds;

    public static void main(String[] args) throws Exception {
        try {
            EventQueue.invokeAndWait(ScrollbarCtrlClickTest::initAndShowGUI);
            test();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void initAndShowGUI() {
        frame = new Frame("ScrollbarDimensionTest");
        ta = new TextArea("", 30, 100);


        scrollbar = new Scrollbar(Scrollbar.VERTICAL,
                0, 10, 0, 20);

        // Just setting layout so scrollbar thumb will be big enough to use
        frame.setLayout(new BorderLayout());
        frame.add("East", scrollbar);
        frame.add("West", ta);

        scrollbar.addAdjustmentListener(e -> {
            System.out.println(e.paramString());
            ta.append(e.paramString() + "\n");
            latch.countDown();
        });

        frame.pack();
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
            sbBounds = new Rectangle(locationOnScreen, size);
        });

        robot.mouseMove(sbBounds.x + sbBounds.width / 2,
                sbBounds.y + sbBounds.height - 50);

        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
        robot.keyRelease(KeyEvent.VK_CONTROL);

        if (!latch.await(1, TimeUnit.SECONDS)) {
            throw new RuntimeException("Timed out waiting for latch");
        }
    }
}
