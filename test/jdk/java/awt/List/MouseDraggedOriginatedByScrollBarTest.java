/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6240151
 * @key headful
 * @summary XToolkit: Dragging the List scrollbar initiates DnD
 * @requires os.family == "linux"
 * @run main MouseDraggedOriginatedByScrollBarTest
*/

import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.List;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

public class MouseDraggedOriginatedByScrollBarTest {
    private static Frame frame;
    private static volatile Point loc;
    private static List list;
    private static final int XOFFSET = 10;
    private static final int YOFFSET = 20;

    public static void main(String[] args) throws Exception {
        try {
            EventQueue.invokeAndWait(() -> createUI());
            test();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void createUI() {
        frame = new Frame();
        list = new List(4, false);

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

        list.addMouseMotionListener(
            new MouseMotionAdapter(){
                @Override
                public void mouseDragged(MouseEvent me){
                    System.out.println(me);
                    throw new RuntimeException("Mouse dragged event detected.");
                }
            });

        list.addMouseListener(
            new MouseAdapter() {
                public void mousePressed(MouseEvent me) {
                    System.out.println(me);
                    throw new RuntimeException("Mouse pressed event detected.");
                }

                public void mouseReleased(MouseEvent me) {
                    System.out.println(me);
                    throw new RuntimeException("Mouse released event detected.");
                }

                public void mouseClicked(MouseEvent me){
                    System.out.println(me);
                    throw new RuntimeException("Mouse clicked event detected.");
                }
            });

        frame.setLayout(new FlowLayout());
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void test() throws Exception {
        Robot robot = new Robot();
        robot.waitForIdle();
        robot.delay(1000);
        robot.setAutoWaitForIdle(true);

        EventQueue.invokeAndWait(() -> {
            Point p = list.getLocationOnScreen();
            p.translate(list.getWidth() - XOFFSET, YOFFSET);
            loc = p;
        });
        robot.mouseMove(loc.x, loc.y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        for (int i = 0; i < 30; i++) {
            robot.mouseMove(loc.x, loc.y + i);
        }
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(100);
    }
}
