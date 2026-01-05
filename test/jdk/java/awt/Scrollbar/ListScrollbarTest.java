/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4029465
 * @summary Win95 Multiselect List doesn't display scrollbar
 * @key headful
 * @run main ListScrollbarTest
 */

import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.List;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;

public class ListScrollbarTest {

    private static final Color BG_COLOR = Color.RED;
    private static Robot robot;
    private static Frame frame;
    private static List list;
    private static int counter = 0;
    private static volatile Rectangle listBounds;

    public static void main(String[] args) throws Exception {
        try {
            EventQueue.invokeAndWait(ListScrollbarTest::createAndShowUI);
            test();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void test() throws Exception {
        robot = new Robot();
        robot.waitForIdle();
        robot.delay(500);

        EventQueue.invokeAndWait(() -> {
            Point locationOnScreen = list.getLocationOnScreen();
            Dimension size = list.getSize();
            listBounds = new Rectangle(locationOnScreen, size);
        });

        Point point = new Point(listBounds.x + listBounds.width - 5,
                listBounds.y + listBounds.height / 2);


        for (int i = 0; i < 4; i++) {
            scrollbarCheck(point, false);
            addListItem();
        }
        scrollbarCheck(point, true);
    }

    public static boolean areColorsSimilar(Color c1, Color c2, int tolerance) {
        return Math.abs(c1.getRed() - c2.getRed()) <= tolerance
                && Math.abs(c1.getGreen() - c2.getGreen()) <= tolerance
                && Math.abs(c1.getBlue() - c2.getBlue()) <= tolerance;
    }

    private static void scrollbarCheck(Point point, boolean isScrollbarExpected) {
        Color pixelColor = robot.getPixelColor(point.x, point.y);
        boolean areColorsSimilar = areColorsSimilar(BG_COLOR, pixelColor, 5);

        if (isScrollbarExpected && areColorsSimilar) {
            throw new RuntimeException(("""
                    Scrollbar is expected, but pixel color \
                    is similar to the background color
                    %s pixel color
                    %s bg color""")
                    .formatted(pixelColor, BG_COLOR));
        }

        if (!isScrollbarExpected && !areColorsSimilar) {
            throw new RuntimeException(("""
                    Scrollbar is not expected, but pixel color \
                    is not similar to the background color
                    %s pixel color
                    %s bg color""")
                    .formatted(pixelColor, BG_COLOR));
        }
    }

    private static void addListItem() throws Exception {
        EventQueue.invokeAndWait(() -> {
            counter++;
            System.out.println("Adding list item " + counter);
            list.add("List Item " + counter);
            frame.validate();
        });
        robot.waitForIdle();
        robot.delay(150);
    }

    private static void createAndShowUI() {
        frame = new Frame("ListScrollbarTest");
        list = new List(3, true);
        list.setBackground(BG_COLOR);

        // do not draw border around items, it can affect screen capture
        list.setFocusable(false);

        frame.add(list);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
