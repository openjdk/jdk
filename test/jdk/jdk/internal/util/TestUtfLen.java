/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8366421
 * @summary Test for ModifiedUtf.utfLen() return type change from int to long to avoid overflow
 * @modules java.base/jdk.internal.util
 * @run main TestUtfLen
 */

import jdk.internal.util.ModifiedUtf;

public class TestUtfLen {
    private static final String ONE_BYTE   = "A";        // 1-byte UTF-8
    private static final String TWO_BYTE   = "\u0100";   // 2-byte UTF-8
    private static final String THREE_BYTE = "\u2600";   // 3-byte UTF-8

    public static void main(String[] args) {
        String chunk = ONE_BYTE + TWO_BYTE + THREE_BYTE;
        long perChunkLen = ModifiedUtf.utfLen(chunk, 0);
        if (perChunkLen != 6L) {
            throw new RuntimeException("Expected perChunkLen=6 but got " + perChunkLen);
        }

        int iterations = (Integer.MAX_VALUE / 6) + 1;
        long total = 0L;
        for (int i = 0; i < iterations; i++) {
            total += ModifiedUtf.utfLen(chunk, 0);
        }
        long expected = perChunkLen * iterations;
        if (total != expected) {
            throw new RuntimeException("Expected total=" + expected + " but got " + total);
        }
        System.out.println("PASSED");
    }
}