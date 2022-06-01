/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
* @summary Test vectorization of popcount for Long
* @requires vm.compiler2.enabled
* @requires vm.cpu.features ~= ".*avx512bw.*" | vm.cpu.features ~= ".*sve.*"
* @requires os.arch=="x86" | os.arch=="i386" | os.arch=="amd64" | os.arch=="x86_64" | os.arch=="aarch64"
* @library /test/lib /
* @run driver compiler.vectorization.TestPopCountVectorLong
*/

package compiler.vectorization;
import compiler.lib.ir_framework.*;
import java.util.Random;


public class TestPopCountVectorLong {
    private long[] input;
    private int[] output;
    private static final int LEN = 1024;
    private Random rng;

    public static void main(String args[]) {
        TestFramework.run(TestPopCountVectorLong.class);
    }

    public TestPopCountVectorLong() {
        input = new long[LEN];
        output = new int[LEN];
        rng = new Random(42);
        for (int i = 0; i < LEN; ++i) {
            input[i] = rng.nextLong();
        }
    }

    @Test // needs to be run in (fast) debug mode
    @Warmup(10000)
    @IR(counts = {"PopCountVL", ">= 1"}) // At least one PopCountVL node is generated if vectorization is successful
    public void vectorizeBitCount() {
        for (int i = 0; i < LEN; ++i) {
            output[i] = Long.bitCount(input[i]);
        }
        checkResult();
    }

    public void checkResult() {
        for (int i = 0; i < LEN; ++i) {
            int expected = Long.bitCount(input[i]);
            if (output[i] != expected) {
                throw new RuntimeException("Invalid result: output[" + i + "] = " + output[i] + " != " + expected);
            }
        }
    }
}

