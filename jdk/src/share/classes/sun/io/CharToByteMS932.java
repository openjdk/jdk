/*
 * Copyright 1997-1998 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Tables and data to convert Unicode to MS932
 *
 * @author  ConverterGenerator tool
 */

public class CharToByteMS932 extends CharToByteMS932DB {
    CharToByteJIS0201 cbJIS0201 = new CharToByteJIS0201();

    public String getCharacterEncoding() {
        return "MS932";
    }

    protected int convSingleByte(char inputChar, byte[] outputByte) {
        byte b;

        // \u0000 - \u007F map straight through
        if ((inputChar &0xFF80) == 0) {
            outputByte[0] = (byte)inputChar;
            return 1;
        }

        if ((b = cbJIS0201.getNative(inputChar)) == 0)
            return 0;

        outputByte[0] = b;
        return 1;
    }
}
