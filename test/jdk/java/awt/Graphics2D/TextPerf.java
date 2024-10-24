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
 * @bug  4190429
 * @key headful
 * @summary In this bug, text drawing performance should be reasonable.
 *          And should (per string) be consistent with the size of the
 *          rectangle in which the string is drawn, not the rectangle
 *          bounding the whole window.
 */

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Panel;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TextPerf extends Canvas {

    static volatile CountDownLatch paintLatch = new CountDownLatch(1);
    static volatile long paintTime = 5000; // causes test fail if it is not updated.
    static volatile Frame frame;

    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(TextPerf::createUI);
        paintLatch.await(5, TimeUnit.SECONDS);
        if (paintTime > 2000) {
            throw new RuntimeException("Paint time is " + paintTime + "ms");
        }
        if (frame != null) {
            EventQueue.invokeAndWait(frame::dispose);
        }
    }

    static void createUI() {
        frame = new Frame("TextPerf");
        frame.setLayout(new BorderLayout());
        TextPerf tp = new TextPerf();
        frame.add(tp, BorderLayout.CENTER);
        frame.pack();
        frame.setVisible(true);
    }

    public Dimension getPreferredSize() {
        Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
        return new Dimension(d.width - 50, d.height - 50);
    }

    static Font[] fonts = {
        new Font(Font.SERIF, Font.PLAIN, 10),
        new Font(Font.SANS_SERIF, Font.PLAIN, 10),
        new Font(Font.MONOSPACED, Font.ITALIC, 10),
        new Font(Font.SERIF, Font.PLAIN, 14),
        new Font(Font.SERIF, Font.BOLD, 12),
    };

    public void paint(Graphics g1) {

        Graphics2D g = (Graphics2D)g1;
        String text = "Hello,_Wgjpqy!";
        Toolkit.getDefaultToolkit().sync();
        long startTime = System.currentTimeMillis();
        FontMetrics[] cachedMetrics = new FontMetrics[fonts.length];
        Dimension size = getSize();
        int prim = 0;
        int spaceWidth = 5;
        Color cols[] = { Color.red, Color.blue, Color.yellow,
                         Color.green, Color.pink, Color.orange} ;

        for (int y = 20; y < size.height; y += 20) {
            int i = 0;
            for (int x = 0; x < size.width; i++) {
                Font font = fonts[i % fonts.length];
                FontMetrics metrics = cachedMetrics[i % fonts.length];
                if (metrics == null) {
                    metrics = g.getFontMetrics(font);
                    cachedMetrics[i % fonts.length] = metrics;
                }

                g.setFont(font);
                g.setColor(cols[i % cols.length]);
                switch (prim++) {
                  case 0:  g.drawString(text, x, y);
                           break;
                  case 1:  g.drawBytes(text.getBytes(), 0, text.length(), x, y);
                           break;
                  case 2:  char[] chs= new char[text.length()];
                           text.getChars(0,text.length(), chs, 0);
                           g.drawChars(chs, 0, text.length(), x, y);
                           break;
                  case 3:  GlyphVector gv = font.createGlyphVector(
                                              g.getFontRenderContext(), text);
                           g.drawGlyphVector(gv, (float)x, (float)y);
                  default: prim = 0;
                }

                x += metrics.stringWidth(text) + spaceWidth;
            }
        }

        // Draw some transformed text to verify correct bounds calculated
        AffineTransform at = AffineTransform.getTranslateInstance(50, 50);
        at.scale(7.0,7.0);
        at.rotate(1.0);
        g.transform(at);
        g.setColor(Color.black);
        Font font = new Font(Font.SERIF, Font.PLAIN, 20);
        RenderingHints hints = new RenderingHints(null);
        hints.put(RenderingHints.KEY_ANTIALIASING,
                  RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHints(hints);
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics(font);
        g.drawString("Java", 5,5);

        Toolkit.getDefaultToolkit().sync();
        long endTime = System.currentTimeMillis();
        paintTime = endTime - startTime;
        String msg = "repainted in " + paintTime + " milliseconds";
        System.out.println(msg);
        System.out.flush();

        paintLatch.countDown();
     }
}
