/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6616089
 * @summary Displays a dithered Canvas on all available GraphicsConfigurations
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MultiDitherTest
 */

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Canvas;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Label;
import java.awt.LayoutManager;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.ColorModel;
import java.awt.image.MemoryImageSource;
import java.util.List;

public class MultiDitherTest extends Panel implements Runnable {
    final static int NOOP = 0;
    final static int RED = 1;
    final static int GREEN = 2;
    final static int BLUE = 3;
    final static int ALPHA = 4;
    final static int SATURATION = 5;
    final static String calcString = "Calculating...";
    static LayoutManager dcLayout = new FlowLayout(FlowLayout.CENTER, 10, 5);
    Thread runner;
    DitherControls XControls;
    DitherControls YControls;
    DitherCanvas canvas;

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                Depending on the GraphicsConfiguration, the dithering may be in
                color or in grayscale and/or display at a lower bitdepth.
                The number of GraphicsConfigurations will be printed in the
                TextArea below as the test is starting up.
                Ensure that there are as many Frames created as there are
                available GraphicsConfigurations.
                Examine each Frame to ensure it displays the dither pattern.
                If all Canvases display correctly, the test PASSES.
                Otherwise, the test FAILS.
                The GC button runs the garbage collector.
                This button can be ignored for now.

                           """;
        PassFailJFrame passFailJFrame = PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .build();

        EventQueue.invokeAndWait(() -> {
            for (Frame frame : MultiDitherTest.initialize()) {
                PassFailJFrame.addTestWindow(frame);
                frame.setVisible(true);
            }
        });
        passFailJFrame.awaitAndCheck();
    }

    public MultiDitherTest(GraphicsConfiguration gc) {
        String xSpec, ySpec;
        int[] xValues = new int[2];
        int[] yValues = new int[2];

        xSpec = "red";
        ySpec = "blue";
        int xMethod = colorMethod(xSpec, xValues);
        int yMethod = colorMethod(ySpec, yValues);

        setLayout(new BorderLayout());
        XControls = new DitherControls(this, xValues[0], xValues[1],
                xMethod, false);
        YControls = new DitherControls(this, yValues[0], yValues[1],
                yMethod, true);
        YControls.addRenderButton();
        YControls.addGCButton();
        add("North", XControls);
        add("South", YControls);
        add("Center", canvas = new DitherCanvas(gc));
    }

    private static List<Frame> initialize() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gds = ge.getScreenDevices();
        Frame[] frames = new Frame[0];
        System.out.println(gds.length + " screens detected");

        for (int j = 0; j < gds.length; j++) {

            GraphicsDevice gd = gds[j];
            GraphicsConfiguration[] gcs = gd.getConfigurations();
            frames = new Frame[gcs.length];
            System.out.println(gcs.length + " GraphicsConfigurations available on screen " + j);
            for (int i = 0; i < gcs.length; i++) {
                Frame f = new Frame("MultiDitherTest " + (i + 1), gcs[i]);
                f.setLayout(new BorderLayout());
                f.setLocation(gcs[i].getBounds().x + 100 + (i * 10),
                        gcs[i].getBounds().y + 100 + (i * 10));
                MultiDitherTest ditherTest = new MultiDitherTest(gcs[i]);
                f.add("Center", ditherTest);
                f.pack();
                f.addWindowListener(new WindowAdapter() {
                    public void windowClosing(WindowEvent ev) {
                        ev.getWindow().dispose();
                    }
                });
                f.setVisible(true);
                ditherTest.start();
                frames[i] = f;
            }

        }
        return List.of(frames);
    }

    int colorMethod(String s, int[] values) {
        int method = NOOP;

        if (s == null) {
            s = "";
        }

        String lower = s.toLowerCase();
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
            values[0] = 0;
            values[1] = 0;
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

        values[0] = begval;
        values[1] = endval;

        return method;
    }

    public void start() {
        runner = new Thread(this);
        runner.start();
    }

    public void stop() {
        runner = null;
    }

    public void destroy() {
        remove(XControls);
        remove(YControls);
        remove(canvas);
    }

    void applyMethod(int[] c, int method, int step, int total, int[] values) {
        if (method == NOOP) {
            return;
        }
        int val = ((total < 2)
                ? values[0]
                : values[0] + ((values[1] - values[0]) * step / (total - 1)));
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
                if (c[0] == 0) c[0] = min;
                if (c[1] == 0) c[1] = min;
                if (c[2] == 0) c[2] = min;
                break;
        }
    }

    public void run() {
        canvas.setImage(null);
        Image img = calculateImage();
        synchronized (this) {
            if (img != null && runner == Thread.currentThread()) {
                canvas.setImage(img);
            }
        }
    }

    /**
     * Calculates and returns the image.  Halts the calculation and returns
     * null if the Application is stopped during the calculation.
     */
    Image calculateImage() {
        Thread me = Thread.currentThread();

        int width = canvas.getSize().width;
        int height = canvas.getSize().height;
        int[] xValues = new int[2];
        int[] yValues = new int[2];
        int xMethod = XControls.getParams(xValues);
        int yMethod = YControls.getParams(yValues);
        int[] pixels = new int[width * height];
        int[] c = new int[4];
        int index = 0;

        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                c[0] = c[1] = c[2] = 0;
                c[3] = 255;
                if (xMethod < yMethod) {
                    applyMethod(c, xMethod, i, width, xValues);
                    applyMethod(c, yMethod, j, height, yValues);
                } else {
                    applyMethod(c, yMethod, j, height, yValues);
                    applyMethod(c, xMethod, i, width, xValues);
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

    static class DitherCanvas extends Canvas {
        Image img;
        GraphicsConfiguration mGC;

        public DitherCanvas(GraphicsConfiguration gc) {
            super(gc);
            mGC = gc;
        }

        public GraphicsConfiguration getGraphicsConfig() {
            return mGC;
        }

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

        public void update(Graphics g) {
            paint(g);
        }

        public Dimension getMinimumSize() {
            return new Dimension(20, 20);
        }

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

    static class DitherControls extends Panel implements ActionListener {
        TextField start;
        TextField end;
        Button button;
        Choice choice;
        MultiDitherTest panel;
        Button gc;

        public DitherControls(MultiDitherTest app, int s, int e, int type,
                              boolean vertical) {
            panel = app;
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

        public void addGCButton() {
            add(gc = new Button("GC"));
            gc.addActionListener(this);
        }

        public int getParams(int[] values) {
            values[0] = Integer.parseInt(start.getText());
            values[1] = Integer.parseInt(end.getText());
            return choice.getSelectedIndex();
        }

        public void actionPerformed(ActionEvent e) {
            if (e.getSource() == button) {
                panel.start();
            } else if (e.getSource() == gc) {
                System.gc();
            }
        }
    }
}