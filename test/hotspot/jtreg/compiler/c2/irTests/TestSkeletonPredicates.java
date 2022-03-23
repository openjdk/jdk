/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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

package compiler.c2.irTests;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Utils;
import java.util.Random;

/*
 * @test
 * @bug 8278228
 * @summary C2: Improve identical back-to-back if elimination
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestSkeletonPredicates
 */

public class TestSkeletonPredicates {
    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:-UseLoopPredicate", "-XX:LoopUnrollLimit=240", "-XX:+StressIGVN", "-XX:StressSeed=255527877");
        TestFramework.runWithFlags("-XX:-UseLoopPredicate", "-XX:LoopUnrollLimit=240", "-XX:+StressIGVN");
    }

    static volatile int barrier;

    @ForceInline
    static boolean test1_helper(int start, int stop, double[] array1, double[] array2) {
        for (int i = start; i < stop; i++) {
            if ((i % 2) == 0) {
                array1[i] = 42.42;
            } else {
                barrier = 0x42;
            }
        }
        return false;
    }

    @Test
    @IR(counts = { IRNode.COUNTEDLOOP, "3" })
    static double[] test1(int stop, double[] array2) {
        double[] array1 = null;
        array1 = new double[10];
        for (int j = 0; j < stop; j++) {
            if (test1_helper(8, j, array1, array2)) {
                return null;
            }
        }
        return array1;
    }

    @Run(test = "test1")
    void test1_runner() {
        double[] array2 = new double[10];
        double[] array3 = new double[1000];
        test1_helper(1, 1000, array3, array3);
        test1(11, array3);
    }
}
