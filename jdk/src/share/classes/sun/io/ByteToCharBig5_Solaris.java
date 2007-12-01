/*
 * Copyright 2001 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 *
 *
 *
 *
 */
public class ByteToCharBig5_Solaris extends ByteToCharBig5 {
    public ByteToCharBig5_Solaris() {}

    public String getCharacterEncoding() {
        return "Big5_Solaris";
    }

    protected char getUnicode(int byte1, int byte2) {
        //
        char c = super.getUnicode(byte1, byte2);
        if (c == REPLACE_CHAR) {
            if (byte1 == 0xf9) {
                switch (byte2) {
                    case 0xD6:
                        c = (char)0x7881;
                        break;
                    case 0xD7:
                        c = (char)0x92B9;
                        break;
                    case 0xD8:
                        c = (char)0x88CF;
                        break;
                    case 0xD9:
                        c = (char)0x58BB;
                        break;
                    case 0xDA:
                        c = (char)0x6052;
                        break;
                    case 0xDB:
                        c = (char)0x7CA7;
                        break;
                    case 0xDC:
                        c = (char)0x5AFA;
                        break;
                }
            }
        }
        return c;
    }
}
