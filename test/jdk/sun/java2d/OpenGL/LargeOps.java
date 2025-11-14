/*
 * Copyright (c) 2004, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6219284 6358147 6274813 6578452
 * @key headful
 * @summary Verifies that OGLRenderer.drawPoly(),
 * OGLTextRenderer.drawGlyphList(), and OGLMaskFill work properly when the
 * operation parameters exceed the capacity of the render queue.  With the
 * single-threaded OpenGL pipeline, there are some operations that require
 * a separate buffer to be spawned if the parameters cannot fit entirely on
 * the standard buffer.  This test exercises this special case.
 * @run main/othervm  -Dsun.java2d.opengl=True -Dsun.java2d.opengl.lcdshader=true LargeOps
 */

import java.awt.Canvas;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class LargeOps extends Canvas {

    private static final int NUM_POINTS = 8000;
    private int[] xPoints, yPoints;
    private String str;

    public LargeOps() {
        xPoints = new int[NUM_POINTS];
        yPoints = new int[NUM_POINTS];
        for (int i = 0; i < NUM_POINTS; i++) {
            xPoints[i] = (i % 2 == 0) ? 10 : 400;
            yPoints[i] = (i % 2 == 1) ? i+3 : i;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < NUM_POINTS; i+=11) {
            sb.append("ThisIsATest");
        }
        str = sb.toString();
    }

    public void paint(Graphics g) {
        Graphics2D g2d = (Graphics2D)g;
        g2d.setColor(Color.white);
        g2d.fillRect(0, 0, getWidth(), getHeight());

        // draw large polyline
        g2d.setColor(Color.green);
        g2d.drawPolyline(xPoints, yPoints, NUM_POINTS);

        // draw long string
        g2d.setColor(Color.blue);
        g2d.drawString(str, 10, 100);

        // draw long string with larger pt size
        Font font = g2d.getFont();
        g2d.setFont(font.deriveFont(40.0f));
        g2d.drawString(str, 10, 150);

        // do the same with LCD hints enabled
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                             RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2d.setFont(font);
        g2d.drawString(str, 10, 200);
        g2d.setFont(font.deriveFont(43.0f));
        g2d.drawString(str, 10, 250);

        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                             RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HBGR);
        g2d.setFont(font);
        g2d.drawString(str, 10, 300);
        g2d.setFont(font.deriveFont(37.0f));
        g2d.drawString(str, 10, 350);
    }

    static volatile Frame frame;
    static volatile LargeOps test;

    static void createUI() {
        frame = new Frame("OpenGL LargeOps Test");
        frame.addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                    frame.dispose();
                }
            });
        test = new LargeOps();
        frame.add(test);
        frame.setSize(600, 600);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) throws Exception {
        try {
             Robot robot = new Robot();
             EventQueue.invokeAndWait(LargeOps::createUI);
             robot.waitForIdle();
             robot.delay(6000);
        } finally {
            if (frame != null) {
                 EventQueue.invokeAndWait(frame::dispose);
            }
        }
    }
}
