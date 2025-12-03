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
 * @test id=all-flags-fixed-stress-seed
 * @bug 8371146
 * @summary Test where the pre_init was pinned before the pre-loop but after the
 *          Auto_Vectorization_Check, and so it should not be used for the auto
 *          vectorization aliasing check, to avoid a bad (circular) graph.
 * @requires vm.gc.Z
 * @run main/othervm
 *      -XX:+IgnoreUnrecognizedVMOptions
 *      -XX:CompileCommand=compileonly,*TestAliasingCheckPreLimitNotAvailable::test
 *      -XX:-TieredCompilation
 *      -Xcomp
 *      -XX:+UseZGC
 *      -XX:+UnlockDiagnosticVMOptions -XX:+StressLoopPeeling -XX:StressSeed=4
 *      -XX:LoopUnrollLimit=48
 *      compiler.loopopts.superword.TestAliasingCheckPreLimitNotAvailable
 */

/*
 * @test id=all-flags-no-stress-seed
 * @bug 8371146
 * @requires vm.gc.Z
 * @run main/othervm
 *      -XX:+IgnoreUnrecognizedVMOptions
 *      -XX:CompileCommand=compileonly,*TestAliasingCheckPreLimitNotAvailable::test
 *      -XX:-TieredCompilation
 *      -Xcomp
 *      -XX:+UseZGC
 *      -XX:+UnlockDiagnosticVMOptions -XX:+StressLoopPeeling
 *      -XX:LoopUnrollLimit=48
 *      compiler.loopopts.superword.TestAliasingCheckPreLimitNotAvailable
 */

/*
 * @test id=fewer-flags
 * @bug 8371146
 * @run main/othervm
 *      -XX:+IgnoreUnrecognizedVMOptions
 *      -XX:CompileCommand=compileonly,*TestAliasingCheckPreLimitNotAvailable::test
 *      -XX:-TieredCompilation
 *      -Xcomp
 *      -XX:LoopUnrollLimit=48
 *      compiler.loopopts.superword.TestAliasingCheckPreLimitNotAvailable
 */

/*
 * @test id=minimal-flags
 * @bug 8371146
 * @run main/othervm
 *      -XX:+IgnoreUnrecognizedVMOptions
 *      -XX:CompileCommand=compileonly,*TestAliasingCheckPreLimitNotAvailable::test
 *      -XX:-TieredCompilation
 *      -Xcomp
 *      compiler.loopopts.superword.TestAliasingCheckPreLimitNotAvailable
 */

/*
 * @test id=vanilla
 * @bug 8371146
 * @run main compiler.loopopts.superword.TestAliasingCheckPreLimitNotAvailable
 */

package compiler.loopopts.superword;

public class TestAliasingCheckPreLimitNotAvailable {
    static int sum;
    static boolean condition;
    static int zero;
    static int twoDimensional[][] = new int[20][20];

    static void test() {
        int innerCount = 0;
        int conditionCount = 0;
        int oneDimensional[] = new int[10];
        for (int i = 2; i > 0; --i) {
            for (int j = i; j < 10; j++) {
                innerCount += 1;
                oneDimensional[1] += innerCount;
                oneDimensional[j] += zero;
                if (condition) {
                    conditionCount += 1;
                    oneDimensional[1] += conditionCount;
                    sum += oneDimensional[1];
                }
                twoDimensional[j] = twoDimensional[j + 1];
            }
        }
    }

    public static void main(String[] args) {
        test();
    }
}
