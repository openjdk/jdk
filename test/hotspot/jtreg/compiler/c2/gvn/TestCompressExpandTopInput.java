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
 * @bug 8351645
 * @summary C2: ExpandBitsNode::Ideal hits assert because of TOP input
 * @library /test/lib /
 * @run driver compiler.intrinsics.TestCompressExpandTopInput
 */

package compiler.intrinsics;

import compiler.lib.ir_framework.*;
import compiler.lib.verify.*;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

public class TestCompressExpandTopInput {

    public static int [] array_I0 = IntStream.range(0, 10000).toArray();
    public static int [] array_I1 = IntStream.range(10000, 20000).toArray();
    public static long [] array_L0 = LongStream.range(0, 10000).toArray();
    public static long [] array_L1 = LongStream.range(10000, 20000).toArray();

    public static int oneI = 1;
    public static long oneL = 1L;

    public static long [] GOLD_COMPRESS_LONG = testCompressBitsLong();
    public static long [] GOLD_EXPAND_LONG = testExpandBitsLong();
    public static int [] GOLD_COMPRESS_INT = testCompressBitsInt();
    public static int [] GOLD_EXPAND_INT = testExpandBitsInt();

    @Test
    public static long[] testExpandBitsLong() {
        long[] out = new long[10000];
        for (int i = 0; i < out.length; i++) {
            long y = array_L0[i] % oneL;
            long x = (array_L1[i] | 4294967298L) << -7640610671680100954L;
            out[i] = Long.expand(y, x);
        }
        return out;
    }

    @Check(test="testExpandBitsLong")
    public static void checkExpandBitsLong(long [] actual) {
        for (int i = 0; i < GOLD_EXPAND_LONG.length; i++) {
            Verify.checkEQ(GOLD_EXPAND_LONG[i], actual[i]);
        }
    }

    @Test
    public static long[] testCompressBitsLong() {
        long[] out = new long[10000];
        for (int i = 0; i < out.length; i++) {
            long y = array_L0[i] % oneL;
            long x = (array_L1[i] | 4294967298L) << -7640610671680100954L;
            out[i] = Long.compress(y, x);
        }
        return out;
    }

    @Check(test="testCompressBitsLong")
    public static void checkCompressBitsLong(long [] actual) {
        for (int i = 0; i < GOLD_COMPRESS_LONG.length; i++) {
            Verify.checkEQ(GOLD_COMPRESS_LONG[i], actual[i]);
        }
    }

    @Test
    public static int[] testExpandBitsInt() {
        int[] out = new int[10000];
        for (int i = 0; i < out.length; i++) {
            int y = array_I0[i] % oneI;
            int x = (array_I1[i] | 22949672) << -76406101;
            out[i] = Integer.expand(y, x);
        }
        return out;
    }

    @Check(test="testExpandBitsInt")
    public static void checkExpandBitsInt(int [] actual) {
        for (int i = 0; i < GOLD_EXPAND_INT.length; i++) {
            Verify.checkEQ(GOLD_EXPAND_INT[i], actual[i]);
        }
    }

    @Test
    public static int[] testCompressBitsInt() {
        int[] out = new int[10000];
        for (int i = 0; i < out.length; i++) {
            int y = array_I0[i] % oneI;
            int x = (array_I1[i] | 429497) << -764061068;
            out[i] = Integer.compress(y, x);
        }
        return out;
    }

    @Check(test="testCompressBitsInt")
    public static void checkCompressBitsInt(int [] actual) {
        for (int i = 0; i < GOLD_COMPRESS_INT.length; i++) {
            Verify.checkEQ(GOLD_COMPRESS_INT[i], actual[i]);
        }
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:+StressIGVN");
    }
}
