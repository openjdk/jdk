
/*
 * Copyright 2003-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.nio.cs.ext;


import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import sun.nio.cs.StandardCharsets;
import sun.nio.cs.SingleByteDecoder;
import sun.nio.cs.SingleByteEncoder;
import sun.nio.cs.HistoricallyNamedCharset;

public class IBM420
    extends Charset
    implements HistoricallyNamedCharset
{

    public IBM420() {
        super("IBM420", ExtendedCharsets.aliasesFor("IBM420"));
    }

    public String historicalName() {
        return "Cp420";
    }

    public boolean contains(Charset cs) {
        return (cs instanceof IBM420);
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

        private final static String byteToCharTable =

            "\uF8F5\u0061\u0062\u0063\u0064\u0065\u0066\u0067" +        // 0x80 - 0x87
            "\u0068\u0069\uFEB7\uF8F4\uFEBB\uF8F7\uFEBF\uFEC3" +        // 0x88 - 0x8F
            "\uFEC7\u006A\u006B\u006C\u006D\u006E\u006F\u0070" +        // 0x90 - 0x97
            "\u0071\u0072\uFEC9\uFECA\uFECB\uFECC\uFECD\uFECE" +        // 0x98 - 0x9F
            "\uFECF\u00F7\u0073\u0074\u0075\u0076\u0077\u0078" +        // 0xA0 - 0xA7
            "\u0079\u007A\uFED0\uFED1\uFED3\uFED5\uFED7\uFED9" +        // 0xA8 - 0xAF
            "\uFEDB\uFEDD\uFEF5\uFEF6\uFEF7\uFEF8\uFFFD\uFFFD" +        // 0xB0 - 0xB7
            "\uFEFB\uFEFC\uFEDF\uFEE1\uFEE3\uFEE5\uFEE7\uFEE9" +        // 0xB8 - 0xBF
            "\u061B\u0041\u0042\u0043\u0044\u0045\u0046\u0047" +        // 0xC0 - 0xC7
            "\u0048\u0049\u00AD\uFEEB\uFFFD\uFEEC\uFFFD\uFEED" +        // 0xC8 - 0xCF
            "\u061F\u004A\u004B\u004C\u004D\u004E\u004F\u0050" +        // 0xD0 - 0xD7
            "\u0051\u0052\uFEEF\uFEF0\uFEF1\uFEF2\uFEF3\u0660" +        // 0xD8 - 0xDF
            "\u00D7\u2007\u0053\u0054\u0055\u0056\u0057\u0058" +        // 0xE0 - 0xE7
            "\u0059\u005A\u0661\u0662\uFFFD\u0663\u0664\u0665" +        // 0xE8 - 0xEF
            "\u0030\u0031\u0032\u0033\u0034\u0035\u0036\u0037" +        // 0xF0 - 0xF7
            "\u0038\u0039\uFFFD\u0666\u0667\u0668\u0669\u009F" +        // 0xF8 - 0xFF
            "\u0000\u0001\u0002\u0003\u009C\t\u0086\u007F" +    // 0x00 - 0x07
            "\u0097\u008D\u008E\u000B\f\r\u000E\u000F" +        // 0x08 - 0x0F
            "\u0010\u0011\u0012\u0013\u009D\n\b\u0087" +        // 0x10 - 0x17
            "\u0018\u0019\u0092\u008F\u001C\u001D\u001E\u001F" +        // 0x18 - 0x1F
            "\u0080\u0081\u0082\u0083\u0084\n\u0017\u001B" +    // 0x20 - 0x27
            "\u0088\u0089\u008A\u008B\u008C\u0005\u0006\u0007" +        // 0x28 - 0x2F
            "\u0090\u0091\u0016\u0093\u0094\u0095\u0096\u0004" +        // 0x30 - 0x37
            "\u0098\u0099\u009A\u009B\u0014\u0015\u009E\u001A" +        // 0x38 - 0x3F
            "\u0020\u00A0\uFE7C\uFE7D\u0640\uF8FC\uFE80\uFE81" +        // 0x40 - 0x47
            "\uFE82\uFE83\u00A2\u002E\u003C\u0028\u002B\u007C" +        // 0x48 - 0x4F
            "\u0026\uFE84\uFE85\uFFFD\uFFFD\uFE8B\uFE8D\uFE8E" +        // 0x50 - 0x57
            "\uFE8F\uFE91\u0021\u0024\u002A\u0029\u003B\u00AC" +        // 0x58 - 0x5F
            "\u002D\u002F\uFE93\uFE95\uFE97\uFE99\uFE9B\uFE9D" +        // 0x60 - 0x67
            "\uFE9F\uFEA1\u00A6\u002C\u0025\u005F\u003E\u003F" +        // 0x68 - 0x6F
            "\uFEA3\uFEA5\uFEA7\uFEA9\uFEAB\uFEAD\uFEAF\uF8F6" +        // 0x70 - 0x77
            "\uFEB3\u060C\u003A\u0023\u0040\'\u003D\"";         // 0x78 - 0x7F

    }

    private static class Encoder extends SingleByteEncoder {
            public Encoder(Charset cs) {
                super(cs, index1, index2, 0xFF00, 0x00FF, 8);
            }

            private final static String index2 =


            "\u0000\u0001\u0002\u0003\u0037\u002D\u002E\u002F" +
            "\u0016\u0005\u0015\u000B\f\r\u000E\u000F" +
            "\u0010\u0011\u0012\u0013\u003C\u003D\u0032\u0026" +
            "\u0018\u0019\u003F\'\u001C\u001D\u001E\u001F" +
            "\u0040\u005A\u007F\u007B\u005B\u006C\u0050\u007D" +
            "\u004D\u005D\\\u004E\u006B\u0060\u004B\u0061" +
            "\u00F0\u00F1\u00F2\u00F3\u00F4\u00F5\u00F6\u00F7" +
            "\u00F8\u00F9\u007A\u005E\u004C\u007E\u006E\u006F" +
            "\u007C\u00C1\u00C2\u00C3\u00C4\u00C5\u00C6\u00C7" +
            "\u00C8\u00C9\u00D1\u00D2\u00D3\u00D4\u00D5\u00D6" +
            "\u00D7\u00D8\u00D9\u00E2\u00E3\u00E4\u00E5\u00E6" +
            "\u00E7\u00E8\u00E9\u0000\u0000\u0000\u0000\u006D" +
            "\u0000\u0081\u0082\u0083\u0084\u0085\u0086\u0087" +
            "\u0088\u0089\u0091\u0092\u0093\u0094\u0095\u0096" +
            "\u0097\u0098\u0099\u00A2\u00A3\u00A4\u00A5\u00A6" +
            "\u00A7\u00A8\u00A9\u0000\u004F\u0000\u0000\u0007" +
            "\u0020\u0021\"\u0023\u0024\u0015\u0006\u0017" +
            "\u0028\u0029\u002A\u002B\u002C\t\n\u001B" +
            "\u0030\u0031\u001A\u0033\u0034\u0035\u0036\b" +
            "\u0038\u0039\u003A\u003B\u0004\u0014\u003E\u00FF" +
            "\u0041\u0000\u004A\u0000\u0000\u0000\u006A\u0000" +
            "\u0000\u0000\u0000\u0000\u005F\u00CA\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u00E0" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u00A1" +
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
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u0079\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u00C0" +
            "\u0000\u0000\u0000\u00D0\u0000\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0044\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u00DF\u00EA\u00EB\u00ED" +
            "\u00EE\u00EF\u00FB\u00FC\u00FD\u00FE\u0000\u0000" +
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
            "\u0000\u0000\u0000\u0000\u00E1\u0000\u0000\u0000" +
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
            "\u0000\u0000\u0000\u0000\u0000\u008B\u0080\u0077" +
            "\u008D\u0000\u0000\u0000\u0000\u0045\u0000\u0000" +
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
            "\u0000\u0000\u0042\u0043\u0000\u0000\u0046\u0047" +
            "\u0048\u0049\u0051\u0052\u0000\u0000\u0000\u0000" +
            "\u0000\u0055\u0000\u0056\u0057\u0058\u0000\u0059" +
            "\u0000\u0062\u0000\u0063\u0000\u0064\u0000\u0065" +
            "\u0000\u0066\u0000\u0067\u0000\u0068\u0000\u0069" +
            "\u0000\u0070\u0000\u0071\u0000\u0072\u0000\u0073" +
            "\u0000\u0074\u0000\u0075\u0000\u0076\u0000\u0000" +
            "\u0000\u0078\u0000\u0000\u0000\u008A\u0000\u0000" +
            "\u0000\u008C\u0000\u0000\u0000\u008E\u0000\u0000" +
            "\u0000\u008F\u0000\u0000\u0000\u0090\u0000\u009A" +
            "\u009B\u009C\u009D\u009E\u009F\u00A0\u00AA\u00AB" +
            "\u0000\u00AC\u0000\u00AD\u0000\u00AE\u0000\u00AF" +
            "\u0000\u00B0\u0000\u00B1\u0000\u00BA\u0000\u00BB" +
            "\u0000\u00BC\u0000\u00BD\u0000\u00BE\u0000\u00BF" +
            "\u0000\u00CB\u00CD\u00CF\u0000\u00DA\u00DB\u00DC" +
            "\u00DD\u00DE\u0000\u00B2\u00B3\u00B4\u00B5\u0000" +
            "\u0000\u00B8\u00B9\u0000\u0000\u0000";

    private final static short index1[] = {
            0, 248, 248, 248, 248, 248, 492, 248, 248, 248, 248, 248, 248, 248, 248, 248,
            248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248,
            741, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248,
            248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248,
            248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248,
            248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248,
            248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248,
            248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248,
            248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248,
            248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248,
            248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248,
            248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248,
            248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248,
            248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248,
            248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248,
            248, 248, 248, 248, 248, 248, 248, 248, 753, 248, 248, 248, 248, 248, 1006, 248,

        };
    }
}
