/*
 * Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

public class Surrogate {

    public static final int UCS4_SURROGATE_MIN = 0x10000;
    public static final int UCS4_MAX = (1 << 20) + UCS4_SURROGATE_MIN - 1;

    // UTF-16 surrogate-character ranges
    //
    public static final char MIN_HIGH = '\uD800';
    public static final char MAX_HIGH = '\uDBFF';
    public static final char MIN_LOW  = '\uDC00';
    public static final char MAX_LOW  = '\uDFFF';
    public static final char MIN = MIN_HIGH;
    public static final char MAX = MAX_LOW;

    public static boolean neededFor(int uc) {
        return (uc >= UCS4_SURROGATE_MIN) && (uc <= UCS4_MAX);
    }

    public static boolean isHigh(int c) {
        return (MIN_HIGH <= c) && (c <= MAX_HIGH);
    }

    static char high(int uc) {
        return (char)(0xd800 | (((uc - UCS4_SURROGATE_MIN) >> 10) & 0x3ff));
    }

    public static boolean isLow(int c) {
        return (MIN_LOW <= c) && (c <= MAX_LOW);
    }

    static char low(int uc) {
        return (char)(0xdc00 | ((uc - UCS4_SURROGATE_MIN) & 0x3ff));
    }

    public static boolean is(int c) {
        return (MIN <= c) && (c <= MAX);
    }

    static int toUCS4(char c, char d) {
        return (((c & 0x3ff) << 10) | (d & 0x3ff)) + 0x10000;
    }

}
