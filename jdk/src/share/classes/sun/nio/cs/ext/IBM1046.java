
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

public class IBM1046
    extends Charset
    implements HistoricallyNamedCharset
{

    public IBM1046() {
        super("x-IBM1046", ExtendedCharsets.aliasesFor("x-IBM1046"));
    }

    public String historicalName() {
        return "Cp1046";
    }

    public boolean contains(Charset cs) {
        return (cs instanceof IBM1046);
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

            "\uFE88\u00D7\u00F7\uF8F6\uF8F5\uF8F4\uF8F7\uFE71" +        // 0x80 - 0x87
            "\u0088\u25A0\u2502\u2500\u2510\u250C\u2514\u2518" +        // 0x88 - 0x8F
            "\uFE79\uFE7B\uFE7D\uFE7F\uFE77\uFE8A\uFEF0\uFEF3" +        // 0x90 - 0x97
            "\uFEF2\uFECE\uFECF\uFED0\uFEF6\uFEF8\uFEFA\uFEFC" +        // 0x98 - 0x9F
            "\u00A0\uF8FA\uF8F9\uF8F8\u00A4\uF8FB\uFE8B\uFE91" +        // 0xA0 - 0xA7
            "\uFE97\uFE9B\uFE9F\uFEA3\u060C\u00AD\uFEA7\uFEB3" +        // 0xA8 - 0xAF
            "\u0660\u0661\u0662\u0663\u0664\u0665\u0666\u0667" +        // 0xB0 - 0xB7
            "\u0668\u0669\uFEB7\u061B\uFEBB\uFEBF\uFECA\u061F" +        // 0xB8 - 0xBF
            "\uFECB\uFE80\uFE81\uFE83\uFE85\uFE87\uFE89\uFE8D" +        // 0xC0 - 0xC7
            "\uFE8F\uFE93\uFE95\uFE99\uFE9D\uFEA1\uFEA5\uFEA9" +        // 0xC8 - 0xCF
            "\uFEAB\uFEAD\uFEAF\uFEB1\uFEB5\uFEB9\uFEBD\uFEC3" +        // 0xD0 - 0xD7
            "\uFEC7\uFEC9\uFECD\uFECC\uFE82\uFE84\uFE8E\uFED3" +        // 0xD8 - 0xDF
            "\u0640\uFED1\uFED5\uFED9\uFEDD\uFEE1\uFEE5\uFEEB" +        // 0xE0 - 0xE7
            "\uFEED\uFEEF\uFEF1\uFE70\uFE72\uFE74\uFE76\uFE78" +        // 0xE8 - 0xEF
            "\uFE7A\uFE7C\uFE7E\uFED7\uFEDB\uFEDF\uF8FC\uFEF5" +        // 0xF0 - 0xF7
            "\uFEF7\uFEF9\uFEFB\uFEE3\uFEE7\uFEEC\uFEE9\uFFFD" +        // 0xF8 - 0xFF
            "\u0000\u0001\u0002\u0003\u0004\u0005\u0006\u0007" +        // 0x00 - 0x07
            "\b\t\n\u000B\f\r\u000E\u000F" +    // 0x08 - 0x0F
            "\u0010\u0011\u0012\u0013\u0014\u0015\u0016\u0017" +        // 0x10 - 0x17
            "\u0018\u0019\u001A\u001B\u001C\u001D\u001E\u001F" +        // 0x18 - 0x1F
            "\u0020\u0021\"\u0023\u0024\u0025\u0026\'" +        // 0x20 - 0x27
            "\u0028\u0029\u002A\u002B\u002C\u002D\u002E\u002F" +        // 0x28 - 0x2F
            "\u0030\u0031\u0032\u0033\u0034\u0035\u0036\u0037" +        // 0x30 - 0x37
            "\u0038\u0039\u003A\u003B\u003C\u003D\u003E\u003F" +        // 0x38 - 0x3F
            "\u0040\u0041\u0042\u0043\u0044\u0045\u0046\u0047" +        // 0x40 - 0x47
            "\u0048\u0049\u004A\u004B\u004C\u004D\u004E\u004F" +        // 0x48 - 0x4F
            "\u0050\u0051\u0052\u0053\u0054\u0055\u0056\u0057" +        // 0x50 - 0x57
            "\u0058\u0059\u005A\u005B\\\u005D\u005E\u005F" +    // 0x58 - 0x5F
            "\u0060\u0061\u0062\u0063\u0064\u0065\u0066\u0067" +        // 0x60 - 0x67
            "\u0068\u0069\u006A\u006B\u006C\u006D\u006E\u006F" +        // 0x68 - 0x6F
            "\u0070\u0071\u0072\u0073\u0074\u0075\u0076\u0077" +        // 0x70 - 0x77
            "\u0078\u0079\u007A\u007B\u007C\u007D\u007E\u007F";         // 0x78 - 0x7F

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
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u0088\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u00A0\u0000\u0000\u0000\u00A4\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u00AD\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0081" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0082" +
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
            "\u00AC\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u00BB" +
            "\u0000\u0000\u0000\u00BF\u0000\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u00E0\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u0000\u0000\u0000\u0000\u00B0\u00B1\u00B2\u00B3" +
            "\u00B4\u00B5\u00B6\u00B7\u00B8\u00B9\u0000\u0000" +
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
            "\u0000\u0000\u0000\u0000\u008B\u0000\u008A\u0000" +
            "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
            "\u008D\u0000\u0000\u0000\u008C\u0000\u0000\u0000" +
            "\u008E\u0000\u0000\u0000\u008F\u0000\u0000\u0000" +
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
            "\u0000\u0000\u0000\u0000\u0089\u0000\u0000\u0000" +
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
            "\u0000\u0085\u0084\u0083\u0086\u00A3\u00A2\u00A1" +
            "\u00A5\u00F6\u0000\u0000\u0000\u0000\u0000\u0000" +
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
            "\u0000\u0000\u00EB\u0087\u00EC\u0000\u00ED\u0000" +
            "\u00EE\u0094\u00EF\u0090\u00F0\u0091\u00F1\u0092" +
            "\u00F2\u0093\u00C1\u00C2\u00DC\u00C3\u00DD\u00C4" +
            "\u0000\u00C5\u0080\u00C6\u0095\u00A6\u0000\u00C7" +
            "\u00DE\u00C8\u0000\u00A7\u0000\u00C9\u0000\u00CA" +
            "\u0000\u00A8\u0000\u00CB\u0000\u00A9\u0000\u00CC" +
            "\u0000\u00AA\u0000\u00CD\u0000\u00AB\u0000\u00CE" +
            "\u0000\u00AE\u0000\u00CF\u0000\u00D0\u0000\u00D1" +
            "\u0000\u00D2\u0000\u00D3\u0000\u00AF\u0000\u00D4" +
            "\u0000\u00BA\u0000\u00D5\u0000\u00BC\u0000\u00D6" +
            "\u0000\u00BD\u0000\u0000\u0000\u00D7\u0000\u0000" +
            "\u0000\u00D8\u0000\u00D9\u00BE\u00C0\u00DB\u00DA" +
            "\u0099\u009A\u009B\u00E1\u0000\u00DF\u0000\u00E2" +
            "\u0000\u00F3\u0000\u00E3\u0000\u00F4\u0000\u00E4" +
            "\u0000\u00F5\u0000\u00E5\u0000\u00FB\u0000\u00E6" +
            "\u0000\u00FC\u0000\u00FE\u0000\u00E7\u00FD\u00E8" +
            "\u0000\u00E9\u0096\u00EA\u0098\u0097\u0000\u00F7" +
            "\u009C\u00F8\u009D\u00F9\u009E\u00FA\u009F\u0000" +
            "\u0000\u0000";

    private final static short index1[] = {
            0, 248, 248, 248, 248, 248, 492, 248, 248, 248, 248, 248, 248, 248, 248, 248,
            248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248,
            248, 248, 248, 248, 248, 748, 248, 248, 248, 248, 248, 248, 248, 248, 248, 248,
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
            248, 248, 248, 248, 248, 248, 248, 248, 909, 248, 248, 248, 248, 248, 1162, 248,

        };
    }
}
