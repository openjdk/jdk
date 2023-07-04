/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTree;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.event.InputEvent;

/*
  @test
  @bug 4273712 4396746
  @summary tests that mouse exit event doesn't trigger drag
  @key headful
  @run main MouseExitGestureTriggerTest
*/

public class MouseExitGestureTriggerTest {

    boolean recognized = false;
    volatile JFrame frame;
    volatile JPanel panel;
    volatile JTree tree;
    volatile DragSource dragSource;
    volatile Point srcPoint;
    volatile Rectangle r;
    volatile DragGestureListener dgl;
    static final int FRAME_ACTIVATION_TIMEOUT = 2000;
    static final int RECOGNITION_TIMEOUT = 1000;

    public static void main(String[] args) throws Exception {
        MouseExitGestureTriggerTest test = new MouseExitGestureTriggerTest();
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
        frame = new JFrame("MouseExitGestureTriggerTest");
        panel = new JPanel();
        tree = new JTree();

        dragSource = DragSource.getDefaultDragSource();
        dgl = new DragGestureListener() {
            public void dragGestureRecognized(DragGestureEvent dge) {
                Thread.dumpStack();
                recognized = true;
            }
        };

        tree.setEditable(true);
        dragSource.createDefaultDragGestureRecognizer(tree,
                                                      DnDConstants.ACTION_MOVE,
                                                      dgl);
        panel.add(tree);
        frame.getContentPane().add(panel);
        frame.setLocation(200, 200);

        frame.pack();
        frame.setVisible(true);
    }

    public void start() throws Exception {
        final Robot robot = new Robot();
        Thread.sleep(FRAME_ACTIVATION_TIMEOUT);

        clickRootNode(robot);
        clickRootNode(robot);
        clickRootNode(robot);

        Thread.sleep(RECOGNITION_TIMEOUT);

        EventQueue.invokeAndWait(() -> {
            if (recognized) {
                throw new RuntimeException("Mouse exit event triggered drag");
            }
        });
    }

    void clickRootNode(final Robot robot) throws Exception {
        EventQueue.invokeAndWait(() -> {
            srcPoint = tree.getLocationOnScreen();
            r = tree.getRowBounds(0);
        });
        srcPoint.translate(r.x + 2 * r.width /3 , r.y + r.height / 2);
        robot.mouseMove(srcPoint.x ,srcPoint.y);

        robot.mousePress(InputEvent.BUTTON1_MASK);
        Thread.sleep(10);
        robot.mouseRelease(InputEvent.BUTTON1_MASK);
        Thread.sleep(10);
    }
}
