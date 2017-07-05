/*
 * Copyright 1997-2003 Sun Microsystems, Inc.  All Rights Reserved.
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
package sun.io;

import sun.nio.cs.ext.IBM942;

/**
* Tables and data to convert Cp942 to Unicode.
*
* @author Malcolm Ayres, assisted by UniMap program
*/
public class ByteToCharCp942
        extends ByteToCharDBCS_ASCII

{
    private static IBM942 nioCoder = new IBM942();

        // Return the character set id
        public String getCharacterEncoding()
        {
                return "Cp942";
        }


        private static final boolean leadByte[] = {
                false, false, false, false, false, false, false, false,  // 00 - 07
                false, false, false, false, false, false, false, false,  // 08 - 0F
                false, false, false, false, false, false, false, false,  // 10 - 17
                false, false, false, false, false, false, false, false,  // 18 - 1F
                false, false, false, false, false, false, false, false,  // 20 - 27
                false, false, false, false, false, false, false, false,  // 28 - 2F
                false, false, false, false, false, false, false, false,  // 30 - 37
                false, false, false, false, false, false, false, false,  // 38 - 3F
                false, false, false, false, false, false, false, false,  // 40 - 47
                false, false, false, false, false, false, false, false,  // 48 - 4F
                false, false, false, false, false, false, false, false,  // 50 - 57
                false, false, false, false, false, false, false, false,  // 58 - 5F
                false, false, false, false, false, false, false, false,  // 60 - 67
                false, false, false, false, false, false, false, false,  // 68 - 6F
                false, false, false, false, false, false, false, false,  // 70 - 77
                false, false, false, false, false, false, false, false,  // 78 - 7F
                false, true,  true,  true,  true,  false, false, false,  // 80 - 87
                true,  true,  true,  true,  true,  true,  true,  true,   // 88 - 8F
                true,  true,  true,  true,  true,  true,  true,  true,   // 90 - 97
                true,  true,  true,  true,  true,  true,  true,  true,   // 98 - 9F
                false, false, false, false, false, false, false, false,  // A0 - A7
                false, false, false, false, false, false, false, false,  // A8 - AF
                false, false, false, false, false, false, false, false,  // B0 - B7
                false, false, false, false, false, false, false, false,  // B8 - BF
                false, false, false, false, false, false, false, false,  // C0 - C7
                false, false, false, false, false, false, false, false,  // C8 - CF
                false, false, false, false, false, false, false, false,  // D0 - D7
                false, false, false, false, false, false, false, false,  // D8 - DF
                true,  true,  true,  true,  true,  true,  true,  true,   // E0 - E7
                true,  true,  true,  false, false, false, false, false,  // E8 - EF
                true,  true,  true,  true,  true,  true,  true,  true,   // F0 - F7
                true,  true,  true,  true,  true,  false, false, false,  // F8 - FF
        };


        private static final String singleByteToChar =
                "\u0000\u0001\u0002\u0003\u0004\u0005\u0006\u0007" +
                "\u0008\u0009\n\u000B\u000C\r\u000E\u000F" +
                "\u0010\u0011\u0012\u0013\u0014\u0015\u0016\u0017" +
                "\u0018\u0019\u001C\u001B\u007F\u001D\u001E\u001F" +
                "\u0020\u0021\"\u0023\u0024\u0025\u0026\u0027" +
                "\u0028\u0029\u002A\u002B\u002C\u002D\u002E\u002F" +
                "\u0030\u0031\u0032\u0033\u0034\u0035\u0036\u0037" +
                "\u0038\u0039\u003A\u003B\u003C\u003D\u003E\u003F" +
                "\u0040\u0041\u0042\u0043\u0044\u0045\u0046\u0047" +
                "\u0048\u0049\u004A\u004B\u004C\u004D\u004E\u004F" +
                "\u0050\u0051\u0052\u0053\u0054\u0055\u0056\u0057" +
                "\u0058\u0059\u005A\u005B\u00A5\u005D\u005E\u005F" +
                "\u0060\u0061\u0062\u0063\u0064\u0065\u0066\u0067" +
                "\u0068\u0069\u006A\u006B\u006C\u006D\u006E\u006F" +
                "\u0070\u0071\u0072\u0073\u0074\u0075\u0076\u0077" +
                "\u0078\u0079\u007A\u007B\u007C\u007D\u203E\u001A" +
                "\u00A2\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD" +
                "\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD" +
                "\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD" +
                "\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD" +
                "\u00A3\uFF61\uFF62\uFF63\uFF64\uFF65\uFF66\uFF67" +
                "\uFF68\uFF69\uFF6A\uFF6B\uFF6C\uFF6D\uFF6E\uFF6F" +
                "\uFF70\uFF71\uFF72\uFF73\uFF74\uFF75\uFF76\uFF77" +
                "\uFF78\uFF79\uFF7A\uFF7B\uFF7C\uFF7D\uFF7E\uFF7F" +
                "\uFF80\uFF81\uFF82\uFF83\uFF84\uFF85\uFF86\uFF87" +
                "\uFF88\uFF89\uFF8A\uFF8B\uFF8C\uFF8D\uFF8E\uFF8F" +
                "\uFF90\uFF91\uFF92\uFF93\uFF94\uFF95\uFF96\uFF97" +
                "\uFF98\uFF99\uFF9A\uFF9B\uFF9C\uFF9D\uFF9E\uFF9F" +
                "\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD" +
                "\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD" +
                "\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD" +
                "\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\u00AC\\\u007E"
                ;

        public ByteToCharCp942() {
                super();
                super.mask1 = 0xFFC0;
                super.mask2 = 0x003F;
                super.shift = 6;
                super.leadByte = this.leadByte;
                super.singleByteToChar = this.singleByteToChar;
                super.index1 = nioCoder.getDecoderIndex1();
                super.index2 = nioCoder.getDecoderIndex2();
        }
}
