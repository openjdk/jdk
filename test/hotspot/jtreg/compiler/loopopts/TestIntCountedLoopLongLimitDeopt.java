/*
 * Copyright (c) 2026 IBM and/or its affiliates. All rights reserved.
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

package compiler.loopopts;

import compiler.lib.generators.Generator;
import compiler.whitebox.CompilerWhiteBoxTest;
import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;

import java.lang.reflect.Method;
import java.net.*;
import java.nio.file.Paths;

import static compiler.lib.generators.Generators.*;

/*
 * @test
 * @bug 8336759
 * @key randomness
 * @summary Test long limits in int counted loops are speculatively converted
 *          to int for counted loop optimizations - deoptimization traps.
 * @requires vm.compiler2.enabled
 * @requires (vm.opt.StressLongCountedLoop == null | vm.opt.StressLongCountedLoop != 0)
 * @requires (vm.opt.PerMethodTrapLimit == null | vm.opt.PerMethodTrapLimit >= 5)
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:-BackgroundCompilation
 *                   ${test.main.class}
 */
public class TestIntCountedLoopLongLimitDeopt {

    private static final Generator<Long> SMALL_INT_RANGE_LONGS = G.uniformLongs(0, 1024 * 1024 - 1);
    private static final int LARGE_STRIDE = Integer.MAX_VALUE / 1024 / 1024;

    public static void main(String[] args) throws Exception {
        testDeoptimizations();
    }

    public static int testCountedLoopWithOverflow(int init, long limit) {
        int sum = 0;
        for (int i = init; i < limit; i += LARGE_STRIDE) {
            sum += LARGE_STRIDE;

            if (i < 0) {
                return -1; // overflow detected!
            }
        }
        return sum;
    }

    public static int testCountedLoopWithUnderflow(int init, long limit) {
        int sum = 0;
        for (int i = init; i > limit; i -= LARGE_STRIDE) {
            sum -= LARGE_STRIDE;

            if (i > 0) {
                return 1; // underflow detected!
            }
        }
        return sum;
    }

    // Test speculative narrowing handles missing Loop Limit Check Parse Predicate. After the first
    // compilation deoptimizes (limit out of int range), the parse predicate is not regenerated on
    // recompilation. The loop must gracefully fall back to a non-counted loop instead of crashing.
    public static int testEarlyReturn(long limit) {
        int sum = 0;
        for (int i = 0; i < limit; i++) {
            sum += i;
            if (sum > 100) {
                return sum;
            }
        }
        return sum;
    }

    // Test that an out-of-int-range long limit directly triggers the loop_limit_check trap.
    // Early exit at i >= 100 bounds iteration count after deopt.
    public static int testOutOfRangeLongLimit(long limit) {
        int sum = 0;
        for (int i = 0; i < limit; i++) {
            sum += i;
            if (i >= 100) return sum;
        }
        return sum;
    }

    public static int testEarlyBreak(long limit) {
        int sum = 0;
        for (int i = 0; i < limit; i++) {
            sum += i;
            if (sum > 100) {
                break;
            }
        }
        return sum;
    }

    private static void assertIsCompiled(Method m) {
        if (!WhiteBox.getWhiteBox().isMethodCompiled(m) || WhiteBox.getWhiteBox().getMethodCompilationLevel(m) != CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION) {
            throw new AssertionError("should still be compiled");
        }
    }

    private static void assertIsNotCompiled(Method m) {
        if (WhiteBox.getWhiteBox().isMethodCompiled(m) && WhiteBox.getWhiteBox().getMethodCompilationLevel(m) == CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION) {
            throw new AssertionError("should have been deoptimized");
        }
    }

    private static void compile(Method m) {
        WhiteBox.getWhiteBox().enqueueMethodForCompilation(m, CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION);
        assertIsCompiled(m);
    }

    public static ClassLoader newClassLoader() {
        try {
            return new URLClassLoader(new URL[]{
                    Paths.get(System.getProperty("test.classes", ".")).toUri().toURL(),
            }, null);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Unexpected URL conversion failure", e);
        }
    }

    // Compile the method with a known "good" value that doesn't trap, then invoke it with a "bad" value that should
    // cause a deoptimization and trap. Assert the method is deoptimized after the trap.
    // Note: -XX:-BackgroundCompilation is required
    private static void assertShouldTrap(Method method, Object[] compilingArgs, Object[] trappingArgs, int expectedCompilingResult, int expectedTrappingResult) throws Exception {
        Class<?> c = newClassLoader().loadClass(TestIntCountedLoopLongLimitDeopt.class.getName());
        Method m = c.getDeclaredMethod(method.getName(), method.getParameterTypes());
        int observed;

        // compile for the fast path
        assertIsNotCompiled(m); // COMP_LEVEL_NONE, interpreter
        observed = (int) m.invoke(null, compilingArgs); // run once so all classes are loaded, COMP_LEVEL_FULL_PROFILE, C1
        Asserts.assertEQ(expectedCompilingResult, observed);
        compile(m); // COMP_LEVEL_FULL_OPTIMIZATION, C2

        // observe de-optimization with trapping value
        observed = (int) m.invoke(null, trappingArgs); // trapped, COMP_LEVEL_FULL_PROFILE, C1
        Asserts.assertEQ(expectedTrappingResult, observed);
        assertIsNotCompiled(m); // should deoptimize

        // compile and invoke again to make sure trap was properly recorded
        compile(m); // COMP_LEVEL_FULL_OPTIMIZATION, C2
        observed = (int) m.invoke(null, trappingArgs); // should not trap this time
        Asserts.assertEQ(expectedTrappingResult, observed);
        assertIsCompiled(m); // no de-opt
    }

    private static void testDeoptimizations() throws Exception {
        long compileArg = (SMALL_INT_RANGE_LONGS.next() + 1) * LARGE_STRIDE; // compile with a known "good" value that doesn't trap
        int init = G.uniformInts(0, 10).next();

        Method testCountedLoopWithOverflow = TestIntCountedLoopLongLimitDeopt.class.getDeclaredMethod("testCountedLoopWithOverflow", int.class, long.class);
        // Although Integer.MAX_VALUE is within int range, it still always traps. LARGE_STRIDE causes the IV to overflow past it.
        assertShouldTrap(testCountedLoopWithOverflow, new Object[]{ init, compileArg }, new Object[]{ init, (long) Integer.MAX_VALUE }, (int) compileArg, -1);
        assertShouldTrap(testCountedLoopWithOverflow, new Object[]{ init, compileArg }, new Object[]{ init, (long) Integer.MAX_VALUE + 1L }, (int) compileArg, -1);
        assertShouldTrap(testCountedLoopWithOverflow, new Object[]{ init, compileArg }, new Object[]{ init, (long) Integer.MAX_VALUE + compileArg }, (int) compileArg, -1);

        Method testCountedLoopWithUnderflow = TestIntCountedLoopLongLimitDeopt.class.getDeclaredMethod("testCountedLoopWithUnderflow", int.class, long.class);
        // Similar, Integer.MIN_VALUE always traps with IV underflow.
        assertShouldTrap(testCountedLoopWithUnderflow, new Object[]{ -init, -compileArg }, new Object[]{ -init, (long) Integer.MIN_VALUE }, (int) -compileArg, 1);
        assertShouldTrap(testCountedLoopWithUnderflow, new Object[]{ -init, -compileArg }, new Object[]{ -init, (long) Integer.MIN_VALUE - 1L }, (int) -compileArg, 1);
        assertShouldTrap(testCountedLoopWithUnderflow, new Object[]{ -init, -compileArg }, new Object[]{ -init, (long) Integer.MIN_VALUE - compileArg }, (int) -compileArg, 1);

        // Test that an out-of-int-range limit triggers the loop_limit_check trap.
        // Compile with limit=14: sum(0..13) = 91, never hits early exit.
        // Trap with limit=MAX+1: deopt, interpreter runs, early exit at i=100: sum(0..100) = 5050.
        Method testOutOfRangeLongLimit = TestIntCountedLoopLongLimitDeopt.class.getDeclaredMethod("testOutOfRangeLongLimit", long.class);
        assertShouldTrap(testOutOfRangeLongLimit, new Object[]{ 14L }, new Object[]{ (long) Integer.MAX_VALUE + 1L }, 91, 5050);

        // Test loops with early exits (return/break). After deopt, the Loop Limit Check Parse Predicate is not
        // regenerated. Speculative narrowing must bail out gracefully instead of asserting.
        Method testEarlyReturn = TestIntCountedLoopLongLimitDeopt.class.getDeclaredMethod("testEarlyReturn", long.class);
        assertShouldTrap(testEarlyReturn, new Object[]{ 42L }, new Object[]{ (long) Integer.MAX_VALUE + 1L }, 105, 105);

        Method testEarlyBreak = TestIntCountedLoopLongLimitDeopt.class.getDeclaredMethod("testEarlyBreak", long.class);
        assertShouldTrap(testEarlyBreak, new Object[]{ 42L }, new Object[]{ (long) Integer.MAX_VALUE + 1L }, 105, 105);
    }
}
