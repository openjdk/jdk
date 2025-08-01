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
 * @bug 8358066
 * @summary Test for bug in Convert.utfNumChars()
 * @modules jdk.compiler/com.sun.tools.javac.util
 * @run main TestUtfNumChars
 */

import com.sun.tools.javac.util.Convert;

import java.util.function.IntPredicate;
import java.util.stream.IntStream;

public class TestUtfNumChars {

    public static void main(String[] args) {

        // This is the string "ab«cd≤ef🟢gh"
        String s = "ab\u00ABcd\u2264ef\ud83d\udd34gh";

        // This is its modified UTF-8 encoding
        byte[] utf8 = Convert.string2utf(s);    // UTF-8: 61 62 c2 ab 63 64 e2 89 a4 65 66 ed a0 bd ed b4 b4 67 68
                                                // Bytes: 00 01 02 03 04 05 06 07 08 09 10 11 12 13 14 15 16 17 18
                                                // Chars: 00 01 02 .. 03 04 05 .. .. 06 07 08 .. .. 09 .. .. 10 11

        // These are the offsets in "utf8" marking the boundaries of encoded Java charcters
        int[] offsets = new int[] {
            0, 1, 2, 4, 5, 6, 9, 10, 11, 14, 17, 18
        };
        IntPredicate boundary = off -> off == utf8.length || IntStream.of(offsets).anyMatch(off2 -> off2 == off);

        // Check Convert.utfNumChars() on every subsequence
        for (int i = 0; i < offsets.length; i++) {
            int i_off = offsets[i];
            if (!boundary.test(i_off))
                continue;
            for (int j = i; j < offsets.length; j++) {
                int j_off = offsets[j];
                if (!boundary.test(j_off))
                    continue;
                int nchars = Convert.utfNumChars(utf8, i_off, j_off - i_off);
                if (nchars != j - i)
                    throw new AssertionError(String.format("nchars %d != %d for [%d, %d)", nchars, j - i, i_off, j_off));
            }
        }
    }
}
