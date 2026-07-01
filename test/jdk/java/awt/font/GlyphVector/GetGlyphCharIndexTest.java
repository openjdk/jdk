/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test getGlyphCharIndex() results from layout
 * @bug 8152680 8361381
 */

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;

public class GetGlyphCharIndexTest {
    public static void main(String[] args) {
        Font font = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        FontRenderContext frc = new FontRenderContext(null, false, false);
        GlyphVector gv = font.layoutGlyphVector(frc, "abc".toCharArray(), 1, 3,
                                                Font.LAYOUT_LEFT_TO_RIGHT);
        int idx0 = gv.getGlyphCharIndex(0);
        if (idx0 != 0) {
           throw new RuntimeException("Expected 0, got " + idx0);
        }

        // This is the encoding-independent Khmer string "បានស្នើសុំនៅតែត្រូវបានបដិសេធ"
        // We can't check for more details like e.g. correct line breaking because it is font and platform dependent,
        // but we can at least chack that the created GlyphVector has monotonically increasing character indices.
        // This is guaranteed by HarfBuzz's HB_BUFFER_CLUSTER_LEVEL_MONOTONE_CHARACTERS cluster level which is used
        // in the OpenJDK layout implementation.
        String khmer = "\u1794\u17b6\u1793\u179f\u17d2\u1793\u17be\u179f\u17bb\u17c6\u1793\u17c5" +
                "\u178f\u17c2\u178f\u17d2\u179a\u17bc\u179c\u1794\u17b6\u1793\u1794\u178a\u17b7\u179f\u17c1\u1792";
        font = new Font(Font.DIALOG, Font.PLAIN, 12);
        gv = font.layoutGlyphVector(frc, khmer.toCharArray(), 0, khmer.length(), 0);
        int[] indices = gv.getGlyphCharIndices(0, gv.getNumGlyphs(), null);
        for (int i = 0; i < (indices.length - 1); i++) {
            if (indices[i] > indices[i + 1]) {
                throw new RuntimeException("Glyph character indices are supposed to be monotonically growing, but character index at position " +
                        i + " is bigger then the one at position " + (i + 1) + ", i.e. " + indices[i] + " > " + indices[i + 1] + ".");
            }
        }
    }
}
