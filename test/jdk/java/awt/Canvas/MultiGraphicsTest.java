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
 * @summary Display an image in all available GraphicsConfigurations
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MultiGraphicsTest
 */

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URL;
import java.util.List;

public class MultiGraphicsTest extends Canvas {
    final static String IMAGEFILE = "duke_404.gif";
    static Image jim;
    MediaTracker tracker;
    int w, h;

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                This test displays several Windows containing an image,
                one Window for each available GraphicsConfiguration.
                Depending on the GraphicsConfiguration, images may be
                displayed in color or in grayscale and/or displayed at a
                lower bitdepth.
                The number of GraphicsConfigurations will be printed below
                as the test is starting up.
                Ensure that there are as many Windows created as there are
                available GraphicsConfigurations.
                Examine each Window to ensure it displays Duke_404.
                If all Canvases display correctly, the test PASSES.
                Otherwise, the test FAILS."
                """;
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(initialize())
                .build()
                .awaitAndCheck();
    }

    public MultiGraphicsTest(GraphicsConfiguration gc) {
        super(gc);
        tracker = new MediaTracker(this);
        tracker.addImage(jim, 0);
        try {
            tracker.waitForAll();
        } catch (java.lang.InterruptedException e) {
            System.err.println(e);
        }
        w = jim.getWidth(this);
        h = jim.getHeight(this);
    }

    private static List<Frame> initialize() {
        URL imgURL;
        imgURL = MultiGraphicsTest.class.getResource(IMAGEFILE);
        if (imgURL == null) {
            System.err.println("Unable to locate " + IMAGEFILE);
            return null;
        }
        jim = Toolkit.getDefaultToolkit().getImage(imgURL);

        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();
        GraphicsConfiguration[] gc = gd.getConfigurations();
        Frame[] frames = new Frame[gc.length];
        System.out.println(gc.length + " available GraphicsConfigurations");
        for (int i = 0; i < gc.length; i++) {
            Frame f = new Frame("GraphicsTest " + (i + 1));
            f.setLayout(new BorderLayout());
            f.setLocation(100 + (i * 10), 100 + (i * 10));
            MultiGraphicsTest gcTest = new MultiGraphicsTest(gc[i]);
            f.add("Center", gcTest);
            f.pack();
            f.addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent ev) {
                    ev.getWindow().setVisible(false);
                }
            });
            frames[i] = f;
        }
        return List.of(frames);
    }

    public void paint(Graphics g) {
        g.drawImage(jim, 0, 0, w, h, this);
    }

    public void update(Graphics g) {
        paint(g);
    }

    public Dimension getMinimumSize() {
        return new Dimension(w, h);
    }

    public Dimension getPreferredSize() {
        return new Dimension(w, h);
    }
}
