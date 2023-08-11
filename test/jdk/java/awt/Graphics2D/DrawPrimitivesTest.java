/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, JetBrains s.r.o.. All rights reserved.
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
 * @bug 8287600 8291266 8299207
 * @requires os.family == "mac"
 * @summary [macosx] Some primitives do not render in metal pipeline
 * @run main DrawPrimitivesTest
 */

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CountDownLatch;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public abstract class DrawPrimitivesTest extends JFrame {
    private final static int W = 800;
    private final static int H = 800;
    private final static Color[] color = { Color.RED, Color.BLUE, Color.GREEN};
    private final static int COLOR_TOLERANCE = 10;
    private final CountDownLatch latchRender = new CountDownLatch(1);
    private volatile int frameX0 = 0;
    private volatile int frameY0 = 0;
    private final String name;


    private static boolean isAlmostEqual(Color c1, Color c2) {
        return Math.abs(c1.getRed() - c2.getRed()) < COLOR_TOLERANCE &&
                Math.abs(c1.getGreen() - c2.getGreen()) < COLOR_TOLERANCE &&
                Math.abs(c1.getBlue() - c2.getBlue()) < COLOR_TOLERANCE;

    }

    public static void main(String[] args) throws InterruptedException, AWTException, InvocationTargetException {
        new DrawPrimitivesTest("drawLine") {
            public void renderPrimitive(Graphics2D g2d, int x0, int y0, int w, int h) {
                g2d.drawLine(x0, y0, x0+w, y0+h);
            }
        }.runTest();

        new DrawPrimitivesTest("fillRect") {
            public void renderPrimitive(Graphics2D g2d, int x0, int y0, int w, int h) {
                g2d.fillRect(x0, y0, w, h);
            }
        }.runTest();

        new DrawPrimitivesTest("fillOvalAA") {
            public void renderPrimitive(Graphics2D g2d, int x0, int y0, int w, int h) {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.fillOval(x0, y0, w, h);
            }
        }.runTest();
    }

    public abstract void renderPrimitive(Graphics2D g2d, int x0, int y0, int w, int h);

    public DrawPrimitivesTest(String name) {
        super();
        this.name = name;
    }

    public void runTest() throws InterruptedException, InvocationTargetException, AWTException {
        SwingUtilities.invokeLater(() -> {
            add(new JPanel() {
                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(W, H);
                }

                @Override
                public void paintComponent(Graphics g) {
                    Graphics2D g2d = (Graphics2D) g;
                    g2d.setColor(Color.YELLOW);
                    int c = 0;
                    for (int i = 0; i < W; i += 10) {
                        for (int j = 0; j < H; j += 10) {
                            c = (c + 1) % color.length;
                            g2d.setColor(color[c]);
                            renderPrimitive(g2d, i, j, 10, 10);
                        }
                    }
                    Point p = getLocationOnScreen();
                    frameX0 = p.x;
                    frameY0 = p.y - getInsets().top;

                    latchRender.countDown();
                }
            });
            setPreferredSize(new Dimension(W, H));
            pack();
            setVisible(true);
        });

        latchRender.await();
        Thread.sleep(1000);

        Robot robot = new Robot();

        boolean hasEmptyContent = true;
        l:for (int i = frameX0 + W/3; i < frameX0 + (2*W)/3; i++) {
            for (int j = 0; j < 10; j += 2) {
                if (isAlmostEqual(robot.getPixelColor(i, frameY0 + H / 2 + j), Color.RED)) {
                    hasEmptyContent = false;
                    break l;
                }
            }
        }

        SwingUtilities.invokeAndWait(() -> {
            setVisible(false);
            dispose();
        });

        if (hasEmptyContent) {
            throw new RuntimeException(name + ": Empty content");
        }
    }
}
