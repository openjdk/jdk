/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8298118
 * @summary Test that the JVM doesn't fail due to split-if causing empty loop to temporarily have more than one phi
 *
 * @run main/othervm -Xcomp -XX:CompileCommand=compileonly,compiler.loopopts.TestEmptyLoopMultiplePhis::test
 *      compiler.loopopts.TestEmptyLoopMultiplePhis
 */

package compiler.loopopts;

public class TestEmptyLoopMultiplePhis {
    static int[] iArrFld = new int[10];
    static int x;

    static void test(int a) {
        int i;
        for (int j = 6; j < 129; ++j) {
            a >>= 37388;
            a += 1001;
        }
        a >>>= -8;
        for (i = 0; i < 8; ++i) {
            iArrFld[i] = i;
        }
        for (int j = i; j < 7; ) {
            x = a;
        }
    }

    public static void main(String[] strArr) {
        test(2);
    }
}
