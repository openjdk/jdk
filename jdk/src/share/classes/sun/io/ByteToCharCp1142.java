/*
 * Copyright 1998 Sun Microsystems, Inc.  All Rights Reserved.
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

/**
 * A table to convert Cp1142 to Unicode.  This converter differs from
 * Cp277 is one code point, 0x5A, which changes from \u00A4 to \u20AC.
 * @author  Alan Liu
 */
public class ByteToCharCp1142 extends ByteToCharCp277 {
    public ByteToCharCp1142() {}

    public String getCharacterEncoding() {
        return "Cp1142";
    }

    protected char getUnicode(int byteIndex) {
        // Change single code point with respect to parent.
        // [Careful -- if the code point in question is >= 0x80, make
        //  sure you do the comparison like this: (byteIndex == (byte)0x??)]
        return (byteIndex == 0x5A) ? '\u20AC' : super.getUnicode(byteIndex);
    }
}

//eof
