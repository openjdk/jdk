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

/**
 */

public class CharToByteBig5_Solaris extends CharToByteBig5 {

    public String getCharacterEncoding() {
        return "Big5_Solaris";
    }

    protected int getNative(char ch) {
        int nativeVal;

        if ((nativeVal = super.getNative(ch)) != 0) {
            return nativeVal;
        }

        switch (ch) {
            case 0x7881:
                nativeVal = 0xF9D6;
                break;
            case 0x92B9:
                nativeVal = 0xF9D7;
                break;
            case 0x88CF:
                nativeVal = 0xF9D8;
                break;
            case 0x58BB:
                nativeVal = 0xF9D9;
                break;
            case 0x6052:
                nativeVal = 0xF9DA;
                break;
            case 0x7CA7:
                nativeVal = 0xF9DB;
                break;
            case 0x5AFA:
                nativeVal = 0xF9DC;
                break;
            }
        return nativeVal;
    }
}
