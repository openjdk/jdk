/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8033936 8172510
 * @summary Verify that correct ItemEvent is received while selection &
 *          deselection of multi select List items.
 * @library /test/lib
 * @build jdk.test.lib.Platform
 * @run main ItemEventTest
 */

// Pass -save to the test to enable screenshots at each select/deselect

import java.awt.AWTException;
import java.awt.Event;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.List;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import jdk.test.lib.Platform;

public final class ItemEventTest extends Frame {
    private static final String expectedSelectionOrder = "01230123";

    private static boolean saveScreenshots;

    private final StringBuffer actualSelectionOrder
            = new StringBuffer(expectedSelectionOrder.length());

    private final List list;
    private final Robot robot;

    private ItemEventTest() throws AWTException {
        robot = new Robot();
        robot.setAutoWaitForIdle(true);

        list = new List(4, true);
        list.add("0");
        list.add("1");
        list.add("2");
        list.add("3");

        add(list);

        setSize(400,400);
        setLayout(new FlowLayout());
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
        robot.waitForIdle();
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean handleEvent(Event e) {
        if ((e.target instanceof List)
            && (e.id == Event.LIST_DESELECT
                || e.id == Event.LIST_SELECT)) {
            logEvent("handleEvent: ", e.arg);
        }
        return true;
    }

    private void logEvent(String method, Object listItem) {
        actualSelectionOrder.append(listItem);
        System.out.println(method + listItem);
    }

    private void testHandleEvent() {
        // When no ItemListener is added to List, parent's handleEvent is
        // called with ItemEvent.
        performTest();
    }

    private void testItemListener() {
        list.addItemListener(ie
                -> logEvent("testItemListener: ", ie.getItem()));
        performTest();
    }

    private void performTest() {
        actualSelectionOrder.setLength(0);

        final Rectangle rect = getListBoundsOnScreen();
        final int dY = rect.height / list.getItemCount();
        final Point loc = new Point(rect.x + rect.width / 2,
                                    rect.y + dY / 2);

        if (Platform.isOSX()) {
            robot.keyPress(KeyEvent.VK_META);
        }

        // First loop to select & Second loop to deselect the list items.
        for (int j = 0; j < 2; ++j) {
            for (int i = 0; i < list.getItemCount(); ++i) {
                robot.mouseMove(loc.x, loc.y + i * dY);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                robot.waitForIdle();

                if (saveScreenshots) {
                    saveImage(robot.createScreenCapture(rect));
                }
            }
        }

        if (Platform.isOSX()) {
            robot.keyRelease(KeyEvent.VK_META);
        }

        if (!expectedSelectionOrder.contentEquals(actualSelectionOrder)) {
            saveImage(robot.createScreenCapture(rect));

            throw new RuntimeException("ItemEvent for selection & deselection"
                + " of multi select List's item is not correct"
                + " Expected : " + expectedSelectionOrder
                + " Actual : " + actualSelectionOrder);
        }
    }

    private Rectangle getListBoundsOnScreen() {
        return new Rectangle(list.getLocationOnScreen(),
                             list.getSize());
    }

    private static int imageNo = 0;

    private static void saveImage(RenderedImage image) {
        try {
            ImageIO.write(image,
                          "png",
                          new File(String.format("image-%02d.png",
                                                 ++imageNo)));
        } catch (IOException ignored) {
        }
    }

    public static void main(String[] args) throws AWTException {
        saveScreenshots = args.length > 0 && "-save".equals(args[0]);

        ItemEventTest test = new ItemEventTest();
        try {
            test.testHandleEvent();
            test.testItemListener();
        } finally {
            test.dispose();
        }
    }
}
