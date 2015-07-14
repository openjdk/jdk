/*
 * Copyright (c) 2004, 2015, Oracle and/or its affiliates. All rights reserved.
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


/* @test
   @bug 4328745 5090704
   @summary exercise getLayoutFlags, getGlyphCharIndex, getGlyphCharIndices
 */

import java.awt.*;
import java.awt.event.*;
import java.awt.font.*;
import java.awt.geom.*;

public class TestLayoutFlags {

    static public void main(String[] args) {
        new TestLayoutFlags().runTest();
    }

    void runTest() {

        Font font = new Font("Lucida Sans", Font.PLAIN, 24);

        String latin1 = "This is a latin1 string"; // none
        String hebrew = "\u05d0\u05d1\u05d2\u05d3"; // rtl
        String arabic = "\u0646\u0644\u0622\u0646"; // rtl + mc/g
        String hindi = "\u0939\u093f\u0923\u094d\u0921\u0940"; // ltr + reorder
        //      String tamil = "\u0b9c\u0bcb"; // ltr + mg/c + split

        FontRenderContext frc = new FontRenderContext(null, true, true);

        // get glyph char indices needs to initializes layoutFlags before use (5090704)
        {
          GlyphVector gv = font.createGlyphVector(frc, "abcde");
          int ix = gv.getGlyphCharIndex(0);
          if (ix != 0) {
            throw new Error("glyph 0 incorrectly mapped to char " + ix);
          }
          int[] ixs = gv.getGlyphCharIndices(0, gv.getNumGlyphs(), null);
          for (int i = 0; i < ixs.length; ++i) {
            if (ixs[i] != i) {
              throw new Error("glyph " + i + " incorrectly mapped to char " + ixs[i]);
            }
          }
        }

        GlyphVector latinGV = makeGlyphVector("Lucida Sans", frc, latin1, false, 1 /* ScriptRun.LATIN */);
        GlyphVector hebrewGV = makeGlyphVector("Lucida Sans", frc, hebrew, true, 5 /* ScriptRun.HEBREW */);
        GlyphVector arabicGV = makeGlyphVector("Lucida Sans", frc, arabic, true, 6 /* ScriptRun.ARABIC */);
        GlyphVector hindiGV = makeGlyphVector("Lucida Sans", frc, hindi, false, 7 /* ScriptRun.DEVANAGARI */);
        //      GlyphVector tamilGV = makeGlyphVector("Devanagari MT for IBM", frc, tamil, false, 12 /* ScriptRun.TAMIL */);

        GlyphVector latinPos = font.createGlyphVector(frc, latin1);
        Point2D pt = latinPos.getGlyphPosition(0);
        pt.setLocation(pt.getX(), pt.getY() + 1.0);
        latinPos.setGlyphPosition(0, pt);

        GlyphVector latinTrans = font.createGlyphVector(frc, latin1);
        latinTrans.setGlyphTransform(0, AffineTransform.getRotateInstance(.15));

        test("latin", latinGV, GlyphVector.FLAG_HAS_POSITION_ADJUSTMENTS);
        test("hebrew", hebrewGV, GlyphVector.FLAG_RUN_RTL |
             GlyphVector.FLAG_HAS_POSITION_ADJUSTMENTS);
        test("arabic", arabicGV, GlyphVector.FLAG_RUN_RTL |
             GlyphVector.FLAG_HAS_POSITION_ADJUSTMENTS);
        test("hindi", hindiGV, GlyphVector.FLAG_COMPLEX_GLYPHS |
             GlyphVector.FLAG_HAS_POSITION_ADJUSTMENTS);
        //      test("tamil", tamilGV, GlyphVector.FLAG_COMPLEX_GLYPHS);
        test("pos", latinPos, GlyphVector.FLAG_HAS_POSITION_ADJUSTMENTS);
        test("trans", latinTrans, GlyphVector.FLAG_HAS_TRANSFORMS);
    }

    GlyphVector makeGlyphVector(String fontname, FontRenderContext frc, String text, boolean rtl, int script) {
        Font font = new Font(fontname, Font.PLAIN, 14);
        System.out.println("asking for " + fontname + " and got " + font.getFontName());
        int flags = rtl ? 1 : 0;
        return font.layoutGlyphVector(frc, text.toCharArray(), 0, text.length(), flags);
    }

    void test(String name, GlyphVector gv, int expectedFlags) {
        expectedFlags &= gv.FLAG_MASK;
        int computedFlags = computeFlags(gv) & gv.FLAG_MASK;
        int actualFlags = gv.getLayoutFlags() & gv.FLAG_MASK;

        System.out.println("\n*** " + name + " ***");
        System.out.println(" test flags");
        System.out.print("expected ");
        printFlags(expectedFlags);
        System.out.print("computed ");
        printFlags(computedFlags);
        System.out.print("  actual ");
        printFlags(actualFlags);

        if (expectedFlags != actualFlags) {
            throw new Error("layout flags in test: " + name +
                            " expected: " + Integer.toHexString(expectedFlags) +
                            " but got: " + Integer.toHexString(actualFlags));
        }
    }

    static public void printFlags(int flags) {
        System.out.print("flags:");
        if ((flags & GlyphVector.FLAG_HAS_POSITION_ADJUSTMENTS) != 0) {
            System.out.print(" pos");
        }
        if ((flags & GlyphVector.FLAG_HAS_TRANSFORMS) != 0) {
            System.out.print(" trans");
        }
        if ((flags & GlyphVector.FLAG_RUN_RTL) != 0) {
            System.out.print(" rtl");
        }
        if ((flags & GlyphVector.FLAG_COMPLEX_GLYPHS) != 0) {
            System.out.print(" complex");
        }
        if ((flags & GlyphVector.FLAG_MASK) == 0) {
            System.out.print(" none");
        }
        System.out.println();
    }

    int computeFlags(GlyphVector gv) {
        validateCharIndexMethods(gv);

        int result = 0;
        if (glyphsAreRTL(gv)) {
            result |= GlyphVector.FLAG_RUN_RTL;
        }
        if (hasComplexGlyphs(gv)) {
            result |= GlyphVector.FLAG_COMPLEX_GLYPHS;
        }

        return result;
    }

    /**
     * throw an exception if getGlyphCharIndices returns a different result than
     * you get from iterating through getGlyphCharIndex one at a time.
     */
    void validateCharIndexMethods(GlyphVector gv) {
        int[] indices = gv.getGlyphCharIndices(0, gv.getNumGlyphs(), null);
        for (int i = 0; i < gv.getNumGlyphs(); ++i) {
            if (gv.getGlyphCharIndex(i) != indices[i]) {
                throw new Error("glyph index mismatch at " + i);
            }
        }
    }

    /**
     * Return true if the glyph indices are pure ltr
     */
    boolean glyphsAreLTR(GlyphVector gv) {
        int[] indices = gv.getGlyphCharIndices(0, gv.getNumGlyphs(), null);
        for (int i = 0; i < indices.length; ++i) {
            if (indices[i] != i) {
                return false;
            }
        }
        return true;
    }

    /**
     * Return true if the glyph indices are pure rtl
     */
    boolean glyphsAreRTL(GlyphVector gv) {
        int[] indices = gv.getGlyphCharIndices(0, gv.getNumGlyphs(), null);
        for (int i = 0; i < indices.length; ++i) {
            if (indices[i] != indices.length - i - 1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Return true if there is a local reordering (the run is not ltr or rtl).
     * !!! We can't have mixed bidi runs in the glyphs.
     */
    boolean hasComplexGlyphs(GlyphVector gv) {
        return !glyphsAreLTR(gv) && !glyphsAreRTL(gv);
    }
}

/*
rect getPixelBounds(frc, x, y)
rect getGlyphPixelBounds(frc, int, x, y)
getGlyphOutline(int index, x, y)
getGlyphInfo()
*/
