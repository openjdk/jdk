/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Button;
import java.awt.Canvas;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Label;
import java.awt.LayoutManager;
import java.awt.Panel;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.TextField;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.awt.image.ColorModel;
import java.awt.image.MemoryImageSource;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;

import jtreg.SkippedException;

/*
 * @test
 * @bug 4312921
 * @key multimon
 * @library /java/awt/regtesthelpers /test/lib
 * @build PassFailJFrame
 * @summary Tests that no garbage is painted on primary screen with DGA
 * @run main/manual MultiScreenTest
 */

public class MultiScreenTest {
    static GraphicsEnvironment ge;
    static GraphicsDevice[] gs;

    public static void main(String[] args) throws Exception {
        ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        gs = ge.getScreenDevices();
        if (gs.length < 2) {
            throw new SkippedException("You have only one monitor in your system");
        }
        MultiScreenTest obj = new MultiScreenTest();
        String INSTRUCTIONS =
                "This test is to be run only on multiscreen machine. " +
                "You have " + gs.length + " monitors in your system.\n" +
                "Actively drag the DitherTest frames on the secondary screen and " +
                "if you see garbage appearing on your primary screen " +
                "test failed otherwise it passed.";

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(obj::init)
                .positionTestUI(MultiScreenTest::positionTestWindows)
                .build()
                .awaitAndCheck();
    }

    private static void positionTestWindows(List<Window> windows, PassFailJFrame.InstructionUI instructionUI) {
        // Do nothing - the location of each window is set when they're created
    }

    public List<JFrame> init() {
        List<JFrame> list = new ArrayList<>();
        for (int j = 0; j < gs.length; j++) {
            GraphicsConfiguration[] gc = gs[j].getConfigurations();
            if (gc.length > 0) {
                for (int i = 0; i < gc.length && i < 10; i++) {
                    JFrame f = new JFrame(gc[i]);
                    GCCanvas c = new GCCanvas(gc[i]);
                    Rectangle gcBounds = gc[i].getBounds();
                    int xoffs = gcBounds.x;
                    int yoffs = gcBounds.y;

                    f.getContentPane().add(c);
                    f.setTitle("Screen# " + j + ", GC#" + i);
                    f.setSize(300, 200);
                    // test displaying in right location
                    f.setLocation(400 + xoffs, (i * 150) + yoffs);
                    list.add(f);

                    Frame ditherfs = new Frame("DitherTest GC#" + i, gc[i]);
                    ditherfs.setLayout(new BorderLayout());
                    DitherTest ditherTest = new DitherTest(gc[i]);
                    ditherfs.add("Center", ditherTest);
                    ditherfs.setBounds(300, 200, 300, 200);
                    ditherfs.setLocation(750 + xoffs, (i * 50) + yoffs);
                    ditherfs.pack();
                    ditherfs.show();
                    ditherTest.start();
                }
            }
        }
        return list;
    }


    static class GCCanvas extends Canvas {

        GraphicsConfiguration gc;
        Rectangle bounds;
        Dimension size = getSize();

        public GCCanvas(GraphicsConfiguration gc) {
            super(gc);
            this.gc = gc;
            bounds = gc.getBounds();
        }

        @Override
        public void paint( Graphics _g ) {

            Graphics2D g = (Graphics2D) _g;

            g.drawRect(0, 0, size.width-1, size.height-1);
            g.setColor(Color.lightGray);
            g.draw3DRect(1, 1, size.width-3, size.height-3, true);

            g.setColor(Color.red);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g.drawString("HELLO!", 110, 10);

            g.setColor(Color.blue);
            g.drawString("ScreenSize="+Integer.toString(bounds.width)+"X"+
                    Integer.toString(bounds.height), 10, 20);
            g.setColor(Color.green);
            g.drawString(gc.toString(), 10, 30);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

            g.setColor(Color.orange);
            g.fillRect(40, 20, 50, 50);

            g.setColor(Color.red);
            g.drawRect(100, 20, 30, 30);

            g.setColor(Color.gray);
            g.drawLine(220, 20, 280, 40);

            g.setColor(Color.cyan);
            g.fillArc(150, 30, 30, 30, 0, 200);
        }

        @Override
        public Dimension getPreferredSize(){
            return new Dimension(300, 200);
        }
    }

    static class DitherCanvas extends Canvas {
        Image img;
        static String calcString = "Calculating...";

        GraphicsConfiguration mGC;

        public DitherCanvas(GraphicsConfiguration gc) {
            super(gc);
            mGC = gc;
        }

        public GraphicsConfiguration getGraphicsConfig() {
            return mGC;
        }

        @Override
        public void paint(Graphics g) {
            int w = getSize().width;
            int h = getSize().height;
            if (img == null) {
                super.paint(g);
                g.setColor(Color.black);
                FontMetrics fm = g.getFontMetrics();
                int x = (w - fm.stringWidth(calcString)) / 2;
                int y = h / 2;
                g.drawString(calcString, x, y);
            } else {
                g.drawImage(img, 0, 0, w, h, this);
            }
        }

        @Override
        public void update(Graphics g) {
            paint(g);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(20, 20);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(200, 200);
        }

        public Image getImage() {
            return img;
        }

        public void setImage(Image img) {
            this.img = img;
            paint(getGraphics());
        }
    }

    static class DitherTest extends Panel implements Runnable {
        final static int NOOP = 0;
        final static int RED = 1;
        final static int GREEN = 2;
        final static int BLUE = 3;
        final static int ALPHA = 4;
        final static int SATURATION = 5;

        Thread runner;

        DitherControls XControls;
        DitherControls YControls;
        DitherCanvas canvas;

        public DitherTest(GraphicsConfiguration gc) {
            String xspec, yspec;
            int xvals[] = new int[2];
            int yvals[] = new int[2];

            xspec = "red";
            yspec = "blue";
            int xmethod = colormethod(xspec, xvals);
            int ymethod = colormethod(yspec, yvals);

            setLayout(new BorderLayout());
            XControls = new DitherControls(this, xvals[0], xvals[1],
                    xmethod, false);
            YControls = new DitherControls(this, yvals[0], yvals[1],
                    ymethod, true);
            YControls.addRenderButton();
            add("North", XControls);
            add("South", YControls);
            add("Center", canvas = new DitherCanvas(gc));
        }

        public void start() {
            runner = new Thread(this);
            runner.start();
        }

        int colormethod(String s, int vals[]) {
            int method = NOOP;

            if (s == null) {
                s = "";
            }

            String lower = s.toLowerCase();
            int len = 0;
            if (lower.startsWith("red")) {
                method = RED;
                lower = lower.substring(3);
            } else if (lower.startsWith("green")) {
                method = GREEN;
                lower = lower.substring(5);
            } else if (lower.startsWith("blue")) {
                method = BLUE;
                lower = lower.substring(4);
            } else if (lower.startsWith("alpha")) {
                method = ALPHA;
                lower = lower.substring(4);
            } else if (lower.startsWith("saturation")) {
                method = SATURATION;
                lower = lower.substring(10);
            }

            if (method == NOOP) {
                vals[0] = 0;
                vals[1] = 0;
                return method;
            }

            int begval = 0;
            int endval = 255;

            try {
                int dash = lower.indexOf('-');
                if (dash < 0) {
                    begval = endval = Integer.parseInt(lower);
                } else {
                    begval = Integer.parseInt(lower.substring(0, dash));
                    endval = Integer.parseInt(lower.substring(dash + 1));
                }
            } catch (Exception e) {
            }

            if (begval < 0) {
                begval = 0;
            }
            if (endval < 0) {
                endval = 0;
            }
            if (begval > 255) {
                begval = 255;
            }
            if (endval > 255) {
                endval = 255;
            }

            vals[0] = begval;
            vals[1] = endval;

            return method;
        }

        void applymethod(int c[], int method, int step, int total, int vals[]) {
            if (method == NOOP)
                return;
            int val = ((total < 2)
                    ? vals[0]
                    : vals[0] + ((vals[1] - vals[0]) * step / (total - 1)));
            switch (method) {
                case RED:
                    c[0] = val;
                    break;
                case GREEN:
                    c[1] = val;
                    break;
                case BLUE:
                    c[2] = val;
                    break;
                case ALPHA:
                    c[3] = val;
                    break;
                case SATURATION:
                    int max = Math.max(Math.max(c[0], c[1]), c[2]);
                    int min = max * (255 - val) / 255;
                    if (c[0] == 0) {
                        c[0] = min;
                    }
                    if (c[1] == 0) {
                        c[1] = min;
                    }
                    if (c[2] == 0) {
                        c[2] = min;
                    }
                    break;
            }
        }

        @Override
        public void run() {
            canvas.setImage(null);  // Wipe previous image
            Image img = calculateImage();
            synchronized (this) {
                if (img != null && runner == Thread.currentThread()) {
                    canvas.setImage(img);
                }
            }
        }

        /**
         * Calculates and returns the image.  Halts the calculation and returns
         * null if stopped during the calculation.
         */
        Image calculateImage() {
            Thread me = Thread.currentThread();

            int width = canvas.getSize().width;
            int height = canvas.getSize().height;
            int xvals[] = new int[2];
            int yvals[] = new int[2];
            int xmethod = XControls.getParams(xvals);
            int ymethod = YControls.getParams(yvals);
            int pixels[] = new int[width * height];
            int c[] = new int[4];
            int index = 0;

            for (int j = 0; j < height; j++) {
                for (int i = 0; i < width; i++) {
                    c[0] = c[1] = c[2] = 0;
                    c[3] = 255;
                    if (xmethod < ymethod) {
                        applymethod(c, xmethod, i, width, xvals);
                        applymethod(c, ymethod, j, height, yvals);
                    } else {
                        applymethod(c, ymethod, j, height, yvals);
                        applymethod(c, xmethod, i, width, xvals);
                    }
                    pixels[index++] = ((c[3] << 24) |
                            (c[0] << 16) |
                            (c[1] << 8) |
                            (c[2] << 0));

                }
                // Poll once per row to see if we've been told to stop.
                if (runner != me) {
                    return null;
                }
            }

            return createImage(new MemoryImageSource(width, height,
                    ColorModel.getRGBdefault(), pixels, 0, width));
        }

        public String getInfo() {
            return "An interactive demonstration of dithering.";
        }

        public String[][] getParameterInfo() {
            String[][] info = {
                    {"xaxis", "{RED, GREEN, BLUE, PINK, ORANGE, MAGENTA, CYAN, WHITE, YELLOW, GRAY, DARKGRAY}",
                            "The color of the Y axis.  Default is RED."},
                    {"yaxis", "{RED, GREEN, BLUE, PINK, ORANGE, MAGENTA, CYAN, WHITE, YELLOW, GRAY, DARKGRAY}",
                            "The color of the X axis.  Default is BLUE."}
            };
            return info;
        }
    }

    static class DitherControls extends Panel implements ActionListener {
        TextField start;
        TextField end;
        Button button;
        Choice choice;
        DitherTest dt;

        static LayoutManager dcLayout = new FlowLayout(FlowLayout.CENTER, 10, 5);

        public DitherControls(DitherTest app, int s, int e, int type,
                              boolean vertical) {
            dt = app;
            setLayout(dcLayout);
            add(new Label(vertical ? "Vertical" : "Horizontal"));
            add(choice = new Choice());
            choice.addItem("Noop");
            choice.addItem("Red");
            choice.addItem("Green");
            choice.addItem("Blue");
            choice.addItem("Alpha");
            choice.addItem("Saturation");
            choice.select(type);
            add(start = new TextField(Integer.toString(s), 4));
            add(end = new TextField(Integer.toString(e), 4));
        }

        public void addRenderButton() {
            add(button = new Button("New Image"));
            button.addActionListener(this);
        }

        public int getParams(int vals[]) {
            vals[0] = Integer.parseInt(start.getText());
            vals[1] = Integer.parseInt(end.getText());
            return choice.getSelectedIndex();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (e.getSource() == button) {
                dt.start();
            }
        }
    }
}
