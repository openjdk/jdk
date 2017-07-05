/*
 * Copyright (c) 2006, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.InputEvent;

import test.java.awt.regtesthelpers.Util;

/**
 * @test
 * @key headful
 * @bug 4955110
 * @summary tests that DragSourceDragEvent.getDropAction() accords to its new
 *          spec (does not depend on the user drop action)
 * @library ../../regtesthelpers
 * @build Util
 * @run main/othervm Button2DragTest
 * @author Alexander.Gerasimov area=dnd
 */
public final class Button2DragTest {

    private volatile boolean dropSuccess;

    private static Frame frame;

    public static void main(final String[] args) {
        Button2DragTest test = new Button2DragTest();
        try {
            test.run();
        } finally {
            if (frame != null) {
                frame.dispose();
            }
        }
    }

    public void run() {
        frame = new Frame();

        final DragSourceListener dragSourceListener = new DragSourceAdapter() {
            public void dragDropEnd(DragSourceDropEvent e) {
                dropSuccess = e.getDropSuccess();
                System.err.println("Drop was successful: " + dropSuccess);
            }
        };
        DragGestureListener dragGestureListener = new DragGestureListener() {
            public void dragGestureRecognized(DragGestureEvent dge) {
                dge.startDrag(null, new StringSelection("OK"), dragSourceListener);
            }
        };
        new DragSource().createDefaultDragGestureRecognizer(frame, DnDConstants.ACTION_MOVE,
                                                            dragGestureListener);

        DropTargetAdapter dropTargetListener = new DropTargetAdapter() {
            public void drop(DropTargetDropEvent dtde) {
                dtde.acceptDrop(DnDConstants.ACTION_MOVE);
                dtde.dropComplete(true);
                System.err.println("Drop");
            }
        };
        new DropTarget(frame, dropTargetListener);

        //What would normally go into main() will probably go here.
        //Use System.out.println for diagnostic messages that you want
        //to read after the test is done.
        frame.setUndecorated(true);
        frame.setBounds(100, 100, 200, 200);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        Robot robot = Util.createRobot();

        Util.waitForIdle(robot);

        Point startPoint = frame.getLocationOnScreen();
        Point endPoint = new Point(startPoint);
        startPoint.translate(50, 50);
        endPoint.translate(150, 150);

        Util.drag(robot, startPoint, endPoint, InputEvent.BUTTON2_MASK);

        Util.waitForIdle(robot);
        robot.delay(500);

        if (dropSuccess) {
            System.err.println("test passed");
        } else {
            throw new RuntimeException("test failed: drop was not successful");
        }
    }

}
