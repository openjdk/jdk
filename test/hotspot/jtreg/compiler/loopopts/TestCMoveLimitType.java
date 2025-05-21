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

/*
 * @test
 * @key stress randomness
 * @bug 8299975
 * @summary Limit underflow protection CMoveINode in PhaseIdealLoop::do_unroll must also protect type from underflow
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions
 *                   -XX:+UnlockDiagnosticVMOptions -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,compiler.loopopts.TestCMoveLimitType::test*
 *                   -XX:CompileCommand=dontinline,compiler.loopopts.TestCMoveLimitType::dontInline
 *                   -XX:RepeatCompilation=50 -XX:+StressIGVN
 *                   -Xbatch
 *                   compiler.loopopts.TestCMoveLimitType
*/

/*
 * @test
 * @key stress randomness
 * @bug 8299975
 * @summary Limit underflow protection CMoveINode in PhaseIdealLoop::do_unroll must also protect type from underflow
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,compiler.loopopts.TestCMoveLimitType::test*
 *                   -XX:CompileCommand=dontinline,compiler.loopopts.TestCMoveLimitType::dontInline
 *                   -XX:RepeatCompilation=50 -XX:+StressIGVN
 *                   -XX:+IgnoreUnrecognizedVMOptions -Xcomp -XX:+TraceLoopOpts
 *                   compiler.loopopts.TestCMoveLimitType
*/

// Note: if this test fails too intermittently then increase the RepeatCompilation, at the cost of more runtime.

package compiler.loopopts;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;

public class TestCMoveLimitType {
    static int[] iArr = new int[10000];

    static int test_buf(CharBuffer src, ByteBuffer dst) {
        // code reduced from sun.nio.cs.ext.EUC_JP$Encoder::encodeBufferLoop
        int outputSize = 0;
        byte[] outputByte;
        byte[] tmpBuf = new byte[3];

        while (src.hasRemaining()) {
            outputByte = tmpBuf;
            char c = src.get();
            if (c % 3 == 0) {
                outputSize = -2147483648; // int:min -> leads to underflow
            } else {
                outputByte[0] = (byte) 0;
                outputByte[1] = (byte) 1;
                outputByte[2] = (byte) 2;
                outputSize = 3; // mostly 3, to get LOOP to unroll (profiling info)
            }
            if (dst.remaining() < outputSize) {
                return 102;
            }
            // outputSize: int:min..3 -> Phi limited to 0..2
            // PreMainPost: main loop Phi limited to 1..2
            // Unroll(2):
            // Phi of first iteration is 1..2
            // Phi of second is therefore const 2 -> collapse to constant.
            // Increment it by 1, get const int:3 -> incr value goes into exit condition.
            // This loop cannot take the backedge, the tripcount has collapsed.
            // Exit condition must therefore be constant folded, to make loop without phi disappear.
            // Exit limit is the CMoveI created in do_unroll (original limit -1).
            // It protects agains underflow (outputSize can be int:min).
            // CMoveI's type is int, because it does outputSize-1 -> underflow.
            // CmpI(int:3, int) cannot be constant folded, however.
            // Solution: insert CastII after CMoveI, to prevent type underflow.
            // Then we have CmpI(int:3, CastII(CMoveI)) = CmpI(int:3, int:<=3) = GE
            // -> Bool [lt] constant folds to false -> fixed
            for (int i = 0; i < outputSize; i++) { // LOOP
                dst.put(outputByte[i]);
            }
        }
        return 103;
    }

    static CharBuffer makeSrc() {
        CharBuffer src = CharBuffer.allocate(100);
        for (int j = 0; j < 100; j++) {
            if (j % 31 == 0) {
                src.put((char)(0 + (j%3)*3)); // some 0
            } else {
                src.put((char)(1 + (j%3)*3)); // mostly 2
            }
        }
        src.position(0);
        return src;
    }

    static void test_simple(boolean flag) {
        int x = flag ? Integer.MIN_VALUE : 3;
        dontInline();
        // x has type "int:min..3" == "<=3"
        // Leads to Peel and 2x Unroll
        // Exit check needs to collapse
        for (int i = 0; i < x; i++) {
            iArr[i * 2] = 666 + i;
        }
    }

    static void dontInline() {}

    static public void main(String[] args) {
        for (int i = 0; i < 10_000; i++) {
            try {
                test_simple(i % 2 == 0);
            } catch (Exception e) {}
        }
        for (int i = 0; i < 6_000; i++) {
            CharBuffer src = makeSrc();
            ByteBuffer dst = ByteBuffer.allocate(10_000);
            test_buf(src, dst); // call many times -> multiple compilations with different profiling
        }
    }
}
