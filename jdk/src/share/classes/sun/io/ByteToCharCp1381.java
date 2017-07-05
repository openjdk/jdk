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

import sun.nio.cs.ext.IBM1381;

/**
* Tables and data to convert Cp1381 to Unicode.
*
* @author Malcolm Ayres, assisted by UniMap program
*/
public class ByteToCharCp1381
        extends ByteToCharDBCS_ASCII

{

        private final static IBM1381 nioCoder = new IBM1381();

        // Return the character set id
        public String getCharacterEncoding()
        {
                return "Cp1381";
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
                false, false, false, false, false, false, false, false,  // 80 - 87
                false, false, false, false, true,  true,  true,  true,   // 88 - 8F
                true,  true,  true,  true,  true,  true,  true,  true,   // 90 - 97
                true,  true,  true,  true,  true,  true,  true,  true,   // 98 - 9F
                true,  true,  true,  true,  true,  true,  true,  true,   // A0 - A7
                true,  true,  false, false, false, false, false, false,  // A8 - AF
                true,  true,  true,  true,  true,  true,  true,  true,   // B0 - B7
                true,  true,  true,  true,  true,  true,  true,  true,   // B8 - BF
                true,  true,  true,  true,  true,  true,  true,  true,   // C0 - C7
                true,  true,  true,  true,  true,  true,  true,  true,   // C8 - CF
                true,  true,  true,  true,  true,  true,  true,  true,   // D0 - D7
                true,  true,  true,  true,  true,  true,  true,  true,   // D8 - DF
                true,  true,  true,  true,  true,  true,  true,  true,   // E0 - E7
                true,  true,  true,  true,  true,  true,  true,  true,   // E8 - EF
                true,  true,  true,  true,  true,  true,  true,  true,   // F0 - F7
                false, false, false, false, false, false, false, false,  // F8 - FF
        };

        public ByteToCharCp1381() {
                super();
                super.mask1 = 0xFFE0;
                super.mask2 = 0x001F;
                super.shift = 5;
                super.leadByte = this.leadByte;
                super.singleByteToChar = nioCoder.getDecoderSingleByteMappings();
                super.index1 = nioCoder.getDecoderIndex1();
                super.index2 = nioCoder.getDecoderIndex2();
        }
}
