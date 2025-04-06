/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8339303
 * @summary Test that the matcher does not create dead nodes when matching
 *          address expressions with two immediate offsets.
 * @requires os.maxMemory > 4G
 *
 * @run main/othervm -Xmx4g -Xbatch -XX:-TieredCompilation
 *      -XX:CompileOnly=compiler.c2.TestMatcherTwoImmOffsets::test
 *      compiler.c2.TestMatcherTwoImmOffsets
 */

package compiler.c2;

public class TestMatcherTwoImmOffsets {
    static final int[] a1 = new int[10];
    int[] a2;
    static TestMatcherTwoImmOffsets o = new TestMatcherTwoImmOffsets();

    public static void test() {
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 100; j++) {
                int[][] nArray = new int[10][];
                for (int k = 0; k < nArray.length; k++) {}
            }
            for (long j = 1L; j < 3L; j++) {
                a1[(int) j]--;
            }
            o.a2 = a1;
        }
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10; i++) {
            test();
        }
    }
}
