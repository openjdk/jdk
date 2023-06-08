/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.AWTException;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.ScrollPane;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;

import static java.awt.EventQueue.invokeAndWait;

/*
 * @test
 * @bug 8297923
 * @key headful
 * @requires os.family=="windows"
 * @summary Verifies no GDI objects are leaked after scrolling continuously
 * @run main/othervm -Dsun.java2d.d3d=false ScrollPaneLeakTest
 */
public class ScrollPaneLeakTest {

    /**
     * The number of times the test repeats scrolling cycles.
     */
    private static final int REPEATS = 1;

    /**
     * The number of times the robot moves the scroll bar thumb down and up
     * per one cycle.
     */
    private static final int UP_DOWN_CYCLES = 20;

    private static final Color CANVAS_FOREGROUND = new Color(200, 240, 200);
    private static final Color CANVAS_BACKGROUND = new Color(240, 200, 240);
    private static final Color SCROLL_PANE_BACKGROUND = new Color(240, 240, 200);

    private static final Dimension CANVAS_SIZE = new Dimension(400, 600);
    private static final Dimension FRAME_SIZE = new Dimension(CANVAS_SIZE.width * 2,
                                                              3 * CANVAS_SIZE.height / 4);
    private static final Dimension SCROLL_PANE_SIZE = new Dimension(CANVAS_SIZE.width,
                                                                    CANVAS_SIZE.height / 2);

    private static Frame frame;
    private static ScrollPane scroll;

    private static final AtomicReference<Rectangle> frameBounds = new AtomicReference<>();

    private static final AtomicReference<Rectangle> scrollBounds = new AtomicReference<>();

    private static final AtomicReference<Integer> vertBarWidth = new AtomicReference<>();
    private static final AtomicReference<Integer> horzBarHeight = new AtomicReference<>();

    public static void main(String[] args)
            throws InterruptedException, InvocationTargetException, AWTException {
        try {
            invokeAndWait(ScrollPaneLeakTest::createUI);

            final Robot robot = new Robot();
            robot.waitForIdle();

            invokeAndWait(() -> frame.setExtendedState(frame.getExtendedState()
                                                       | Frame.MAXIMIZED_BOTH));
            robot.waitForIdle();

            invokeAndWait(() -> {
                scrollBounds.set(new Rectangle(scroll.getLocationOnScreen(),
                                               scroll.getSize()));

                vertBarWidth.set(scroll.getVScrollbarWidth());
                horzBarHeight.set(scroll.getHScrollbarHeight());
            });
            robot.waitForIdle();

            invokeAndWait(() -> scroll.setScrollPosition(0, 0));
            robot.waitForIdle();
            robot.delay(1000);

            final Rectangle sb = scrollBounds.get();
            final int vbar = vertBarWidth.get();
            final int hbar = horzBarHeight.get() * 2;

            final Point pos = new Point();
            for (int no = 0; no < REPEATS; no++) {
                pos.x = sb.x + sb.width - vbar / 3;
                pos.y = sb.y + hbar;

                robot.mouseMove(pos.x, pos.y);
                robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
                for (int i = 0; i < UP_DOWN_CYCLES; i++) {
                    while (++pos.y < sb.y + sb.height - hbar) {
                        robot.mouseMove(pos.x, pos.y);
                        robot.delay(5);
                    }
                    while (--pos.y > sb.y + hbar) {
                        robot.mouseMove(pos.x, pos.y);
                        robot.delay(5);
                    }
                }
                robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);

                invokeAndWait(() -> frame.setExtendedState(frame.getExtendedState()
                                                           | Frame.ICONIFIED));
                robot.delay(500);
                invokeAndWait(() -> frame.setExtendedState(frame.getExtendedState()
                                                           & ~Frame.ICONIFIED));
                robot.delay(500);
            }

            invokeAndWait(() -> scroll.setScrollPosition(0, sb.height / 2));

            invokeAndWait(() -> {
                Rectangle bounds = frame.getBounds();
                frameBounds.set(bounds);
            });

            // Throws OutOfMemoryError when the test fails
            robot.createScreenCapture(frameBounds.get());

            System.out.println("Robot created a screenshot: test passed");
        } finally {
            invokeAndWait(frame::dispose);
        }
    }

    private static void createUI() {
        frame = new Frame("Scroll Pane Leak Test");
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });

        frame.setLayout(new FlowLayout(FlowLayout.CENTER));
        frame.setLocation(0, 0);

        Canvas canvas = new Canvas() {
            @Override
            public void paint(Graphics g) {
                g.setColor(CANVAS_FOREGROUND);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        canvas.setBackground(CANVAS_BACKGROUND);
        canvas.setSize(CANVAS_SIZE);

        scroll = new ScrollPane(ScrollPane.SCROLLBARS_ALWAYS);
        scroll.add(canvas);
        scroll.setSize(SCROLL_PANE_SIZE);
        scroll.setBackground(SCROLL_PANE_BACKGROUND);

        frame.add(scroll);
        frame.setSize(FRAME_SIZE);

        frame.setVisible(true);
    }

}
