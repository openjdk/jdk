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
package compiler.c2.gvn;

import compiler.lib.generators.Generator;
import compiler.lib.generators.Generators;
import compiler.lib.ir_framework.DontCompile;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Run;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8356813
 * @summary Test that Value method of ModINode is working as expected.
 * @library /test/lib /
 * @run driver compiler.c2.gvn.ModINodeValueTests
 */
public class ModINodeValueTests {
    private static final Generator<Integer> G = Generators.G.ints();

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {
        "nonNegativeDividend", "nonNegativeDividendInRange",
        "negativeDividend", "negativeDividendInRange",
        "modByKnownBoundsUpper", "modByKnownBoundsUpperInRange",
        "modByKnownBoundsLower", "modByKnownBoundsLowerInRange",
        "modByKnownBoundsLimitedByDividendUpper", "modByKnownBoundsLimitedByDividendUpperInRange",
        "modByKnownBoundsLimitedByDividendLower", "modByKnownBoundsLimitedByDividendLowerInRange"
    })
    public void runMethod() {
        int a = G.next();
        int b = G.next();

        int min = Integer.MIN_VALUE;
        int max = Integer.MAX_VALUE;

        assertResult(0, 0);
        assertResult(a, b);
        assertResult(min, min);
        assertResult(max, max);
    }

    @DontCompile
    public void assertResult(int x, int y) {
        Asserts.assertEQ(x != 0 && 123 % x < 0, nonNegativeDividend(x));
        Asserts.assertEQ(x != 0 && 123 % x <= 0, nonNegativeDividendInRange(x));
        Asserts.assertEQ(x != 0 && -123 % x > 0, negativeDividend(x));
        Asserts.assertEQ(x != 0 && -123 % x >= 0, negativeDividendInRange(x));
        Asserts.assertEQ(x % (((byte) y) + 129) > 255, modByKnownBoundsUpper(x, y));
        Asserts.assertEQ(x % (((byte) y) + 129) >= 255, modByKnownBoundsUpperInRange(x, y));
        Asserts.assertEQ(x % (((byte) y) + 129) < -255, modByKnownBoundsLower(x, y));
        Asserts.assertEQ(x % (((byte) y) + 129) <= -255, modByKnownBoundsLowerInRange(x, y));
        Asserts.assertEQ(((byte) x) % (((char) y) + 1) > 127, modByKnownBoundsLimitedByDividendUpper(x, y));
        Asserts.assertEQ(((byte) x) % (((char) y) + 1) >= 127, modByKnownBoundsLimitedByDividendUpperInRange(x, y));
        Asserts.assertEQ(((byte) x) % (((char) y) + 1) < -128, modByKnownBoundsLimitedByDividendLower(x, y));
        Asserts.assertEQ(((byte) x) % (((char) y) + 1) <= -128, modByKnownBoundsLimitedByDividendLowerInRange(x, y));
    }

    @Test
    @IR(failOn = {IRNode.MOD_I, IRNode.CMP_I})
    // The sign of the result of % is the same as the sign of the dividend,
    // i.e., posVal % x < 0 => false.
    public boolean nonNegativeDividend(int x) {
        return x != 0 && 123 % x < 0;
    }

    @Test
    @IR(counts = {IRNode.MOD_I, "1"})
    // The sign of the result of % is the same as the sign of the dividend,
    // i.e., posVal % x < 0 => false.
    // This uses <= to verify the % is not optimized away
    public boolean nonNegativeDividendInRange(int x) {
        return x != 0 && 123 % x <= 0;
    }

    @Test
    @IR(failOn = {IRNode.MOD_I, IRNode.CMP_I})
    // The sign of the result of % is the same as the sign of the dividend,
    // i.e., negValue % x > 0 => false.
    public boolean negativeDividend(int x) {
        return x != 0 && -123 % x > 0;
    }

    @Test
    @IR(counts = {IRNode.MOD_I, "1"})
    // The sign of the result of % is the same as the sign of the dividend,
    // i.e., negValue % x > 0 => false.
    // This uses >= to verify the % is not optimized away
    public boolean negativeDividendInRange(int x) {
        return x != 0 && -123 % x >= 0;
    }

    @Test
    @IR(failOn = {IRNode.MOD_I, IRNode.CMP_I})
    // The magnitude of the result is less than the divisor.
    public boolean modByKnownBoundsUpper(int x, int y) {
        // d = ((byte) y) + 129 => [1, 256] divisor
        // x % d => [-255, 255]
        return x % (((byte) y) + 129) > 255;
    }

    @Test
    @IR(counts = {IRNode.MOD_I, "1"})
    // The magnitude of the result is less than the divisor.
    public boolean modByKnownBoundsUpperInRange(int x, int y) {
        // d = ((byte) y) + 129 => [1, 256] divisor
        // x % d => [-255, 255]
        // in bounds, cannot optimize
        return x % (((byte) y) + 129) >= 255;
    }

    @Test
    @IR(failOn = {IRNode.MOD_I, IRNode.CMP_I})
    // The magnitude of the result is less than the divisor
    public boolean modByKnownBoundsLower(int x, int y) {
        // d = ((byte) y) + 129 => [1, 256] divisor
        // x % d => [-255, 255]
        return x % (((byte) y) + 129) < -255;
    }

    @Test
    @IR(counts = {IRNode.MOD_I, "1"})
    // The magnitude of the result is less than the divisor
    public boolean modByKnownBoundsLowerInRange(int x, int y) {
        // d = ((byte) y) + 129 => [1, 256] divisor
        // x % d => [-255, 255]
        // in bounds, cannot optimize
        return x % (((byte) y) + 129) <= -255;
    }

    @Test
    @IR(failOn = {IRNode.MOD_I, IRNode.CMP_I})
    // The result is closer to zero than or equal to the dividend.
    public boolean modByKnownBoundsLimitedByDividendUpper(int x, int y) {
        // d = ((char) y) + 1 => [1, 65536] divisor
        // e = ((byte) x) => [-128, 127]
        // e % d => [-128, 127]
        return ((byte) x) % (((char) y) + 1) > 127;
    }

    @Test
    @IR(counts = {IRNode.MOD_I, "1"})
    // The result is closer to zero than or equal to the dividend.
    public boolean modByKnownBoundsLimitedByDividendUpperInRange(int x, int y) {
        // d = ((char) y) + 1 => [1, 65536] divisor
        // e = ((byte) x) => [-128, 127]
        // e % d => [-128, 127]
        // in bounds, cannot optimize
        return ((byte) x) % (((char) y) + 1) >= 127;
    }

    @Test
    @IR(failOn = {IRNode.MOD_I, IRNode.CMP_I})
    // The result is closer to zero than or equal to the dividend.
    public boolean modByKnownBoundsLimitedByDividendLower(int x, int y) {
        // d = ((char) y) + 1 => [1, 65536] divisor
        // e = ((byte) x) => [-128, 127]
        // e % d => [-128, 127]
        return ((byte) x) % (((char) y) + 1) < -128;
    }

    @Test
    @IR(counts = {IRNode.MOD_I, "1"})
    // The result is closer to zero than or equal to the dividend.
    public boolean modByKnownBoundsLimitedByDividendLowerInRange(int x, int y) {
        // d = ((char) y) + 1 => [1, 65536] divisor
        // e = ((byte) x) => [-128, 127]
        // e % d => [-128, 127]
        // in bounds, cannot optimize
        return ((byte) x) % (((char) y) + 1) <= -128;
    }
}
