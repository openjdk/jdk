/*
 * Copyright (c) 2007, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Color;
import java.awt.Font;
import static java.awt.Font.*;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;


/*
 * @test
 * @bug 8396849
 * @summary Check that logical fonts differ by style and differ from dialog plain
 */

public class LogicalFontTest {

   // Don't include DIALOG or DIALOG_INPUT since in almost all cases DIALOG -> SANS_SERIF
   // and DIALOG_INPUT -> MONOSPACED.
   private static final String[] FONTS = { MONOSPACED, SANS_SERIF, SERIF };
   private static final int[] STYLES = { PLAIN, BOLD, ITALIC, BOLD | ITALIC } ;

   public static void main(String[] args) {

       BufferedImage dialogPlain = createImage(DIALOG, PLAIN);
       BufferedImage[][] fonts = new BufferedImage[FONTS.length][STYLES.length];
       for (int i = 0 ; i < FONTS.length; i++) {
           for (int j = 0 ; j < STYLES.length; j++ ) {
               fonts[i][j] = createImage(FONTS[i], STYLES[j]);
           }
       }

       /* This tests a couple of things
        * 1. A particular style of a font must differ from all the other styles of the font.
        * 2. No font must be the same as DIALOG PLAIN, except for SANS_SERIF PLAIN,
        *    because Dialog is typically an alias for SansSerif, so that's expected.
        *
        * The test uses just 'ABC' to try to be safe. For many writing systems there is
        * only one font available, as styles such as italic are not applicable.
        * In other words there can be no expectation that every code point is a different
        * glyph in all cases.
        * There is a chance that this will fail on some system where essentially no fonts
        * are installed, so a font has to be re-used.
        * That should be considered a platform configuration problem.
        */
       for (int i = 0 ; i < FONTS.length; i++ ) {
           for (int j = 0 ; j < STYLES.length; j++ ) {
               BufferedImage img = fonts[i][j];
               for (int k = 0 ; k < STYLES.length; k++ ) {
                   if (j == k) {
                       continue;
                   }
                   System.out.println("compare " + FONTS[i] + "." + STYLES[j] + " vs ." + STYLES[k]);
                   compare(img, fonts[i][k]);
               }
               if (!SANS_SERIF.equals(FONTS[i])) {
                   System.out.println("compare " + FONTS[i] + "." + STYLES[j] + " vs Dialog");
                   compare(img, dialogPlain);
               }
           }
       }
   }

   static BufferedImage createImage(String fname, int style) {
       int w = 50, h = 50;
       BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
       Graphics2D g2d = bi.createGraphics();
       g2d.setColor(Color.white);
       g2d.fillRect(0, 0, 50, 50);
       g2d.setColor(Color.black);
       Font f = new Font(fname, style, 20);
       g2d.setFont(f);
       g2d.drawString("ABC", 20, 30);
       return bi;
   }


   static void compare(BufferedImage bi1, BufferedImage bi2) {
       int w = bi1.getWidth();
       int h = bi1.getHeight();

       for (int x = 0; x < w; x++ ) {
           for (int y = 0; y < h; y++ ) {
              if (bi1.getRGB(x, y) != bi2.getRGB(x, y)) {
                   return;
              }
          }
      }
      throw new RuntimeException("No differences.");
   }
}
