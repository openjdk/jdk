/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import sun.misc.JavaLangAccess;
import sun.misc.SharedSecrets;

/*
 * @test
 * @summary Test JavaLangAccess.formatUnsignedInt/-Long
 * @bug 8050114
 */
public class FormatUnsigned {

    static final JavaLangAccess jla = SharedSecrets.getJavaLangAccess();

    public static void testFormatUnsignedInt() {
        testFormatUnsignedInt("7fffffff", Integer.MAX_VALUE, 8, 4, 0, 8);
        testFormatUnsignedInt("80000000", Integer.MIN_VALUE, 8, 4, 0, 8);
        testFormatUnsignedInt("4711", 04711, 4, 3, 0, 4);
        testFormatUnsignedInt("4711", 0x4711, 4, 4, 0, 4);
        testFormatUnsignedInt("1010", 0b1010, 4, 1, 0, 4);
        testFormatUnsignedInt("00001010", 0b1010, 8, 1, 0, 8);
        testFormatUnsignedInt("\u0000\u000000001010", 0b1010, 10, 1, 2, 8);
    }

    public static void testFormatUnsignedLong() {
        testFormatUnsignedLong("7fffffffffffffff", Long.MAX_VALUE, 16, 4, 0, 16);
        testFormatUnsignedLong("8000000000000000", Long.MIN_VALUE, 16, 4, 0, 16);
        testFormatUnsignedLong("4711", 04711L, 4, 3, 0, 4);
        testFormatUnsignedLong("4711", 0x4711L, 4, 4, 0, 4);
        testFormatUnsignedLong("1010", 0b1010L, 4, 1, 0, 4);
        testFormatUnsignedLong("00001010", 0b1010L, 8, 1, 0, 8);
        testFormatUnsignedLong("\u0000\u000000001010", 0b1010L, 10, 1, 2, 8);
    }

    public static void testFormatUnsignedInt(String expected, int value, int arraySize, int shift, int offset, int length) {
        char[] chars = new char[arraySize];
        jla.formatUnsignedInt(value, shift, chars, offset, length);
        String s = new String(chars);
        if (!expected.equals(s)) {
            throw new Error(s + " should be equal to expected " + expected);
        }
    }

    public static void testFormatUnsignedLong(String expected, long value, int arraySize, int shift, int offset, int length) {
        char[] chars = new char[arraySize];
        jla.formatUnsignedLong(value, shift, chars, offset, length);
        String s = new String(chars);
        if (!expected.equals(s)) {
            throw new Error(s + " should be equal to expected " + expected);
        }
    }

    public static void main(String[] args) {
        testFormatUnsignedInt();
        testFormatUnsignedLong();
    }
}
