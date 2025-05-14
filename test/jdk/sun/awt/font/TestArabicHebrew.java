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
    "،؛؟ءآأؤإئا"
    + "بةتثجحخدذر"
    + "زسشصضطظعغـ"
    + "فقكلمنهوىي"
    + "ًٌٍَُِّْﺀﺁ"
    + "ﺂﺃﺄﺅﺆﺇﺈﺉﺊﺋ"
    + "ﺌﺍﺎﺏﺐﺑﺒﺓﺔﺕ"
    + "ﺖﺗﺘﺙﺚﺛﺜﺝﺞﺟ"
    + "ﺠﺡﺢﺣﺤﺥﺦﺧﺨﺩ"
    + "ﺪﺫﺬﺭﺮﺯﺰﺱﺲﺳ"
    + "ﺴﺵﺶﺷﺸﺹﺺﺻﺼﺽ"
    + "ﺾﺿﻀﻁﻂﻃﻄﻅﻆﻇ"
    + "ﻈﻉﻊﻋﻌﻍﻎﻏﻐﻑ"
    + "ﻒﻓﻔﻕﻖﻗﻘﻙﻚﻛ"
    + "ﻜﻝﻞﻟﻠﻡﻢﻣﻤﻥ"
    + "ﻦﻧﻨﻩﻪﻫﻬﻭﻮﻯ"
    + "ﻰﻱﻲﻳﻴﻵﻶﻷﻸﻹ"
    + "ﻺﻻﻼ";

    // hebrew table includes all characters in hebrew block

    static final String hebrew =
    "֑֖֚֒֓֔֕֗֘֙"
    + "֛֣֤֥֜֝֞֟֠֡"
    + "֦֧֪֭֮֨֩֫֬֯"
    + "ְֱֲֳִֵֶַָֹ"
    + "ֻּֽ־ֿ׀ׁׂ׃ׄ"
    + "אבגדהוזחטי"
    + "ךכלםמןנסעף"
    + "פץצקרשתװױײ"
    + "׳״";

    // latin 1 supplement table includes all non-control characters
    // in this range.  Included because of comment in code that claims
    // some problems displaying this range with some SJIS fonts.

    static final String latin1sup =
    "\u00a0¡¢£¤¥¦§"
    + "¨©ª«¬\u00ad®¯°±"
    + "²³´µ¶·¸¹º»"
    + "¼½¾¿ÀÁÂÃÄÅ"
    + "ÆÇÈÉÊËÌÍÎÏ"
    + "ÐÑÒÓÔÕÖ×ØÙ"
    + "ÚÛÜÝÞßàáâã"
    + "äåæçèéêëìí"
    + "îïðñòóôõö÷"
    + "øùúûüýþÿ";

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
