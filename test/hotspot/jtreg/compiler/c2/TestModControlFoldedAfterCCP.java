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
 * @bug 8359602
 * @summary The control input of a ModI node should be discarded if it is
 *          possible to prove that the divisor can never be 0.
 *          VerifyIterativeGVN checks that this optimization was applied
 * @run main/othervm -XX:CompileCommand=quiet -XX:-TieredCompilation
 *      -XX:+UnlockDiagnosticVMOptions -Xcomp -XX:+IgnoreUnrecognizedVMOptions
 *      -XX:CompileCommand=compileonly,compiler.c2.TestModControlFoldedAfterCCP::test
 *      -XX:VerifyIterativeGVN=1110 compiler.c2.TestModControlFoldedAfterCCP
 * @run main compiler.c2.TestModControlFoldedAfterCCP
 *
 */

package compiler.c2;

public class TestModControlFoldedAfterCCP {
    static void test() {
        int i22, i24 = -1191, i28;
        int iArr1[] = new int[1];
        for (int i = 1;i < 100; i++) {
            for (int j = 4; j > i; j--) {
                i22 = i24;

                // divisor is either -1191 or -13957
                iArr1[0] = 5 % i22;
            }
            for (i28 = i; i28 < 2; ++i28) {
                i24 = -13957;
            }
        }
    }

    public static void main(String[] args) {
        test();
    }
}