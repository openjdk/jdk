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
import jdk.test.lib.Utils;

import java.util.Random;

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

    @Run(test = {"testInt1", "testInt2", "testInt3", "testInt4",
                 "testLong1", "testLong2", "testLong3", "testLong4", "testLong5"})
    public void run() {
        Random r = Utils.getRandomInstance();
        long x = r.nextLong();
        long y = r.nextLong();

        Asserts.assertEQ(0, testInt1(false, false));
        Asserts.assertEQ(0, testInt1(false, true));
        Asserts.assertEQ(0, testInt1(true, false));
        Asserts.assertEQ(0, testInt1(true, true));
        Asserts.assertEQ(0, testInt2(false, false));
        Asserts.assertEQ(0, testInt2(false, true));
        Asserts.assertEQ(0, testInt2(true, false));
        Asserts.assertEQ(0, testInt2(true, true));
        Asserts.assertEQ(0, testInt3((int) x, (int) y));
        Asserts.assertEQ(0, testInt4());
        Asserts.assertEQ(0, testLong1(false, false));
        Asserts.assertEQ(0, testLong1(false, true));
        Asserts.assertEQ(0, testLong1(true, false));
        Asserts.assertEQ(0, testLong1(true, true));
        Asserts.assertEQ(0, testLong2(false, false));
        Asserts.assertEQ(0, testLong2(false, true));
        Asserts.assertEQ(0, testLong2(true, false));
        Asserts.assertEQ(0, testLong2(true, true));
        Asserts.assertEQ(0, testLong3(x, y));
        Asserts.assertEQ(0, testLong4());
        Asserts.assertEQ(0, testLong5((int) x, false));
        Asserts.assertEQ(0, testLong5((int) x, true));
    }

    @Test
    @IR(applyIfPlatformOr = {"x64", "true", "aarch64", "true"}, failOn = {IRNode.CMP_U, IRNode.CALL})
    int testInt1(boolean b1, boolean b2) {
        int v1 = b1 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        int v2 = b2 ? 0 : 2;
        return Integer.compareUnsigned(v1, v2) <= 0 ? one() : 0;
    }

    @Test
    @IR(applyIfPlatformOr = {"x64", "true", "aarch64", "true"}, failOn = {IRNode.CMP_U, IRNode.CALL})
    int testInt2(boolean b1, boolean b2) {
        int v1 = b1 ? 2 : 0;
        int v2 = b2 ? -1 : 2;
        return Integer.compareUnsigned(v1, v2) > 0 ? one() : 0;
    }

    @Test
    @IR(applyIfPlatformOr = {"x64", "true", "aarch64", "true"}, failOn = {IRNode.CMP_U, IRNode.CALL})
    int testInt3(int x, int y) {
        int v1 = x | Integer.MIN_VALUE; // Set the highest bit, make v1 negative
        int v2 = y & Integer.MAX_VALUE; // Unset the highest bit, make v2 non-negative
        return Integer.compareUnsigned(v1, v2) <= 0 ? one() : 0;
    }

    @Test
    @IR(applyIfPlatformOr = {"x64", "true", "aarch64", "true"}, failOn = {IRNode.CMP_U, IRNode.CALL})
    int testInt4() {
        return Integer.compareUnsigned(oneInline(), 1) != 0 ? one() : 0;
    }

    @Test
    @IR(applyIfPlatformOr = {"x64", "true", "aarch64", "true"}, failOn = {IRNode.CMP_UL, IRNode.CALL})
    int testLong1(boolean b1, boolean b2) {
        long v1 = b1 ? Long.MIN_VALUE : Long.MAX_VALUE;
        long v2 = b2 ? 0 : 2;
        return Long.compareUnsigned(v1, v2) <= 0 ? one() : 0;
    }

    @Test
    @IR(applyIfPlatformOr = {"x64", "true", "aarch64", "true"}, failOn = {IRNode.CMP_UL, IRNode.CALL})
    int testLong2(boolean b1, boolean b2) {
        long v1 = b1 ? 2 : 0;
        long v2 = b2 ? -1 : 2;
        return Long.compareUnsigned(v1, v2) > 0 ? one() : 0;
    }

    @Test
    @IR(applyIfPlatformOr = {"x64", "true", "aarch64", "true"}, failOn = {IRNode.CMP_UL, IRNode.CALL})
    int testLong3(long x, long y) {
        long v1 = x | Long.MIN_VALUE; // Set the highest bit, make v1 negative
        long v2 = y & Long.MAX_VALUE; // Unset the highest bit, make v2 non-negative
        return Long.compareUnsigned(v1, v2) <= 0 ? one() : 0;
    }

    @Test
    @IR(applyIfPlatformOr = {"x64", "true", "aarch64", "true"}, failOn = {IRNode.CMP_UL, IRNode.CALL})
    int testLong4() {
        return Long.compareUnsigned(oneInline(), 1L) != 0 ? one() : 0;
    }

    @Test
    @IR(applyIfPlatformOr = {"x64", "true", "aarch64", "true"}, failOn = {IRNode.CMP_UL, IRNode.CALL})
    int testLong5(int x, boolean b2) {
        long v1 = Integer.toUnsignedLong(x);
        long v2 = b2 ? Integer.toUnsignedLong(-1) : -1L;
        return Long.compareUnsigned(v1, v2) > 0 ? one() : 0;
    }
}
