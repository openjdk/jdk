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
 * @bug 8334228
 * @summary Test sorting of VPointer by offset, when subtraction of two offsets can overflow.
 * @run main/othervm -XX:CompileCommand=compileonly,compiler.vectorization.TestOffsetSorting::test -Xcomp compiler.vectorization.TestOffsetSorting
 * @run main compiler.vectorization.TestOffsetSorting
 */

package compiler.vectorization;

public class TestOffsetSorting {
    static int RANGE = 10_000;

    public static void main(String[] args) {
        int[] a = new int[RANGE];
        for (int i = 0; i < 10_000; i++) {
            try {
                test(a, 0);
                throw new RuntimeException("test should go out-of-bounds");
            } catch (ArrayIndexOutOfBoundsException e) {
            }
        }
    }

    static void test(int[] a, int invar) {
        int large = (1 << 28) + (1 << 20);
        for (int i = 0; i < 1_000; i++) {
            a[i + invar - large] = 42;
            a[i + invar + large] = 42;
        }
    }
}
