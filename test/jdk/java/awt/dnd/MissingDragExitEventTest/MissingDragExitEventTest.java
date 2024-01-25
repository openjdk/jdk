/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @key headful
 * @bug 8027913
 * @library ../../regtesthelpers
 * @build Util
 * @compile MissingDragExitEventTest.java
 * @run main/othervm MissingDragExitEventTest
 * @author Sergey Bylokhov
 */

import test.java.awt.regtesthelpers.Util;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class MissingDragExitEventTest {

    private static volatile JFrame frame;
    private static boolean FAILED;
    private static boolean MOUSE_ENTERED_DT;
    private static boolean MOUSE_ENTERED;
    private static boolean MOUSE_EXIT_TD;
    private static boolean MOUSE_EXIT;
    private static int SIZE = 100;
    private static CountDownLatch dropCompleteLatch = new CountDownLatch(1);

    private static void initAndShowUI() {
        frame = new JFrame("Test frame");
        frame.setUndecorated(true);
        frame.setSize(SIZE, SIZE);
        frame.setLocationRelativeTo(null);
        final JTextArea jta = new JTextArea();
        jta.setBackground(Color.RED);
        frame.add(jta);
        jta.setText("1234567890");
        jta.setFont(jta.getFont().deriveFont(50f));
        jta.setDragEnabled(true);
        jta.selectAll();
        jta.setDropTarget(new DropTarget(jta, DnDConstants.ACTION_COPY,
                                         new TestdropTargetListener()));
        jta.addMouseListener(new TestMouseAdapter());
        frame.pack();
        frame.setAlwaysOnTop(true);
        frame.setVisible(true);
    }

    public static void main(final String[] args) throws Exception {
        try {
            final Robot r = new Robot();
            r.setAutoDelay(50);
            r.mouseMove(100, 100);
            Util.waitForIdle(r);

            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    initAndShowUI();
                }
            });
            final AtomicReference<Point> insidePoint = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> insidePoint.set(frame.getLocationOnScreen()));
            final Point inside = insidePoint.get();
            inside.translate(2,20);
            final Point outer = new Point(inside);
            outer.translate(-20, 0);
            r.mouseMove(inside.x, inside.y);
            r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            try {
                for (int i = 0; i < 3; ++i) {
                    Util.mouseMove(r, inside, outer);
                    Util.mouseMove(r, outer, inside);
                }
            } finally {
                r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            }

            if (!dropCompleteLatch.await(10, TimeUnit.SECONDS)) {
                captureScreen(r);
                throw new RuntimeException(
                        "Waited too long, but the drop is not completed");
            }
            if (FAILED || !MOUSE_ENTERED || !MOUSE_ENTERED_DT || !MOUSE_EXIT ||
                !MOUSE_EXIT_TD) {
                System.out.println(
                        "Events, FAILED = " + FAILED + ", MOUSE_ENTERED = " +
                        MOUSE_ENTERED + ", MOUSE_ENTERED_DT = " +
                        MOUSE_ENTERED_DT + ", MOUSE_EXIT = " + MOUSE_EXIT +
                        ", MOUSE_EXIT_TD = " + MOUSE_EXIT_TD);
                captureScreen(r);
                throw new RuntimeException("Failed");
            }
        } finally {
            if (frame != null) {
                frame.dispose();
            }
        }
    }

    private static void captureScreen(Robot r) {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        try {
            ImageIO.write(r.createScreenCapture(
                    new Rectangle(0, 0, screenSize.width, screenSize.height)),
                          "png", new File("FailedScreenImage.png"));
        } catch (IOException ignore) {
        }
    }

    static class TestdropTargetListener extends DropTargetAdapter {

        private volatile boolean inside;

        @Override
        public void dragEnter(final DropTargetDragEvent dtde) {
            if (inside) {
                FAILED = true;
                Thread.dumpStack();
            }
            inside = true;
            MOUSE_ENTERED_DT = true;
        }

        @Override
        public void dragOver(final DropTargetDragEvent dtde) {
            if (!inside) {
                FAILED = true;
                Thread.dumpStack();
            }
        }

        @Override
        public void dragExit(final DropTargetEvent dte) {
            if (!inside) {
                FAILED = true;
                Thread.dumpStack();
            }
            inside = false;
            MOUSE_EXIT_TD = true;
            System.out.println("Drag exit");
        }

        @Override
        public void drop(final DropTargetDropEvent dtde) {
            if (!inside) {
                FAILED = true;
                Thread.dumpStack();
            }
            inside = false;
            System.out.println("Drop complete");
            dropCompleteLatch.countDown();
        }
    }

    static class TestMouseAdapter extends MouseAdapter {

        private volatile boolean inside;

        @Override
        public void mouseEntered(final MouseEvent e) {
            if (inside) {
                FAILED = true;
                Thread.dumpStack();
            }
            inside = true;
            MOUSE_ENTERED = true;
        }

        @Override
        public void mouseExited(final MouseEvent e) {
            System.out.println( "Mouse exit");
            if (!inside) {
                FAILED = true;
                Thread.dumpStack();
            }
            inside = false;
            MOUSE_EXIT = true;
        }
    }
}
