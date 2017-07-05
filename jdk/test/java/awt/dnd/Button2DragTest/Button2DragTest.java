/*
 * Copyright (c) 2006, 2014, Oracle and/or its affiliates. All rights reserved.
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
  test
  @bug 4955110
  @summary tests that a drag ends on button2 release
  @author Alexander.Gerasimov area=dnd
  @library    ../../regtesthelpers
  @build      Util
  @run applet/othervm Button2DragTest.html
*/


/**
 * Button2DragTest.java
 *
 * summary: tests that DragSourceDragEvent.getDropAction() accords to its new spec
 *          (does not depend on the user drop action)
 *
 */

import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import test.java.awt.regtesthelpers.Util;


public class Button2DragTest extends Applet {

    private volatile boolean dropSuccess;

    private Frame frame;


    public void init() {
        // Set up the environment -- set the layout manager, add
        // buttons, etc.
        setLayout(new BorderLayout());

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
    }


    public void start() {
        //Get things going.  Request focus, set size, et cetera
        setSize(200,200);
        setVisible(true);
        validate();

        //What would normally go into main() will probably go here.
        //Use System.out.println for diagnostic messages that you want
        //to read after the test is done.

        frame.setBounds(100, 100, 200, 200);
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
