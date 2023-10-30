/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.EventQueue;
import java.awt.GridLayout;
import java.awt.Robot;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Dimension;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JList;

/*
  @test
  @bug 4388802
  @summary tests that a drag can be initiated with MOUSE_MOVED event
  @key headful
  @run main DragTriggerEventTest
*/

public class DragTriggerEventTest {

    volatile JFrame frame;
    volatile JList list;
    volatile DropTargetPanel panel;
    volatile Point srcPoint;
    volatile Rectangle cellBounds;
    volatile Point dstPoint;
    volatile Dimension d;
    static final int FRAME_ACTIVATION_TIMEOUT = 3000;
    volatile boolean mouse1Pressed = false;
    volatile boolean ctrlPressed = false;

    public static void main(String[] args) throws Exception {
        DragTriggerEventTest test = new DragTriggerEventTest();
        EventQueue.invokeAndWait(test::init);
        try {
            test.start();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (test.frame != null) {
                    test.frame.dispose();
                }
            });
        }
    }

    public void init() {
        list = new JList(new String[] {"one", "two", "three", "four"});
        list.setDragEnabled(true);
        panel = new DropTargetPanel();

        frame = new JFrame();
        frame.setTitle("DragTriggerEventTest");
        frame.setLocation(200, 200);
        frame.getContentPane().setLayout(new GridLayout(2, 1));
        frame.getContentPane().add(list);
        frame.getContentPane().add(panel);

        frame.pack();
        frame.setVisible(true);
    }

    public void start() throws Exception {
        Robot robot;
        robot = new Robot();

        EventQueue.invokeAndWait(() -> {
            srcPoint = list.getLocationOnScreen();
            cellBounds = list.getCellBounds(0, 0);
        });

        srcPoint.translate(cellBounds.x + cellBounds.width / 2,
                           cellBounds.y + cellBounds.height / 2);

        EventQueue.invokeAndWait(() -> {
            dstPoint = panel.getLocationOnScreen();
            d = panel.getSize();
        });
        dstPoint.translate(d.width / 2, d.height / 2);

        for (int delay = 32; delay < 10000 && !panel.getResult(); delay *= 2) {
            System.err.println("attempt to drag with delay " + delay);
            robot.mouseMove(srcPoint.x, srcPoint.y);
            robot.mousePress(InputEvent.BUTTON1_MASK);
            mouse1Pressed = true;
            robot.mouseRelease(InputEvent.BUTTON1_MASK);
            mouse1Pressed = false;

            robot.keyPress(KeyEvent.VK_CONTROL);
            ctrlPressed = true;
            robot.mousePress(InputEvent.BUTTON1_MASK);
            mouse1Pressed = true;

            Point p = new Point(srcPoint);
            while (!p.equals(dstPoint)) {
                p.translate(sign(dstPoint.x - p.x),
                            sign(dstPoint.y - p.y));
                robot.mouseMove(p.x, p.y);
                robot.delay(delay);
            }
        }
        if (mouse1Pressed) {
            robot.mouseRelease(InputEvent.BUTTON1_MASK);
        }
        if (ctrlPressed) {
            robot.keyRelease(KeyEvent.VK_CONTROL);
        }

        EventQueue.invokeAndWait(() -> {
            if (!panel.getResult()) {
                throw new RuntimeException("The test failed.");
            }
        });
    }

    public static int sign(int n) {
        return n < 0 ? -1 : n == 0 ? 0 : 1;
    }
}

class DropTargetPanel extends JPanel implements DropTargetListener {

    private boolean passed = false;
    final Dimension preferredDimension = new Dimension(200, 100);

    public DropTargetPanel() {
        setDropTarget(new DropTarget(this, this));
    }

    public Dimension getPreferredSize() {
        return preferredDimension;
    }

    public void dragEnter(DropTargetDragEvent dtde) {
        passed = true;
    }

    public void dragExit(DropTargetEvent dte) {
        passed = true;
    }

    public void dragOver(DropTargetDragEvent dtde) {
        passed = true;
    }

    public void dropActionChanged(DropTargetDragEvent dtde) {
        passed = true;
    }

    public void drop(DropTargetDropEvent dtde) {
        passed = true;
        dtde.rejectDrop();
    }

    public boolean getResult() {
        return passed;
    }
}
