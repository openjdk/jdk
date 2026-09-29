/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

import java.util.Arrays;

/**
 * @test
 * @bug 8302323 8351443
 * @summary Test StringBuilder.repeat sanity tests
 * @run testng/othervm -XX:-CompactStrings StringBuilderRepeat
 * @run testng/othervm -XX:+CompactStrings StringBuilderRepeat
 */
@Test
public class StringBuilderRepeat {
    private static class MyChars implements CharSequence {
        private static final char[] DATA = new char[] { 'a', 'b', 'c' };

        @Override
        public int length() {
            return DATA.length;
        }

        @Override
        public char charAt(int index) {
            return DATA[index];
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new String(Arrays.copyOfRange(DATA, start, end));
        }
    }

    private static final MyChars MYCHARS = new MyChars();

    public void sanity() {
        StringBuilder sb = new StringBuilder();
        // prime the StringBuilder
        sb.append("repeat");

        // single character Latin1
        sb.repeat('1', 0);
        sb.repeat('2', 1);
        sb.repeat('3', 5);

        // single string Latin1 (optimized)
        sb.repeat("1", 0);
        sb.repeat("2", 1);
        sb.repeat("3", 5);

        // multi string Latin1
        sb.repeat("-1", 0);
        sb.repeat("-2", 1);
        sb.repeat("-3", 5);

        // single character UTF16
        sb.repeat('\u2460', 0);
        sb.repeat('\u2461', 1);
        sb.repeat('\u2462', 5);

        // single string UTF16 (optimized)
        sb.repeat("\u2460", 0);
        sb.repeat("\u2461", 1);
        sb.repeat("\u2462", 5);

        // multi string UTF16

        sb.repeat("-\u2460", 0);
        sb.repeat("-\u2461", 1);
        sb.repeat("-\u2462", 5);

        // CharSequence
        sb.repeat(MYCHARS, 3);

        // null
        sb.repeat((String)null, 0);
        sb.repeat((String)null, 1);
        sb.repeat((String)null, 5);
        sb.repeat((CharSequence)null, 0);
        sb.repeat((CharSequence)null, 1);
        sb.repeat((CharSequence)null, 5);


        String expected = "repeat233333233333-2-3-3-3-3-3\u2461\u2462\u2462\u2462\u2462\u2462\u2461\u2462\u2462\u2462\u2462\u2462-\u2461-\u2462-\u2462-\u2462-\u2462-\u2462abcabcabc" +
                          "nullnullnullnullnullnullnullnullnullnullnullnull";
        assertEquals(sb.toString(), expected);

        // Codepoints

        sb.setLength(0);

        sb.repeat(0, 0);
        sb.repeat(0, 1);
        sb.repeat(0, 5);
        sb.repeat((int)' ', 0);
        sb.repeat((int)' ', 1);
        sb.repeat((int)' ', 5);
        sb.repeat(0x2460, 0);
        sb.repeat(0x2461, 1);
        sb.repeat(0x2462, 5);
        sb.repeat(0x10FFFF, 0);
        sb.repeat(0x10FFFF, 1);
        sb.repeat(0x10FFFF, 5);

        expected = "\u0000\u0000\u0000\u0000\u0000\u0000\u0020\u0020\u0020\u0020\u0020\u0020\u2461\u2462\u2462\u2462\u2462\u2462\udbff\udfff\udbff\udfff\udbff\udfff\udbff\udfff\udbff\udfff\udbff\udfff";
        assertEquals(sb.toString(), expected);

    }

    public void exceptions() {
        StringBuilder sb = new StringBuilder();

        try {
            sb.repeat(' ', Integer.MAX_VALUE);
            throw new RuntimeException("No OutOfMemoryError thrown");
        } catch (OutOfMemoryError | IndexOutOfBoundsException ex) {
            // Okay
        }

        try {
            sb.repeat("    ", Integer.MAX_VALUE);
            throw new RuntimeException("No OutOfMemoryError thrown");
        } catch (OutOfMemoryError | IndexOutOfBoundsException ex) {
            // Okay
        }

        try {
            sb.repeat(MYCHARS, Integer.MAX_VALUE);
            throw new RuntimeException("No OutOfMemoryError thrown");
        } catch (OutOfMemoryError | IndexOutOfBoundsException ex) {
            // Okay
        }

        try {
            sb.repeat(' ', -1);
            throw new RuntimeException("No IllegalArgumentException thrown");
        } catch (IllegalArgumentException ex) {
            // Okay
        }

        try {
            sb.repeat("abc", -1);
            throw new RuntimeException("No IllegalArgumentException thrown");
        } catch (IllegalArgumentException ex) {
            // Okay
        }

        try {
            sb.repeat(MYCHARS, -1);
            throw new RuntimeException("No IllegalArgumentException thrown");
        } catch (IllegalArgumentException ex) {
            // Okay
        }

        try {
            sb.repeat(0x10FFFF + 1, -1);
            throw new RuntimeException("No IllegalArgumentException thrown");
        } catch (IllegalArgumentException ex) {
            // Okay
        }

        try {
            sb.repeat(-1, -1);
            throw new RuntimeException("No IllegalArgumentException thrown");
        } catch (IllegalArgumentException ex) {
            // Okay
        }

    }
}
