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
 * @bug 8359603
 * @summary Redundant ConvD2L->ConvL2D->ConvD2L sequences should be
 *          simplified to a single ConvD2L operation
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:CompileCommand=quiet
 *      -XX:CompileCommand=compileonly,compiler.c2.TestRedundantConvD2LElimination::test
 *      -XX:-TieredCompilation -Xbatch -XX:VerifyIterativeGVN=1110 compiler.c2.TestRedundantConvD2LElimination
 * @run main compiler.c2.TestRedundantConvD2LElimination
 *
 */

package compiler.c2;

public class TestRedundantConvD2LElimination {
    static long instanceCount;

    static void test(double d) {
        int i = 1;
        int j = 1;
        while (++i < 3) {
            for (; 8 > j; ++j) {
                instanceCount = (long)d;
                d = instanceCount;
            }
        }
    }
    public static void main(String[] strArr) {
        for (int i = 0; i < 50_000; ++i) {
            test(1);
        }
    }
}