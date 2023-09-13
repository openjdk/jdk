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

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Panel;
import java.awt.Polygon;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;

/*
 * @test
 * @bug 4210936 4214524
 * @summary Tests the results of the hit test methods on 3 different
 *          Shape objects - Polygon, Area, and GeneralPath.  Both an
 *          automatic test for constraint compliance and a manual
 *          test for correctness are included in this one class.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main PathHitTest
 */

/*
 * @test
 * @bug 4210936 4214524
 * @summary Tests the results of the hit test methods on 3 different
 *          Shape objects - Polygon, Area, and GeneralPath.  Both an
 *          automatic test for constraint compliance and a manual
 *          test for correctness are included in this one class.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual PathHitTest manual
 */

public class PathHitTest {

    public static final int BOXSIZE = 5;
    public static final int BOXCENTER = 2;
    public static final int TESTSIZE = 400;
    public static final int NUMTESTS = (TESTSIZE + BOXSIZE - 1) / BOXSIZE;

    public static Shape[] testShapes = new Shape[5];
    public static String[] testNames = {
            "Polygon",
            "EvenOdd GeneralPath",
            "NonZero GeneralPath",
            "Area from EO GeneralPath",
            "Area from NZ GeneralPath",
    };

    static {
        GeneralPath gpeo = new GeneralPath(GeneralPath.WIND_EVEN_ODD);
        Ellipse2D ell = new Ellipse2D.Float();
        Point2D center = new Point2D.Float();
        AffineTransform at = new AffineTransform();
        for (int i = 0; i < 360; i += 30) {
            center.setLocation(100, 0);
            at.setToTranslation(200, 200);
            at.rotate(i * Math.PI / 180);
            at.transform(center, center);
            ell.setFrame(center.getX() - 50, center.getY() - 50, 100, 100);
            gpeo.append(ell, false);
        }
        GeneralPath side = new GeneralPath();
        side.moveTo(0, 0);
        side.lineTo(15, 10);
        side.lineTo(30, 0);
        side.lineTo(45, -10);
        side.lineTo(60, 0);
        append4sides(gpeo, side, 20, 20);
        side.reset();
        side.moveTo(0, 0);
        side.quadTo(15, 10, 30, 0);
        side.quadTo(45, -10, 60, 0);
        append4sides(gpeo, side, 320, 20);
        side.reset();
        side.moveTo(0, 0);
        side.curveTo(15, 10, 45, -10, 60, 0);
        append4sides(gpeo, side, 20, 320);

        GeneralPath gpnz = new GeneralPath(GeneralPath.WIND_NON_ZERO);
        gpnz.append(gpeo, false);
        Polygon p = new Polygon();
        p.addPoint( 50,  50);
        p.addPoint( 60, 350);
        p.addPoint(250, 340);
        p.addPoint(260, 150);
        p.addPoint(140, 140);
        p.addPoint(150, 260);
        p.addPoint(340, 250);
        p.addPoint(350,  60);
        testShapes[0] = p;
        testShapes[1] = gpeo;
        testShapes[2] = gpnz;
        testShapes[3] = new Area(gpeo);
        testShapes[3].getPathIterator(null);
        testShapes[4] = new Area(gpnz);
        testShapes[4].getPathIterator(null);
    }

    private static void append4sides(GeneralPath path, GeneralPath side,
                                     double xoff, double yoff) {
        AffineTransform at = new AffineTransform();
        at.setToTranslation(xoff, yoff);
        for (int i = 0; i < 4; i++) {
            path.append(side.getPathIterator(at), i != 0);
            at.rotate(Math.toRadians(90), 30, 30);
        }
    }

    public static void main(String[] argv) throws Exception {
        if (argv.length > 0 && argv[0].equals("manual")) {
            PathHitTestManual.doManual();
        } else {
            int totalerrs = 0;
            for (int i = 0; i < testShapes.length; i++) {
                totalerrs += testshape(testShapes[i], testNames[i]);
            }
            if (totalerrs != 0) {
                throw new RuntimeException(totalerrs +
                        " constraint conditions violated!");
            }
        }
    }

    public static int testshape(Shape s, String name) {
        int numerrs = 0;
        long start = System.currentTimeMillis();
        for (int y = 0; y < TESTSIZE; y += BOXSIZE) {
            for (int x = 0; x < TESTSIZE; x += BOXSIZE) {
                boolean rectintersects = s.intersects(x, y, BOXSIZE, BOXSIZE);
                boolean rectcontains = s.contains(x, y, BOXSIZE, BOXSIZE);
                boolean pointcontains = s.contains(x + BOXCENTER, y + BOXCENTER);
                if (rectcontains && !rectintersects) {
                    System.err.println("rect is contained " +
                            "but does not intersect!");
                    numerrs++;
                }
                if (rectcontains && !pointcontains) {
                    System.err.println("rect is contained " +
                            "but center is not contained!");
                    numerrs++;
                }
                if (pointcontains && !rectintersects) {
                    System.err.println("center is contained " +
                            "but rect does not intersect!");
                    numerrs++;
                }
            }
        }
        long end = System.currentTimeMillis();
        System.out.println(name + " completed in " +
                (end - start) + "ms with " +
                numerrs + " errors");
        return numerrs;
    }

    static class PathHitTestManual extends Panel {
        private static final String INSTRUCTIONS = """
            This test displays the results of hit testing 5 different Shape
            objects one at a time.

            You can switch between shapes using the Choice component located
            at the bottom of the window.

            Each square in the test represents the
            return values of the hit testing operators for that square region:

                yellow - not yet tested
                translucent blue overlay - the shape being tested

                black - all outside
                dark gray - rectangle intersects shape
                light gray - rectangle intersects and center point is inside shape
                white - rectangle is entirely contained in shape
                red - some constraint was violated, including:
                    rectangle is contained, but center point is not
                    rectangle is contained, but rectangle.intersects is false
                    centerpoint is contained, but rectangle.intersects is false

            Visually inspect the results to see if they match the above table.
            Note that it is not a violation for rectangles that are entirely
            inside the path to be light gray instead of white since sometimes
            the path is complex enough to make an exact determination expensive.
            You might see this on the GeneralPath NonZero example where the
            circles that make up the path cross over the interior of the shape
            and cause the hit testing methods to guess that the rectangle is
            not guaranteed to be contained within the shape.
            """;

        PathHitTestCanvas phtc;

        public void init() {
            setLayout(new BorderLayout());
            phtc = new PathHitTestCanvas();
            add("Center", phtc);
            final Choice ch = new Choice();
            for (int i = 0; i < PathHitTest.testNames.length; i++) {
                ch.add(PathHitTest.testNames[i]);
            }
            ch.addItemListener(e -> phtc.setShape(ch.getSelectedIndex()));
            ch.select(0);
            phtc.setShape(0);
            add("South", ch);
        }

        public void start() {
            phtc.start();
        }

        public void stop() {
            phtc.stop();
        }

        public static class PathHitTestCanvas extends Canvas implements Runnable {
            public static final Color[] colors = {
                                        /* contains?  point in?  intersects? */
                    Color.black,        /*    NO         NO          NO      */
                    Color.darkGray,     /*    NO         NO          YES     */
                    Color.red,          /*    NO         YES         NO      */
                    Color.lightGray,    /*    NO         YES         YES     */
                    Color.red,          /*    YES        NO          NO      */
                    Color.red,          /*    YES        NO          YES     */
                    Color.red,          /*    YES        YES         NO      */
                    Color.white,        /*    YES        YES         YES     */
                    Color.yellow,       /*     used for untested points      */
            };

            public Dimension getPreferredSize() {
                return new Dimension(TESTSIZE, TESTSIZE);
            }

            public synchronized void start() {
                if (!testdone) {
                    renderer = new Thread(this);
                    renderer.setPriority(Thread.MIN_PRIORITY);
                    renderer.start();
                }
            }

            public synchronized void stop() {
                renderer = null;
            }

            private Thread renderer;
            private int shapeIndex = 0;
            private byte[] indices = new byte[NUMTESTS * NUMTESTS];
            boolean testdone = false;

            private synchronized void setShape(int index) {
                shapeIndex = index;
                testdone = false;
                start();
            }

            public void run() {
                Thread me = Thread.currentThread();
                Graphics2D g2d = (Graphics2D) getGraphics();
                byte[] indices;
                Shape s = testShapes[shapeIndex];
                synchronized (this) {
                    if (renderer != me) {
                        return;
                    }
                    this.indices = new byte[NUMTESTS * NUMTESTS];
                    java.util.Arrays.fill(this.indices, (byte) 8);
                    indices = this.indices;
                }

                System.err.printf("%s %s\n", g2d, Color.yellow);
                g2d.setColor(Color.yellow);
                g2d.fillRect(0, 0, TESTSIZE, TESTSIZE);
                int numtests = 0;
                long start = System.currentTimeMillis();
                for (int y = 0; renderer == me && y < TESTSIZE; y += BOXSIZE) {
                    for (int x = 0; renderer == me && x < TESTSIZE; x += BOXSIZE) {
                        byte index = 0;
                        if (s.intersects(x, y, BOXSIZE, BOXSIZE)) {
                            index += 1;
                        }
                        if (s.contains(x + BOXCENTER, y + BOXCENTER)) {
                            index += 2;
                        }
                        if (s.contains(x, y, BOXSIZE, BOXSIZE)) {
                            index += 4;
                        }
                        numtests++;
                        int i = (y / BOXSIZE) * NUMTESTS + (x / BOXSIZE);
                        indices[i] = index;
                        g2d.setColor(colors[index]);
                        g2d.fillRect(x, y, BOXSIZE, BOXSIZE);
                    }
                }
                synchronized (this) {
                    if (renderer != me) {
                        return;
                    }
                    g2d.setColor(new Color(0, 0, 1, .2f));
                    g2d.fill(s);
                    testdone = true;
                    long end = System.currentTimeMillis();
                    System.out.println(numtests + " tests took " + (end - start) + "ms");
                }
            }

            public void paint(Graphics g) {
                g.setColor(Color.yellow);
                g.fillRect(0, 0, TESTSIZE, TESTSIZE);
                byte[] indices = this.indices;
                if (indices != null) {
                    for (int y = 0; y < TESTSIZE; y += BOXSIZE) {
                        for (int x = 0; x < TESTSIZE; x += BOXSIZE) {
                            int i = (y / BOXSIZE) * NUMTESTS + (x / BOXSIZE);
                            g.setColor(colors[indices[i]]);
                            g.fillRect(x, y, BOXSIZE, BOXSIZE);
                        }
                    }
                }
                Graphics2D g2d = (Graphics2D) g;
                g2d.setColor(new Color(0, 0, 1, .2f));
                g2d.fill(testShapes[shapeIndex]);
            }
        }

        static volatile PathHitTestManual pathHitTestManual;

        private static void createAndShowGUI() {
            pathHitTestManual = new PathHitTestManual();
            Frame frame = new Frame("PathHitTestManual test window");

            frame.add(pathHitTestManual);
            frame.setSize(400, 450);

            PassFailJFrame.addTestWindow(frame);
            PassFailJFrame.positionTestWindow(frame, PassFailJFrame.Position.HORIZONTAL);

            frame.setVisible(true);

            pathHitTestManual.init();
            pathHitTestManual.start();
        }

        public static void doManual() throws Exception {
            PassFailJFrame passFailJFrame = new PassFailJFrame.Builder()
                    .title("PathHitTestManual Instructions")
                    .instructions(INSTRUCTIONS)
                    .testTimeOut(5)
                    .rows(30)
                    .columns(70)
                    .screenCapture()
                    .build();

            EventQueue.invokeAndWait(PathHitTestManual::createAndShowGUI);
            try {
                passFailJFrame.awaitAndCheck();
            } finally {
                pathHitTestManual.stop();
            }
        }
    }
}
