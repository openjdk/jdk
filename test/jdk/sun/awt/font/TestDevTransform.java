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
 * @bug 4269775 8341535
 * @summary Check that different text rendering APIs agree
 */

/**
 * Draw into an image rendering the same text string nine different
 * ways: as a TextLayout, a simple String, and a GlyphVector, each
 * with three different x scale factors. The expectation is that each
 * set of three strings would appear the same although offset in y to
 * avoid overlap. The bug was that the y positions of the individual characters
 * of the TextLayout and GlyphVector were wrong, so the strings appeared
 * to be rendered at different angles.
 */

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.util.HashMap;

public class TestDevTransform {

    static HashMap<RenderingHints.Key, Object> hints = new HashMap<>();

    static {
      hints.put(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
      hints.put(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      hints.put(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    static String test = "This is only a test";
    static double angle = Math.PI / 6.0;  // Rotate 30 degrees
    static final int W = 400, H = 400;

    static void draw(Graphics2D g2d, TextLayout layout,
                      float x, float y, float scalex) {
        AffineTransform saveTransform = g2d.getTransform();
        g2d.translate(x, y);
        g2d.rotate(angle);
        g2d.scale(scalex, 1f);
        layout.draw(g2d, 0f, 0f);
        g2d.setTransform(saveTransform);
      }

    static void draw(Graphics2D g2d, String string,
                      float x, float y, float scalex) {
      AffineTransform saveTransform = g2d.getTransform();
      g2d.translate(x, y);
      g2d.rotate(angle);
      g2d.scale(scalex, 1f);
      g2d.drawString(string, 0f, 0f);
      g2d.setTransform(saveTransform);
    }

    static void draw(Graphics2D g2d, GlyphVector gv,
                      float x, float y, float scalex) {
        AffineTransform saveTransform = g2d.getTransform();
        g2d.translate(x, y);
        g2d.rotate(angle);
        g2d.scale(scalex, 1f);
        g2d.drawGlyphVector(gv, 0f, 0f);
        g2d.setTransform(saveTransform);
      }

    static void init(Graphics2D g2d) {
         g2d.setColor(Color.white);
         g2d.fillRect(0, 0, W, H);
         g2d.setColor(Color.black);
         g2d.scale(1.481f, 1.481);   // Convert to 108 dpi
         g2d.addRenderingHints(hints);
         Font font = new Font(Font.DIALOG, Font.PLAIN, 12);
         g2d.setFont(font);
    }

    static void compare(BufferedImage bi1, String name1, BufferedImage bi2, String name2) throws Exception {
        int nonWhite1 = 0;
        int nonWhite2 = 0;
        int differences = 0;
        int whitePixel = Color.white.getRGB();
        for (int x = 0; x < bi1.getWidth(); x++) {
            for (int y = 0; y < bi1.getHeight(); y++) {
                int pix1 = bi1.getRGB(x, y);
                int pix2 = bi2.getRGB(x, y);
                if (pix1 != whitePixel) { nonWhite1++; }
                if (pix2 != whitePixel) { nonWhite2++; }
                if (bi1.getRGB(x, y) != bi2.getRGB(x, y)) {
                    differences++;
                }
            }
        }
        int nonWhite = (nonWhite1 < nonWhite2) ? nonWhite1 : nonWhite2;
        if (differences > 0 && ((nonWhite / differences) < 20)) {
             ImageIO.write(bi1, "png", new File(name1 + ".png"));
             ImageIO.write(bi2, "png", new File(name2 + ".png"));
             System.err.println("nonWhite image 1 = " + nonWhite1);
             System.err.println("nonWhite image 2 = " + nonWhite2);
             System.err.println("Number of non-white differing pixels=" + differences);
             throw new RuntimeException("Different rendering: " + differences + " pixels differ.");
        }
    }

    public static void main(String args[]) throws Exception {

      BufferedImage tl_Image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
      {
          Graphics2D tl_g2d = tl_Image.createGraphics();
          init(tl_g2d);
          FontRenderContext frc = tl_g2d.getFontRenderContext();
          // Specify font from graphics to be sure it is the same as the other cases.
          TextLayout tl = new TextLayout(test, tl_g2d.getFont(), frc);
          draw(tl_g2d, tl, 10f, 12f, 3.0f);
          draw(tl_g2d, tl, 10f, 24f, 1.0f);
          draw(tl_g2d, tl, 10f, 36f, 0.33f);
      }

      BufferedImage st_Image = new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB);
      {
          Graphics2D st_g2d = st_Image.createGraphics();
          init(st_g2d);
          draw(st_g2d, test, 10f, 12f, 3.0f);
          draw(st_g2d, test, 10f, 24f, 1.0f);
          draw(st_g2d, test, 10f, 36f, .33f);
      }

      BufferedImage gv_Image = new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB);
      {
          Graphics2D gv_g2d = gv_Image.createGraphics();
          init(gv_g2d);
          FontRenderContext frc = gv_g2d.getFontRenderContext();
          GlyphVector gv = gv_g2d.getFont().createGlyphVector(frc, test);
          draw(gv_g2d, gv, 10f, 12f, 3.0f);
          draw(gv_g2d, gv, 10f, 24f, 1.0f);
          draw(gv_g2d, gv, 10f, 36f, .33f);
      }

      compare(tl_Image, "textlayout", st_Image, "string");
      compare(gv_Image, "glyphvector", st_Image, "string");
  }
}
