/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package compiler.c2.gvn;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;
import jdk.test.lib.Platform;
import jdk.test.lib.Utils;
import java.util.Random;

/*
 * @test
 * @bug 8378413
 * @key randomness
 * @summary Verify that URShift{I,L}Node::Ideal optimizes ((x << C) + y) >>> C
 *          regardless of Add input order, i.e. it is commutative w.r.t. the addition.
 * @library /test/lib /
 * @run main ${test.main.class}
 */
public class MissedURShiftIAddILShiftIdeal {

    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        var framework = new TestFramework();
        framework.addScenarios(new Scenario(0));
        if (Platform.isDebugBuild()) {
            framework.addScenarios(new Scenario(1, "-XX:+IgnoreUnrecognizedVMOptions", "-XX:VerifyIterativeGVN=1110"));
        }
        framework.start();
    }

    @Run(test = {"testI", "testICommuted", "testIComputedY",
                  "testL", "testLCommuted", "testLComputedY"})
    public void runMethod() {
        int xi = RANDOM.nextInt();
        int yi = RANDOM.nextInt();
        int ai = RANDOM.nextInt();
        int bi = RANDOM.nextInt();
        long xl = RANDOM.nextLong();
        long yl = RANDOM.nextLong();
        long al = RANDOM.nextLong();
        long bl = RANDOM.nextLong();

        assertResultI(xi, yi, ai, bi);
        assertResultL(xl, yl, al, bl);
    }

    @DontCompile
    public void assertResultI(int x, int y, int a, int b) {
        Asserts.assertEQ(((x << 3) + y) >>> 3, testI(x, y));
        Asserts.assertEQ((y + (x << 5)) >>> 5, testICommuted(x, y));
        Asserts.assertEQ(((x << 7) + (a ^ b)) >>> 7, testIComputedY(x, a, b));
    }

    @DontCompile
    public void assertResultL(long x, long y, long a, long b) {
        Asserts.assertEQ(((x << 9) + y) >>> 9, testL(x, y));
        Asserts.assertEQ((y + (x << 11)) >>> 11, testLCommuted(x, y));
        Asserts.assertEQ(((x << 13) + (a ^ b)) >>> 13, testLComputedY(x, a, b));
    }

    @Test
    // ((x << 3) + y) >>> 3  =>  (x + (y >>> 3)) & mask
    @IR(counts = {IRNode.LSHIFT_I,  "0",
                  IRNode.URSHIFT_I, "1",
                  IRNode.AND_I,     "1"})
    static int testI(int x, int y) {
        return ((x << 3) + y) >>> 3;
    }

    @Test
    // (y + (x << 5)) >>> 5  =>  (x + (y >>> 5)) & mask  (commuted Add)
    @IR(counts = {IRNode.LSHIFT_I,  "0",
                  IRNode.URSHIFT_I, "1",
                  IRNode.AND_I,     "1"})
    static int testICommuted(int x, int y) {
        return (y + (x << 5)) >>> 5;
    }

    @Test
    // ((x << 7) + (a ^ b)) >>> 7  =>  (x + ((a ^ b) >>> 7)) & mask
    // Computed y (a ^ b) has higher _idx than LShift, so LShift stays in Add's in(1).
    @IR(counts = {IRNode.LSHIFT_I,  "0",
                  IRNode.URSHIFT_I, "1",
                  IRNode.AND_I,     "1"})
    static int testIComputedY(int x, int a, int b) {
        return ((x << 7) + (a ^ b)) >>> 7;
    }

    @Test
    // ((x << 9) + y) >>> 9  =>  (x + (y >>> 9)) & mask
    @IR(counts = {IRNode.LSHIFT_L,  "0",
                  IRNode.URSHIFT_L, "1",
                  IRNode.AND_L,     "1"})
    static long testL(long x, long y) {
        return ((x << 9) + y) >>> 9;
    }

    @Test
    // (y + (x << 11)) >>> 11  =>  (x + (y >>> 11)) & mask  (commuted Add)
    @IR(counts = {IRNode.LSHIFT_L,  "0",
                  IRNode.URSHIFT_L, "1",
                  IRNode.AND_L,     "1"})
    static long testLCommuted(long x, long y) {
        return (y + (x << 11)) >>> 11;
    }

    @Test
    // ((x << 13) + (a ^ b)) >>> 13  =>  (x + ((a ^ b) >>> 13)) & mask
    // Computed y (a ^ b) has higher _idx than LShift, so LShift stays in Add's in(1).
    @IR(counts = {IRNode.LSHIFT_L,  "0",
                  IRNode.URSHIFT_L, "1",
                  IRNode.AND_L,     "1"})
    static long testLComputedY(long x, long a, long b) {
        return ((x << 13) + (a ^ b)) >>> 13;
    }
}
