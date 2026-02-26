/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
     * font.familyname = 'TTFTest'
     * font.fontname = 'TTFTest'
     * font.fullname = 'TTFTest'
     * #Use below values for Type 1 font
     * #font.familyname = 'Type1Test'
     * #font.fontname = 'Type1Test'
     * #font.fullname = 'Type1Test'
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
     * #Use commented lines to generate Type1 font
     * #pfb = 'test.pfb'     # PostScript Type1
     * #p64 = 'test.pfb.txt' # PostScript Type1 Base64
     *
     * font.generate(ttf)
     * #font.generate(pfb)
     *
     * with open(ttf, 'rb') as f1:
     *   encoded = base64.b64encode(f1.read())
     *   with open(t64, 'wb') as f2:
     *     f2.write(encoded)
     *
     * #with open(pfb, 'rb') as f3:
     *   #encoded = base64.b64encode(f3.read())
     *   #with open(p64, 'wb') as f4:
     *     #f4.write(encoded)
     * </pre>
     */
    private static final String TTF_BYTES = "AAEAAAANAIAAAwBQRkZUTbCUBjwAABcAAAAAHE9TLzKD7vqWAAABWAAAAGBjbWFw11zF/AAAAvwAAANSY3Z0IABEBREAAAZQAAAABGdhc3D//wADAAAW+AAAAAhnbHlmgVJ3qAAAB4gAAAnMaGVhZC1MmToAAADcAAAANmhoZWEIcgJiAAABFAAAACRobXR4L1UevAAAAbgAAAFEbG9jYb8EwZoAAAZUAAABNG1heHAA4ABCAAABOAAAACBuYW1lLzI4NgAAEVQAAAGtcG9zdBSfZd0AABMEAAAD8QABAAAAAQAAp/gvll8PPPUACwgAAAAAAOXDJ3QAAAAA5cMndABEAAACZAVVAAAACAACAAAAAAAAAAEAAAVVAAAAuAJYAAAAAAJkAAEAAAAAAAAAAAAAAAAAAAAJAAEAAACZAAgAAgAIAAIAAgAAAAEAAQAAAEAALgABAAEABAJXAZAABQAABTMFmQAAAR4FMwWZAAAD1wBmAhIAAAIABQMAAAAAAACAACADAgAAABECAKgAAAAAUGZFZACAAAn//wZm/mYAuAVVAAAAAAABAAAAAADIAMgAAAAgAAEC7ABEAAAAAAJYAGQCWABkAlgAZAJYAGQCWABkAjkAAAJYAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAZABkAGQAAAAFAAAAAwAAACwAAAAEAAAA7AABAAAAAAJMAAMAAQAAACwAAwAKAAAA7AAEAMAAAAAoACAABAAIAA0AIAA5AFoAegCFAK0GBQYcBt0HDwiRCOIYDiAPIC8gb/7///v//wAAAAkAIAAwAEEAYQCFAK0GAAYcBt0HDwiQCOIYDiALICggYP7///n//wAA/+f/2P/R/8v/wf+a+kj6Mvly+UH3wfdx6EbgSuAy4AIBcwAAAAEAKAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADgAAAAMABAAFAAYAAgBzAHQAdQAMAAAAAAFgAAAAAAAAABwAAAAJAAAADAAAAAMAAAANAAAADQAAAAIAAAAgAAAAIAAAAAcAAAAwAAAAOQAAAAgAAABBAAAAWgAAABIAAABhAAAAegAAACwAAACFAAAAhQAAAEYAAACtAAAArQAAAEcAAAYAAAAGBQAAAEgAAAYcAAAGHAAAAE4AAAbdAAAG3QAAAE8AAAcPAAAHDwAAAFAAAAiQAAAIkQAAAFEAAAjiAAAI4gAAAFMAABgOAAAYDgAAAFQAACALAAAgDwAAAFUAACAoAAAgLwAAAFoAACBgAAAgbwAAAGIAAP7/AAD+/wAAAHIAAP/5AAD/+wAAAHMAARC9AAEQvQAAAHYAARDNAAEQzQAAAHcAATQwAAE0PwAAAHgAAbygAAG8owAAAIgAAdFzAAHRegAAAIwADgABAA4AAQAAAJQADgAgAA4AIQAAAJUADgB+AA4AfwAAAJcAAAEGAAABAAAAAAAAAAEDBAUGAgAAAAAAAAAAAAAAAAAAAAEAAAcAAAAAAAAAAAAAAAAAAAAICQoLDA0ODxARAAAAAAAAABITFBUWFxgZGhscHR4fICEiIyQlJicoKSorAAAAAAAALC0uLzAxMjM0NTY3ODk6Ozw9Pj9AQUJDREUAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAARAURAAAALAAsADQAPABEAEwAVABUAFwAZABsAHQAfACEAIwAlACcAKQArAC0ALwAxADMANQA3ADkAOwA9AD8AQQBDAEUARwBJAEsATQBPAFEAUwBVAFcAWQBbAF0AYYBjgGWAZ4BpgGuAbYBvgHGAc4B1gHeAeYB7gH2Af4CBgIOAhYCHgImAi4CNgI+AkYCTgJWAl4CZgJuAnYCfgKGAo4ClgKeAqYCrgK2Ar4CxgLOAtYC3gLmAu4C9gL+AwYDDgMWAx4DJgMuAzYDPgNGA04DVgNeA2YDbgN2A34DhgOOA5YDngOmA64DtgO+A8YDzgPWA94D5gPuA/YD/gQGBA4EFgQeBCYELgQ2BD4ERgROBFYEXgRmBG4EdgR+BIYEjgSWBJ4EpgSuBLYEvgTGBM4E1gTeBOYAAgBEAAACZAVVAAMABwAusQEALzyyBwQA7TKxBgXcPLIDAgDtMgCxAwAvPLIFBADtMrIHBgH8PLIBAgDtMjMRIRElIREhRAIg/iQBmP5oBVX6q0QEzQAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAAAAgBkAGQB9ADIAAMABwAANzUhFSE1IRVkAZD+cAGQZGRkZGT//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAD//wBkAGQB9ADIEgYALAAA//8AZABkAfQAyBIGACwAAP//AGQAZAH0AMgSBgAsAAAAAAAOAK4AAQAAAAAAAAAAAAIAAQAAAAAAAQAHABMAAQAAAAAAAgAHACsAAQAAAAAAAwAjAHsAAQAAAAAABAAHAK8AAQAAAAAABQAPANcAAQAAAAAABgAHAPcAAwABBAkAAAAAAAAAAwABBAkAAQAOAAMAAwABBAkAAgAOABsAAwABBAkAAwBGADMAAwABBAkABAAOAJ8AAwABBAkABQAeALcAAwABBAkABgAOAOcAAAAAVABUAEYAVABlAHMAdAAAVFRGVGVzdAAAUgBlAGcAdQBsAGEAcgAAUmVndWxhcgAARgBvAG4AdABGAG8AcgBnAGUAIAAyAC4AMAAgADoAIABUAFQARgBUAGUAcwB0ACAAOgAgADIANAAtADIALQAyADAAMgA2AABGb250Rm9yZ2UgMi4wIDogVFRGVGVzdCA6IDI0LTItMjAyNgAAVABUAEYAVABlAHMAdAAAVFRGVGVzdAAAVgBlAHIAcwBpAG8AbgAgADAAMAAxAC4AMAAwADAAAFZlcnNpb24gMDAxLjAwMAAAVABUAEYAVABlAHMAdAAAVFRGVGVzdAAAAAAAAgAAAAAAAP9nAGYAAAAAAAAAAAAAAAAAAAAAAAAAAACZAAAAAQECAQMBBAEFAQYAAwATABQAFQAWABcAGAAZABoAGwAcACQAJQAmACcAKAApACoAKwAsAC0ALgAvADAAMQAyADMANAA1ADYANwA4ADkAOgA7ADwAPQBEAEUARgBHAEgASQBKAEsATABNAE4ATwBQAFEAUgBTAFQAVQBWAFcAWABZAFoAWwBcAF0BBwEIAQkBCgELAQwBDQEOAQ8BEAERARIBEwEUARUBFgEXARgBGQEaARsBHAEdAR4BHwEgASEBIgEjASQBJQEmAScBKAEpASoBKwEsAS0BLgEvATABMQEyATMBNAE1ATYBNwE4ATkBOgE7ATwBPQE+AT8BQAFBAUIBQwFEAUUBRgFHAUgBSQFKAUsBTAFNAU4BTwFQAVEBUgFTAVQBVQFWAVcBWAFZB3VuaTAwMEQHdW5pMDAwOQd1bmkwMDBBB3VuaTAwMEIHdW5pMDAwQwd1bmkwMDg1B3VuaTAwQUQHdW5pMDYwMAd1bmkwNjAxB3VuaTA2MDIHdW5pMDYwMwd1bmkwNjA0B3VuaTA2MDUHdW5pMDYxQwd1bmkwNkREB3VuaTA3MEYHdW5pMDg5MAd1bmkwODkxB3VuaTA4RTIHdW5pMTgwRQd1bmkyMDBCB3VuaTIwMEMHdW5pMjAwRAd1bmkyMDBFB3VuaTIwMEYHdW5pMjAyOAd1bmkyMDI5B3VuaTIwMkEHdW5pMjAyQgd1bmkyMDJDB3VuaTIwMkQHdW5pMjAyRQd1bmkyMDJGB3VuaTIwNjAHdW5pMjA2MQd1bmkyMDYyB3VuaTIwNjMHdW5pMjA2NAd1bmkyMDY1B3VuaTIwNjYHdW5pMjA2Nwd1bmkyMDY4B3VuaTIwNjkHdW5pMjA2QQd1bmkyMDZCB3VuaTIwNkMHdW5pMjA2RAd1bmkyMDZFB3VuaTIwNkYHdW5pRkVGRgd1bmlGRkY5B3VuaUZGRkEHdW5pRkZGQgZ1MTEwQkQGdTExMENEBnUxMzQzMAZ1MTM0MzEGdTEzNDMyBnUxMzQzMwZ1MTM0MzQGdTEzNDM1BnUxMzQzNgZ1MTM0MzcGdTEzNDM4BnUxMzQzOQZ1MTM0M0EGdTEzNDNCBnUxMzQzQwZ1MTM0M0QGdTEzNDNFBnUxMzQzRgZ1MUJDQTAGdTFCQ0ExBnUxQkNBMgZ1MUJDQTMGdTFEMTczBnUxRDE3NAZ1MUQxNzUGdTFEMTc2BnUxRDE3NwZ1MUQxNzgGdTFEMTc5BnUxRDE3QQZ1RTAwMDEGdUUwMDIwBnVFMDAyMQZ1RTAwN0UGdUUwMDdGAAAAAAAAAf//AAIAAAABAAAAAOUNt1MAAAAA5cMndAAAAADlwyd0";

    /**
     * Same font as above, but in PostScript Type1 (PFB) format with different font name.
     */
    private static final String TYPE1_BYTES = "gAFfBwAAJSFQUy1BZG9iZUZvbnQtMS4wOiBUeXBlMVRlc3QgMDAxLjAwMAolJVRpdGxlOiBUeXBlMVRlc3QKJVZlcnNpb246IDAwMS4wMDAKJSVDcmVhdGlvbkRhdGU6IFR1ZSBGZWIgMjQgMTU6MzQ6MDUgMjAyNgolJUNyZWF0b3I6IEpheWF0aGlydGggUmFvIEQgVgolIDIwMjYtMi0yNDogQ3JlYXRlZCB3aXRoIEZvbnRGb3JnZSAoaHR0cDovL2ZvbnRmb3JnZS5vcmcpCiUgR2VuZXJhdGVkIGJ5IEZvbnRGb3JnZSAyMDI1MTAwOSAoaHR0cDovL2ZvbnRmb3JnZS5zZi5uZXQvKQolJUVuZENvbW1lbnRzCgoxMCBkaWN0IGJlZ2luCi9Gb250VHlwZSAxIGRlZgovRm9udE1hdHJpeCBbMC4wMDA0ODgyODEgMCAwIDAuMDAwNDg4MjgxIDAgMCBdcmVhZG9ubHkgZGVmCi9Gb250TmFtZSAvVHlwZTFUZXN0IGRlZgovRm9udEJCb3ggezEwMCAxMDAgNTAwIDIwMCB9cmVhZG9ubHkgZGVmCi9QYWludFR5cGUgMCBkZWYKL0ZvbnRJbmZvIDkgZGljdCBkdXAgYmVnaW4KIC92ZXJzaW9uICgwMDEuMDAwKSByZWFkb25seSBkZWYKIC9Ob3RpY2UgKCkgcmVhZG9ubHkgZGVmCiAvRnVsbE5hbWUgKFR5cGUxVGVzdCkgcmVhZG9ubHkgZGVmCiAvRmFtaWx5TmFtZSAoVHlwZTFUZXN0KSByZWFkb25seSBkZWYKIC9XZWlnaHQgKFJlZ3VsYXIpIHJlYWRvbmx5IGRlZgogL0l0YWxpY0FuZ2xlIDAgZGVmCiAvaXNGaXhlZFBpdGNoIGZhbHNlIGRlZgogL1VuZGVybGluZVBvc2l0aW9uIC0yMDQuOCBkZWYKIC9VbmRlcmxpbmVUaGlja25lc3MgMTAyLjQgZGVmCmVuZCByZWFkb25seSBkZWYKL0VuY29kaW5nIDI1NiBhcnJheQogMCAxIDI1NSB7IDEgaW5kZXggZXhjaCAvLm5vdGRlZiBwdXR9IGZvcgpkdXAgOS91bmkwMDA5IHB1dApkdXAgMTAvdW5pMDAwQSBwdXQKZHVwIDExL3VuaTAwMEIgcHV0CmR1cCAxMi91bmkwMDBDIHB1dApkdXAgMTMvdW5pMDAwRCBwdXQKZHVwIDMyL3NwYWNlIHB1dApkdXAgNDgvemVybyBwdXQKZHVwIDQ5L29uZSBwdXQKZHVwIDUwL3R3byBwdXQKZHVwIDUxL3RocmVlIHB1dApkdXAgNTIvZm91ciBwdXQKZHVwIDUzL2ZpdmUgcHV0CmR1cCA1NC9zaXggcHV0CmR1cCA1NS9zZXZlbiBwdXQKZHVwIDU2L2VpZ2h0IHB1dApkdXAgNTcvbmluZSBwdXQKZHVwIDY1L0EgcHV0CmR1cCA2Ni9CIHB1dApkdXAgNjcvQyBwdXQKZHVwIDY4L0QgcHV0CmR1cCA2OS9FIHB1dApkdXAgNzAvRiBwdXQKZHVwIDcxL0cgcHV0CmR1cCA3Mi9IIHB1dApkdXAgNzMvSSBwdXQKZHVwIDc0L0ogcHV0CmR1cCA3NS9LIHB1dApkdXAgNzYvTCBwdXQKZHVwIDc3L00gcHV0CmR1cCA3OC9OIHB1dApkdXAgNzkvTyBwdXQKZHVwIDgwL1AgcHV0CmR1cCA4MS9RIHB1dApkdXAgODIvUiBwdXQKZHVwIDgzL1MgcHV0CmR1cCA4NC9UIHB1dApkdXAgODUvVSBwdXQKZHVwIDg2L1YgcHV0CmR1cCA4Ny9XIHB1dApkdXAgODgvWCBwdXQKZHVwIDg5L1kgcHV0CmR1cCA5MC9aIHB1dApkdXAgOTcvYSBwdXQKZHVwIDk4L2IgcHV0CmR1cCA5OS9jIHB1dApkdXAgMTAwL2QgcHV0CmR1cCAxMDEvZSBwdXQKZHVwIDEwMi9mIHB1dApkdXAgMTAzL2cgcHV0CmR1cCAxMDQvaCBwdXQKZHVwIDEwNS9pIHB1dApkdXAgMTA2L2ogcHV0CmR1cCAxMDcvayBwdXQKZHVwIDEwOC9sIHB1dApkdXAgMTA5L20gcHV0CmR1cCAxMTAvbiBwdXQKZHVwIDExMS9vIHB1dApkdXAgMTEyL3AgcHV0CmR1cCAxMTMvcSBwdXQKZHVwIDExNC9yIHB1dApkdXAgMTE1L3MgcHV0CmR1cCAxMTYvdCBwdXQKZHVwIDExNy91IHB1dApkdXAgMTE4L3YgcHV0CmR1cCAxMTkvdyBwdXQKZHVwIDEyMC94IHB1dApkdXAgMTIxL3kgcHV0CmR1cCAxMjIv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";

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
