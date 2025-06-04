/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Button;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Robot;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.InputEvent;
import java.io.File;
import java.io.InputStream;
import java.io.Serializable;

/*
  @test
  @bug 4492640
  @summary tests that inter-JVM dnd works properly for ACTION_LINK
  @key headful
  @run main InterJVMLinkTest
*/

public class InterJVMLinkTest {

    public static final int CODE_NOT_RETURNED = -1;
    public static final int CODE_OK = 0;
    public static final int CODE_FAILURE = 1;
    public static final int FRAME_ACTIVATION_TIMEOUT = 2000;
    public static final int DROP_TIMEOUT = 60000;

    private int returnCode = CODE_NOT_RETURNED;

    volatile Frame frame;
    volatile DropTargetPanel panel;
    volatile Robot robot = null;
    volatile Point p;
    volatile Dimension d;

    public static void main(String[] args) throws Exception {
        InterJVMLinkTest test = new InterJVMLinkTest();
        if (args.length > 0) {
            test.run(args);
        } else {
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
    }

    public void run(String[] args) {
        try {
            if (args.length != 4) {
                throw new RuntimeException("Incorrect command line arguments.");
            }

            int x = Integer.parseInt(args[0]);
            int y = Integer.parseInt(args[1]);
            int w = Integer.parseInt(args[2]);
            int h = Integer.parseInt(args[3]);

            DragSourcePanel panel = new DragSourcePanel();
            frame = new Frame();

            frame.setTitle("DragSource frame");
            frame.setLocation(300, 200);
            frame.add(panel);
            frame.pack();
            frame.setVisible(true);

            Thread.sleep(FRAME_ACTIVATION_TIMEOUT);

            Point sourcePoint = panel.getLocationOnScreen();
            Dimension d = panel.getSize();
            sourcePoint.translate(d.width / 2, d.height / 2);

            Point targetPoint = new Point(x + w / 2, y + h / 2);

            robot = new Robot();
            robot.mouseMove(sourcePoint.x, sourcePoint.y);
            robot.mousePress(InputEvent.BUTTON1_MASK);
            for (; !sourcePoint.equals(targetPoint);
                 sourcePoint.translate(sign(targetPoint.x - sourcePoint.x),
                                       sign(targetPoint.y - sourcePoint.y))) {
                robot.mouseMove(sourcePoint.x, sourcePoint.y);
                Thread.sleep(50);
            }
            robot.mouseRelease(InputEvent.BUTTON1_MASK);

            Thread.sleep(DROP_TIMEOUT);

            System.exit(InterJVMLinkTest.CODE_OK);
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(InterJVMLinkTest.CODE_FAILURE);
        }
    }

    public static int sign(int n) {
        return n < 0 ? -1 : n == 0 ? 0 : 1;
    }

    public void init() {
        panel = new DropTargetPanel();

        frame = new Frame();
        frame.setTitle("InterJVMLinkTest DropTarget frame");
        frame.setLocation(10, 200);
        frame.add(panel);

        frame.pack();
        frame.setVisible(true);
    }

    public void start() throws Exception {
        Thread.sleep(FRAME_ACTIVATION_TIMEOUT);

        EventQueue.invokeAndWait(() -> {
            p = panel.getLocationOnScreen();
            d = panel.getSize();
        });

        String javaPath = System.getProperty("java.home", "");
        String command = javaPath + File.separator + "bin" +
            File.separator + "java -cp " + System.getProperty("test.classes", ".") +
            " InterJVMLinkTest " +
            p.x + " " + p.y + " " + d.width + " " + d.height;

        Process process = Runtime.getRuntime().exec(command);
        returnCode = process.waitFor();

        InputStream errorStream = process.getErrorStream();
        int count = errorStream.available();
        if (count > 0) {
            byte[] b = new byte[count];
            errorStream.read(b);
            System.err.println("========= Child VM System.err ========");
            System.err.print(new String(b));
            System.err.println("======================================");
        }

        switch (returnCode) {
        case CODE_NOT_RETURNED:
            System.err.println("Child VM: failed to start");
            break;
        case CODE_OK:
            System.err.println("Child VM: normal termination");
            break;
        case CODE_FAILURE:
            System.err.println("Child VM: abnormal termination");
            break;
        }
        if (panel == null || (panel.isEntered() && !panel.isDropped())) {
            throw new RuntimeException("The test failed.");
        }
    }
}

class DragSourceButton extends Button implements Serializable,
                                                 DragGestureListener {
    final Transferable transferable = new StringSelection("TEXT");
    final DragSourceListener dragSourceListener = new DragSourceAdapter() {
            public void dragDropEnd(DragSourceDropEvent dsde) {
                System.exit(InterJVMLinkTest.CODE_OK);
            }
        };

    public DragSourceButton() {
        super("DragSourceButton");

        DragSource ds = DragSource.getDefaultDragSource();
        ds.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_LINK,
                                              this);
    }

    public void dragGestureRecognized(DragGestureEvent dge) {
        dge.startDrag(null, transferable, dragSourceListener);
    }
}

class DragSourcePanel extends Panel {

    final Dimension preferredDimension = new Dimension(200, 200);

    public DragSourcePanel() {
        setLayout(new GridLayout(1, 1));
        add(new DragSourceButton());
    }

    public Dimension getPreferredSize() {
        return preferredDimension;
    }
}

class DropTargetPanel extends Panel implements DropTargetListener {

    final Dimension preferredDimension = new Dimension(200, 200);
    boolean entered = false;
    boolean dropped = false;

    public DropTargetPanel() {
        setDropTarget(new DropTarget(this, DnDConstants.ACTION_LINK, this));
    }

    public Dimension getPreferredSize() {
        return preferredDimension;
    }

    public void dragEnter(DropTargetDragEvent dtde) {
        entered = true;
    }

    public void dragExit(DropTargetEvent dte) {}

    public void dragOver(DropTargetDragEvent dtde) {}

    public void drop(DropTargetDropEvent dtde) {
        dtde.rejectDrop();
        dropped = true;
    }

    public void dropActionChanged(DropTargetDragEvent dtde) {}

    public boolean isEntered() {
        return entered;
    }

    public boolean isDropped() {
        return dropped;
    }
}
