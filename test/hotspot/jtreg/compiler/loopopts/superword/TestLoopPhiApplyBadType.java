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
 * @test id=all-flags-1
 * @bug 8371065
 * @summary A bug in VTransformLoopPhiNode::apply led us to copy the type of phi->in(1)
 *          which we did not expect to ever be a constant, but always the full type range.
 *          Setting the phi type to that value meant that the phi would wrongly constant
 *          fold, and lead to wrong results.
 * @run main/othervm
 *      -XX:+IgnoreUnrecognizedVMOptions
 *      -XX:CompileCommand=compileonly,*TestLoopPhiApplyBadType::test*
 *      -XX:CompileCommand=dontinline,*TestLoopPhiApplyBadType::notInlined
 *      -XX:-TieredCompilation
 *      -XX:-UseOnStackReplacement
 *      -XX:-BackgroundCompilation
 *      -XX:PartialPeelNewPhiDelta=1
 *      -Xbatch
 *      -XX:+UnlockDiagnosticVMOptions
 *      -XX:+StressIGVN
 *      -XX:StressSeed=212574406
 *      compiler.loopopts.superword.TestLoopPhiApplyBadType
 */

/*
 * @test id=fewer-flags-1
 * @bug 8371065
 * @run main/othervm
 *      -XX:+IgnoreUnrecognizedVMOptions
 *      -XX:CompileCommand=compileonly,*TestLoopPhiApplyBadType::test*
 *      -XX:CompileCommand=dontinline,*TestLoopPhiApplyBadType::notInlined
 *      -XX:-TieredCompilation
 *      -XX:-UseOnStackReplacement
 *      -XX:-BackgroundCompilation
 *      -XX:PartialPeelNewPhiDelta=1
 *      -Xbatch
 *      -XX:+UnlockDiagnosticVMOptions
 *      -XX:+StressIGVN
 *      compiler.loopopts.superword.TestLoopPhiApplyBadType
 */

/*
 * @test id=all-flags-2
 * @bug 8371065 8371472
 * @run main/othervm
 *      -XX:+IgnoreUnrecognizedVMOptions
 *      -XX:CompileCommand=compileonly,*TestLoopPhiApplyBadType::test*
 *      -XX:CompileCommand=dontinline,*TestLoopPhiApplyBadType::notInlined
 *      -XX:-TieredCompilation
 *      -Xbatch
 *      -XX:+UnlockDiagnosticVMOptions
 *      -XX:+StressIGVN
 *      -XX:StressSeed=3497198372
 *      compiler.loopopts.superword.TestLoopPhiApplyBadType
 */

/*
 * @test id=fewer-flags-2
 * @bug 8371065 8371472
 * @run main/othervm
 *      -XX:+IgnoreUnrecognizedVMOptions
 *      -XX:CompileCommand=compileonly,*TestLoopPhiApplyBadType::test*
 *      -XX:CompileCommand=dontinline,*TestLoopPhiApplyBadType::notInlined
 *      -XX:-TieredCompilation
 *      -Xbatch
 *      -XX:+UnlockDiagnosticVMOptions
 *      -XX:+StressIGVN
 *      compiler.loopopts.superword.TestLoopPhiApplyBadType
 */

/*
 * @test id=minimal-flags
 * @bug 8371065
 * @run main/othervm
 *      -XX:+IgnoreUnrecognizedVMOptions
 *      -XX:CompileCommand=compileonly,*TestLoopPhiApplyBadType::test*
 *      -XX:CompileCommand=dontinline,*TestLoopPhiApplyBadType::notInlined
 *      compiler.loopopts.superword.TestLoopPhiApplyBadType
 */

/*
 * @test id=no-flags
 * @bug 8371065
 * @run main compiler.loopopts.superword.TestLoopPhiApplyBadType
 */

package compiler.loopopts.superword;

public class TestLoopPhiApplyBadType {
    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            int[] array = test1();
            int j;
            boolean abort = false;
            for (j = 0; j < array.length-2; j++) {
                if (array[j] != (j | 1)) {
                    System.out.println("For " + j + " " + array[j]);
                    abort = true;
                }
            }
            for (; j < array.length; j++) {
                if (array[j] != j) {
                    System.out.println("For " + j + " " + array[j]);
                    abort = true;
                }
            }
            if (abort) {
                throw new RuntimeException("Failure");
            }
        }

        int gold2 = test2();
        for (int i = 0; i < 10; i++) {
            int res = test2();
            if (gold2 != res) {
                throw new RuntimeException("Wrong value: " + res + " vs " + gold2);
            }
        }
    }

    private static int[] test1() {
        int limit = 2;
        for (; limit < 4; limit *= 2);
        int k = limit / 4;

        int[] array = new int[1000];
        int[] flags = new int[1000];
        int[] flags2 = new int[1000];
        int j;
        for (j = 0; j < 10; j += k) {

        }
        notInlined(array);
        for (int i = 0; ; i++) {
            synchronized (new Object()) {}
            int ii = Integer.min(Integer.max(i, 0), array.length-1);
            int v = array[ii];
            if (flags2[ii] * (j-10) != 0) {
                throw new RuntimeException("never taken" + v);
            }
            if (i * k >= array.length - 2) {
                break;
            }
            if (flags[ii]*(j-10)  != 0) {
                throw new RuntimeException("never taken");
            }
            array[ii] = v | 1;
        }
        return array;
    }

    static int test2() {
        int arr[] = new int[400];
        int x = 34;
        for (int i = 1; i < 50; i++) {
            for (int k = 3; 201 > k; ++k) {
                x += Math.min(k, arr[k - 1]);
            }
        }
        return x;
    }

    private static void notInlined(int[] array) {
        for (int i = 0; i < array.length; i++) {
            array[i] = i;
        }
    }
}
