/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

import sun.nio.cs.ext.IBM933;

//EBIDIC DBCSONLY Korean

public class CharToByteCp834 extends CharToByteCp933
{
    public CharToByteCp834() {
       super();
       subBytes = new byte[] {(byte)0xfe, (byte)0xfe};
    }

    protected boolean doSBCS() {
        return false;
    }

    protected int encodeHangul(char ch) {
        int theBytes = super.encodeHangul(ch);
        if (theBytes == -1) {
            // Cp834 has 6 additional non-roundtrip char->bytes
            // mappings, see#6379808
            if (ch == '\u00b7') {
                return 0x4143;
            } else if (ch == '\u00ad') {
                return 0x4148;
            } else if (ch == '\u2015') {
                return 0x4149;
            } else if (ch == '\u223c') {
                return 0x42a1;
            } else if (ch == '\uff5e') {
                return 0x4954;
            } else if (ch == '\u2299') {
                return 0x496f;
            }
        } else if (((theBytes & 0xff00)>>8) == 0) {
            //SBCS, including 0
            return -1;
        }
        return theBytes;
    }

    public int getMaxBytesPerChar() {
       return 2;
    }

    public String getCharacterEncoding() {
       return "Cp834";
    }
}
