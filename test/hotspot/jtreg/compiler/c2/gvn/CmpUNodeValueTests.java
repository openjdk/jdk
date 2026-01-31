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

import compiler.lib.generators.Generators;
import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8375653
 * @summary Test that Value computations of CmpUNode are being performed as expected.
 * @library /test/lib /
 * @run driver ${test.main.class}
 */
public class CmpUNodeValueTests {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @DontInline
    public static int one() {
        return 1;
    }

    // Move to a separate method to prevent javac folding the constant comparison
    @ForceInline
    public static int oneInline() {
        return 1;
    }

    @Run(test = {"testIntControl", "testIntLT", "testIntLE", "testIntGT", "testIntGE", "testIntEQ", "testIntNE",
                 "testLongControl", "testLongLT", "testLongLE", "testLongGT", "testLongGE", "testLongEQ", "testLongNE"})
    public void run() {
        var stream = Generators.G.longs();
        long x = stream.next();
        long y = stream.next();

        Asserts.assertEQ(0, testIntControl(false, false));
        Asserts.assertEQ(0, testIntControl(false, true));
        Asserts.assertEQ(0, testIntControl(true, false));
        Asserts.assertEQ(1, testIntControl(true, true));
        Asserts.assertEQ(0, testIntLT((int) x));
        Asserts.assertEQ(0, testIntLE(false, false));
        Asserts.assertEQ(0, testIntLE(false, true));
        Asserts.assertEQ(0, testIntLE(true, false));
        Asserts.assertEQ(0, testIntLE(true, true));
        Asserts.assertEQ(0, testIntGT(false, false));
        Asserts.assertEQ(0, testIntGT(false, true));
        Asserts.assertEQ(0, testIntGT(true, false));
        Asserts.assertEQ(0, testIntGT(true, true));
        Asserts.assertEQ(0, testIntGE((int) x, (int) y));
        Asserts.assertEQ(0, testIntEQ());
        Asserts.assertEQ(0, testIntNE(false, false));
        Asserts.assertEQ(0, testIntNE(false, true));
        Asserts.assertEQ(0, testIntNE(true, false));
        Asserts.assertEQ(0, testIntNE(true, true));

        Asserts.assertEQ(0, testLongControl(false, false));
        Asserts.assertEQ(0, testLongControl(false, true));
        Asserts.assertEQ(0, testLongControl(true, false));
        Asserts.assertEQ(1, testLongControl(true, true));
        Asserts.assertEQ(0, testLongLT((int) x, false));
        Asserts.assertEQ(0, testLongLT((int) x, true));
        Asserts.assertEQ(0, testLongLE(false, false));
        Asserts.assertEQ(0, testLongLE(false, true));
        Asserts.assertEQ(0, testLongLE(true, false));
        Asserts.assertEQ(0, testLongLE(true, true));
        Asserts.assertEQ(0, testLongGT(false, false));
        Asserts.assertEQ(0, testLongGT(false, true));
        Asserts.assertEQ(0, testLongGT(true, false));
        Asserts.assertEQ(0, testLongGT(true, true));
        Asserts.assertEQ(0, testLongGE((int) x, (int) y));
        Asserts.assertEQ(0, testLongEQ());
        Asserts.assertEQ(0, testLongNE(false, false));
        Asserts.assertEQ(0, testLongNE(false, true));
        Asserts.assertEQ(0, testLongNE(true, false));
        Asserts.assertEQ(0, testLongNE(true, true));
    }

    @Test
    @IR(applyIfPlatformOr = {"x64", "true", "aarch64", "true"}, counts = {IRNode.CMP_U, "1"})
    int testIntControl(boolean b1, boolean b2) {
        int v1 = b1 ? 1 : -1;
        int v2 = b2 ? 1 : 0;
        return Integer.compareUnsigned(v1, v2) > 0 ? 0 : one();
    }

    @Test
    @IR(applyIfPlatformOr = {"x64", "true", "aarch64", "true"}, failOn = {IRNode.CMP_U, IRNode.CALL})
    int testIntLT(int x) {
        int v1 = x & Integer.MAX_VALUE; // Unset the highest bit, make v1 non-negative
        int v2 = Integer.MIN_VALUE;
        return Integer.compareUnsigned(v1, v2) < 0 ? 0 : one();
    }

    @Test
    @IR(applyIfPlatformOr = {"x64", "true", "aarch64", "true"}, failOn = {IRNode.CMP_U, IRNode.CALL})
    int testIntLE(boolean b1, boolean b2) {
        int v1 = b1 ? 2 : 0;
        int v2 = b2 ? -1 : 2;
        return Integer.compareUnsigned(v1, v2) <= 0 ? 0 : one();
    }

    @Test
    @IR(applyIfPlatformOr = {"x64", "true", "aarch64", "true"}, failOn = {IRNode.CMP_U, IRNode.CALL})
    int testIntGT(boolean b1, boolean b2) {
        int v1 = b1 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        int v2 = b2 ? 0 : 2;
        return Integer.compareUnsigned(v1, v2) > 0 ? 0 : one();
    }

    @Test
    @IR(applyIfPlatformOr = {"x64", "true", "aarch64", "true"}, failOn = {IRNode.CMP_U, IRNode.CALL})
    int testIntGE(int x, int y) {
        int v1 = x | Integer.MIN_VALUE; // Set the highest bit, make v1 negative
        int v2 = y & Integer.MAX_VALUE; // Unset the highest bit, make v2 non-negative
        return Integer.compareUnsigned(v1, v2) >= 0 ? 0 : one();
    }

    @Test
    @IR(applyIfPlatformOr = {"x64", "true", "aarch64", "true"}, failOn = {IRNode.CMP_U, IRNode.CALL})
    int testIntEQ() {
        return Integer.compareUnsigned(oneInline(), 1) == 0 ? 0 : one();
    }

    @Test
    @IR(applyIfPlatformOr = {"x64", "true", "aarch64", "true"}, failOn = {IRNode.CMP_U, IRNode.CALL})
    int testIntNE(boolean b1, boolean b2) {
        int v1 = b1 ? 1 : -1;
        int v2 = b2 ? 0 : 2;
        return Integer.compareUnsigned(v1, v2) != 0 ? 0 : one();
    }

    @Test
    @IR(applyIfPlatformOr = {"x64", "true", "aarch64", "true"}, counts = {IRNode.CMP_UL, "1"})
    int testLongControl(boolean b1, boolean b2) {
        long v1 = b1 ? 1 : -1;
        long v2 = b2 ? 1 : 0;
        return Long.compareUnsigned(v1, v2) > 0 ? 0 : one();
    }


    @Test
    @IR(applyIfPlatformOr = {"x64", "true", "aarch64", "true"}, failOn = {IRNode.CMP_UL, IRNode.CALL})
    int testLongLT(int x, boolean b2) {
        long v1 = Integer.toUnsignedLong(x);
        long v2 = Integer.MIN_VALUE;
        return Long.compareUnsigned(v1, v2) < 0 ? 0 : one();
    }

    @Test
    @IR(applyIfPlatformOr = {"x64", "true", "aarch64", "true"}, failOn = {IRNode.CMP_UL, IRNode.CALL})
    int testLongLE(boolean b1, boolean b2) {
        long v1 = b1 ? 2 : 0;
        long v2 = b2 ? -1 : 2;
        return Long.compareUnsigned(v1, v2) <= 0 ? 0 : one();
    }

    @Test
    @IR(applyIfPlatformOr = {"x64", "true", "aarch64", "true"}, failOn = {IRNode.CMP_UL, IRNode.CALL})
    int testLongGT(boolean b1, boolean b2) {
        long v1 = b1 ? Long.MIN_VALUE : Long.MAX_VALUE;
        long v2 = b2 ? 0 : 2;
        return Long.compareUnsigned(v1, v2) > 0 ? 0 : one();
    }

    @Test
    @IR(applyIfPlatformOr = {"x64", "true", "aarch64", "true"}, failOn = {IRNode.CMP_UL, IRNode.CALL})
    int testLongGE(int x, int y) {
        long v1 = x | Long.MIN_VALUE; // Set the highest bit, make v1 negative
        long v2 = y & Long.MAX_VALUE; // Unset the highest bit, make v2 non-negative
        return Long.compareUnsigned(v1, v2) >= 0 ? 0 : one();
    }

    @Test
    @IR(applyIfPlatformOr = {"x64", "true", "aarch64", "true"}, failOn = {IRNode.CMP_UL, IRNode.CALL})
    int testLongEQ() {
        return Long.compareUnsigned(oneInline(), 1L) == 0 ? 0 : one();
    }

    @Test
    @IR(applyIfPlatformOr = {"x64", "true", "aarch64", "true"}, failOn = {IRNode.CMP_UL, IRNode.CALL})
    int testLongNE(boolean b1, boolean b2) {
        long v1 = b1 ? 1 : -1;
        long v2 = b2 ? 0 : 2;
        return Long.compareUnsigned(v1, v2) != 0 ? 0 : one();
    }
}
