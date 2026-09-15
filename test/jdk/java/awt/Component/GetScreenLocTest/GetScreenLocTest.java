/*
 * Copyright (c) 2002, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @test
 * @key headful
 * @bug 4356202
 * @summary Tests that getLocationOnScreen returns valid value(WindowMaker
 *          only).
 */

public class GetScreenLocTest {
    static Frame bigFrame, smallFrame;
    static Canvas canvas;
    static int state = 0; // there are three states - (-1,-1),(0,0),(1,1)
    static Robot robot;
    static Point p;

    static void bigPause() {
        Toolkit.getDefaultToolkit().sync();
        robot.waitForIdle();
        robot.delay(1000);
    }

    static void doPress(Point p) {
        robot.mouseMove(p.x, p.y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    private static void test() throws Exception {
        robot = new Robot();
        bigPause();

        EventQueue.invokeAndWait(() -> {
            p = canvas.getLocationOnScreen();
        });
        doPress(p);
        p.x += 1;
        p.y += 1;
        doPress(p);
        p.x -= 2;
        p.y -= 2;
        doPress(p);
        bigPause();

        // ...and at the end the state should be 2
        if (state != 2) {
            throw new RuntimeException("wrong state: " + state);
        }
    }
    private static void createAndShowGUI() {
        bigFrame = new Frame();
        bigFrame.setUndecorated(true);
        bigFrame.setSize(200, 200);
        bigFrame.setLocationRelativeTo(null);
        bigFrame.setVisible(true);
        smallFrame = new Frame();
        smallFrame.setUndecorated(true);
        smallFrame.setLayout(new BorderLayout());
        smallFrame.setSize(120, 150);
        smallFrame.setLocationRelativeTo(null);

        canvas = new MyCanvas();
        smallFrame.add(canvas, BorderLayout.CENTER);

        canvas.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                switch(state) {
                    case 0: // the first event should be (0,0)
                        if (e.getX() != 0 || e.getY() != 0) {
                            System.out.println("state 0: wrong location" + e);
                            break;
                        }
                        state++;
                        break;
                    case 1: // the second event should be (1,1)
                        if (e.getX() != 1 || e.getY() != 1) {
                            System.out.println("state 1: wrong location " + e);
                            break;
                        }
                        state++;
                        break;
                    case 2: // this should never happen
                        System.out.println("state 2: wrong location " + e);
                }
            }
        });
        smallFrame.pack();
        smallFrame.setVisible(true);
    }

    private static class MyCanvas extends Canvas {
        public Dimension getPreferredSize() {
            return new Dimension(100, 100);
        }
        public void paint(Graphics g) {
            super.paint(g);
            g.setColor(Color.blue);
            Rectangle r = getBounds();
            g.fillRect(0, 0, r.width, r.height);
        }
    }

    public static void main(String[] args) throws Exception {
        try {
            EventQueue.invokeAndWait(GetScreenLocTest::createAndShowGUI);
            test();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (smallFrame != null) {
                    smallFrame.dispose();
                }
                if (bigFrame != null) {
                    bigFrame.dispose();
                }
            });
        }
    }
}
