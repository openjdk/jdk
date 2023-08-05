/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4975116 7040022 8023577 8025447 8286624
 * @key headful
 * @summary verify the rounding of negative coordinates in Shape objects
 * @run main/othervm CoordinateTruncationBug
 */

import java.awt.Frame;
import java.awt.Canvas;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Robot;
import java.awt.Rectangle;
import java.awt.Point;
import java.awt.Dimension;
import java.awt.Color;
import java.awt.AWTException;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;

public class CoordinateTruncationBug {
    static boolean failure;
    static boolean verbose;

    static final int W = 80;
    static final int H = 80;

    static final Line2D vertline = new Line2D.Float(-0.7f, 0f, -0.7f, H);
    static final Line2D horizline = new Line2D.Float(0f, -0.7f, W, -0.7f);

    public static void main(String argv[]) {
        verbose = (argv.length > 0);
        new Screen().test();
        new BufImg().test();
        new VolImg().test();
        if (failure) {
            throw new RuntimeException("Test failed due to 1 or more failures");
        }
    }

    public static abstract class Test {
        public abstract String getName();
        public abstract void makeDest();
        public abstract void runTest();
        public abstract void dispose();
        public abstract BufferedImage getSnapshot();

        public void test() {
            makeDest();
            runTest();
            dispose();
        }

        public void runTest(Graphics2D g2d) {
            g2d.setColor(Color.white);
            g2d.fillRect(0, 0, W, H);

            if (!checkAllWhite()) {
                System.err.println("Aborting test of "+getName()+
                                   " due to readback failure!");
                return;
            }

            g2d.setColor(Color.red);
            g2d.draw(vertline);
            g2d.draw(horizline);
            if (!checkAllWhite()) {
                System.err.println(getName()+" failed!");
                failure = true;
            }
        }

        public boolean checkAllWhite() {
            BufferedImage bimg = getSnapshot();
            if (bimg == null) {
                System.err.println(getName()+" returned null snapshot!");
                return false;
            }
            boolean ret = true;
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    int rgb = bimg.getRGB(x, y);
                    if (rgb != -1) {
                        System.err.println(getName()+"("+x+", "+y+") == "+
                                           Integer.toHexString(rgb));
                        if (verbose) {
                            ret = false;
                        } else {
                            return false;
                        }
                    }
                }
            }
            return ret;
        }
    }

    public static class Screen extends Test {
        Frame frame;
        TestCanvas canvas;

        public String getName() {
            return "Screen";
        }

        public void makeDest() {
            frame = new Frame("Screen test");
            frame.setUndecorated(true);
            canvas = new TestCanvas(this);
            frame.add(canvas);
            frame.pack();
            frame.setLocationRelativeTo(null);
        }

        public void runTest() {
            frame.show();
            canvas.waitForTest();
        }

        public Graphics2D createGraphics() {
            return null;
        }

        public BufferedImage getSnapshot() {
            // bypass window animation
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
            };

            try {
                Robot r = new Robot();
                Point p = canvas.getLocationOnScreen();
                return r.createScreenCapture(new Rectangle(p.x, p.y, W, H));
            } catch (AWTException e) {
                return null;
            }
        }

        public void dispose() {
            frame.hide();
            frame.dispose();
        }

        public static class TestCanvas extends Canvas {
            Test test;
            boolean done;

            public TestCanvas(Test test) {
                this.test = test;
            }

            public Dimension getPreferredSize() {
                return new Dimension(W, H);
            }

            public synchronized void waitForTest() {
                while (!done) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        System.err.println(getName()+" interrupted!");
                        failure = true;
                        break;
                    }
                }
            }

            public void paint(Graphics g) {
                if (!done) {
                    test.runTest((Graphics2D) g);
                    notifyDone();
                }
            }

            public synchronized void notifyDone() {
                done = true;
                notifyAll();
            }
        }
    }

    public abstract static class Standalone extends Test {
        public abstract Graphics2D createGraphics();

        public void runTest() {
            Graphics2D g2d = createGraphics();
            runTest(g2d);
            g2d.dispose();
        }
    }

    public static class BufImg extends Standalone {
        public BufferedImage bimg;

        public String getName() {
            return "BufferedImage";
        }

        public void makeDest() {
            bimg = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        }

        public Graphics2D createGraphics() {
            return bimg.createGraphics();
        }

        public BufferedImage getSnapshot() {
            return bimg;
        }

        public void dispose() {
        }
    }

    public static class VolImg extends Standalone {
        Frame frame;
        VolatileImage vimg;

        public String getName() {
            return "VolatileImage";
        }

        public void makeDest() {
            frame = new Frame();
            frame.setSize(W, H);
            frame.setLocationRelativeTo(null);
            frame.show();
            vimg = frame.createVolatileImage(W, H);
        }

        public Graphics2D createGraphics() {
            return vimg.createGraphics();
        }

        public BufferedImage getSnapshot() {
            return vimg.getSnapshot();
        }

        public void dispose() {
            vimg.flush();
            frame.hide();
            frame.dispose();
        }
    }
}
