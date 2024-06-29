/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2024, JetBrains s.r.o.. All rights reserved.
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

package renderperf;

import java.awt.AWTException;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Insets;
import java.awt.LinearGradientPaint;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Transparency;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferShort;
import java.awt.image.VolatileImage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntBinaryOperator;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;


public final class RenderPerfTest {

    private final static String VERSION = "RenderPerfTest 2024.02";

    private static final HashSet<String> ignoredTests = new HashSet<>();

    static {
        // add ignored tests here
        // ignoredTests.add("testMyIgnoredTest");
        ignoredTests.add("testCalibration"); // not from command line
    }

    private final static String EXEC_MODE_ROBOT = "robot";
    private final static String EXEC_MODE_BUFFER = "buffer";
    private final static String EXEC_MODE_VOLATILE = "volatile";
    private final static String EXEC_MODE_DEFAULT = EXEC_MODE_ROBOT;

    public final static List<String> EXEC_MODES = Arrays.asList(EXEC_MODE_ROBOT, EXEC_MODE_BUFFER, EXEC_MODE_VOLATILE);

    private static String EXEC_MODE = EXEC_MODE_DEFAULT;

    private final static String GC_MODE_DEF = "def";
    private final static String GC_MODE_ALL = "all";

    private static String GC_MODE = GC_MODE_DEF;

    // System properties:
    private final static boolean CALIBRATION = "true".equalsIgnoreCase(System.getProperty("CALIBRATION", "false"));
    private final static boolean REPORT_OVERALL_FPS = "true".equalsIgnoreCase(System.getProperty("REPORT_OVERALL_FPS", "false"));

    private final static boolean DUMP_SAMPLES = "true".equalsIgnoreCase(System.getProperty("DUMP_SAMPLES", "false"));
    private final static boolean TRACE = "true".equalsIgnoreCase(System.getProperty("TRACE", "false"));
    private final static boolean TRACE_CONFIGURE = "true".equalsIgnoreCase(System.getProperty("TRACE_CONFIGURE", "false"));
    private final static boolean TRACE_SYNC = "true".equalsIgnoreCase(System.getProperty("TRACE_SYNC", "false"));

    private final static boolean DELAY_START = "true".equalsIgnoreCase(System.getProperty("DelayStart", "false"));
    private final static boolean DELAY_TEST = "true".equalsIgnoreCase(System.getProperty("DelayTest", "false"));

    private final static boolean ROBOT_TIME_DELAY = "true".equalsIgnoreCase(System.getProperty("ROBOT_TIME_DELAY", "true"));
    private final static boolean ROBOT_TIME_ROUND = "true".equalsIgnoreCase(System.getProperty("ROBOT_TIME_ROUND", "false"));

    private final static boolean TEXT_VERSION = "true".equalsIgnoreCase(System.getProperty("TEXT_VERSION", "true"));

    // time scale multiplier to get more samples so refined metrics:
    private final static int TIME_SCALE = Integer.getInteger("TIME_SCALE", 1);

    // default settings:
    private static boolean VERBOSE = false;
    private static boolean VERBOSE_FONT_CONFIG = false;
    private static boolean VERBOSE_GRAPHICS_CONFIG = false;

    private static int REPEATS = 1;

    private static boolean USE_FPS = true;

    private static int NW = 1;

    private final static int N_DEFAULT = 1000;
    private static int N = N_DEFAULT;
    private final static float WIDTH = 800;
    private final static float HEIGHT = 800;
    private final static float R = 25;
    private final static int BW = 50;
    private final static int BH = 50;
    private final static int IMAGE_W = (int) (WIDTH + BW);
    private final static int IMAGE_H = (int) (HEIGHT + BH);

    // Test attributes:
    private static String TEXT_STR = TEXT_VERSION ? VERSION : "The quick brown fox jumps over the lazy dog";

    private static String TEXT_FONT = Font.DIALOG;
    private static int TEXT_SIZE_DEFAULT = 12;
    private static int TEXT_SIZE_LARGE = 32;

    private final static int COUNT = 600 * TIME_SCALE;
    private final static int MIN_COUNT = 20;
    private final static int MAX_SAMPLE_COUNT = 2 * COUNT;

    private static int WARMUP_COUNT = MIN_COUNT;

    private final static int DELAY = 1;
    private final static int CYCLE_DELAY = DELAY;

    private final static long MIN_MEASURE_TIME_NS = 1000L * 1000 * 1000 * TIME_SCALE; // 1s min
    private final static long MAX_MEASURE_TIME_NS = 6000L * 1000 * 1000 * TIME_SCALE; // 6s max
    private final static int MAX_FRAME_CYCLES = 1000 * TIME_SCALE / CYCLE_DELAY;

    private final static int COLOR_TOLERANCE = 10;

    private final static Color[] MARKER = {Color.RED, Color.BLUE, Color.GREEN};

    private final static Toolkit TOOLKIT = Toolkit.getDefaultToolkit();

    private final static long FRAME_MAX = 60;
    private final static long FRAME_PREC_IN_NANOS = (1000L * 1000 * 1000) / (2L * FRAME_MAX);

    interface Configurable {
        void configure(Graphics2D g2d, boolean enabled);
    }

    final static class ConfigurableAA implements Configurable {
        @Override
        public void configure(final Graphics2D g2d, final boolean enabled) {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    enabled ? RenderingHints.VALUE_ANTIALIAS_ON
                            : RenderingHints.VALUE_ANTIALIAS_OFF);
        }
    }

    final static class ConfigurableTextAA implements Configurable {
        @Override
        public void configure(final Graphics2D g2d, final boolean enabled) {
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    enabled ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON
                            : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        }
    }

    final static class ConfigurableTextLCD implements Configurable {
        @Override
        public void configure(final Graphics2D g2d, final boolean enabled) {
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    enabled ? RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB
                            : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        }
    }

    final static class ConfigurableXORMode implements Configurable {
        @Override
        public void configure(final Graphics2D g2d, final boolean enabled) {
            if (enabled) {
                g2d.setXORMode(Color.WHITE);
            } else {
                g2d.setPaintMode();
            }
        }
    }

    final static class ConfigurableXORModeTextLCD implements Configurable {
        @Override
        public void configure(final Graphics2D g2d, final boolean enabled) {
            if (enabled) {
                g2d.setXORMode(Color.WHITE);
            } else {
                g2d.setPaintMode();
            }
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    enabled ? RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB
                            : RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT);
        }
    }

    final static class Particles {
        private final float[] bx;
        private final float[] by;
        private final float[] vx;
        private final float[] vy;
        private final float r;
        private final int n;

        private final float x0;
        private final float y0;
        private final float width;
        private final float height;

        Particles(int n, float r, float x0, float y0, float width, float height) {
            bx = new float[n];
            by = new float[n];
            vx = new float[n];
            vy = new float[n];
            this.n = n;
            this.r = r;
            this.x0 = x0;
            this.y0 = y0;
            this.width = width;
            this.height = height;
            for (int i = 0; i < n; i++) {
                bx[i] = (float) (x0 + r + 0.1 + Math.random() * (width - 2 * r - 0.2 - x0));
                by[i] = (float) (y0 + r + 0.1 + Math.random() * (height - 2 * r - 0.2 - y0));
                vx[i] = 0.1f * (float) (Math.random() * 2 * r - r);
                vy[i] = 0.1f * (float) (Math.random() * 2 * r - r);
            }

        }

        void render(Graphics2D g2d, ParticleRenderer renderer) {
            for (int i = 0; i < n; i++) {
                renderer.render(g2d, i, bx, by, vx, vy);
            }
        }

        void update() {
            for (int i = 0; i < n; i++) {
                bx[i] += vx[i];
                if (bx[i] + r > width || bx[i] - r < x0) vx[i] = -vx[i];
                by[i] += vy[i];
                if (by[i] + r > height || by[i] - r < y0) vy[i] = -vy[i];
            }
        }
    }

    interface Renderable {
        void setup(Graphics2D g2d, boolean enabled);

        void render(Graphics2D g2d);

        void update();
    }

    final static class ParticleRenderable implements Renderable {
        final Particles balls;
        final ParticleRenderer renderer;
        Configurable configure = null;

        ParticleRenderable(final Particles balls, final ParticleRenderer renderer) {
            this.balls = balls;
            this.renderer = renderer;
        }

        @Override
        public void setup(final Graphics2D g2d, final boolean enabled) {
            if (configure != null) {
                if (TRACE_CONFIGURE) {
                    System.out.println("configure(" + configure.getClass().getSimpleName() + "): " + enabled);
                }
                configure.configure(g2d, enabled);
            }
        }

        @Override
        public void render(Graphics2D g2d) {
            balls.render(g2d, renderer);
        }

        @Override
        public void update() {
            balls.update();
        }

        public ParticleRenderable configure(final Configurable configure) {
            this.configure = configure;
            return this;
        }
    }

    interface ParticleRenderer {
        void render(Graphics2D g2d, int id, float[] x, float[] y, float[] vx, float[] vy);
    }

    final static class CalibrationParticleRenderer implements ParticleRenderer {

        CalibrationParticleRenderer() {
        }

        @Override
        public void render(Graphics2D g2d, int id, float[] x, float[] y, float[] vx, float[] vy) {
            // no-op
        }
    }

    final static class MixedParticleRenderer implements ParticleRenderer {

        private final ParticleRenderer[] renderers;

        MixedParticleRenderer(ParticleRenderer... renderers) {
            this.renderers = renderers;
        }

        @Override
        public void render(Graphics2D g2d, int id, float[] x, float[] y, float[] vx, float[] vy) {
            renderers[id % renderers.length].render(g2d, id, x, y, vx, vy);
        }
    }

    final static class BatchedParticleRenderer implements ParticleRenderer {

        private final ParticleRenderer[] renderers;

        BatchedParticleRenderer(ParticleRenderer... renderers) {
            this.renderers = renderers;
        }

        @Override
        public void render(Graphics2D g2d, int id, float[] x, float[] y, float[] vx, float[] vy) {
            final int step = N / renderers.length;
            renderers[(id / step) % renderers.length].render(g2d, id, x, y, vx, vy);
        }
    }

    static class FlatParticleRenderer implements ParticleRenderer {
        Color[] colors;
        float r;

        FlatParticleRenderer(int n, float r) {
            colors = new Color[n];
            this.r = r;
            for (int i = 0; i < n; i++) {
                colors[i] = new Color((float) Math.random(),
                        (float) Math.random(), (float) Math.random());
            }
        }

        @Override
        public void render(Graphics2D g2d, int id, float[] x, float[] y, float[] vx, float[] vy) {
            g2d.setColor(colors[id % colors.length]);
            g2d.fillOval((int) (x[id] - r), (int) (y[id] - r), (int) (2 * r), (int) (2 * r));
        }

    }

    static class ClipFlatParticleRenderer extends FlatParticleRenderer {

        ClipFlatParticleRenderer(int n, float r) {
            super(n, r);
        }

        @Override
        public void render(Graphics2D g2d, int id, float[] x, float[] y, float[] vx, float[] vy) {
            if ((id % 10) == 0) {
                g2d.setColor(colors[id % colors.length]);
                g2d.setClip(new Ellipse2D.Double((int) (x[id] - r), (int) (y[id] - r), (int) (2 * r), (int) (2 * r)));
                g2d.fillRect((int) (x[id] - 2 * r), (int) (y[id] - 2 * r), (int) (4 * r), (int) (4 * r));
            }
        }

    }

    static class WhiteTextParticleRenderer implements ParticleRenderer {
        final float r;
        final Font font;

        WhiteTextParticleRenderer(float r) {
            this(r, TEXT_SIZE_DEFAULT);
        }

        WhiteTextParticleRenderer(float r, int fontSize) {
            this.r = r;
            font = new Font(TEXT_FONT, Font.PLAIN, fontSize);
        }

        void setPaint(Graphics2D g2d, int id) {
            g2d.setColor(Color.WHITE);
        }

        @Override
        public void render(Graphics2D g2d, int id, float[] x, float[] y, float[] vx, float[] vy) {
            setPaint(g2d, id);
            g2d.setFont(font);
            g2d.drawString(TEXT_STR, (int) (x[id] - r), (int) (y[id] - r));
            g2d.drawString(TEXT_STR, (int) (x[id] - r), (int) y[id]);
            g2d.drawString(TEXT_STR, (int) (x[id] - r), (int) (y[id] + r));
        }
    }

    static class TextParticleRenderer extends WhiteTextParticleRenderer {
        Color[] colors;

        float r;

        TextParticleRenderer(int n, float r) {
            this(n,r, TEXT_SIZE_DEFAULT);
        }

        TextParticleRenderer(int n, float r, int fontSize) {
            super(r, fontSize);
            colors = new Color[n];
            this.r = r;
            for (int i = 0; i < n; i++) {
                colors[i] = new Color((float) Math.random(),
                        (float) Math.random(), (float) Math.random());
            }
        }

        void setPaint(Graphics2D g2d, int id) {
            g2d.setColor(colors[id % colors.length]);
        }
    }

    static class LargeTextParticleRenderer extends TextParticleRenderer {

        LargeTextParticleRenderer(int n, float r) {
            super(n, r, TEXT_SIZE_LARGE);
        }

        @Override
        public void render(Graphics2D g2d, int id, float[] x, float[] y, float[] vx, float[] vy) {
            if (id % 100 != 0) return;
            super.render(g2d, id, x, y, vx, vy);
        }
    }

    static class FlatOvalRotParticleRenderer extends FlatParticleRenderer {

        FlatOvalRotParticleRenderer(int n, float r) {
            super(n, r);
        }

        void setPaint(Graphics2D g2d, int id) {
            g2d.setColor(colors[id % colors.length]);
        }

        @Override
        public void render(Graphics2D g2d, int id, float[] x, float[] y, float[] vx, float[] vy) {
            setPaint(g2d, id);
            if (Math.abs(vx[id] + vy[id]) > 0.001) {
                AffineTransform t = (AffineTransform) g2d.getTransform().clone();
                double l = vx[id] / Math.sqrt(vx[id] * vx[id] + vy[id] * vy[id]);
                if (vy[id] < 0) {
                    l = -l;
                }
                g2d.translate(x[id], y[id]);
                g2d.rotate(Math.acos(l));
                g2d.fillOval(-(int) r, (int) (-0.5 * r), (int) (2 * r), (int) r);
                g2d.setTransform(t);
            } else {
                g2d.fillOval((int) (x[id] - r), (int) (y[id] - 0.5 * r),
                        (int) (2 * r), (int) r);
            }
        }
    }

    static class LinGradOvalRotParticleRenderer extends FlatOvalRotParticleRenderer {

        LinGradOvalRotParticleRenderer(int n, float r) {
            super(n, r);
        }

        @Override
        void setPaint(Graphics2D g2d, int id) {
            Point2D start = new Point2D.Double(-r, -0.5 * r);
            Point2D end = new Point2D.Double(2 * r, r);
            float[] dist = {0.0f, 1.0f};
            Color[] cls = {colors[id % colors.length], colors[(colors.length - id) % colors.length]};
            LinearGradientPaint p = new LinearGradientPaint(start, end, dist, cls);
            g2d.setPaint(p);
        }
    }

    static class LinGrad3OvalRotParticleRenderer extends FlatOvalRotParticleRenderer {

        LinGrad3OvalRotParticleRenderer(int n, float r) {
            super(n, r);
        }

        @Override
        void setPaint(Graphics2D g2d, int id) {
            Point2D start = new Point2D.Double(-r, -0.5 * r);
            Point2D end = new Point2D.Double(2 * r, r);
            float[] dist = {0.0f, 0.5f, 1.0f};
            Color[] cls = {
                    colors[id % colors.length],
                    colors[(colors.length - id) % colors.length],
                    colors[(id * 5) % colors.length]};
            LinearGradientPaint p = new LinearGradientPaint(start, end, dist, cls);
            g2d.setPaint(p);
        }
    }

    static class RadGrad3OvalRotParticleRenderer extends FlatOvalRotParticleRenderer {

        RadGrad3OvalRotParticleRenderer(int n, float r) {
            super(n, r);
        }

        @Override
        void setPaint(Graphics2D g2d, int id) {
            Point2D start = new Point2D.Double();
            float[] dist = {0.0f, 0.5f, 1.0f};
            Color[] cls = {
                    colors[id % colors.length],
                    colors[(colors.length - id) % colors.length],
                    colors[(id * 5) % colors.length]};
            RadialGradientPaint p = new RadialGradientPaint(start, r, dist, cls);
            g2d.setPaint(p);
        }
    }

    static class FlatBoxParticleRenderer extends FlatParticleRenderer {

        FlatBoxParticleRenderer(int n, float r) {
            super(n, r);
        }

        @Override
        public void render(Graphics2D g2d, int id, float[] x, float[] y, float[] vx, float[] vy) {
            g2d.setColor(colors[id % colors.length]);
            g2d.fillRect((int) (x[id] - r), (int) (y[id] - r), (int) (2 * r), (int) (2 * r));
        }
    }

    static class ClipFlatBoxParticleRenderer extends FlatParticleRenderer {

        ClipFlatBoxParticleRenderer(int n, float r) {
            super(n, r);
        }

        @Override
        public void render(Graphics2D g2d, int id, float[] x, float[] y, float[] vx, float[] vy) {
            if ((id % 10) == 0) {
                g2d.setColor(colors[id % colors.length]);
                g2d.setClip((int) (x[id] - r), (int) (y[id] - r), (int) (2 * r), (int) (2 * r));
                g2d.fillRect((int) (x[id] - 2 * r), (int) (y[id] - 2 * r), (int) (4 * r), (int) (4 * r));
            }
        }
    }

    static class ImgParticleRenderer extends FlatParticleRenderer {
        BufferedImage dukeImg;

        ImgParticleRenderer(int n, float r) {
            super(n, r);
            try {
                dukeImg = ImageIO.read(
                        Objects.requireNonNull(
                                RenderPerfTest.class.getClassLoader().getResourceAsStream(
                                        "renderperf/images/duke.png")));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void render(Graphics2D g2d, int id, float[] x, float[] y, float[] vx, float[] vy) {
            g2d.setColor(colors[id % colors.length]);
            g2d.drawImage(dukeImg, (int) (x[id] - r), (int) (y[id] - r), (int) (2 * r), (int) (2 * r), null);
        }

    }

    static class VolImgParticleRenderer extends ImgParticleRenderer {
        VolatileImage volImg;

        VolImgParticleRenderer(int n, float r) {
            super(n, r);
        }

        @Override
        public void render(Graphics2D g2d, int id, float[] x, float[] y, float[] vx, float[] vy) {
            GraphicsConfiguration config = g2d.getDeviceConfiguration();
            if (volImg == null) {
                volImg = config.createCompatibleVolatileImage(dukeImg.getWidth(), dukeImg.getHeight(),
                        Transparency.TRANSLUCENT);
                Graphics2D g = volImg.createGraphics();
                g.setComposite(AlphaComposite.Src);
                g.drawImage(dukeImg, null, null);
                g.dispose();
            } else {
                int status = volImg.validate(config);
                if (status == VolatileImage.IMAGE_INCOMPATIBLE) {
                    volImg = config.createCompatibleVolatileImage(dukeImg.getWidth(), dukeImg.getHeight(),
                            Transparency.TRANSLUCENT);
                }
                if (status != VolatileImage.IMAGE_OK) {
                    Graphics2D g = volImg.createGraphics();
                    g.setComposite(AlphaComposite.Src);
                    g.drawImage(dukeImg, null, null);
                    g.dispose();
                }
            }
            Composite savedComposite = g2d.getComposite();
            g2d.setComposite(AlphaComposite.SrcOver);
            g2d.drawImage(volImg, (int) (x[id] - r), (int) (y[id] - r), (int) (2 * r), (int) (2 * r), null);
            g2d.setComposite(savedComposite);
        }
    }

    static class FlatBoxRotParticleRenderer extends FlatParticleRenderer {

        FlatBoxRotParticleRenderer(int n, float r) {
            super(n, r);
        }

        @Override
        public void render(Graphics2D g2d, int id, float[] x, float[] y, float[] vx, float[] vy) {
            g2d.setColor(colors[id % colors.length]);
            if (Math.abs(vx[id] + vy[id]) > 0.001) {
                AffineTransform t = (AffineTransform) g2d.getTransform().clone();
                double l = vx[id] / Math.sqrt(vx[id] * vx[id] + vy[id] * vy[id]);
                if (vy[id] < 0) {
                    l = -l;
                }
                g2d.translate(x[id], y[id]);
                g2d.rotate(Math.acos(l));
                g2d.fillRect(-(int) r, -(int) r, (int) (2 * r), (int) (2 * r));
                g2d.setTransform(t);
            } else {
                g2d.fillRect((int) (x[id] - r), (int) (y[id] - r),
                        (int) (2 * r), (int) (2 * r));
            }
        }
    }

    static class WiredParticleRenderer extends FlatParticleRenderer {

        WiredParticleRenderer(int n, float r) {
            super(n, r);
        }

        @Override
        public void render(Graphics2D g2d, int id, float[] x, float[] y, float[] vx, float[] vy) {
            g2d.setColor(colors[id % colors.length]);
            g2d.drawOval((int) (x[id] - r), (int) (y[id] - r), (int) (2 * r), (int) (2 * r));
        }
    }

    static class WiredBoxParticleRenderer extends FlatParticleRenderer {

        WiredBoxParticleRenderer(int n, float r) {
            super(n, r);
        }

        @Override
        public void render(Graphics2D g2d, int id, float[] x, float[] y, float[] vx, float[] vy) {
            g2d.setColor(colors[id % colors.length]);
            g2d.drawRect((int) (x[id] - r), (int) (y[id] - r), (int) (2 * r), (int) (2 * r));
        }
    }

    static class SegParticleRenderer extends FlatParticleRenderer {

        SegParticleRenderer(int n, float r) {
            super(n, r);
        }

        @Override
        public void render(Graphics2D g2d, int id, float[] x, float[] y, float[] vx, float[] vy) {
            double v = Math.sqrt(vx[id] * vx[id] + vy[id] * vy[id]);
            float nvx = (float) (vx[id] / v);
            float nvy = (float) (vy[id] / v);
            g2d.setColor(colors[id % colors.length]);
            g2d.drawLine((int) (x[id] - r * nvx), (int) (y[id] - r * nvy),
                    (int) (x[id] + 2 * r * nvx), (int) (y[id] + 2 * r * nvy));
        }
    }

    static class WiredQuadParticleRenderer extends FlatParticleRenderer {

        WiredQuadParticleRenderer(int n, float r) {
            super(n, r);
        }

        @Override
        public void render(Graphics2D g2d, int id, float[] x, float[] y, float[] vx, float[] vy) {
            if (id > 2 && (id % 3) == 0) {
                g2d.setColor(colors[id % colors.length]);
                g2d.draw(new QuadCurve2D.Float(x[id - 3], y[id - 3], x[id - 2], y[id - 2], x[id - 1], y[id - 1]));
            }
        }
    }

    static class FlatQuadParticleRenderer extends FlatParticleRenderer {

        FlatQuadParticleRenderer(int n, float r) {
            super(n, r);
        }

        @Override
        public void render(Graphics2D g2d, int id, float[] x, float[] y, float[] vx, float[] vy) {
            if (id > 2 && (id % 3) == 0) {
                g2d.setColor(colors[id % colors.length]);
                g2d.fill(new QuadCurve2D.Float(x[id - 3], y[id - 3], x[id - 2], y[id - 2], x[id - 1], y[id - 1]));
            }
        }
    }

    static class BlitImageParticleRenderer extends FlatParticleRenderer {
        BufferedImage image;

        BlitImageParticleRenderer(int n, float r, BufferedImage img) {
            super(n, r);
            image = img;
            fill(image);
        }

        @Override
        public void render(Graphics2D g2d, int id, float[] x, float[] y, float[] vx, float[] vy) {
            g2d.drawImage(image, (int) (x[id] - r), (int) (y[id] - r), (int) (2 * r), (int) (2 * r), null);
        }

        private static void fill(final Image image) {
            final Graphics2D graphics = (Graphics2D) image.getGraphics();
            graphics.setComposite(AlphaComposite.Src);
            for (int i = 0; i < image.getHeight(null); ++i) {
                graphics.setColor(new Color(i, 0, 0));
                graphics.fillRect(0, i, image.getWidth(null), 1);
            }
            graphics.dispose();
        }
    }

    static class SwBlitImageParticleRenderer extends BlitImageParticleRenderer {

        SwBlitImageParticleRenderer(int n, float r, final int type) {
            super(n, r, makeUnmanagedBI(type));
        }

        private static BufferedImage makeUnmanagedBI(final int type) {
            final BufferedImage bi = new BufferedImage(17, 33, type);
            final DataBuffer db = bi.getRaster().getDataBuffer();
            if (db instanceof DataBufferInt) {
                ((DataBufferInt) db).getData();
            } else if (db instanceof DataBufferShort) {
                ((DataBufferShort) db).getData();
            } else if (db instanceof DataBufferByte) {
                ((DataBufferByte) db).getData();
            }
            bi.setAccelerationPriority(0.0f);
            return bi;
        }
    }

    static class SurfaceBlitImageParticleRenderer extends BlitImageParticleRenderer {

        SurfaceBlitImageParticleRenderer(int n, float r, final int type) {
            super(n, r, makeManagedBI(type));
        }

        private static BufferedImage makeManagedBI(final int type) {
            final BufferedImage bi = new BufferedImage(17, 33, type);
            bi.setAccelerationPriority(1.0f);
            return bi;
        }
    }

    final static class PerfMeter {

        private final FrameHandler fh;
        private final String name;
        private final PerfMeterExecutor executor;

        PerfMeter(final FrameHandler fh, String name) {
            this.fh = fh;
            this.name = name;
            executor = getExecutor();
        }

        void exec(final Renderable renderable) throws Exception {
            executor.exec(name, renderable);
        }

        private PerfMeterExecutor getExecutor() {
            switch (EXEC_MODE) {
                default:
                case EXEC_MODE_ROBOT:
                    return new PerfMeterRobot(fh);
                case EXEC_MODE_BUFFER:
                    fh.prepareImageProvider(false);
                    return new PerfMeterImageProvider(fh);
                case EXEC_MODE_VOLATILE:
                    fh.prepareImageProvider(true);
                    return new PerfMeterImageProvider(fh);
            }
        }
    }

    static void paintTest(final Renderable renderable, final Graphics2D g2d,
                          final Color markerColor, final boolean doSync) {
        // clip to frame:
        g2d.setClip(0, 0, IMAGE_W, IMAGE_H);
        // clear background:
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, IMAGE_W, IMAGE_H);

        // render test:
        renderable.setup(g2d, true);
        renderable.render(g2d);
        renderable.setup(g2d, false);

        // draw marker at end:
        g2d.setClip(0, 0, BW, BH);
        g2d.setColor(markerColor);
        g2d.fillRect(0, 0, BW, BH);

        if (doSync) {
            // synchronize toolkit:
            TOOLKIT.sync();
        }
    }

    final static class FrameHandler {

        private boolean calibrate = VERBOSE;

        private int threadId = -1;
        private int frameId = -1;

        private final GraphicsConfiguration gc;

        private JFrame frame = null;

        private final CountDownLatch latchShownFrame = new CountDownLatch(1);
        private final CountDownLatch latchClosedFrame = new CountDownLatch(1);

        private ImageProvider imageProvider = null;

        FrameHandler(GraphicsConfiguration gc) {
            this.gc = gc;
        }

        void setIds(int threadId, int frameId) {
            this.threadId = threadId;
            this.frameId = frameId;
        }

        void prepareFrameEDT(final String title) {
            if (frame == null) {
                frame = new JFrame(gc);
                frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
                frame.addComponentListener(new ComponentAdapter() {
                    @Override
                    public void componentShown(ComponentEvent e) {
                        latchShownFrame.countDown();
                    }
                });
                frame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent e) {
                        latchClosedFrame.countDown();
                    }
                });
            }
            frame.setTitle(title);
        }

        void showFrameEDT(final JPanel panel) {
            if (frame != null) {
                panel.setPreferredSize(new Dimension(IMAGE_W, IMAGE_H));
                panel.setBackground(Color.BLACK);

                frame.getContentPane().removeAll();
                frame.getContentPane().add(panel);
                frame.getContentPane().revalidate();

                if (!frame.isVisible()) {
                    if (frameId != -1) {
                        final int off = (frameId - 1) * 100;
                        final Rectangle gcBounds = gc.getBounds();
                        final int xoff = gcBounds.x + off;
                        final int yoff = gcBounds.y + off;

                        if ((xoff != 0) || (yoff != 0)) {
                            frame.setLocation(xoff, yoff);
                        }
                    }
                    frame.pack();
                    frame.setVisible(true);
                }
            }
        }

        void waitFrameShown() throws Exception {
            latchShownFrame.await();
        }

        void resetFrame() throws Exception {
            if (frame != null) {
                SwingUtilities.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        frame.getContentPane().removeAll();
                        frame.getContentPane().revalidate();
                    }
                });
            }
        }

        void repaintFrame() throws Exception {
            if (frame != null) {
                SwingUtilities.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        frame.repaint();
                    }
                });
            }
        }

        private void waitFrameHidden() throws Exception {
            latchClosedFrame.await();
        }

        void hideFrameAndWait() throws Exception {
            if (frame != null) {
                SwingUtilities.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        frame.setVisible(false);
                        frame.dispose();
                        frame = null;
                    }
                });
                waitFrameHidden();
            }
        }

        void prepareImageProvider(final boolean useVolatile) {
            if (this.imageProvider == null) {
                this.imageProvider = new ImageProvider(useVolatile);
            }
        }
    }

    static abstract class PerfMeterExecutor {

        protected final static int SCORE_MAIN = 0;
        protected final static int SCORE_ERROR = 1;
        protected final static int SCORE_OTHER = 2;

        protected final static int PCT_00 = 0;
        protected final static int PCT_10 = 1;
        protected final static int PCT_25 = 2;
        protected final static int PCT_50 = 3;
        protected final static int PCT_75 = 4;
        protected final static int PCT_90 = 5;
        protected final static int PCT_100 = 6;

        private final static IntBinaryOperator INC_MOD_FUNC = new IntBinaryOperator() {
            public int applyAsInt(int x, int y) {
                return (x + 1) % y;
            }
        };

        private static final AtomicInteger headerMark = new AtomicInteger(1);

        /* members */
        protected final FrameHandler fh;
        protected final boolean skipWait;

        protected final AtomicInteger paintIdx = new AtomicInteger(0);
        protected final AtomicInteger markerIdx = new AtomicInteger(0);
        protected final AtomicLong markerStartTime = new AtomicLong(0);
        protected final AtomicLong markerPaintTime = new AtomicLong(0);

        protected String name = null;
        protected int skippedFrames = 0;

        protected int frames = 0;
        // test timestamp data:
        protected long[] testTimestamp = new long[MAX_SAMPLE_COUNT];
        // test duration data (ns):
        protected long[] testTime = new long[MAX_SAMPLE_COUNT];

        protected final double[] scores = new double[SCORE_OTHER + 1];
        protected final double[] results = new double[PCT_100 + 1];

        protected PerfMeterExecutor(final boolean skipWait, final FrameHandler fh) {
            this.skipWait = skipWait;
            this.fh = fh;
        }

        protected void beforeExec() {
        }

        protected void afterExec() {
        }

        protected void reset() {
            paintIdx.set(0);
            markerIdx.set(0);
            markerStartTime.set(0);
            markerPaintTime.set(0);
        }

        protected void updateMarkerIdx() {
            markerIdx.accumulateAndGet(MARKER.length, INC_MOD_FUNC);
        }

        protected final void exec(final String testName, final Renderable renderable) throws Exception {
            if (TRACE) System.out.print("\n!");
            this.name = testName + (isMultiThreads() ? ("-" + fh.threadId) : "");

            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    fh.prepareFrameEDT(name);
                    // call beforeExec() after frame is created:
                    beforeExec();

                    final JPanel panel = new JPanel() {
                        @Override
                        protected void paintComponent(Graphics g) {
                            if (TRACE) System.out.print("P");
                            paintPanel(renderable, g);
                            if (TRACE) System.out.print("Q");
                        }
                    };
                    fh.showFrameEDT(panel);
                    if (TRACE) System.out.print(">>");
                }
            });

            // Wait frame to be shown:
            fh.waitFrameShown();

            if (TRACE) System.out.print(":");

            // Reset before warmup:
            reset();

            if (WARMUP_COUNT > 0) {
                // Warmup to prepare frame synchronization:
                for (int i = 0; i < WARMUP_COUNT; i++) {
                    updateMarkerIdx();
                    renderable.update();
                    fh.repaintFrame();
                    sleep(10);
                    while (markerStartTime.get() == 0) {
                        if (TRACE) System.out.print("-");
                        sleep(1);
                    }
                    markerStartTime.set(0);
                }
                // Reset before measurements:
                reset();
            }
            if (TRACE) System.out.print(":>>");

            // signal thread is ready for test
            readyCount.countDown();
            if (TRACE_SYNC) traceSync(name + " ready => waiting start signal...");

            // wait start signal:
            triggerStart.await();
            // Run Benchmark (all threads):
            if (TRACE_SYNC) traceSync(name + " benchmark started");

            int cycles = 0;
            frames = 0;
            long paintStartTime = 0L;
            long paintElapsedTime = 0L;
            long lastFrameTime = 0L;

            final long startTime = System.nanoTime();
            final long minTime = startTime + MIN_MEASURE_TIME_NS;
            final long endTime = startTime + MAX_MEASURE_TIME_NS;

            // Start 1st measurement:
            fh.repaintFrame();

            for (; ; ) {
                long t;
                if ((t = markerStartTime.getAndSet(0L)) > 0L) {
                    paintStartTime = t;
                    if (TRACE) System.out.print("|");
                }

                boolean wait = true;

                if (paintStartTime > 0L) {
                    // get optional elapsed time:
                    paintElapsedTime = markerPaintTime.get();

                    if (TRACE) System.out.print(".");
                    wait = !skipWait;
                    final Color c = getMarkerColor();

                    if (isAlmostEqual(c, MARKER[markerIdx.get()])) {
                        final long durationNs = getElapsedTime((paintElapsedTime != 0L) ? paintElapsedTime : paintStartTime);
                        if ((durationNs > 0L) && (frames < MAX_SAMPLE_COUNT)) {
                            testTimestamp[frames] = paintStartTime - startTime;
                            testTime[frames] = durationNs;
                        }
                        if (REPORT_OVERALL_FPS) {
                            lastFrameTime = System.nanoTime();
                        }
                        if (TRACE) System.out.print("R");
                        frames++;
                        paintStartTime = 0L;
                        paintElapsedTime = 0L;
                        cycles = 0;
                        updateMarkerIdx();
                        renderable.update();
                        fh.repaintFrame();
                    } else if (cycles >= MAX_FRAME_CYCLES) {
                        if (TRACE) System.out.print("M");
                        skippedFrames++;
                        paintStartTime = 0L;
                        paintElapsedTime = 0L;
                        cycles = 0;
                        updateMarkerIdx();
                        fh.repaintFrame();
                    } else {
                        if (TRACE) System.out.print("-");
                    }
                }
                final long currentTime = System.nanoTime();
                if ((frames >= MIN_COUNT) && (currentTime >= endTime)) {
                    break;
                }
                if ((frames >= COUNT) && (currentTime >= minTime)) {
                    break;
                }
                if (wait) {
                    sleep(CYCLE_DELAY);
                }
                cycles++;
            } // end measurements

            // signal test completed:
            completedCount.countDown();
            if (TRACE_SYNC) traceSync(name + " completed => waiting stop signal...");

            // wait stop signal:
            triggerStop.await();
            // Stop Benchmark (all threads):
            if (TRACE_SYNC) traceSync(name + " stopped");

            if (DELAY_TEST) {
                sleep(1000);
            }
            fh.resetFrame();

            // Process results:
            if (REPORT_OVERALL_FPS && (lastFrameTime != 0)) {
                final double elapsedTime = (lastFrameTime - startTime);
                final double elapsedFPS = 1000000000.0 * frames / elapsedTime;

                System.err.println(frames + " in " + (elapsedTime / 1000000) + " ms: ~ " + elapsedFPS + " FPS");
            }

            processTimes();

            if (TRACE) System.out.print("<<\n");
            afterExec();

            // Log header once:
            if (headerMark.getAndDecrement() == 1) {
                System.err.println(getHeader());
            }

            // Log report:
            System.err.println(getResults());

            // signal test done:
            doneCount.countDown();
            if (TRACE_SYNC) traceSync(name + " done => waiting exit signal...");

            // wait exit signal:
            triggerExit.await();
            // Stop Benchmark (all threads):
            if (TRACE_SYNC) traceSync(name + " exited");
        }

        protected abstract void paintPanel(final Renderable renderable, final Graphics g);

        protected abstract long getElapsedTime(long paintTime);

        protected abstract Color getMarkerColor() throws Exception;

        protected boolean isAlmostEqual(Color c1, Color c2) {
            return (Math.abs(c1.getRed() - c2.getRed()) < COLOR_TOLERANCE) &&
                    (Math.abs(c1.getGreen() - c2.getGreen()) < COLOR_TOLERANCE) &&
                    (Math.abs(c1.getBlue() - c2.getBlue()) < COLOR_TOLERANCE);
        }

        protected void processTimes() {
            if (frames != 0) {
                frames = Math.min(frames, MAX_SAMPLE_COUNT);

                if (DUMP_SAMPLES) {
                    // Dump all results:
                    final File file = new File("./rp-" + replaceNonFileNameChars(name) + "-samples.csv");
                    System.out.println("Writing samples to : " + file.getAbsolutePath());

                    try (final PrintWriter w = new PrintWriter(file, Charset.forName("UTF-8"))) {
                        w.write("# ");
                        w.write(VERSION);
                        w.write(" - ");
                        w.write("Test: ");
                        w.write(name);
                        w.write('\n');

                        for (int i = 0; i < frames; i++) {
                            w.write(Double.toString(millis(testTimestamp[i])));
                            w.write(',');
                            w.write(Double.toString(millis(testTime[i])));
                            w.write('\n');
                        }
                    } catch (IOException ioe) {
                        System.err.println("IO exception:");
                        ioe.printStackTrace();
                    }
                }
                // Ignore first 10% (warmup at the beginning):
                final int first = (int) Math.floor(frames * 0.10);
                final int last = frames - 1;

                // free testTimestamp to avoid any future usage:
                testTimestamp = null;

                // note: testTime array is modified below:
                // Sort values to get percentiles:
                Arrays.sort(testTime, first, frames);

                final long[] pcts = getPercentiles(testTime, first, last);

                final long median = pcts[PCT_50];

                if (USE_FPS) {
                    scores[SCORE_MAIN] = fps(median);

                    results[PCT_100] = fps(pcts[PCT_00]);
                    results[PCT_90] = fps(pcts[PCT_10]);
                    results[PCT_75] = fps(pcts[PCT_25]);
                    results[PCT_50] = fps(pcts[PCT_50]);
                    results[PCT_25] = fps(pcts[PCT_75]);
                    results[PCT_10] = fps(pcts[PCT_90]);
                    results[PCT_00] = fps(pcts[PCT_100]);

                    // STDDEV = IQR / 1.35 = (Q3 - Q1) * 20 / 27
                    scores[SCORE_ERROR] = (results[PCT_75] - results[PCT_25]) * 20L / 27L;
                    scores[SCORE_OTHER] = millis(median);
                } else {
                    scores[SCORE_MAIN] = millis(median);

                    results[PCT_00] = millis(pcts[PCT_00]);
                    results[PCT_10] = millis(pcts[PCT_10]);
                    results[PCT_25] = millis(pcts[PCT_25]);
                    results[PCT_50] = millis(pcts[PCT_50]);
                    results[PCT_75] = millis(pcts[PCT_75]);
                    results[PCT_90] = millis(pcts[PCT_90]);
                    results[PCT_100] = millis(pcts[PCT_100]);

                    // STDDEV = IQR / 1.35 = (Q3 - Q1) * 20 / 27 (normal distribution ?)
                    scores[SCORE_ERROR] = (results[PCT_75] - results[PCT_25]) * 20L / 27L;
                    scores[SCORE_OTHER] = fps(median);

                    // System.out.println("stddev(IQR) = " + scores[SCORE_ERROR]);

                    // MAD = Median Absolute Deviation:
                    for (int i = first; i <= last; i++) {
                        testTime[i] = Math.abs(testTime[i] - median);
                    }
                    // Sort values to get percentiles:
                    Arrays.sort(testTime, first, frames);

                    // STDDEV = 1.4826 * MAD (normal distribution ?)
                    scores[SCORE_ERROR] = 1.4826 * millis(testTime[pctIndex(first, last, 0.50)]); // 50% (median)

                    // System.out.println("stddev(MAD) = " + scores[SCORE_ERROR]);
                }
                // free testTime to avoid any future usage:
                testTime = null;
            }
        }

        protected static String getHeader() {
            if (VERBOSE) {
                return String.format("%-25s : %s ± %s %s [%s] (p00: ... p10: ... p25: ... p50: ... p75: ... p90: ... p100: ... %s) (... frames)",
                        "Test Name", (USE_FPS ? "Median(FPS)" : "Median(TimeMs)"), (USE_FPS ? "Stddev(FPS)" : "Stddev(TimeMs)"), "Unit",
                        (!USE_FPS ? "Median(FPS)" : "Median(TimeMs)"), "Unit");
            }
            return String.format("%-25s : %s ± %s %s",
                    "Test Name", (USE_FPS ? "Median(FPS)" : "Median(TimeMs)"), (USE_FPS ? "Stddev(FPS)" : "Stddev(TimeMs)"), "Unit");

        }

        protected String getResults() {
            if (skippedFrames > 0) {
                System.err.println(name + " : " + skippedFrames + " frame(s) skipped");
            }
            if (VERBOSE) {
                return String.format("%-25s : %.3f ± %.3f %s [%.3f %s] (p00: %.3f p10: %.3f p25: %.3f p50: %.3f p75: %.3f p90: %.3f p100: %.3f %s) (%d frames)",
                    name, scores[SCORE_MAIN], scores[SCORE_ERROR], (USE_FPS ? "FPS" : "ms"),
                    scores[SCORE_OTHER], (USE_FPS ? "ms" : "FPS"),
                    results[PCT_00], results[PCT_10], results[PCT_25], results[PCT_50], results[PCT_75], results[PCT_90], results[PCT_100],
                    (USE_FPS ? "FPS" : "ms"),
                    frames);
            }
            return String.format("%-25s : %.3f ± %.3f %s",
                     name, scores[SCORE_MAIN], scores[SCORE_ERROR], (USE_FPS ? "FPS" : "ms"));
        }

        protected double fps(long timeNs) {
            return 1e9 / timeNs;
        }

        protected double millis(long timeNs) {
            return 1e-6 * timeNs;
        }

        protected static long[] getPercentiles(final long[] data, final int first, final int last) {
            final long[] pcts = new long[PCT_100 + 1];
            pcts[PCT_00] = data[first]; // 0% (min)
            pcts[PCT_10] = data[pctIndex(first, last, 0.10)]; //  10%
            pcts[PCT_25] = data[pctIndex(first, last, 0.25)]; //  25% (Q1)
            pcts[PCT_50] = data[pctIndex(first, last, 0.50)]; //  50% (Median)
            pcts[PCT_75] = data[pctIndex(first, last, 0.75)]; //  75% (Q3)
            pcts[PCT_90] = data[pctIndex(first, last, 0.90)]; //  90%
            pcts[PCT_100] = data[pctIndex(first, last, 1.00)]; // 100% (max)
            return pcts;
        }

        protected static int pctIndex(final int min, final int last, final double pct) {
            return min + (int) Math.round((last - min) * pct);
        }
    }

    final static class PerfMeterRobot extends PerfMeterExecutor {

        private int nRobotTimes = 0;
        private long[] robotTime = (fh.calibrate) ? new long[COUNT] : null;

        private int nDelayTimes = 0;
        private long[] delayTime = (ROBOT_TIME_DELAY) ? null : new long[COUNT];

        private long lastPaintTime = 0L;
        private int renderedMarkerIdx = -1;

        private Robot robot = null;

        PerfMeterRobot(final FrameHandler fh) {
            super(true, fh);
        }

        protected void beforeExec() {
            try {
                robot = new Robot();
            } catch (AWTException ae) {
                throw new RuntimeException(ae);
            }
        }

        protected void reset() {
            super.reset();
            nRobotTimes = 0;
            nDelayTimes = 0;
            lastPaintTime = 0L;
            renderedMarkerIdx = -1;
        }

        protected void paintPanel(final Renderable renderable, final Graphics g) {
            final int idx = markerIdx.get();
            final long start = System.nanoTime();

            final Graphics2D g2d = (Graphics2D) g.create();
            try {
                paintTest(renderable, g2d, MARKER[idx], false);
            } finally {
                g2d.dispose();
            }

            // Update paintIdx:
            paintIdx.incrementAndGet();

            // publish start time:
            if (idx != renderedMarkerIdx) {
                renderedMarkerIdx = idx;
                markerStartTime.set(start);
            }
        }

        protected long getElapsedTime(final long paintTime) {
            final long now = System.nanoTime();
            long duration = (!ROBOT_TIME_DELAY) ? roundDuration(now - paintTime) : 0L;
            if (lastPaintTime != 0L) {
                final long delay = roundDuration(now - lastPaintTime);
                if (ROBOT_TIME_DELAY) {
                    duration = delay;
                } else if (nDelayTimes < COUNT) {
                    delayTime[nDelayTimes++] = delay;
                }
            }
            lastPaintTime = now;
            return duration;
        }

        private static long roundDuration(final long durationNs) {
            return (durationNs <= 0L) ? 0L : (
                    (ROBOT_TIME_ROUND) ?
                            FRAME_PREC_IN_NANOS * (long) Math.rint(((double) durationNs) / FRAME_PREC_IN_NANOS) : durationNs
            );
        }

        protected Color getMarkerColor() {
            final Point frameOffset = fh.frame.getLocationOnScreen();
            final Insets insets = fh.frame.getInsets();
            final int px = frameOffset.x + insets.left + BW / 2;
            final int py = frameOffset.y + insets.top + BH / 2;

            final long beforeRobot = (fh.calibrate) ? System.nanoTime() : 0L;

            final Color c = robot.getPixelColor(px, py);

            if ((fh.calibrate) && (nRobotTimes < COUNT)) {
                robotTime[nRobotTimes++] = System.nanoTime() - beforeRobot;
            }
            return c;
        }

        protected String getResults() {
            if (fh.calibrate && (nRobotTimes != 0)) {
                fh.calibrate = false; // only first time

                Arrays.sort(robotTime);

                final long[] pcts = getPercentiles(robotTime, 0, nRobotTimes - 1);

                // free testTime to avoid any future usage:
                testTime = null;

                System.err.printf("%-25s : %.3f ms (p00: %.3f p10: %.3f p25: %.3f p50: %.3f p75: %.3f p90: %.3f p100: %.3f ms) (%d times)%n",
                        "Robot" + (isMultiThreads() ? ("-" + fh.threadId) : ""), millis(pcts[PCT_50]),
                        millis(pcts[PCT_00]), millis(pcts[PCT_10]), millis(pcts[PCT_25]), millis(pcts[PCT_50]),
                        millis(pcts[PCT_75]), millis(pcts[PCT_90]), millis(pcts[PCT_100]), nRobotTimes);
            }
            if (nDelayTimes != 0) {
                Arrays.sort(delayTime);

                final long[] pcts = getPercentiles(robotTime, 0, nDelayTimes - 1);

                // free delayTime to avoid any future usage:
                delayTime = null;

                System.err.printf("%-25s : %.3f ms [%.3f FPS] (p00: %.3f p10: %.3f p25: %.3f p50: %.3f p75: %.3f p90: %.3f p100: %.3f ms) (%d times)%n",
                        "DelayTime-" + name + (isMultiThreads() ? ("-" + fh.threadId) : ""), millis(pcts[PCT_50]), fps(pcts[PCT_50]),
                        millis(pcts[PCT_00]), millis(pcts[PCT_10]), millis(pcts[PCT_25]), millis(pcts[PCT_50]),
                        millis(pcts[PCT_75]), millis(pcts[PCT_90]), millis(pcts[PCT_100]), nDelayTimes);
            }
            return super.getResults();
        }
    }

    final static class PerfMeterImageProvider extends PerfMeterExecutor {
        private final ImageProvider imageProvider;

        PerfMeterImageProvider(final FrameHandler fh) {
            super(false, fh);
            this.imageProvider = fh.imageProvider;
        }

        protected void beforeExec() {
            imageProvider.create(fh.frame.getGraphicsConfiguration(), IMAGE_W, IMAGE_H);
        }

        protected void afterExec() {
            imageProvider.reset();
        }

        protected void paintPanel(final Renderable renderable, final Graphics g) {
            // suppose image provider is ready yet
            final int idx = markerIdx.get();
            long start = System.nanoTime();

            // Get Graphics from image provider:
            final Graphics2D g2d = imageProvider.createGraphics();
            try {
                paintTest(renderable, g2d, MARKER[idx], true);
            } finally {
                g2d.dispose();
            }

            final long now = System.nanoTime();

            // Update paintIdx:
            paintIdx.incrementAndGet();

            // publish start time:
            markerStartTime.set(start);
            // publish elapsed time:
            markerPaintTime.set(now - start);

            // Draw image on screen:
            g.drawImage(imageProvider.getImage(), 0, 0, null);
        }

        protected long getElapsedTime(long paintTime) {
            return paintTime;
        }

        protected Color getMarkerColor() {
            final int px = BW / 2;
            final int py = BH / 2;

            return new Color(imageProvider.getSnapshot().getRGB(px, py));
        }
    }

    private final static class ImageProvider {
        private final static int TRANSPARENCY = Transparency.TRANSLUCENT;

        private final boolean useVolatile;
        private Image image = null;

        private ImageProvider(boolean useVolatile) {
            this.useVolatile = useVolatile;
        }

        void create(GraphicsConfiguration gc, int width, int height) {
            this.image = (useVolatile) ? gc.createCompatibleVolatileImage(width, height, TRANSPARENCY)
                    : gc.createCompatibleImage(width, height, TRANSPARENCY);
        }

        public void reset() {
            image = null;
        }

        public Image getImage() {
            return image;
        }

        public Graphics2D createGraphics() {
            return (useVolatile) ? ((VolatileImage) image).createGraphics()
                    : ((BufferedImage) image).createGraphics();
        }

        public BufferedImage getSnapshot() {
            return (useVolatile) ? ((VolatileImage) image).getSnapshot()
                    : (BufferedImage) image;
        }
    }

    private final FrameHandler fh;

    private final Particles balls = new Particles(N, R, BW, BH, WIDTH, HEIGHT);

    private final ParticleRenderer calibRenderer = new CalibrationParticleRenderer();
    private final ParticleRenderer flatRenderer = new FlatParticleRenderer(N, R);
    private final ParticleRenderer clipFlatRenderer = new ClipFlatParticleRenderer(N, R);
    private final ParticleRenderer flatOvalRotRenderer = new FlatOvalRotParticleRenderer(N, R);
    private final ParticleRenderer flatBoxRenderer = new FlatBoxParticleRenderer(N, R);
    private final ParticleRenderer clipFlatBoxParticleRenderer = new ClipFlatBoxParticleRenderer(N, R);
    private final ParticleRenderer flatBoxRotRenderer = new FlatBoxRotParticleRenderer(N, R);
    private final ParticleRenderer linGradOvalRotRenderer = new LinGradOvalRotParticleRenderer(N, R);
    private final ParticleRenderer linGrad3OvalRotRenderer = new LinGrad3OvalRotParticleRenderer(N, R);
    private final ParticleRenderer radGrad3OvalRotRenderer = new RadGrad3OvalRotParticleRenderer(N, R);
    private final ParticleRenderer wiredRenderer = new WiredParticleRenderer(N, R);
    private final ParticleRenderer wiredBoxRenderer = new WiredBoxParticleRenderer(N, R);
    private final ParticleRenderer segRenderer = new SegParticleRenderer(N, R);
    private final ParticleRenderer flatQuadRenderer = new FlatQuadParticleRenderer(N, R);
    private final ParticleRenderer wiredQuadRenderer = new WiredQuadParticleRenderer(N, R);
    private final ParticleRenderer imgRenderer = new ImgParticleRenderer(N, R);
    private final ParticleRenderer volImgRenderer = new VolImgParticleRenderer(N, R);
    private final ParticleRenderer textRenderer = new TextParticleRenderer(N, R);
    private final ParticleRenderer largeTextRenderer = new LargeTextParticleRenderer(N, R);
    private final ParticleRenderer whiteTextRenderer = new WhiteTextParticleRenderer(R);
    private final ParticleRenderer argbSwBlitImageRenderer = new SwBlitImageParticleRenderer(N, R, BufferedImage.TYPE_INT_ARGB);
    private final ParticleRenderer bgrSwBlitImageRenderer = new SwBlitImageParticleRenderer(N, R, BufferedImage.TYPE_INT_BGR);
    private final ParticleRenderer argbSurfaceBlitImageRenderer = new SurfaceBlitImageParticleRenderer(N, R, BufferedImage.TYPE_INT_ARGB);
    private final ParticleRenderer bgrSurfaceBlitImageRenderer = new SurfaceBlitImageParticleRenderer(N, R, BufferedImage.TYPE_INT_BGR);

    private final ParticleRenderer textWiredQuadBatchedRenderer = new BatchedParticleRenderer(textRenderer, wiredQuadRenderer);
    private final ParticleRenderer textWiredQuadMixedRenderer = new MixedParticleRenderer(textRenderer, wiredQuadRenderer);

    private final ParticleRenderer volImgFlatBoxBatchedRenderer = new BatchedParticleRenderer(volImgRenderer, flatBoxRenderer);
    private final ParticleRenderer volImgFlatBoxMixedRenderer = new MixedParticleRenderer(volImgRenderer, flatBoxRenderer);

    private final ParticleRenderer volImgWiredQuadBatchedRenderer = new BatchedParticleRenderer(volImgRenderer, wiredQuadRenderer);
    private final ParticleRenderer volImgWiredQuadMixedRenderer = new MixedParticleRenderer(volImgRenderer, wiredQuadRenderer);

    private final ParticleRenderer volImgTextBatchedRenderer = new BatchedParticleRenderer(volImgRenderer, textRenderer);
    private final ParticleRenderer volImgTextMixedRenderer = new MixedParticleRenderer(volImgRenderer, textRenderer);

    private final static Configurable AA = new ConfigurableAA();
    private final static Configurable TextAA = new ConfigurableTextAA();
    private final static Configurable TextLCD = new ConfigurableTextLCD();
    private final static Configurable XORMode = new ConfigurableXORMode();
    private final static Configurable XORModeLCDText = new ConfigurableXORModeTextLCD();

    RenderPerfTest(final GraphicsConfiguration gc) {
        fh = new FrameHandler(gc);
    }

    ParticleRenderable createPR(final ParticleRenderer renderer) {
        return new ParticleRenderable(balls, renderer);
    }

    PerfMeter createPerfMeter(final String name) {
        return new PerfMeter(fh, name);
    }

    public void testCalibration() throws Exception {
        createPerfMeter(testName).exec(createPR(calibRenderer));
    }

    public void testFlatOval() throws Exception {
        createPerfMeter(testName).exec(createPR(flatRenderer));
    }

    public void testFlatOvalAA() throws Exception {
        createPerfMeter(testName).exec(createPR(flatRenderer).configure(AA));
    }

    public void testClipFlatOval() throws Exception {
        createPerfMeter(testName).exec(createPR(clipFlatRenderer));
    }

    public void testClipFlatOvalAA() throws Exception {
        createPerfMeter(testName).exec(createPR(clipFlatRenderer).configure(AA));
    }

    public void testFlatBox() throws Exception {
        createPerfMeter(testName).exec(createPR(flatBoxRenderer));
    }

    public void testFlatBoxAA() throws Exception {
        createPerfMeter(testName).exec(createPR(flatBoxRenderer).configure(AA));
    }

    public void testClipFlatBox() throws Exception {
        createPerfMeter(testName).exec(createPR(clipFlatBoxParticleRenderer));
    }

    public void testClipFlatBoxAA() throws Exception {
        createPerfMeter(testName).exec(createPR(clipFlatBoxParticleRenderer).configure(AA));
    }

    public void testImage() throws Exception {
        createPerfMeter(testName).exec(createPR(imgRenderer));
    }

    public void testImageAA() throws Exception {
        createPerfMeter(testName).exec(createPR(imgRenderer).configure(AA));
    }

    public void testVolImage() throws Exception {
        createPerfMeter(testName).exec(createPR(volImgRenderer));
    }

    public void testVolImageAA() throws Exception {
        createPerfMeter(testName).exec(createPR(volImgRenderer).configure(AA));
    }

    public void testRotatedBox() throws Exception {
        createPerfMeter(testName).exec(createPR(flatBoxRotRenderer));
    }

    public void testRotatedBoxAA() throws Exception {
        createPerfMeter(testName).exec(createPR(flatBoxRotRenderer).configure(AA));
    }

    public void testRotatedOval() throws Exception {
        createPerfMeter(testName).exec(createPR(flatOvalRotRenderer));
    }

    public void testRotatedOvalAA() throws Exception {
        createPerfMeter(testName).exec(createPR(flatOvalRotRenderer).configure(AA));
    }

    public void testLinGrad3RotatedOval() throws Exception {
        createPerfMeter(testName).exec(createPR(linGrad3OvalRotRenderer));
    }

    public void testLinGrad3RotatedOvalAA() throws Exception {
        createPerfMeter(testName).exec(createPR(linGrad3OvalRotRenderer).configure(AA));
    }

    public void testRadGrad3RotatedOval() throws Exception {
        createPerfMeter(testName).exec(createPR(radGrad3OvalRotRenderer));
    }

    public void testRadGrad3RotatedOvalAA() throws Exception {
        createPerfMeter(testName).exec(createPR(radGrad3OvalRotRenderer).configure(AA));
    }

    public void testLinGradRotatedOval() throws Exception {
        createPerfMeter(testName).exec(createPR(linGradOvalRotRenderer));
    }

    public void testLinGradRotatedOvalAA() throws Exception {
        createPerfMeter(testName).exec(createPR(linGradOvalRotRenderer).configure(AA));
    }

    public void testWiredBubbles() throws Exception {
        createPerfMeter(testName).exec(createPR(wiredRenderer));
    }

    public void testWiredBubblesAA() throws Exception {
        createPerfMeter(testName).exec(createPR(wiredRenderer).configure(AA));
    }

    public void testWiredBox() throws Exception {
        createPerfMeter(testName).exec(createPR(wiredBoxRenderer));
    }

    public void testWiredBoxAA() throws Exception {
        createPerfMeter(testName).exec(createPR(wiredBoxRenderer).configure(AA));
    }

    public void testLines() throws Exception {
        createPerfMeter(testName).exec(createPR(segRenderer));
    }

    public void testLinesAA() throws Exception {
        createPerfMeter(testName).exec(createPR(segRenderer).configure(AA));
    }

    public void testFlatQuad() throws Exception {
        createPerfMeter(testName).exec(createPR(flatQuadRenderer));
    }

    public void testFlatQuadAA() throws Exception {
        createPerfMeter(testName).exec(createPR(flatQuadRenderer).configure(AA));
    }

    public void testWiredQuad() throws Exception {
        createPerfMeter(testName).exec(createPR(wiredQuadRenderer));
    }

    public void testWiredQuadAA() throws Exception {
        createPerfMeter(testName).exec(createPR(wiredQuadRenderer).configure(AA));
    }

    public void testTextNoAA() throws Exception {
        createPerfMeter(testName).exec(createPR(textRenderer));
    }

    public void testTextLCD() throws Exception {
        createPerfMeter(testName).exec(createPR(textRenderer).configure(TextLCD));
    }

    public void testTextGray() throws Exception {
        createPerfMeter(testName).exec(createPR(textRenderer).configure(TextAA));
    }

    public void testLargeTextNoAA() throws Exception {
        createPerfMeter(testName).exec(createPR(largeTextRenderer));
    }

    public void testLargeTextLCD() throws Exception {
        createPerfMeter(testName).exec(createPR(largeTextRenderer).configure(TextLCD));
    }

    public void testLargeTextGray() throws Exception {
        createPerfMeter(testName).exec(createPR(largeTextRenderer).configure(TextAA));
    }

    public void testWhiteTextNoAA() throws Exception {
        createPerfMeter(testName).exec(createPR(whiteTextRenderer));
    }

    public void testWhiteTextLCD() throws Exception {
        createPerfMeter(testName).exec(createPR(whiteTextRenderer).configure(TextLCD));
    }

    public void testWhiteTextGray() throws Exception {
        createPerfMeter(testName).exec(createPR(whiteTextRenderer).configure(TextAA));
    }

    public void testArgbSwBlitImage() throws Exception {
        createPerfMeter(testName).exec(createPR(argbSwBlitImageRenderer));
    }

    public void testBgrSwBlitImage() throws Exception {
        createPerfMeter(testName).exec(createPR(bgrSwBlitImageRenderer));
    }

    public void testArgbSurfaceBlitImage() throws Exception {
        createPerfMeter(testName).exec(createPR(argbSurfaceBlitImageRenderer));
    }

    public void testBgrSurfaceBlitImage() throws Exception {
        createPerfMeter(testName).exec(createPR(bgrSurfaceBlitImageRenderer));
    }

    // XOR mode:
    public void testFlatOval_XOR() throws Exception {
        createPerfMeter(testName).exec(createPR(flatRenderer).configure(XORMode));
    }

    public void testRotatedBox_XOR() throws Exception {
        createPerfMeter(testName).exec(createPR(flatBoxRotRenderer).configure(XORMode));
    }

    public void testLines_XOR() throws Exception {
        createPerfMeter(testName).exec(createPR(segRenderer).configure(XORMode));
    }

    public void testImage_XOR() throws Exception {
        createPerfMeter(testName).exec(createPR(imgRenderer).configure(XORMode));
    }

    public void testTextNoAA_XOR() throws Exception {
        createPerfMeter(testName).exec(createPR(textRenderer).configure(XORMode));
    }

    public void testTextLCD_XOR() throws Exception {
        createPerfMeter(testName).exec(createPR(textRenderer).configure(XORModeLCDText));
    }

    // Mixed/Batched mode:
    public void testTextWiredQuadBat() throws Exception {
        createPerfMeter(testName).exec(createPR(textWiredQuadBatchedRenderer));
    }

    public void testTextWiredQuadMix() throws Exception {
        createPerfMeter(testName).exec(createPR(textWiredQuadMixedRenderer));
    }

    public void testTextWiredQuadAABat() throws Exception {
        createPerfMeter(testName).exec(createPR(textWiredQuadBatchedRenderer).configure(AA));
    }

    public void testTextWiredQuadAAMix() throws Exception {
        createPerfMeter(testName).exec(createPR(textWiredQuadMixedRenderer).configure(AA));
    }

    public void testVolImageFlatBoxBat() throws Exception {
        createPerfMeter(testName).exec(createPR(volImgFlatBoxBatchedRenderer));
    }

    public void testVolImageFlatBoxMix() throws Exception {
        createPerfMeter(testName).exec(createPR(volImgFlatBoxMixedRenderer));
    }

    public void testVolImageFlatBoxAABat() throws Exception {
        createPerfMeter(testName).exec(createPR(volImgFlatBoxBatchedRenderer).configure(AA));
    }

    public void testVolImageFlatBoxAAMix() throws Exception {
        createPerfMeter(testName).exec(createPR(volImgFlatBoxMixedRenderer).configure(AA));
    }

    public void testVolImageWiredQuadBat() throws Exception {
        createPerfMeter(testName).exec(createPR(volImgWiredQuadBatchedRenderer));
    }

    public void testVolImageWiredQuadMix() throws Exception {
        createPerfMeter(testName).exec(createPR(volImgWiredQuadMixedRenderer));
    }

    public void testVolImageWiredQuadAABat() throws Exception {
        createPerfMeter(testName).exec(createPR(volImgWiredQuadBatchedRenderer).configure(AA));
    }

    public void testVolImageWiredQuadAAMix() throws Exception {
        createPerfMeter(testName).exec(createPR(volImgWiredQuadMixedRenderer).configure(AA));
    }

    public void testVolImageTextNoAABat() throws Exception {
        createPerfMeter(testName).exec(createPR(volImgTextBatchedRenderer));
    }

    public void testVolImageTextNoAAMix() throws Exception {
        createPerfMeter(testName).exec(createPR(volImgTextMixedRenderer));
    }

    private static void help() {
        System.out.print("##############################################################\n");
        System.out.printf("# %s\n", VERSION);
        System.out.print("##############################################################\n");
        System.out.println("# java ... RenderPerfTest <args> <tests>");
        System.out.println("#");
        System.out.println("# Supported arguments <args>:");
        System.out.println("# -h                        : display this help");
        System.out.println("# -v                        : set verbose output");
        System.out.println("#");
        System.out.println("# -e=<mode>                  : set the execution mode (default: " + EXEC_MODE_DEFAULT
                                + ") among " + EXEC_MODES);
        System.out.println("#");
        System.out.println("# -f                        : use FPS unit (default)");
        System.out.println("# -t                        : use TIME(ms) unit");
        System.out.println("#");
        System.out.println("# -g=all|\"0-0,0-1...\"       : use 'all' or specific graphics configurations");
        System.out.println("#");
        System.out.println("# -font=\"<name>\"            : set the font name used by all text renderers");
        System.out.println("# -fontSize=<number>        : set the font size used by Text renderers");
        System.out.println("# -fontSizeLarge=<number>   : set the font size used by LargeText renderers");
        System.out.println("# -text=\"<string>\"          : set the text drawn by all text renderers");
        System.out.println("#");
        System.out.println("# -lf                       : list available font names");
        System.out.println("# -lg                       : list available graphics configurations");
        System.out.println("#");
        System.out.println("# -n=<number>               : set number of primitives (default: " + N_DEFAULT + ")");
        System.out.println("# -r=<number>               : set number of test repeats (default: 1)");
        System.out.println("#");
        System.out.println("#");
        System.out.println("# -w=<number>               : use number of test frames (default: 1) per screen");
        System.out.println("#");
        System.out.println("# -u=<number>               : set number of warmup iterations (default: " + MIN_COUNT + ")");
        System.out.println("#");

        System.out.print("# Supported test arguments <tests>:");

        final ArrayList<Method> testCases = new ArrayList<>();
        for (Method m : RenderPerfTest.class.getDeclaredMethods()) {
            if (m.getName().startsWith("test") && !ignoredTests.contains(m.getName())) {
                testCases.add(m);
            }
        }
        testCases.sort(Comparator.comparing(Method::getName));
        for (Method m : testCases) {
            System.out.print(extractTestName(m));
            System.out.print(" ");
        }
        System.out.println();
    }

    private static String extractTestName(final Method m) {
        return m.getName().substring("test".length());
    }

    public static void main(String[] args)
            throws NoSuchMethodException, NumberFormatException {
        // Set the default locale to en-US locale (for Numerical Fields "." ",")
        Locale.setDefault(Locale.US);

        boolean help = false;
        final ArrayList<Method> testCases = new ArrayList<>();

        for (String arg : args) {
            if (arg.length() >= 2) {
                if (arg.startsWith("-")) {
                    switch (arg.substring(1, 2)) {
                        case "e":
                            if (arg.length() >= 3) {
                                EXEC_MODE = arg.substring(3).toLowerCase();
                            }
                            break;
                        case "f":
                            if (arg.length() == 2) {
                                USE_FPS = true;
                            } else {
                                if ((arg.length() > 6) && "font=".equalsIgnoreCase(arg.substring(1, 6))) {
                                    TEXT_FONT = arg.substring(6);
                                    break;
                                }
                                if ((arg.length() > 10) && "fontSize=".equalsIgnoreCase(arg.substring(1, 10))) {
                                    TEXT_SIZE_DEFAULT = Integer.parseInt(arg.substring(10));
                                    break;
                                }
                                if ((arg.length() > 15) && "fontSizeLarge=".equalsIgnoreCase(arg.substring(1, 15))) {
                                    TEXT_SIZE_LARGE = Integer.parseInt(arg.substring(15));
                                    break;
                                }
                            }
                            break;
                        case "g":
                            if (arg.length() >= 3) {
                                GC_MODE = arg.substring(3).toLowerCase();
                            }
                            break;
                        case "h":
                            help = true;
                            break;
                        case "l":
                            if (arg.length() == 3) {
                                if ("f".equalsIgnoreCase(arg.substring(2, 3))) {
                                    VERBOSE_FONT_CONFIG = true;
                                } else if ("g".equalsIgnoreCase(arg.substring(2, 3))) {
                                    VERBOSE_GRAPHICS_CONFIG = true;
                                }
                            }
                            break;
                        case "t":
                            if (arg.length() == 2) {
                                USE_FPS = false;
                            } else {
                                if ((arg.length() > 6) && "text=".equalsIgnoreCase(arg.substring(1, 6))) {
                                    TEXT_STR = arg.substring(6);
                                }
                            }
                            break;
                        case "n":
                            if (arg.length() >= 3) {
                                N = Integer.parseInt(arg.substring(3));
                            }
                            break;
                        case "r":
                            if (arg.length() >= 3) {
                                REPEATS = Integer.parseInt(arg.substring(3));
                            }
                            break;
                        case "v":
                            VERBOSE = true;
                            break;
                        case "w":
                            if (arg.length() >= 3) {
                                NW = Integer.parseInt(arg.substring(3));
                            }
                            break;
                        case "u":
                            if (arg.length() >= 3) {
                                WARMUP_COUNT = Integer.parseInt(arg.substring(3));
                            }
                            break;
                        default:
                            System.err.println("Unsupported argument '" + arg + "' !");
                            help = true;
                    }
                } else {
                    Method m = RenderPerfTest.class.getDeclaredMethod("test" + arg);
                    testCases.add(m);
                }
            }
        }
        if (testCases.isEmpty()) {
            for (Method m : RenderPerfTest.class.getDeclaredMethods()) {
                if (m.getName().startsWith("test") && !ignoredTests.contains(m.getName())) {
                    testCases.add(m);
                }
            }
            testCases.sort(Comparator.comparing(Method::getName));
        }

        if (help) {
            help();
        }

        if (CALIBRATION) {
            Method m = RenderPerfTest.class.getDeclaredMethod("testCalibration");
            testCases.add(0, m); // first
        }

        if (VERBOSE) {
            System.out.print("##############################################################\n");
            System.out.printf("# %s\n", VERSION);
            System.out.print("##############################################################\n");
            System.out.printf("# Java: %s\n", System.getProperty("java.runtime.version"));
            System.out.printf("#   VM: %s %s (%s)\n", System.getProperty("java.vm.name"), System.getProperty("java.vm.version"), System.getProperty("java.vm.info"));
            System.out.printf("#   OS: %s %s (%s)\n", System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"));
            System.out.printf("# CPUs: %d (virtual)\n", Runtime.getRuntime().availableProcessors());
            System.out.print("##############################################################\n");
            System.out.printf("# AWT Toolkit:                %s \n", TOOLKIT.getClass().getSimpleName());
            System.out.printf("# Execution mode:             %s\n", EXEC_MODE);
            System.out.printf("# GraphicsConfiguration mode: %s\n", GC_MODE);
            System.out.print("##############################################################\n");
            System.out.printf("# Repeats:                    %d\n", REPEATS);
            System.out.printf("# NW:                         %d\n", NW);
            System.out.printf("# N:                          %d\n", N);
            System.out.printf("# WARMUP_COUNT:               %d\n", WARMUP_COUNT);
            System.out.printf("# Unit:                       %s\n", USE_FPS ? "FPS" : "TIME(ms)");
            System.out.print("##############################################################\n");
            System.out.printf("# Font:                      '%s'\n", TEXT_FONT);
            System.out.printf("# Text: '%s'\n", TEXT_STR);
            System.out.printf("# FontSize:                   %s\n", TEXT_SIZE_DEFAULT);
            System.out.printf("# FontSizeLarge:              %s\n", TEXT_SIZE_LARGE);
            System.out.print("##############################################################\n");
        }

        // Graphics Configuration handling:
        final Set<String> fontNames = new LinkedHashSet<>();
        final Map<String, GraphicsConfiguration> gcByID = new LinkedHashMap<>();
        final Map<GraphicsConfiguration, String> idByGC = new HashMap<>();

        final GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();

        for (String name : ge.getAvailableFontFamilyNames()) {
            fontNames.add(name);
        }
        // Check font:
        if (!fontNames.contains(TEXT_FONT)) {
            System.err.println("Bad font name: [" + TEXT_FONT + "] !");
            VERBOSE_FONT_CONFIG = true;
        }

        if (VERBOSE_FONT_CONFIG) {
            System.out.print("# Available font names: ");

            for (String name : fontNames) {
                System.out.print("'");
                System.out.print(name);
                System.out.print("' ");
            }
            System.out.println();
        }

        final GraphicsDevice[] gds = ge.getScreenDevices();

        if (VERBOSE_GRAPHICS_CONFIG) {
            System.out.println("# Available GraphicsDevice(s) and their GraphicsConfiguration(s):");
        }

        for (int gdIdx = 0; gdIdx < gds.length; gdIdx++) {
            final GraphicsDevice gd = gds[gdIdx];
            if (VERBOSE_GRAPHICS_CONFIG) {
                System.out.println("# [" + gdIdx + "] = GraphicsDevice[" + gd.getIDstring() + "]");
            }

            final GraphicsConfiguration[] gcs = gd.getConfigurations();

            for (int gcIdx = 0; gcIdx < gcs.length; gcIdx++) {
                final GraphicsConfiguration gc = gcs[gcIdx];
                final String gcId = gdIdx + "-" + gcIdx;
                gcByID.put(gcId, gc);
                idByGC.put(gc, gcId);
                if (VERBOSE_GRAPHICS_CONFIG) {
                    System.out.println("# - [" + gcId + "] = GraphicsConfiguration[" + gc + "] bounds:" + gc.getBounds());
                }
            }
        }

        final Set<GraphicsConfiguration> gcSet = new LinkedHashSet<>();

        if (GC_MODE != null) {
            if (!GC_MODE_DEF.equals(GC_MODE)) {
                if (GC_MODE_ALL.equals(GC_MODE)) {
                    gcSet.addAll(gcByID.values());
                } else {
                    for (String gcKey : GC_MODE.split(",")) {
                        final GraphicsConfiguration gc = gcByID.get(gcKey);
                        if (gc != null) {
                            gcSet.add(gc);
                        } else {
                            System.err.println("Bad GraphicsConfiguration identifier 'x-y' where x is GraphicsDevice ID " +
                                    "and y GraphicsConfiguration ID : [" + gcKey + "] ! (available values: " + gcByID.keySet() + ")");
                        }
                    }
                }
            }
        }
        if (gcSet.isEmpty()) {
            final GraphicsDevice gdDef = ge.getDefaultScreenDevice();
            final GraphicsConfiguration gcDef = gdDef.getDefaultConfiguration();
            final String gcId = idByGC.get(gcDef);

            if (VERBOSE_GRAPHICS_CONFIG) {
                System.out.println("# Using default [" + gcId + "] = GraphicsConfiguration[" + gcDef + "] bounds:" + gcDef.getBounds());
            }
            gcSet.add(gcDef);
        }

        final List<GraphicsConfiguration> gcList = new ArrayList<>(gcSet);
        final int NGC = gcList.size();

        System.out.print("#  Using GraphicsConfiguration(s): ");
        for (GraphicsConfiguration gc : gcList) {
            final String gcId = idByGC.get(gc);
            System.out.print("[" + gcId + "][" + gc + "]");
            System.out.print(" ");
        }
        System.out.println();

        final List<RenderPerfTest> instances = new ArrayList<>();
        int retCode = 0;
        try {
            if (!help) {
                final List<Thread> threads = new ArrayList<>();

                for (int i = 0; i < NGC; i++) {
                    final GraphicsConfiguration gc = gcList.get(i);

                    for (int j = 0; j < NW; j++) {
                        final RenderPerfTest rp = new RenderPerfTest(gc);
                        instances.add(rp);
                        threads.add(rp.createThreadTests(threads.size() + 1, j + 1, testCases));
                    }
                }
                if (TRACE_SYNC) traceSync("testCount: " + testCount);

                initThreads(threads.size());
                initBarrierStart();

                for (Thread thread : threads) {
                    if (TRACE_SYNC) traceSync(thread.getName() + " starting...");
                    thread.start();
                }

                for (int n = 0; n < testCount; n++) {
                    if (VERBOSE) {
                        final int k = n / REPEATS;
                        final String methodName = extractTestName(testCases.get(k));
                        System.out.println("# --- Test [" + (n + 1) + " / " + testCount + "] = " + methodName + " ---");
                    }

                    // reset stop barrier (to be ready):
                    initBarrierStop();

                    if (TRACE_SYNC) traceSync("Waiting " + threadCount + " threads to be ready...");
                    readyCount.await();

                    if (TRACE_SYNC) traceSync("Threads are ready => starting benchmark on " + threadCount + " threads now");
                    triggerStart.countDown();

                    // reset done barrier (to be ready):
                    initBarrierDone();

                    if (TRACE_SYNC) traceSync("Waiting " + threadCount + " threads to complete benchmark...");
                    completedCount.await();

                    if (TRACE_SYNC) traceSync("Test completed on " + threadCount + " threads => stopping benchmark on all threads now");
                    triggerStop.countDown();

                    // reset start barrier (to be ready):
                    initBarrierStart();

                    if (TRACE_SYNC) traceSync("Waiting " + threadCount + " threads to exit test...");
                    doneCount.await();

                    if (TRACE_SYNC) traceSync("Test exited on " + threadCount + " threads => finalize benchmark on all threads now");
                    triggerExit.countDown();
                }

                for (Thread thread : threads) {
                    thread.join();
                    if (TRACE_SYNC) traceSync(thread.getName() + " terminated");
                }
            }
        } catch (Throwable th) {
            System.err.println("Exception occurred during :");
            th.printStackTrace(System.err);
            retCode = 1;
        } finally {
            for (RenderPerfTest rp : instances) {
                try {
                    rp.fh.hideFrameAndWait();
                } catch (Throwable th) {
                    System.err.println("Exception occurred in hideFrameAndWait():");
                    th.printStackTrace(System.err);
                    retCode = 1;
                }
            }
            // ensure jvm immediate shutdown:
            System.exit(retCode);
        }
    }

    // thread synchronization

    private static int threadCount = 0;

    private static int testCount = 0;
    private static volatile String testName = null;

    private static volatile CountDownLatch readyCount = null;
    private static volatile CountDownLatch triggerStart = null;

    private static volatile CountDownLatch completedCount = null;
    private static volatile CountDownLatch triggerStop = null;

    private static volatile CountDownLatch doneCount = null;
    private static volatile CountDownLatch triggerExit = null;

    static void traceSync(final String msg) {
        System.out.println("[" + System.nanoTime() + "] " + msg);
    }

    private static void initThreads(int count) {
        threadCount = count;
        if (TRACE_SYNC) traceSync("initThreads(): threadCount: " + threadCount);
    }

    static boolean isMultiThreads() {
        return threadCount > 1;
    }

    private static void initBarrierStart() {
        readyCount = new CountDownLatch(threadCount);
        triggerStart = new CountDownLatch(1);
    }

    private static void initBarrierStop() {
        completedCount = new CountDownLatch(threadCount);
        triggerStop = new CountDownLatch(1);
    }

    private static void initBarrierDone() {
        doneCount = new CountDownLatch(threadCount);
        triggerExit = new CountDownLatch(1);
    }

    public Thread createThreadTests(final int threadId, final int frameId,
                                    final ArrayList<Method> testCases) throws Exception {
        fh.setIds(threadId, frameId);

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                fh.prepareFrameEDT(VERSION + " [" + fh.threadId + "]");

                final JLabel label = new JLabel((DELAY_START) ? "Waiting 3s before starting benchmark..." : "Starting benchmark...");
                label.setForeground(Color.WHITE);

                final JPanel panel = new JPanel();
                panel.add(label);

                fh.showFrameEDT(panel);
            }
        });

        // Wait frame to be shown:
        fh.waitFrameShown();

        // Set test count per thread:
        testCount = testCases.size() * REPEATS;

        final RenderPerfTest rp = this;
        return new Thread("RenderPerfThread[" + threadId + "]") {
            @Override
            public void run() {
                if (DELAY_START) {
                    RenderPerfTest.sleep(3000);
                }
                try {
                    for (Method m : testCases) {
                        for (int i = 0; i < REPEATS; i++) {
                            testName = extractTestName(m);
                            m.invoke(rp);
                        }
                    }
                } catch (Throwable th) {
                    System.err.println("Exception occurred in RenderPerfThread[" + threadId + "]:");
                    th.printStackTrace(System.err);
                }
            }
        };
    }

    private static void sleep(final long millis) {
        if (millis > 0) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException ie) {
                ie.printStackTrace(System.err);
            }
        }
    }

    /** regular expression used to match characters different than alpha/numeric/_/-/. (1..n) */
    private final static Pattern PATTERN_NON_FILE_NAME = Pattern.compile("[^a-zA-Z0-9\\-_\\.]");

    private static String replaceNonFileNameChars(final String value) {
        return PATTERN_NON_FILE_NAME.matcher(value).replaceAll("_");
    }
}
