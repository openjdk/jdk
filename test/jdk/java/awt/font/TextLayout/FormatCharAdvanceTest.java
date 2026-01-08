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

/*
 * @test
 * @bug 8208377 6562489 8270265 8356803 8356812 4517298
 * @summary Confirm that default-ignorable and ignorable-whitespace
 *          glyphs are not rendered or measured.
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
     * <p>Font created for this test which contains glyphs for 0-9, a-z, A-Z,
     * some whitespace characters, and most characters with Unicode "Format"
     * (Cf) general category; the tests will pass if the font glyphs and
     * advances for default-ignorable and ignorable-whitespace characters
     * are ignored during text measurement and text drawing.
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
     *         '\u0009\u000A\u000B\u000C\u000D\u0085\u00AD' \
     *         '\u0600\u0601\u0602\u0603\u0604\u0605\u061C\u06DD\u070F\u0890\u0891\u08E2\u180E' \
     *         '\u200B\u200C\u200D\u200E\u200F\u2028\u2029\u202A\u202B\u202C\u202D\u202E\u202F' \
     *         '\u2060\u2061\u2062\u2063\u2064\u2065\u2066\u2067\u2068\u2069\u206A\u206B\u206C' \
     *         '\u206D\u206E\u206F\uFEFF\uFFF9\uFFFA\uFFFB' \
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
    private static final String TTF_BYTES = "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cAGQZGRkZGT//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAAAAAAOAK4AAQAAAAAAAAAAAAIAAQAAAAAAAQAEAA0AAQAAAAAAAgAHACIAAQAAAAAAAwAgAGwAAQAAAAAABAAEAJcAAQAAAAAABQAPALwAAQAAAAAABgAEANYAAwABBAkAAAAAAAAAAwABBAkAAQAIAAMAAwABBAkAAgAOABIAAwABBAkAAwBAACoAAwABBAkABAAIAI0AAwABBAkABQAeAJwAAwABBAkABgAIAMwAAAAAVABlAHMAdAAAVGVzdAAAUgBlAGcAdQBsAGEAcgAAUmVndWxhcgAARgBvAG4AdABGAG8AcgBnAGUAIAAyAC4AMAAgADoAIABUAGUAcwB0ACAAOgAgADMAMAAtADUALQAyADAAMgA1AABGb250Rm9yZ2UgMi4wIDogVGVzdCA6IDMwLTUtMjAyNQAAVABlAHMAdAAAVGVzdAAAVgBlAHIAcwBpAG8AbgAgADAAMAAxAC4AMAAwADAAAFZlcnNpb24gMDAxLjAwMAAAVABlAHMAdAAAVGVzdAAAAAAAAgAAAAAAAP9nAGYAAAAAAAAAAAAAAAAAAAAAAAAAAACZAAAAAQECAQMBBAEFAQYAAwATABQAFQAWABcAGAAZABoAGwAcACQAJQAmACcAKAApACoAKwAsAC0ALgAvADAAMQAyADMANAA1ADYANwA4ADkAOgA7ADwAPQBEAEUARgBHAEgASQBKAEsATABNAE4ATwBQAFEAUgBTAFQAVQBWAFcAWABZAFoAWwBcAF0BBwEIAQkBCgELAQwBDQEOAQ8BEAERARIBEwEUARUBFgEXARgBGQEaARsBHAEdAR4BHwEgASEBIgEjASQBJQEmAScBKAEpASoBKwEsAS0BLgEvATABMQEyATMBNAE1ATYBNwE4ATkBOgE7ATwBPQE+AT8BQAFBAUIBQwFEAUUBRgFHAUgBSQFKAUsBTAFNAU4BTwFQAVEBUgFTAVQBVQFWAVcBWAFZB3VuaTAwMEQHdW5pMDAwOQd1bmkwMDBBB3VuaTAwMEIHdW5pMDAwQwd1bmkwMDg1B3VuaTAwQUQHdW5pMDYwMAd1bmkwNjAxB3VuaTA2MDIHdW5pMDYwMwd1bmkwNjA0B3VuaTA2MDUHdW5pMDYxQwd1bmkwNkREB3VuaTA3MEYHdW5pMDg5MAd1bmkwODkxB3VuaTA4RTIHdW5pMTgwRQd1bmkyMDBCB3VuaTIwMEMHdW5pMjAwRAd1bmkyMDBFB3VuaTIwMEYHdW5pMjAyOAd1bmkyMDI5B3VuaTIwMkEHdW5pMjAyQgd1bmkyMDJDB3VuaTIwMkQHdW5pMjAyRQd1bmkyMDJGB3VuaTIwNjAHdW5pMjA2MQd1bmkyMDYyB3VuaTIwNjMHdW5pMjA2NAd1bmkyMDY1B3VuaTIwNjYHdW5pMjA2Nwd1bmkyMDY4B3VuaTIwNjkHdW5pMjA2QQd1bmkyMDZCB3VuaTIwNkMHdW5pMjA2RAd1bmkyMDZFB3VuaTIwNkYHdW5pRkVGRgd1bmlGRkY5B3VuaUZGRkEHdW5pRkZGQgZ1MTEwQkQGdTExMENEBnUxMzQzMAZ1MTM0MzEGdTEzNDMyBnUxMzQzMwZ1MTM0MzQGdTEzNDM1BnUxMzQzNgZ1MTM0MzcGdTEzNDM4BnUxMzQzOQZ1MTM0M0EGdTEzNDNCBnUxMzQzQwZ1MTM0M0QGdTEzNDNFBnUxMzQzRgZ1MUJDQTAGdTFCQ0ExBnUxQkNBMgZ1MUJDQTMGdTFEMTczBnUxRDE3NAZ1MUQxNzUGdTFEMTc2BnUxRDE3NwZ1MUQxNzgGdTFEMTc5BnUxRDE3QQZ1RTAwMDEGdUUwMDIwBnVFMDAyMQZ1RTAwN0UGdUUwMDdGAAAAAAAAAf//AAIAAAABAAAAAOIB6+cAAAAA5F+vtAAAAADkX6+0";

    /**
     * Same font as above, but in PostScript Type1 (PFB) format.
     */
    private static final String TYPE1_BYTES = "gAFSBwAAJSFQUy1BZG9iZUZvbnQtMS4wOiBUZXN0IDAwMS4wMDAKJSVUaXRsZTogVGVzdAolVmVyc2lvbjogMDAxLjAwMAolJUNyZWF0aW9uRGF0ZTogRnJpIE1heSAzMCAyMDo1NTo0OCAyMDI1CiUlQ3JlYXRvcjogRGFuaWVsIEdyZWRsZXIKJSAyMDI1LTUtMzA6IENyZWF0ZWQgd2l0aCBGb250Rm9yZ2UgKGh0dHA6Ly9mb250Zm9yZ2Uub3JnKQolIEdlbmVyYXRlZCBieSBGb250Rm9yZ2UgMjAyMzAxMDEgKGh0dHA6Ly9mb250Zm9yZ2Uuc2YubmV0LykKJSVFbmRDb21tZW50cwoKMTAgZGljdCBiZWdpbgovRm9udFR5cGUgMSBkZWYKL0ZvbnRNYXRyaXggWzAuMDAwNDg4MjgxIDAgMCAwLjAwMDQ4ODI4MSAwIDAgXXJlYWRvbmx5IGRlZgovRm9udE5hbWUgL1Rlc3QgZGVmCi9Gb250QkJveCB7MTAwIDEwMCA1MDAgMjAwIH1yZWFkb25seSBkZWYKL1BhaW50VHlwZSAwIGRlZgovRm9udEluZm8gMTAgZGljdCBkdXAgYmVnaW4KIC92ZXJzaW9uICgwMDEuMDAwKSByZWFkb25seSBkZWYKIC9Ob3RpY2UgKCkgcmVhZG9ubHkgZGVmCiAvRnVsbE5hbWUgKFRlc3QpIHJlYWRvbmx5IGRlZgogL0ZhbWlseU5hbWUgKFRlc3QpIHJlYWRvbmx5IGRlZgogL1dlaWdodCAoUmVndWxhcikgcmVhZG9ubHkgZGVmCiAvRlNUeXBlIDAgZGVmCiAvSXRhbGljQW5nbGUgMCBkZWYKIC9pc0ZpeGVkUGl0Y2ggZmFsc2UgZGVmCiAvVW5kZXJsaW5lUG9zaXRpb24gLTIwNC44IGRlZgogL1VuZGVybGluZVRoaWNrbmVzcyAxMDIuNCBkZWYKZW5kIHJlYWRvbmx5IGRlZgovRW5jb2RpbmcgMjU2IGFycmF5CiAwIDEgMjU1IHsgMSBpbmRleCBleGNoIC8ubm90ZGVmIHB1dH0gZm9yCmR1cCA5L3VuaTAwMDkgcHV0CmR1cCAxMC91bmkwMDBBIHB1dApkdXAgMTEvdW5pMDAwQiBwdXQKZHVwIDEyL3VuaTAwMEMgcHV0CmR1cCAxMy91bmkwMDBEIHB1dApkdXAgMzIvc3BhY2UgcHV0CmR1cCA0OC96ZXJvIHB1dApkdXAgNDkvb25lIHB1dApkdXAgNTAvdHdvIHB1dApkdXAgNTEvdGhyZWUgcHV0CmR1cCA1Mi9mb3VyIHB1dApkdXAgNTMvZml2ZSBwdXQKZHVwIDU0L3NpeCBwdXQKZHVwIDU1L3NldmVuIHB1dApkdXAgNTYvZWlnaHQgcHV0CmR1cCA1Ny9uaW5lIHB1dApkdXAgNjUvQSBwdXQKZHVwIDY2L0IgcHV0CmR1cCA2Ny9DIHB1dApkdXAgNjgvRCBwdXQKZHVwIDY5L0UgcHV0CmR1cCA3MC9GIHB1dApkdXAgNzEvRyBwdXQKZHVwIDcyL0ggcHV0CmR1cCA3My9JIHB1dApkdXAgNzQvSiBwdXQKZHVwIDc1L0sgcHV0CmR1cCA3Ni9MIHB1dApkdXAgNzcvTSBwdXQKZHVwIDc4L04gcHV0CmR1cCA3OS9PIHB1dApkdXAgODAvUCBwdXQKZHVwIDgxL1EgcHV0CmR1cCA4Mi9SIHB1dApkdXAgODMvUyBwdXQKZHVwIDg0L1QgcHV0CmR1cCA4NS9VIHB1dApkdXAgODYvViBwdXQKZHVwIDg3L1cgcHV0CmR1cCA4OC9YIHB1dApkdXAgODkvWSBwdXQKZHVwIDkwL1ogcHV0CmR1cCA5Ny9hIHB1dApkdXAgOTgvYiBwdXQKZHVwIDk5L2MgcHV0CmR1cCAxMDAvZCBwdXQKZHVwIDEwMS9lIHB1dApkdXAgMTAyL2YgcHV0CmR1cCAxMDMvZyBwdXQKZHVwIDEwNC9oIHB1dApkdXAgMTA1L2kgcHV0CmR1cCAxMDYvaiBwdXQKZHVwIDEwNy9rIHB1dApkdXAgMTA4L2wgcHV0CmR1cCAxMDkvbSBwdXQKZHVwIDExMC9uIHB1dApkdXAgMTExL28gcHV0CmR1cCAxMTIvcCBwdXQKZHVwIDExMy9xIHB1dApkdXAgMTE0L3IgcHV0CmR1cCAxMTUvcyBwdXQKZHVwIDExNi90IHB1dApkdXAgMTE3L3UgcHV0CmR1cCAxMTgvdiBwdXQKZHVwIDExOS93IHB1dApkdXAgMTIwL3ggcHV0CmR1cCAxMjEveSBwdXQKZHVwIDEyMi96IHB1dApkdXAgMTMz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";

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
        testChar('\t',     image, g2d, font); // horizontal tab (TAB)
        testChar('\n',     image, g2d, font); // line feed (LF)
        testChar('\u000B', image, g2d, font); // vertical tab (VT)
        testChar('\u000C', image, g2d, font); // form feed (FF)
        testChar('\r',     image, g2d, font); // carriage return (CR)
        testChar('\u0085', image, g2d, font); // next line (NEL)
        testChar('\u00AD', image, g2d, font); // soft hyphen (SHY)
        testChar('\u200B', image, g2d, font); // zero width space (ZWSP)
        testChar('\u200C', image, g2d, font); // zero width non-joiner (ZWNJ)
        testChar('\u200D', image, g2d, font); // zero width joiner (ZWJ)
        testChar('\u200E', image, g2d, font); // left-to-right mark (LRM)
        testChar('\u200F', image, g2d, font); // right-to-left mark (RLM)
        testChar('\u2028', image, g2d, font); // line separator (LS)
        testChar('\u2029', image, g2d, font); // paragraph separator (PS)
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
        testChar('\u206A', image, g2d, font); // inhibit symmetric swapping
        testChar('\u206B', image, g2d, font); // activate symmetric swapping
        testChar('\u206C', image, g2d, font); // inhibit arabic form shaping
        testChar('\u206D', image, g2d, font); // activate arabic form shaping
        testChar('\u206E', image, g2d, font); // national digit shapes
        testChar('\u206F', image, g2d, font); // nominal digit shapes
        testChar('\uFEFF', image, g2d, font); // zero width no-break space (ZWNBSP/BOM)
    }

    private static void testChar(char c, BufferedImage image, Graphics2D g2d, Font font) {

        g2d.setFont(font);
        int w = image.getWidth();
        int h = image.getHeight();
        FontRenderContext frc = g2d.getFontRenderContext();
        FontMetrics metrics = g2d.getFontMetrics(font);
        assertEqual(0, metrics.charWidth(c), "charWidth", c, font);

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
        layout1.draw(g2d, w / 2, h / 2);
        ab1 = findTextBoundingBox(image).width;
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, w, h);
        g2d.setColor(Color.BLACK);
        layout2.draw(g2d, w / 2, h / 2);
        ab2 = findTextBoundingBox(image).width;
        assertEqual(ab1, ab2, "TextLayout.draw", c, font);

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
