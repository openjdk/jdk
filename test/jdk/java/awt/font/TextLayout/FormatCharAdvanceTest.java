/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8208377 6562489 8270265
 * @summary Confirm that format-category glyphs are not rendered or measured.
 */

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.text.AttributedString;
import java.util.Base64;
import java.util.Map;

public class FormatCharAdvanceTest {

    /**
     * <p>Font created for this test which contains glyphs for 0-9, a-z, A-Z, space, and most
     * characters with Unicode general category = Format (Cf); the tests will pass if these
     * format glyphs and their advances are ignored during text measurement and text drawing.
     *
     * <p>The following FontForge Python script was used to generate this font:
     *
     * <pre>
     * import fontforge
     * import base64
     *
     * font = fontforge.font()
     * font.encoding = 'UnicodeFull'
     * font.design_size = 16
     * font.em = 2048
     * font.ascent = 1638
     * font.descent = 410
     * font.familyname = 'Test'
     * font.fontname = 'Test'
     * font.fullname = 'Test'
     * font.copyright = ''
     * font.autoWidth(0, 0, 2048)
     *
     * space = font.createChar(0x20)
     * space.width = 569
     *
     * a = font.createChar(ord('a'))
     * pen = a.glyphPen()
     * pen.moveTo((100, 100))
     * pen.lineTo((100, 200))
     * pen.lineTo((500, 200))
     * pen.lineTo((500, 100))
     * pen.closePath()
     * a.draw(pen)
     * pen = None
     * a.width = 600
     *
     * chars = 'bcdefghijklmnopqrstuvwxyz' \
     *         'ABCDEFGHIJKLMNOPQRSTUVWXYZ' \
     *         '1234567890' \
     *         '\u00AD\u0600\u0601\u0602\u0603\u0604\u0605\u061C\u06DD\u070F\u0890\u0891\u08E2\u180E' \
     *         '\u200B\u200C\u200D\u200E\u200F\u202A\u202B\u202C\u202D\u202E\u2060\u2061\u2062\u2063\u2064' \
     *         '\u2066\u2067\u2068\u2069\u206A\u206B\u206C\u206D\u206E\u206F\uFEFF\uFFF9\uFFFA\uFFFB' \
     *         '\U000110BD\U000110CD\U00013430\U00013431\U00013432\U00013433\U00013434\U00013435\U00013436' \
     *         '\U00013437\U00013438\U00013439\U0001343A\U0001343B\U0001343C\U0001343D\U0001343E\U0001343F' \
     *         '\U0001BCA0\U0001BCA1\U0001BCA2\U0001BCA3\U0001D173\U0001D174\U0001D175\U0001D176\U0001D177' \
     *         '\U0001D178\U0001D179\U0001D17A\U000E0001\U000E0020\U000E0021\U000E007E\U000E007F'
     *
     * for char in set(chars):
     *   glyph = font.createChar(ord(char))
     *   glyph.addReference('a')
     *   glyph.useRefsMetrics('a')
     *
     * ttf = 'test.ttf'     # TrueType
     * t64 = 'test.ttf.txt' # TrueType Base64
     * pfb = 'test.pfb'     # PostScript Type1
     * p64 = 'test.pfb.txt' # PostScript Type1 Base64
     *
     * font.generate(ttf)
     * font.generate(pfb)
     *
     * with open(ttf, 'rb') as f1:
     *   encoded = base64.b64encode(f1.read())
     *   with open(t64, 'wb') as f2:
     *     f2.write(encoded)
     *
     * with open(pfb, 'rb') as f3:
     *   encoded = base64.b64encode(f3.read())
     *   with open(p64, 'wb') as f4:
     *     f4.write(encoded)
     * </pre>
     */
    private static final String TTF_BYTES = "AAEAAAANAIAAAwBQRkZUTaj5NMoAABWEAAAAHE9TLzKEBfqWAAABWAAAAGBjbWFw6BKIbQAAAuQAAAMoY3Z0IABEBREAAAYMAAAABGdhc3D//wADAAAVfAAAAAhnbHlmcb+r/AAABzQAAAksaGVhZCi9kzQAAADcAAAANmhoZWEIcgJeAAABFAAAACRobXR4JX8bnAAAAbgAAAEqbG9jYam4p4gAAAYQAAABIm1heHAA1wBCAAABOAAAACBuYW1lImUC5wAAEGAAAAGJcG9zdBd/2qEAABHsAAADjwABAAAAAQAAEAez8l8PPPUACwgAAAAAAON7pHEAAAAA43ukcQBEAAACZAVVAAAACAACAAAAAAAAAAEAAAVVAAAAuAJYAAAAAAJkAAEAAAAAAAAAAAAAAAAAAAAFAAEAAACQAAgAAgAIAAIAAgAAAAEAAQAAAEAALgABAAEABAJXAZAABQAABTMFmQAAAR4FMwWZAAAD1wBmAhIAAAIABQMAAAAAAACAACADAgAAABECAKgAAAAAUGZFZACAACD//wZm/mYAuAVVAAAAAAABAAAAAADIAMgAAAAgAAEC7ABEAAAAAAKqAAACOQAAAlgAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAAAAAAAFAAAAAwAAACwAAAAEAAAA2gABAAAAAAIiAAMAAQAAACwAAwAKAAAA2gAEAK4AAAAmACAABAAGACAAOQBaAHoArQYFBhwG3QcPCJEI4hgOIA8gLiBkIG/+///7//8AAAAgADAAQQBhAK0GAAYcBt0HDwiQCOIYDiALICogYCBm/v//+f///+P/1P/N/8f/lfpD+i35bfk897z3bOhB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ACgAAP//AGQAZAH0AMgSBgAoAAD//wBkAGQB9ADIEgYAKAAA//8AZABkAfQAyBIGACgAAP//AGQAZAH0AMgSBgAoAAD//wBkAGQB9ADIEgYAKAAA//8AZABkAfQAyBIGACgAAP//AGQAZAH0AMgSBgAoAAD//wBkAGQB9ADIEgYAKAAA//8AZABkAfQAyBIGACgAAP//AGQAZAH0AMgSBgAoAAD//wBkAGQB9ADIEgYAKAAA//8AZABkAfQAyBIGACgAAP//AGQAZAH0AMgSBgAoAAD//wBkAGQB9ADIEgYAKAAA//8AZABkAfQAyBIGACgAAP//AGQAZAH0AMgSBgAoAAD//wBkAGQB9ADIEgYAKAAA//8AZABkAfQAyBIGACgAAP//AGQAZAH0AMgSBgAoAAD//wBkAGQB9ADIEgYAKAAA//8AZABkAfQAyBIGACgAAP//AGQAZAH0AMgSBgAoAAD//wBkAGQB9ADIEgYAKAAA//8AZABkAfQAyBIGACgAAP//AGQAZAH0AMgSBgAoAAD//wBkAGQB9ADIEgYAKAAA//8AZABkAfQAyBIGACgAAP//AGQAZAH0AMgSBgAoAAD//wBkAGQB9ADIEgYAKAAA//8AZABkAfQAyBIGACgAAP//AGQAZAH0AMgSBgAoAAD//wBkAGQB9ADIEgYAKAAA//8AZABkAfQAyBIGACgAAP//AGQAZAH0AMgSBgAoAAD//wBkAGQB9ADIEgYAKAAA//8AZABkAfQAyBIGACgAAP//AGQAZAH0AMgSBgAoAAD//wBkAGQB9ADIEgYAKAAA//8AZABkAfQAyBIGACgAAP//AGQAZAH0AMgSBgAoAAD//wBkAGQB9ADIEgYAKAAA//8AZABkAfQAyBIGACgAAP//AGQAZAH0AMgSBgAoAAD//wBkAGQB9ADIEgYAKAAA//8AZABkAfQAyBIGACgAAP//AGQAZAH0AMgSBgAoAAD//wBkAGQB9ADIEgYAKAAA//8AZABkAfQAyBIGACgAAP//AGQAZAH0AMgSBgAoAAD//wBkAGQB9ADIEgYAKAAA//8AZABkAfQAyBIGACgAAP//AGQAZAH0AMgSBgAoAAD//wBkAGQB9ADIEgYAKAAA//8AZABkAfQAyBIGACgAAP//AGQAZAH0AMgSBgAoAAD//wBkAGQB9ADIEgYAKAAA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";

    /**
     * Same font as above, but in PostScript Type1 (PFB) format.
     */
    private static final String TYPE1_BYTES = "gAHhBgAAJSFQUy1BZG9iZUZvbnQtMS4wOiBUZXN0IDAwMS4wMDAKJSVUaXRsZTogVGVzdAolVmVyc2lvbjogMDAxLjAwMAolJUNyZWF0aW9uRGF0ZTogVHVlIERlYyAxMCAwMDo0Mzo1MCAyMDI0CiUlQ3JlYXRvcjogRGFuaWVsIEdyZWRsZXIKJSAyMDI0LTEyLTEwOiBDcmVhdGVkIHdpdGggRm9udEZvcmdlIChodHRwOi8vZm9udGZvcmdlLm9yZykKJSBHZW5lcmF0ZWQgYnkgRm9udEZvcmdlIDIwMjMwMTAxIChodHRwOi8vZm9udGZvcmdlLnNmLm5ldC8pCiUlRW5kQ29tbWVudHMKCjEwIGRpY3QgYmVnaW4KL0ZvbnRUeXBlIDEgZGVmCi9Gb250TWF0cml4IFswLjAwMDQ4ODI4MSAwIDAgMC4wMDA0ODgyODEgMCAwIF1yZWFkb25seSBkZWYKL0ZvbnROYW1lIC9UZXN0IGRlZgovRm9udEJCb3ggezEwMCAxMDAgNTAwIDIwMCB9cmVhZG9ubHkgZGVmCi9QYWludFR5cGUgMCBkZWYKL0ZvbnRJbmZvIDEwIGRpY3QgZHVwIGJlZ2luCiAvdmVyc2lvbiAoMDAxLjAwMCkgcmVhZG9ubHkgZGVmCiAvTm90aWNlICgpIHJlYWRvbmx5IGRlZgogL0Z1bGxOYW1lIChUZXN0KSByZWFkb25seSBkZWYKIC9GYW1pbHlOYW1lIChUZXN0KSByZWFkb25seSBkZWYKIC9XZWlnaHQgKFJlZ3VsYXIpIHJlYWRvbmx5IGRlZgogL0ZTVHlwZSAwIGRlZgogL0l0YWxpY0FuZ2xlIDAgZGVmCiAvaXNGaXhlZFBpdGNoIGZhbHNlIGRlZgogL1VuZGVybGluZVBvc2l0aW9uIC0yMDQuOCBkZWYKIC9VbmRlcmxpbmVUaGlja25lc3MgMTAyLjQgZGVmCmVuZCByZWFkb25seSBkZWYKL0VuY29kaW5nIDI1NiBhcnJheQogMCAxIDI1NSB7IDEgaW5kZXggZXhjaCAvLm5vdGRlZiBwdXR9IGZvcgpkdXAgMzIvc3BhY2UgcHV0CmR1cCA0OC96ZXJvIHB1dApkdXAgNDkvb25lIHB1dApkdXAgNTAvdHdvIHB1dApkdXAgNTEvdGhyZWUgcHV0CmR1cCA1Mi9mb3VyIHB1dApkdXAgNTMvZml2ZSBwdXQKZHVwIDU0L3NpeCBwdXQKZHVwIDU1L3NldmVuIHB1dApkdXAgNTYvZWlnaHQgcHV0CmR1cCA1Ny9uaW5lIHB1dApkdXAgNjUvQSBwdXQKZHVwIDY2L0IgcHV0CmR1cCA2Ny9DIHB1dApkdXAgNjgvRCBwdXQKZHVwIDY5L0UgcHV0CmR1cCA3MC9GIHB1dApkdXAgNzEvRyBwdXQKZHVwIDcyL0ggcHV0CmR1cCA3My9JIHB1dApkdXAgNzQvSiBwdXQKZHVwIDc1L0sgcHV0CmR1cCA3Ni9MIHB1dApkdXAgNzcvTSBwdXQKZHVwIDc4L04gcHV0CmR1cCA3OS9PIHB1dApkdXAgODAvUCBwdXQKZHVwIDgxL1EgcHV0CmR1cCA4Mi9SIHB1dApkdXAgODMvUyBwdXQKZHVwIDg0L1QgcHV0CmR1cCA4NS9VIHB1dApkdXAgODYvViBwdXQKZHVwIDg3L1cgcHV0CmR1cCA4OC9YIHB1dApkdXAgODkvWSBwdXQKZHVwIDkwL1ogcHV0CmR1cCA5Ny9hIHB1dApkdXAgOTgvYiBwdXQKZHVwIDk5L2MgcHV0CmR1cCAxMDAvZCBwdXQKZHVwIDEwMS9lIHB1dApkdXAgMTAyL2YgcHV0CmR1cCAxMDMvZyBwdXQKZHVwIDEwNC9oIHB1dApkdXAgMTA1L2kgcHV0CmR1cCAxMDYvaiBwdXQKZHVwIDEwNy9rIHB1dApkdXAgMTA4L2wgcHV0CmR1cCAxMDkvbSBwdXQKZHVwIDExMC9uIHB1dApkdXAgMTExL28gcHV0CmR1cCAxMTIvcCBwdXQKZHVwIDExMy9xIHB1dApkdXAgMTE0L3IgcHV0CmR1cCAxMTUvcyBwdXQKZHVwIDExNi90IHB1dApkdXAgMTE3L3UgcHV0CmR1cCAxMTgvdiBwdXQKZHVwIDExOS93IHB1dApkdXAgMTIwL3ggcHV0CmR1cCAxMjEveSBwdXQKZHVwIDEyMi96IHB1dApkdXAgMTczL3VuaTAwQUQgcHV0CnJlYWRvbmx5IGRlZgpjdXJyZW50ZGljdCBlbmQKY3VycmVudGZpbGUgZWV4ZWMKgAI5FQAAdD+EE/NjbKhan/77ULS7JzAqXMCrbi+Vm/IN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LFsw/C/HgAEVAgAACjAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAKMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMAowMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwCjAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAKMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMAowMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwCjAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAKMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMApjbGVhcnRvbWFyawqAAw==";

    public static void main(String[] args) throws Exception {

        BufferedImage image = new BufferedImage(600, 600, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g2d = image.createGraphics();

        byte[] type1Bytes = Base64.getDecoder().decode(TYPE1_BYTES);
        ByteArrayInputStream type1Stream = new ByteArrayInputStream(type1Bytes);
        Font type1Font = Font.createFont(Font.TYPE1_FONT, type1Stream).deriveFont(80f);
        testChars(image, g2d, type1Font);

        byte[] ttfBytes = Base64.getDecoder().decode(TTF_BYTES);
        ByteArrayInputStream ttfStream = new ByteArrayInputStream(ttfBytes);
        Font ttf = Font.createFont(Font.TRUETYPE_FONT, ttfStream).deriveFont(80f);
        testChars(image, g2d, ttf);

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        testChars(image, g2d, ttf);

        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        testChars(image, g2d, ttf);

        Font kerningFont = ttf.deriveFont(Map.of(TextAttribute.KERNING, TextAttribute.KERNING_ON));
        testChars(image, g2d, kerningFont);

        Font dialogFont = new Font(Font.DIALOG, Font.PLAIN, 80);
        testChars(image, g2d, dialogFont);

        Font physicalFont = getPhysicalFont(80);
        if (physicalFont != null) {
            testChars(image, g2d, physicalFont);
        }

        g2d.dispose();
    }

    private static void testChars(BufferedImage image, Graphics2D g2d, Font font) {
        testChar('\u00AD', image, g2d, font); // soft hyphen (SHY)
        testChar('\u200B', image, g2d, font); // zero width space (ZWSP)
        testChar('\u200C', image, g2d, font); // zero width non-joiner (ZWNJ)
        testChar('\u200D', image, g2d, font); // zero width joiner (ZWJ)
        testChar('\u200E', image, g2d, font); // left-to-right mark (LRM)
        testChar('\u200F', image, g2d, font); // right-to-left mark (RLM)
        testChar('\u202A', image, g2d, font); // left-to-right embedding (LRE)
        testChar('\u202B', image, g2d, font); // right-to-left embedding (RLE)
        testChar('\u202C', image, g2d, font); // pop directional formatting (PDF)
        testChar('\u202D', image, g2d, font); // left-to-right override (LRO)
        testChar('\u202E', image, g2d, font); // right-to-left override (RLO)
        testChar('\u2060', image, g2d, font); // word joiner (WJ)
        testChar('\u2061', image, g2d, font); // function application
        testChar('\u2062', image, g2d, font); // invisible times
        testChar('\u2063', image, g2d, font); // invisible separator
        testChar('\u2066', image, g2d, font); // left-to-right isolate (LRI)
        testChar('\u2067', image, g2d, font); // right-to-left isolate (RLI)
        testChar('\u2068', image, g2d, font); // first strong isolate (FSI)
        testChar('\u2069', image, g2d, font); // pop directional isolate (PDI)
        testChar('\uFEFF', image, g2d, font); // zero width no-break space (ZWNBSP/BOM)
    }

    private static void testChar(char c, BufferedImage image, Graphics2D g2d, Font font) {

        g2d.setFont(font);
        int w = image.getWidth();
        int h = image.getHeight();
        FontRenderContext frc = g2d.getFontRenderContext();
        FontMetrics metrics = g2d.getFontMetrics(font);
        String c5 = String.valueOf(c).repeat(5);

        int ab1 = metrics.stringWidth("AB");
        int ab2 = metrics.stringWidth("A" + c5 + "B");
        assertEqual(ab1, ab2, "stringWidth", c, font);

        ab1 = (int) font.getStringBounds("AB", frc).getWidth();
        ab2 = (int) font.getStringBounds("A" + c5 + "B", frc).getWidth();
        assertEqual(ab1, ab2, "getStringBounds", c, font);

        GlyphVector gv1 = font.createGlyphVector(frc, "AB");
        GlyphVector gv2 = font.createGlyphVector(frc, "A" + c5 + "B");
        ab1 = gv1.getPixelBounds(frc, 0, 0).width;
        ab2 = gv2.getPixelBounds(frc, 0, 0).width;
        assertEqual(ab1, ab2, "getPixelBounds", c, font);
        assertEqual(0, gv2.getGlyphPixelBounds(1, frc, 0, 0).width, "getGlyphPixelBounds", c, font);
        assertEqual(0d, gv2.getGlyphVisualBounds(1).getBounds2D().getWidth(), "getGlyphVisualBounds", c, font);
        assertEqual(0d, gv2.getGlyphLogicalBounds(1).getBounds2D().getWidth(), "getGlyphLogicalBounds", c, font);
        assertEqual(0d, gv2.getGlyphOutline(1).getBounds2D().getWidth(), "getGlyphOutline", c, font);
        assertEqual(0d, gv2.getGlyphMetrics(1).getAdvance(), "getGlyphMetrics", c, font);

        ab1 = (int) gv1.getLogicalBounds().getWidth();
        ab2 = (int) gv2.getLogicalBounds().getWidth();
        assertEqual(ab1, ab2, "getLogicalBounds", c, font);

        ab1 = (int) gv1.getVisualBounds().getWidth();
        ab2 = (int) gv2.getVisualBounds().getWidth();
        assertEqual(ab1, ab2, "getVisualBounds", c, font);

        TextLayout layout1 = new TextLayout("AB", font, frc);
        TextLayout layout2 = new TextLayout("A" + c5 + "B", font, frc);
        ab1 = (int) layout1.getAdvance();
        ab2 = (int) layout2.getAdvance();
        assertEqual(ab1, ab2, "getAdvance", c, font);

        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, w, h);
        g2d.setColor(Color.BLACK);
        g2d.drawString("AB", w / 2, h / 2);
        ab1 = findTextBoundingBox(image).width;
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, w, h);
        g2d.setColor(Color.BLACK);
        g2d.drawString("A" + c5 + "B", w / 2, h / 2);
        ab2 = findTextBoundingBox(image).width;
        assertEqual(ab1, ab2, "drawString (using String)", c, font);

        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, w, h);
        g2d.setColor(Color.BLACK);
        g2d.drawChars("AB".toCharArray(), 0, 2, w / 2, h / 2);
        ab1 = findTextBoundingBox(image).width;
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, w, h);
        g2d.setColor(Color.BLACK);
        g2d.drawChars(("A" + c5 + "B").toCharArray(), 0, 7, w / 2, h / 2);
        ab2 = findTextBoundingBox(image).width;
        assertEqual(ab1, ab2, "drawChars", c, font);

        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, w, h);
        g2d.setColor(Color.BLACK);
        g2d.drawGlyphVector(gv1, w / 2, h / 2);
        ab1 = findTextBoundingBox(image).width;
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, w, h);
        g2d.setColor(Color.BLACK);
        g2d.drawGlyphVector(gv2, w / 2, h / 2);
        ab2 = findTextBoundingBox(image).width;
        assertEqual(ab1, ab2, "drawGlyphVector", c, font);

        AttributedString as1 = new AttributedString("AB", Map.of(TextAttribute.FONT, font));
        AttributedString as2 = new AttributedString("A" + c5 + "B", Map.of(TextAttribute.FONT, font));
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, w, h);
        g2d.setColor(Color.BLACK);
        g2d.drawString(as1.getIterator(), w / 2, h / 2);
        ab1 = findTextBoundingBox(image).width;
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, w, h);
        g2d.setColor(Color.BLACK);
        g2d.drawString(as2.getIterator(), w / 2, h / 2);
        ab2 = findTextBoundingBox(image).width;
        assertEqual(ab1, ab2, "drawString (using AttributedCharacterIterator)", c, font);

        int max = metrics.stringWidth("AB") + 2; // add a little wiggle room to the max width
        LineBreakMeasurer measurer1 = new LineBreakMeasurer(as1.getIterator(), frc);
        LineBreakMeasurer measurer2 = new LineBreakMeasurer(as2.getIterator(), frc);
        assertEqual(2, measurer1.nextOffset(max), "nextOffset 1", c, font);
        assertEqual(7, measurer2.nextOffset(max), "nextOffset 2", c, font);
    }

    private static void assertEqual(int i1, int i2, String scenario, char c, Font font) {
        if (i1 != i2) {
            String msg = String.format("%s for char %04x using font %s: %d != %d", scenario, (int) c, font.getName(), i1, i2);
            throw new RuntimeException(msg);
        }
    }

    private static void assertEqual(double d1, double d2, String scenario, char c, Font font) {
        if (d1 != d2) {
            String msg = String.format("%s for char %04x using font %s: %f != %f", scenario, (int) c, font.getName(), d1, d2);
            throw new RuntimeException(msg);
        }
    }

    private static Font getPhysicalFont(int size) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] names = ge.getAvailableFontFamilyNames();
        for (String n : names) {
            switch (n) {
                case Font.DIALOG:
                case Font.DIALOG_INPUT:
                case Font.SERIF:
                case Font.SANS_SERIF:
                case Font.MONOSPACED:
                     continue;
                default:
                    Font f = new Font(n, Font.PLAIN, size);
                    if (f.canDisplayUpTo("AZaz09") == -1) {
                        return f;
                    }
            }
        }
        return null;
    }

    private static Rectangle findTextBoundingBox(BufferedImage image) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int width = image.getWidth();
        int height = image.getHeight();
        int[] rowPixels = new int[width];
        for (int y = 0; y < height; y++) {
            image.getRGB(0, y, width, 1, rowPixels, 0, width);
            for (int x = 0; x < width; x++) {
                boolean white = (rowPixels[x] == -1);
                if (!white) {
                    if (x < minX) {
                        minX = x;
                    }
                    if (y < minY) {
                        minY = y;
                    }
                    if (x > maxX) {
                        maxX = x;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                }
            }
        }
        if (minX != Integer.MAX_VALUE) {
            return new Rectangle(minX, minY, maxX - minX, maxY - minY);
        } else {
            return null;
        }
    }
}
