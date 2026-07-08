/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import static java.lang.System.out;

/**
 * @test
 * @bug 8341381
 * @summary fix cubic offsetting issue (numerical accuracy)
 * @run main/othervm/timeout=20 Bug8341381
 * @modules java.desktop/sun.java2d.marlin
 */
public final class Bug8341381 {

    static final boolean SHOW_GUI = false;

    static final boolean CHECK_PIXELS = true;
    static final boolean TRACE_ALL = false;
    static final boolean TRACE_CHECK_PIXELS = false;

    static final boolean SAVE_IMAGE = false;

    static final boolean INTENSIVE = false;

    static final double DPI = 96;
    static final float STROKE_WIDTH = 15f;

    // delay is 1 frame at 60hz
    static final int DELAY = 16;
    // off-screen test step (1.0 by default)
    static final double STEP = (INTENSIVE) ? 1.0 / 117 : 1.0;

    // stats:
    static int N_TEST = 0;
    static int N_FAIL = 0;

    static final AtomicBoolean isMarlin = new AtomicBoolean();
    static final CountDownLatch latch = new CountDownLatch(1);

    // initialize j.u.l Logger:
    static final Logger log = Logger.getLogger("sun.java2d.marlin");
    public static void main(final String[] args) {
        Locale.setDefault(Locale.US);

        // FIRST: Get Marlin runtime state from its log:

        log.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                final String msg = record.getMessage();
                if (msg != null) {
                    // last space to avoid matching other settings:
                    if (msg.startsWith("sun.java2d.renderer ")) {
                        isMarlin.set(msg.contains("DMarlinRenderingEngine"));
                    }
                }

                final Throwable th = record.getThrown();
                // detect any Throwable:
                if (th != null) {
                    out.println("Test failed:\n" + record.getMessage());
                    th.printStackTrace(out);
                    throw new RuntimeException("Test failed: ", th);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() throws SecurityException {
            }
        });

        out.println("Bug8341381: start");
        final long startTime = System.currentTimeMillis();

        // enable Marlin logging & internal checks:
        System.setProperty("sun.java2d.renderer.log", "true");
        System.setProperty("sun.java2d.renderer.useLogger", "true");

        try {
            startTest();

            out.println("WAITING ...");
            latch.await(15, TimeUnit.SECONDS); // 2s typically

            if (isMarlin.get()) {
                out.println("Marlin renderer used at runtime.");
            } else {
                throw new RuntimeException("Marlin renderer NOT used at runtime !");
            }

            // show test report:
            out.println("TESTS: " + N_TEST + " FAILS: " + N_FAIL);

            if (N_FAIL > 0) {
                throw new RuntimeException("Bug8341381: " + N_FAIL + " / " + N_TEST + " test(s) failed !");
            }

        } catch (InterruptedException ie) {
            throw new RuntimeException(ie);
        } catch (InvocationTargetException ite) {
            throw new RuntimeException(ite);
        } finally {
            final double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
            out.println("Bug8341381: end (" + elapsed + " s)");
        }
    }

    private static void startTest() throws InterruptedException, InvocationTargetException {
        if (SHOW_GUI) {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    final JFrame viewer = new JFrame();
                    viewer.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    viewer.setContentPane(new CanvasPanel(viewer));
                    viewer.pack();
                    viewer.setVisible(true);
                }
            });
            return;
        } else {
            out.println("STEP: " + STEP);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final Context ctx = new Context();
                    final Dimension initialDim = ctx.bugDisplay.getSize(DPI);

                    double w = initialDim.width;
                    double h = initialDim.height;
                    do {
                        ctx.shouldScale(w, h);
                        ctx.paintImage();

                        // resize component:
                        w -= STEP;
                        h -= STEP;

                    } while (ctx.iterate());
                }
            }).start();
        }
    }

    static final class Context {

        final BugDisplay bugDisplay = new BugDisplay();
        double width = 0.0, height = 0.0;

        BufferedImage bimg = null;

        boolean shouldScale(final double w, final double h) {
            if ((w != width) || (h != height) || !bugDisplay.isScaled) {
                width = w;
                height = h;
                bugDisplay.scale(width, height);
                N_TEST++;
                return true;
            }
            return false;
        }

        void paintImage() {
            final int w = bugDisplay.canvasWidth;
            final int h = bugDisplay.canvasHeight;

            if ((bimg == null) || (w > bimg.getWidth()) || (h > bimg.getHeight())) {
                bimg = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
            }
            final Graphics gi = bimg.getGraphics();
            try {
                bugDisplay.paint(gi);
            } finally {
                gi.dispose();
            }
            if (!bugDisplay.checkImage(bimg)) {
                N_FAIL++;
            }
        }

        boolean iterate() {
            if ((bugDisplay.canvasWidth > 10) || (bugDisplay.canvasHeight > 10)) {
                // continue:
                return true;
            }
            out.println("Stop");
            latch.countDown();
            return false;
        }
    }

    static final class CanvasPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        private final Context ctx = new Context();
        private boolean resized = false;
        private Timer timer = null;

        public CanvasPanel(final JFrame frame) {
            timer = new Timer(DELAY, e -> {
                if (resized) {
                    resized = false;

                    if (ctx.iterate()) {
                        // resize component:
                        setSize((int) Math.round(ctx.width - 1), (int) Math.round(ctx.height - 1));
                    } else {
                        timer.stop();
                        if (frame != null) {
                            frame.setVisible(false);
                        }
                    }
                }
            });
            timer.setCoalesce(true);
            timer.setRepeats(true);
            timer.start();
        }

        @Override
        public void paint(final Graphics g) {
            final Dimension dim = getSize();
            if (ctx.shouldScale(dim.width, dim.height)) {
                this.resized = true;
            }
            super.paint(g);

            // paint on buffered image:
            if (CHECK_PIXELS) {
                final int w = ctx.bugDisplay.canvasWidth;
                final int h = ctx.bugDisplay.canvasHeight;
                if (this.resized) {
                    ctx.paintImage();
                }
                g.drawImage(ctx.bimg.getSubimage(0, 0, w, h), 0, 0, null);
            } else {
                ctx.bugDisplay.paint(g);
            }
        }

        @Override
        public Dimension getPreferredSize() {
            return ctx.bugDisplay.getSize(DPI);
        }
    }

    static final class BugDisplay {

        boolean isScaled = false;
        int canvasWidth;
        int canvasHeight;

        private final static java.util.List<CubicCurve2D> curves1 = Arrays.asList(
                new CubicCurve2D.Double(2191.0, 7621.0, 2191.0, 7619.0, 2191.0, 7618.0, 2191.0, 7617.0),
                new CubicCurve2D.Double(2191.0, 7617.0, 2191.0, 7617.0, 2191.0, 7616.0, 2191.0, 7615.0),
                new CubicCurve2D.Double(2198.0, 7602.0, 2200.0, 7599.0, 2203.0, 7595.0, 2205.0, 7590.0),
                new CubicCurve2D.Double(2205.0, 7590.0, 2212.0, 7580.0, 2220.0, 7571.0, 2228.0, 7563.0),
                new CubicCurve2D.Double(2228.0, 7563.0, 2233.0, 7557.0, 2239.0, 7551.0, 2245.0, 7546.0),
                new CubicCurve2D.Double(2245.0, 7546.0, 2252.0, 7540.0, 2260.0, 7534.0, 2267.0, 7528.0),
                new CubicCurve2D.Double(2267.0, 7528.0, 2271.0, 7526.0, 2275.0, 7524.0, 2279.0, 7521.0),
                new CubicCurve2D.Double(2279.0, 7521.0, 2279.0, 7520.0, 2280.0, 7520.0, 2281.0, 7519.0)
        );
        private final static java.util.List<CubicCurve2D> curves2 = Arrays.asList(
                new CubicCurve2D.Double(2281.0, 7519.0, 2282.0, 7518.0, 2282.0, 7517.0, 2283.0, 7516.0),
                new CubicCurve2D.Double(2283.0, 7516.0, 2284.0, 7515.0, 2284.0, 7515.0, 2285.0, 7514.0),
                new CubicCurve2D.Double(2291.0, 7496.0, 2292.0, 7495.0, 2292.0, 7494.0, 2291.0, 7493.0),
                new CubicCurve2D.Double(2291.0, 7493.0, 2290.0, 7492.0, 2290.0, 7492.0, 2289.0, 7492.0),
                new CubicCurve2D.Double(2289.0, 7492.0, 2288.0, 7491.0, 2286.0, 7492.0, 2285.0, 7492.0),
                new CubicCurve2D.Double(2262.0, 7496.0, 2260.0, 7497.0, 2259.0, 7497.0, 2257.0, 7498.0),
                new CubicCurve2D.Double(2257.0, 7498.0, 2254.0, 7498.0, 2251.0, 7499.0, 2248.0, 7501.0),
                new CubicCurve2D.Double(2248.0, 7501.0, 2247.0, 7501.0, 2245.0, 7502.0, 2244.0, 7503.0),
                new CubicCurve2D.Double(2207.0, 7523.0, 2203.0, 7525.0, 2199.0, 7528.0, 2195.0, 7530.0),
                new CubicCurve2D.Double(2195.0, 7530.0, 2191.0, 7534.0, 2186.0, 7538.0, 2182.0, 7541.0)
        );
        private final static java.util.List<CubicCurve2D> curves3 = Arrays.asList(
                new CubicCurve2D.Double(2182.0, 7541.0, 2178.0, 7544.0, 2174.0, 7547.0, 2170.0, 7551.0),
                new CubicCurve2D.Double(2170.0, 7551.0, 2164.0, 7556.0, 2158.0, 7563.0, 2152.0, 7569.0),
                new CubicCurve2D.Double(2152.0, 7569.0, 2148.0, 7573.0, 2145.0, 7577.0, 2141.0, 7582.0),
                new CubicCurve2D.Double(2141.0, 7582.0, 2138.0, 7588.0, 2134.0, 7595.0, 2132.0, 7602.0),
                new CubicCurve2D.Double(2132.0, 7602.0, 2132.0, 7605.0, 2131.0, 7608.0, 2131.0, 7617.0),
                new CubicCurve2D.Double(2131.0, 7617.0, 2131.0, 7620.0, 2131.0, 7622.0, 2131.0, 7624.0),
                new CubicCurve2D.Double(2131.0, 7624.0, 2131.0, 7630.0, 2132.0, 7636.0, 2135.0, 7641.0),
                new CubicCurve2D.Double(2135.0, 7641.0, 2136.0, 7644.0, 2137.0, 7647.0, 2139.0, 7650.0),
                new CubicCurve2D.Double(2139.0, 7650.0, 2143.0, 7658.0, 2149.0, 7664.0, 2155.0, 7670.0),
                new CubicCurve2D.Double(2155.0, 7670.0, 2160.0, 7676.0, 2165.0, 7681.0, 2171.0, 7686.0)
        );
        private final static java.util.List<CubicCurve2D> curves4 = Arrays.asList(
                new CubicCurve2D.Double(2171.0, 7686.0, 2174.0, 7689.0, 2177.0, 7692.0, 2180.0, 7694.0),
                new CubicCurve2D.Double(2180.0, 7694.0, 2185.0, 7698.0, 2191.0, 7702.0, 2196.0, 7706.0),
                new CubicCurve2D.Double(2196.0, 7706.0, 2199.0, 7708.0, 2203.0, 7711.0, 2207.0, 7713.0),
                new CubicCurve2D.Double(2244.0, 7734.0, 2245.0, 7734.0, 2247.0, 7735.0, 2248.0, 7736.0),
                new CubicCurve2D.Double(2248.0, 7736.0, 2251.0, 7738.0, 2254.0, 7739.0, 2257.0, 7739.0),
                new CubicCurve2D.Double(2257.0, 7739.0, 2259.0, 7739.0, 2260.0, 7739.0, 2262.0, 7740.0),
                new CubicCurve2D.Double(2285.0, 7745.0, 2286.0, 7745.0, 2288.0, 7745.0, 2289.0, 7745.0),
                new CubicCurve2D.Double(2289.0, 7745.0, 2290.0, 7745.0, 2290.0, 7744.0, 2291.0, 7743.0),
                new CubicCurve2D.Double(2291.0, 7743.0, 2292.0, 7742.0, 2292.0, 7741.0, 2291.0, 7740.0),
                new CubicCurve2D.Double(2285.0, 7722.0, 2284.0, 7721.0, 2284.0, 7721.0, 2283.0, 7720.0),
                new CubicCurve2D.Double(2283.0, 7720.0, 2282.0, 7719.0, 2282.0, 7719.0, 2281.0, 7718.0),
                new CubicCurve2D.Double(2281.0, 7718.0, 2280.0, 7717.0, 2279.0, 7716.0, 2279.0, 7716.0),
                new CubicCurve2D.Double(2279.0, 7716.0, 2275.0, 7712.0, 2271.0, 7710.0, 2267.0, 7708.0),
                new CubicCurve2D.Double(2267.0, 7708.0, 2260.0, 7702.0, 2252.0, 7697.0, 2245.0, 7691.0),
                new CubicCurve2D.Double(2245.0, 7691.0, 2239.0, 7685.0, 2233.0, 7679.0, 2228.0, 7673.0),
                new CubicCurve2D.Double(2228.0, 7673.0, 2220.0, 7665.0, 2212.0, 7656.0, 2205.0, 7646.0),
                new CubicCurve2D.Double(2205.0, 7646.0, 2203.0, 7641.0, 2200.0, 7637.0, 2198.0, 7634.0)
        );

        private final static Point2D.Double[] extent = {new Point2D.Double(0.0, 0.0), new Point2D.Double(7777.0, 10005.0)};

        private final static Stroke STROKE = new BasicStroke(STROKE_WIDTH);
        private final static Stroke STROKE_DASHED = new BasicStroke(STROKE_WIDTH, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
                10.0f, new float[] {100f, 0f}, 0.0f);

        // members:
        private final java.util.List<CubicCurve2D> allCurves = new ArrayList<>();
        private final Rectangle2D bboxAllCurves = new Rectangle2D.Double();

        BugDisplay() {
            allCurves.addAll(curves1);
            allCurves.addAll(curves2);
            allCurves.addAll(curves3);
            allCurves.addAll(curves4);

            // initialize bounding box:
            double x1 = Double.POSITIVE_INFINITY;
            double y1 = Double.POSITIVE_INFINITY;
            double x2 = Double.NEGATIVE_INFINITY;
            double y2 = Double.NEGATIVE_INFINITY;

            for (final CubicCurve2D c : allCurves) {
                final Rectangle2D r = c.getBounds2D();
                if (r.getMinX() < x1) {
                    x1 = r.getMinX();
                }
                if (r.getMinY() < y1) {
                    y1 = r.getMinY();
                }
                if (r.getMaxX() > x2) {
                    x2 = r.getMaxX();
                }
                if (r.getMaxY() > y2) {
                    y2 = r.getMaxY();
                }
            }
            // add margin of 10%:
            final double m = 1.1 * STROKE_WIDTH;
            bboxAllCurves.setFrameFromDiagonal(x1 - m, y1 - m, x2 + m, y2 + m);
        }

        public void paint(final Graphics g) {
            final Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, this.canvasWidth, this.canvasHeight);

            // ------ scale
            final AffineTransform tx_orig = g2d.getTransform();
            final AffineTransform tx = getDrawTransform();
            g2d.transform(tx);

            // draw bbox:
            if (!CHECK_PIXELS) {
                g2d.setColor(Color.RED);
                g2d.setStroke(STROKE);
                g2d.draw(bboxAllCurves);
            }
            // draw curves:
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
            g2d.setColor(Color.BLACK);

            // dasher + stroker:
            g2d.setStroke(STROKE_DASHED);
            this.allCurves.forEach(g2d::draw);

            // reset
            g2d.setTransform(tx_orig);
        }

        private AffineTransform getDrawTransform() {
            // ------ scale
            double minX = extent[0].x, maxX = extent[1].x;
            double minY = extent[0].y, maxY = extent[1].y;

            // we're scaling and respecting the proportions, check which scale to use
            double sx = this.canvasWidth / Math.abs(maxX - minX);
            double sy = this.canvasHeight / Math.abs(maxY - minY);
            double s = Math.min(sx, sy);

            double m00, m11, m02, m12;
            if (minX < maxX) {
                m00 = s;
                m02 = -s * minX;
            } else {
                // inverted X axis
                m00 = -s;
                m02 = this.canvasWidth + s * maxX;
            }
            if (minY < maxY) {
                m11 = s;
                m12 = -s * minY;
            } else {
                // inverted Y axis
                m11 = -s;
                m12 = this.canvasHeight + s * maxY;
            }

            // scale to the available view port
            AffineTransform scaleTransform = new AffineTransform(m00, 0, 0, m11, m02, m12);

            // invert the Y axis since (0, 0) is at top left for AWT
            AffineTransform invertY = new AffineTransform(1, 0, 0, -1, 0, this.canvasHeight);
            invertY.concatenate(scaleTransform);

            return invertY;
        }

        public Dimension getSize(double dpi) {
            double metricScalingFactor = 0.02539999969303608;
            // 1 inch = 25,4 millimeter
            final double factor = dpi * metricScalingFactor / 25.4;

            int width = (int) Math.ceil(Math.abs(extent[1].x - extent[0].x) * factor);
            int height = (int) Math.ceil(Math.abs(extent[1].y - extent[0].y) * factor);

            return new Dimension(width, height);
        }

        public void scale(double w, double h) {
            double extentWidth = Math.abs(extent[1].x - extent[0].x);
            double extentHeight = Math.abs(extent[1].y - extent[0].y);

            double fx = w / extentWidth;
            if (fx * extentHeight > h) {
                fx = h / extentHeight;
            }
            this.canvasWidth = (int) Math.round(fx * extentWidth);
            this.canvasHeight = (int) Math.round(fx * extentHeight);

            // out.println("canvas scaled (" + canvasWidth + " x " + canvasHeight + ")");

            this.isScaled = true;
        }

        protected boolean checkImage(BufferedImage image) {
            final AffineTransform tx = getDrawTransform();

            final Point2D pMin = new Point2D.Double(bboxAllCurves.getMinX(), bboxAllCurves.getMinY());
            final Point2D pMax = new Point2D.Double(bboxAllCurves.getMaxX(), bboxAllCurves.getMaxY());

            final Point2D tMin = tx.transform(pMin, null);
            final Point2D tMax = tx.transform(pMax, null);

            int xMin = (int) tMin.getX();
            int xMax = (int) tMax.getX();
            if (xMin > xMax) {
                int t = xMin;
                xMin = xMax;
                xMax = t;
            }

            int yMin = (int) tMin.getY();
            int yMax = (int) tMax.getY();
            if (yMin > yMax) {
                int t = yMin;
                yMin = yMax;
                yMax = t;
            }
            // add pixel margin (AA):
            xMin -= 3;
            xMax += 4;
            yMin -= 3;
            yMax += 4;

            if (xMin < 0 || xMax > image.getWidth()
                    || yMin < 0 || yMax > image.getHeight()) {
                return true;
            }

            // out.println("Checking rectangle: " + tMin + " to " + tMax);
            // out.println("X min: " + xMin + " - max: " + xMax);
            // out.println("Y min: " + yMin + " - max: " + yMax);

            final Raster raster = image.getData();
            final int expected = Color.WHITE.getRGB();
            int nBadPixels = 0;

            // horizontal lines:
            for (int x = xMin; x <= xMax; x++) {
                if (!checkPixel(raster, x, yMin, expected)) {
                    nBadPixels++;
                }
                if (!checkPixel(raster, x, yMax, expected)) {
                    nBadPixels++;
                }
            }

            // vertical lines:
            for (int y = yMin; y <= yMax; y++) {
                if (!checkPixel(raster, xMin, y, expected)) {
                    nBadPixels++;
                }
                if (!checkPixel(raster, xMax, y, expected)) {
                    nBadPixels++;
                }
            }

            if (nBadPixels != 0) {
                out.println("(" + canvasWidth + " x " + canvasHeight + ") BAD pixels = " + nBadPixels);

                if (SAVE_IMAGE) {
                    try {
                        final File file = new File("Bug8341381-" + canvasWidth + "-" + canvasHeight + ".png");

                        out.println("Writing file: " + file.getAbsolutePath());
                        ImageIO.write(image.getSubimage(0, 0, canvasWidth, canvasHeight), "PNG", file);
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
                return false;
            } else if (TRACE_ALL) {
                out.println("(" + canvasWidth + " x " + canvasHeight + ") OK");
            }
            return true;
        }

        private final static int[] TMP_RGB = new int[1];

        private static boolean checkPixel(final Raster raster,
                                          final int x, final int y,
                                          final int expected) {

            final int[] rgb = (int[]) raster.getDataElements(x, y, TMP_RGB);

            if (rgb[0] != expected) {
                if (TRACE_CHECK_PIXELS) {
                    out.println("bad pixel at (" + x + ", " + y + ") = " + rgb[0]
                            + " expected = " + expected);
                }
                return false;
            }
            return true;
        }
    }
}
