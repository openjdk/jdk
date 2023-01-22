/*
 * Copyright (c) 2022, BELLSOFT. All rights reserved.
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
* @bug 8153837
* @summary Test integer min and max intrinsics
* @requires vm.flavor == "server" & (vm.opt.TieredStopAtLevel == null | vm.opt.TieredStopAtLevel == 4)
* @library /test/lib /
* @modules java.base/jdk.internal.misc
*
* @build jdk.test.whitebox.WhiteBox
* @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
*
* @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
*                   -server -XX:-BackgroundCompilation -XX:-UseOnStackReplacement
*                   compiler.intrinsics.math.TestMinMaxIntrinsics
*/

package compiler.intrinsics.math;

import java.lang.reflect.Method;
import java.util.function.IntUnaryOperator;
import java.util.function.IntBinaryOperator;
import jdk.test.whitebox.WhiteBox;

import static jdk.test.lib.Asserts.assertEQ;
import static jdk.test.lib.Asserts.assertTrue;
import static compiler.whitebox.CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION;

public class TestMinMaxIntrinsics {

    static WhiteBox wb = WhiteBox.getWhiteBox();
    static int[] intCases = { Integer.MIN_VALUE, -2, -1, 0, 1, 2, Integer.MAX_VALUE };
    public static long im3l = Integer.MIN_VALUE * 3L;

    static void test(IntUnaryOperator std, IntUnaryOperator alt) throws ReflectiveOperationException {
        for (int a : intCases) {
            assertEQ(std.applyAsInt(a), alt.applyAsInt(a), String.format("Failed on %d", a));
        }
        var method = alt.getClass().getDeclaredMethod("applyAsInt", int.class);
        wb.enqueueMethodForCompilation(method, COMP_LEVEL_FULL_OPTIMIZATION);
        assertTrue(wb.isMethodCompiled(method));
        for (int a : intCases) {
            assertEQ(std.applyAsInt(a), alt.applyAsInt(a), String.format("Failed on %d", a));
        }
    }

    static void test(IntBinaryOperator std, IntBinaryOperator alt) throws ReflectiveOperationException {
        for (int a : intCases) {
            for (int b : intCases) {
                assertEQ(std.applyAsInt(a, b), alt.applyAsInt(a, b), String.format("Failed on %d, %d", a, b));
            }
        }
        var method = alt.getClass().getDeclaredMethod("applyAsInt", int.class, int.class);
        wb.enqueueMethodForCompilation(method, COMP_LEVEL_FULL_OPTIMIZATION);
        assertTrue(wb.isMethodCompiled(method));
        for (int a : intCases) {
            for (int b : intCases) {
                assertEQ(std.applyAsInt(a, b), alt.applyAsInt(a, b), String.format("Failed on %d, %d", a, b));
            }
        }
    }

    static int maxL2I(long a, int b) {
        return Math.max((int) a, b);
    }

    static void testL2I() throws NoSuchMethodException {
        assertEQ(0, maxL2I(im3l, 0));
        var method = TestMinMaxIntrinsics.class.getDeclaredMethod("maxL2I", long.class, int.class);
        wb.enqueueMethodForCompilation(method, COMP_LEVEL_FULL_OPTIMIZATION);
        assertTrue(wb.isMethodCompiled(method));
        assertEQ(0, maxL2I(im3l, 0));
    }

    public static void main(String[] args) throws Exception {
        test(a -> (a <= 0) ? a : 0, a -> Math.min(a, 0));
        test(a -> (a <= 1) ? a : 1, a -> Math.min(a, 1));
        test(a -> (a <= -1) ? a : -1, a -> Math.min(a, -1));

        test(a -> (0 >= a) ? 0 : a, a -> Math.max(0, a));
        test(a -> (1 >= a) ? 1 : a, a -> Math.max(1, a));
        test(a -> (-1 >= a) ? -1 : a, a -> Math.max(-1, a));

        test((a, b) -> (a <= b) ? a : b, (a, b) -> Math.min(a, b));
        test((a, b) -> (a >= b) ? a : b, (a, b) -> Math.max(a, b));

        testL2I();
    }
}
