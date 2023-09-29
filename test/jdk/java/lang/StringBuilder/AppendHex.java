/*
 * Copyright (c) 2003, 2020, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @summary Test StringBuilder.appendHex sanity tests
 * @run testng/othervm -XX:-CompactStrings AppendHex
 * @run testng/othervm -XX:+CompactStrings AppendHex
 */
@Test
public class AppendHex {
    public void appendHex() {
        int[] ints = new int[] {
                0x1,
                0x12,
                0x123,
                0x1234,
                0x12345,
                0x123456,
                0x1234567,
                0x12345678
        };
        for (int i = 0; i < ints.length; i++) {
            int v = ints[i];
            assertEquals(
                    Integer.toHexString(v),
                    new StringBuilder().appendHex(v).toString());

            // utf16
            String prefix = "\u8336";
            assertEquals(
                    prefix + Integer.toHexString(v),
                    new StringBuilder(prefix).appendHex(v).toString());
        }

        long[] longs = new long[] {
                0x1,
                0x12,
                0x123,
                0x1234,
                0x12345,
                0x123456,
                0x1234567,
                0x12345678,
                0x123456789L,
                0x123456789aL,
                0x123456789abL,
                0x123456789abcL,
                0x123456789abcdL,
                0x123456789abcdeL,
                0x123456789abcdefL,
                0x123456789abcdef1L,
        };
        for (int i = 0; i < longs.length; i++) {
            long v = longs[i];
            assertEquals(
                    Long.toHexString(v),
                    new StringBuilder().appendHex(v).toString());

            // utf16
            String prefix = "\u8336";
            assertEquals(
                    prefix + Long.toHexString(v),
                    new StringBuilder(prefix).appendHex(v).toString());
        }
    }
}
