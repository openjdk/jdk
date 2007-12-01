/*
 * Copyright 2002-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.nio.cs;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import sun.nio.cs.StandardCharsets;
import sun.nio.cs.SingleByteDecoder;
import sun.nio.cs.SingleByteEncoder;
import sun.nio.cs.HistoricallyNamedCharset;

public class ISO_8859_7
    extends Charset
    implements HistoricallyNamedCharset
{

    public ISO_8859_7() {
        super("ISO-8859-7", StandardCharsets.aliases_ISO_8859_7);
    }

    public String historicalName() {
        return "ISO8859_7";
    }

    public boolean contains(Charset cs) {
        return ((cs.name().equals("US-ASCII"))
                || (cs instanceof ISO_8859_7));
    }

    public CharsetDecoder newDecoder() {
        return new Decoder(this);
    }

    public CharsetEncoder newEncoder() {
        return new Encoder(this);
    }


    /**
     * These accessors are temporarily supplied while sun.io
     * converters co-exist with the sun.nio.cs.{ext} charset coders
     * These facilitate sharing of conversion tables between the
     * two co-existing implementations. When sun.io converters
     * are made extinct these will be unncessary and should be removed
     */

    public String getDecoderSingleByteMappings() {
        return Decoder.byteToCharTable;

    }

    public short[] getEncoderIndex1() {
        return Encoder.index1;

    }
    public String getEncoderIndex2() {
        return Encoder.index2;

    }

    private static class Decoder extends SingleByteDecoder {
        public Decoder(Charset cs) {
            super(cs, byteToCharTable);
        }

        private final static  String byteToCharTable =

        "\u0080\u0081\u0082\u0083\u0084\u0085\u0086\u0087" +     // 0x80 - 0x87
        "\u0088\u0089\u008A\u008B\u008C\u008D\u008E\u008F" +     // 0x88 - 0x8F
        "\u0090\u0091\u0092\u0093\u0094\u0095\u0096\u0097" +     // 0x90 - 0x97
        "\u0098\u0099\u009A\u009B\u009C\u009D\u009E\u009F" +     // 0x98 - 0x9F
        "\u00A0\u2018\u2019\u00A3\u20AC\u20AF\u00A6\u00A7" +     // 0xA0 - 0xA7
        "\u00A8\u00A9\u037A\u00AB\u00AC\u00AD\uFFFD\u2015" +     // 0xA8 - 0xAF
        "\u00B0\u00B1\u00B2\u00B3\u0384\u0385\u0386\u00B7" +     // 0xB0 - 0xB7
        "\u0388\u0389\u038A\u00BB\u038C\u00BD\u038E\u038F" +     // 0xB8 - 0xBF
        "\u0390\u0391\u0392\u0393\u0394\u0395\u0396\u0397" +     // 0xC0 - 0xC7
        "\u0398\u0399\u039A\u039B\u039C\u039D\u039E\u039F" +     // 0xC8 - 0xCF
        "\u03A0\u03A1\uFFFD\u03A3\u03A4\u03A5\u03A6\u03A7" +     // 0xD0 - 0xD7
        "\u03A8\u03A9\u03AA\u03AB\u03AC\u03AD\u03AE\u03AF" +     // 0xD8 - 0xDF
        "\u03B0\u03B1\u03B2\u03B3\u03B4\u03B5\u03B6\u03B7" +     // 0xE0 - 0xE7
        "\u03B8\u03B9\u03BA\u03BB\u03BC\u03BD\u03BE\u03BF" +     // 0xE8 - 0xEF
        "\u03C0\u03C1\u03C2\u03C3\u03C4\u03C5\u03C6\u03C7" +     // 0xF0 - 0xF7
        "\u03C8\u03C9\u03CA\u03CB\u03CC\u03CD\u03CE\uFFFD" +     // 0xF8 - 0xFF
        "\u0000\u0001\u0002\u0003\u0004\u0005\u0006\u0007" +     // 0x00 - 0x07
        "\b\t\n\u000B\f\r\u000E\u000F"                     +     // 0x08 - 0x0F
        "\u0010\u0011\u0012\u0013\u0014\u0015\u0016\u0017" +     // 0x10 - 0x17
        "\u0018\u0019\u001A\u001B\u001C\u001D\u001E\u001F" +     // 0x18 - 0x1F
        "\u0020\u0021\"\u0023\u0024\u0025\u0026\'"         +     // 0x20 - 0x27
        "\u0028\u0029\u002A\u002B\u002C\u002D\u002E\u002F" +     // 0x28 - 0x2F
        "\u0030\u0031\u0032\u0033\u0034\u0035\u0036\u0037" +     // 0x30 - 0x37
        "\u0038\u0039\u003A\u003B\u003C\u003D\u003E\u003F" +     // 0x38 - 0x3F
        "\u0040\u0041\u0042\u0043\u0044\u0045\u0046\u0047" +     // 0x40 - 0x47
        "\u0048\u0049\u004A\u004B\u004C\u004D\u004E\u004F" +     // 0x48 - 0x4F
        "\u0050\u0051\u0052\u0053\u0054\u0055\u0056\u0057" +     // 0x50 - 0x57
        "\u0058\u0059\u005A\u005B\\\u005D\u005E\u005F"     +     // 0x58 - 0x5F
        "\u0060\u0061\u0062\u0063\u0064\u0065\u0066\u0067" +     // 0x60 - 0x67
        "\u0068\u0069\u006A\u006B\u006C\u006D\u006E\u006F" +     // 0x68 - 0x6F
        "\u0070\u0071\u0072\u0073\u0074\u0075\u0076\u0077" +     // 0x70 - 0x77
        "\u0078\u0079\u007A\u007B\u007C\u007D\u007E\u007F";      // 0x78 - 0x7F
    }

    private static class Encoder extends SingleByteEncoder {
        public Encoder(Charset cs) {
            super(cs, index1, index2, 0xFF00, 0x00FF, 8);
        }

    private final static String index2 =

        "\u0000\u0001\u0002\u0003\u0004\u0005\u0006\u0007" +
        "\b\t\n\u000B\f\r\u000E\u000F" +
        "\u0010\u0011\u0012\u0013\u0014\u0015\u0016\u0017" +
        "\u0018\u0019\u001A\u001B\u001C\u001D\u001E\u001F" +
        "\u0020\u0021\"\u0023\u0024\u0025\u0026\'" +
        "\u0028\u0029\u002A\u002B\u002C\u002D\u002E\u002F" +
        "\u0030\u0031\u0032\u0033\u0034\u0035\u0036\u0037" +
        "\u0038\u0039\u003A\u003B\u003C\u003D\u003E\u003F" +
        "\u0040\u0041\u0042\u0043\u0044\u0045\u0046\u0047" +
        "\u0048\u0049\u004A\u004B\u004C\u004D\u004E\u004F" +
        "\u0050\u0051\u0052\u0053\u0054\u0055\u0056\u0057" +
        "\u0058\u0059\u005A\u005B\\\u005D\u005E\u005F" +
        "\u0060\u0061\u0062\u0063\u0064\u0065\u0066\u0067" +
        "\u0068\u0069\u006A\u006B\u006C\u006D\u006E\u006F" +
        "\u0070\u0071\u0072\u0073\u0074\u0075\u0076\u0077" +
        "\u0078\u0079\u007A\u007B\u007C\u007D\u007E\u007F" +
        "\u0080\u0081\u0082\u0083\u0084\u0085\u0086\u0087" +
        "\u0088\u0089\u008A\u008B\u008C\u008D\u008E\u008F" +
        "\u0090\u0091\u0092\u0093\u0094\u0095\u0096\u0097" +
        "\u0098\u0099\u009A\u009B\u009C\u009D\u009E\u009F" +
        "\u00A0\u0000\u0000\u00A3\u0000\u0000\u00A6\u00A7" +
        "\u00A8\u00A9\u0000\u00AB\u00AC\u00AD\u0000\u0000" +
        "\u00B0\u00B1\u00B2\u00B3\u0000\u0000\u0000\u00B7" +
        "\u0000\u0000\u0000\u00BB\u0000\u00BD\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u00AA\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u00B4\u00B5\u00B6\u0000\u00B8\u00B9\u00BA\u0000" +
        "\u00BC\u0000\u00BE\u00BF\u00C0\u00C1\u00C2\u00C3" +
        "\u00C4\u00C5\u00C6\u00C7\u00C8\u00C9\u00CA\u00CB" +
        "\u00CC\u00CD\u00CE\u00CF\u00D0\u00D1\u0000\u00D3" +
        "\u00D4\u00D5\u00D6\u00D7\u00D8\u00D9\u00DA\u00DB" +
        "\u00DC\u00DD\u00DE\u00DF\u00E0\u00E1\u00E2\u00E3" +
        "\u00E4\u00E5\u00E6\u00E7\u00E8\u00E9\u00EA\u00EB" +
        "\u00EC\u00ED\u00EE\u00EF\u00F0\u00F1\u00F2\u00F3" +
        "\u00F4\u00F5\u00F6\u00F7\u00F8\u00F9\u00FA\u00FB" +
        "\u00FC\u00FD\u00FE\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u00AF\u0000\u0000\u00A1" +
        "\u00A2\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u00A4\u0000\u0000\u00A5\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000";

    private final static short index1[] = {
        0, 190, 190, 324, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190,
        190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190,
        559, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190,
        190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190,
        190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190,
        190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190,
        190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190,
        190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190,
        190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190,
        190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190,
        190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190,
        190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190,
        190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190,
        190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190,
        190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190,
        190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190, 190,
      };
   }
}
