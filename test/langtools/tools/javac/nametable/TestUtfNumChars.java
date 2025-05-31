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

public class TestUtfNumChars {

    public static void main(String[] args) {
        String s = "f\u00f8\u00f8bar";          // "føøbar"
        byte[] utf8 = Convert.string2utf(s);    // UTF-8: 66 c3 b8 c3 b8 62 61 72
                                                // Bytes: 00 01 02 03 04 05 06 07
                                                // Chars: 00 01 -- 02 -- 03 04 05
        int[] offsets = new int[] {
            0, 1, 3, 5, 6, 7
        };

        for (int i = 0; i < offsets.length; i++) {
            int i_off = offsets[i];
            for (int j = i; j < offsets.length; j++) {
                int j_off = offsets[j];
                int nchars = Convert.utfNumChars(utf8, i_off, j_off - i_off);
                if (nchars != j - i)
                    throw new AssertionError(String.format("nchars is %d != %d", nchars, j - i));
            }
        }
    }
}
