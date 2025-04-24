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
 * @bug 4198081
 * @key headful
 * @summary Arabic characters should appear instead of boxes and be correctly shaped.
 *          Hebrew characters should appear instead of boxes.
 *          Test is made headful so there's no excuse for test systems not having the fonts.
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;

public class TestArabicHebrew extends Panel {

    static volatile Frame frame;
    static volatile Font font = new Font(Font.DIALOG, Font.PLAIN, 36);

    static void createUI() {
        frame = new Frame("Test Arabic/Hebrew");
        frame.setLayout(new BorderLayout());
        TestArabicHebrew panel = new TestArabicHebrew();
        frame.add(panel, BorderLayout.CENTER);
        frame.pack();
        frame.setVisible(true);
    }

    public static void main(String args[]) throws Exception {
        EventQueue.invokeAndWait(TestArabicHebrew::createUI);
        try {
             checkStrings();
        } finally {
           if (frame != null && args.length == 0) {
               EventQueue.invokeAndWait(frame::dispose);
           }
        }
    }

    static void checkString(String script, String str) {
        int index = font.canDisplayUpTo(str);
        if (index != -1) {
            throw new RuntimeException("Cannot display char " +  index + " for " + script);
        }
    }

    static void checkStrings() {
        checkString("Arabic", arabic);
        checkString("Hebrew", hebrew);
        checkString("Latin-1 Supplement", latin1sup);
    }

    // Table of arabic unicode characters - minimal support level
    // Includes arabic chars from basic block up to 0652 and
    // corresponding shaped characters from the arabic
    // extended-B block from fe80 to fefc (does include lam-alef
    // ligatures).
    // Does not include arabic-indic digits nor "arabic extended"
    // range.

    static final String arabic =
    "\u060c\u061b\u061f\u0621\u0622\u0623\u0624\u0625\u0626\u0627"
    + "\u0628\u0629\u062a\u062b\u062c\u062d\u062e\u062f\u0630\u0631"
    + "\u0632\u0633\u0634\u0635\u0636\u0637\u0638\u0639\u063a\u0640"
    + "\u0641\u0642\u0643\u0644\u0645\u0646\u0647\u0648\u0649\u064a"
    + "\u064b\u064c\u064d\u064e\u064f\u0650\u0651\u0652\ufe80\ufe81"
    + "\ufe82\ufe83\ufe84\ufe85\ufe86\ufe87\ufe88\ufe89\ufe8a\ufe8b"
    + "\ufe8c\ufe8d\ufe8e\ufe8f\ufe90\ufe91\ufe92\ufe93\ufe94\ufe95"
    + "\ufe96\ufe97\ufe98\ufe99\ufe9a\ufe9b\ufe9c\ufe9d\ufe9e\ufe9f"
    + "\ufea0\ufea1\ufea2\ufea3\ufea4\ufea5\ufea6\ufea7\ufea8\ufea9"
    + "\ufeaa\ufeab\ufeac\ufead\ufeae\ufeaf\ufeb0\ufeb1\ufeb2\ufeb3"
    + "\ufeb4\ufeb5\ufeb6\ufeb7\ufeb8\ufeb9\ufeba\ufebb\ufebc\ufebd"
    + "\ufebe\ufebf\ufec0\ufec1\ufec2\ufec3\ufec4\ufec5\ufec6\ufec7"
    + "\ufec8\ufec9\ufeca\ufecb\ufecc\ufecd\ufece\ufecf\ufed0\ufed1"
    + "\ufed2\ufed3\ufed4\ufed5\ufed6\ufed7\ufed8\ufed9\ufeda\ufedb"
    + "\ufedc\ufedd\ufede\ufedf\ufee0\ufee1\ufee2\ufee3\ufee4\ufee5"
    + "\ufee6\ufee7\ufee8\ufee9\ufeea\ufeeb\ufeec\ufeed\ufeee\ufeef"
    + "\ufef0\ufef1\ufef2\ufef3\ufef4\ufef5\ufef6\ufef7\ufef8\ufef9"
    + "\ufefa\ufefb\ufefc";

    // hebrew table includes all characters in hebrew block

    static final String hebrew =
    "\u0591\u0592\u0593\u0594\u0595\u0596\u0597\u0598\u0599\u059a"
    + "\u059b\u059c\u059d\u059e\u059f\u05a0\u05a1\u05a3\u05a4\u05a5"
    + "\u05a6\u05a7\u05a8\u05a9\u05aa\u05ab\u05ac\u05ad\u05ae\u05af"
    + "\u05b0\u05b1\u05b2\u05b3\u05b4\u05b5\u05b6\u05b7\u05b8\u05b9"
    + "\u05bb\u05bc\u05bd\u05be\u05bf\u05c0\u05c1\u05c2\u05c3\u05c4"
    + "\u05d0\u05d1\u05d2\u05d3\u05d4\u05d5\u05d6\u05d7\u05d8\u05d9"
    + "\u05da\u05db\u05dc\u05dd\u05de\u05df\u05e0\u05e1\u05e2\u05e3"
    + "\u05e4\u05e5\u05e6\u05e7\u05e8\u05e9\u05ea\u05f0\u05f1\u05f2"
    + "\u05f3\u05f4";

    // latin 1 supplement table includes all non-control characters
    // in this range.  Included because of comment in code that claims
    // some problems displaying this range with some SJIS fonts.

    static final String latin1sup =
    "\u00a0\u00a1\u00a2\u00a3\u00a4\u00a5\u00a6\u00a7"
    + "\u00a8\u00a9\u00aa\u00ab\u00ac\u00ad\u00ae\u00af\u00b0\u00b1"
    + "\u00b2\u00b3\u00b4\u00b5\u00b6\u00b7\u00b8\u00b9\u00ba\u00bb"
    + "\u00bc\u00bd\u00be\u00bf\u00c0\u00c1\u00c2\u00c3\u00c4\u00c5"
    + "\u00c6\u00c7\u00c8\u00c9\u00ca\u00cb\u00cc\u00cd\u00ce\u00cf"
    + "\u00d0\u00d1\u00d2\u00d3\u00d4\u00d5\u00d6\u00d7\u00d8\u00d9"
    + "\u00da\u00db\u00dc\u00dd\u00de\u00df\u00e0\u00e1\u00e2\u00e3"
    + "\u00e4\u00e5\u00e6\u00e7\u00e8\u00e9\u00ea\u00eb\u00ec\u00ed"
    + "\u00ee\u00ef\u00f0\u00f1\u00f2\u00f3\u00f4\u00f5\u00f6\u00f7"
    + "\u00f8\u00f9\u00fa\u00fb\u00fc\u00fd\u00fe\u00ff";

    public TestArabicHebrew() {
        setLayout(new GridLayout(3, 1));

        FontRenderContext frc = new FontRenderContext(null, false, false);
        add(new SubGlyphPanel("Arabic", arabic, font, frc));
        add(new SubGlyphPanel("Hebrew", hebrew, font, frc));
        add(new SubGlyphPanel("Latin-1 Supplement", latin1sup, font, frc));
    }

  static class SubGlyphPanel extends Panel {
      String title;
      Dimension extent;
      GlyphVector[] vectors;

      static final int kGlyphsPerLine = 20;

      SubGlyphPanel(String title, String chars, Font font, FontRenderContext frc) {

          this.title = title;
          setBackground(Color.white);

          double width = 0;
          double height = 0;

          int max = chars.length();
          vectors = new GlyphVector[(max + kGlyphsPerLine - 1) / kGlyphsPerLine];
          for (int i = 0; i < vectors.length; i++) {
              int start = i * 20;
              int limit = Math.min(max, (i + 1) * kGlyphsPerLine);
              String substr = "";
              for (int j = start; j < limit; ++j) {
                  substr = substr.concat(chars.charAt(j) + " ");
              }
              GlyphVector gv = font.createGlyphVector(frc, substr);
              vectors[i] = gv;
              Rectangle2D bounds = gv.getLogicalBounds();

              width = Math.max(width, bounds.getWidth());
              height += bounds.getHeight();
          }

          extent = new Dimension((int)(width + 1), (int)(height + 1 + 30)); // room for title

          setSize(getPreferredSize());
    }

    public Dimension getPreferredSize() {
        return new Dimension(extent);
    }

    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    public void paint(Graphics g) {
        Graphics2D g2d = (Graphics2D)g;

        g.drawString(title, 10, 20);

        float x = 10;
        float y = 30;
        for (int i = 0; i < vectors.length; ++i) {
            GlyphVector gv = vectors[i];
            Rectangle2D bounds = gv.getLogicalBounds();
            g2d.drawGlyphVector(gv, x, (float)(y - bounds.getY()));
            y += bounds.getHeight();
        }
    }
  }
}
