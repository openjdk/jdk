/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.io;

import sun.nio.cs.ext.JIS_X_0208_Solaris_Decoder;

/**
 *
 * @author Limin Shi
 * @author Mark Son-Bell
 * @author Ian Little
 *
 *
 * PCK Converter for Solaris ja locales
 * refer to : Solaris manpage man PCK(5)
 * Incorporates mappings from NEC row 13 and IBM select extension chars
 * in addition to those defined within JIS X0208:1997 (bugID: 4765370)
 *
 */

public class ByteToCharPCK extends ByteToCharSJIS {
    ByteToCharJIS0201 bcJIS0201 = new ByteToCharJIS0201();
    ByteToCharJIS0208_Solaris bcJIS0208 = new ByteToCharJIS0208_Solaris();

    public String getCharacterEncoding() {
        return "PCK";
    }

    protected char convSingleByte(int b) {
        // If the high bits are all off, it's ASCII == Unicode
        if ((b & 0xFF80) == 0) {
            return (char)b;
        }
        return bcJIS0201.getUnicode(b);
    }

    protected char getUnicode(int c1, int c2) {
        char outChar;
        if ((outChar = super.getUnicode(c1, c2)) != '\uFFFD') {
           // Map JIS X 0208:1983 0x213D <--> U+2015
           return ((outChar != '\u2014')? outChar: '\u2015');
        } else {
            int adjust = c2 < 0x9F ? 1 : 0;
            int rowOffset = c1 < 0xA0 ? 0x70 : 0xB0;
            int cellOffset = (adjust == 1) ? (c2 > 0x7F ? 0x20 : 0x1F) : 0x7E;
            int b1 = ((c1 - rowOffset) << 1) - adjust;
            int b2 = c2 - cellOffset;
            outChar = bcJIS0208.getUnicode(b1, b2);
            return outChar;
        }
    }

    String prt(int i) {
        return Integer.toString(i,16);
    }
}
