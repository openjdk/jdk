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
import java.util.Objects;

/*
 * @test
 * @bug 8288022
 * @key randomness
 * @summary c2: Transform (CastLL (AddL into (AddL (CastLL when possible
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestPushAddThruCast
 */

public class TestPushAddThruCast {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        TestFramework.run();
    }

    final static int length = RANDOM.nextInt(5, Integer.MAX_VALUE);
    final static long llength = RANDOM.nextInt(2, Integer.MAX_VALUE);
    static int i;
    static long l;

    @Test
    @IR(counts = { IRNode.CAST_II, "2" })
    public static int test1() {
        int j = Objects.checkIndex(i, length);
        int k = Objects.checkIndex(i + 1, length);
        return j + k;
    }

    @Run(test = "test1")
    public static void test1_runner() {
        i = RANDOM.nextInt(length-1);
        int res = test1();
        if (res != i * 2 + 1) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    @Test
    @IR(counts = { IRNode.CAST_LL, "2" })
    public static long test2() {
        long j = Objects.checkIndex(l, llength);
        long k = Objects.checkIndex(l + 1, llength);
        return j + k;
    }

    @Run(test = "test2")
    public static void test2_runner() {
        l = RANDOM.nextInt(((int)llength)-1);
        long res = test2();
        if (res != l * 2 + 1) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    // Test commoning of Casts after loop opts when they are at the same control
    @Test
    @IR(phase = CompilePhase.ITER_GVN1, counts = { IRNode.CAST_II, "4" })
    @IR(phase = CompilePhase.OPTIMIZE_FINISHED, counts = { IRNode.CAST_II, "3" })
    public static int test3() {
        int j = Objects.checkIndex(i - 3, length);
        j += Objects.checkIndex(i, length);
        j += Objects.checkIndex(i - 2, length);
        j += Objects.checkIndex(i - 1, length);
        return j;
    }

    @Run(test = "test3")
    public static void test3_runner() {
        i = RANDOM.nextInt(3, length - 1);
        int res = test3();
        if (res != i * 4 - 6) {
            throw new RuntimeException("incorrect result: " + res + " for i = " + i);
        }
    }
}
