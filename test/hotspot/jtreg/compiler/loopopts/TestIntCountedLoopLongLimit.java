/*
 * Copyright (c) 2026 Red Hat and/or its affiliates. All rights reserved.
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
import compiler.lib.ir_framework.*;
import compiler.whitebox.CompilerWhiteBoxTest;
import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;
import jtreg.SkippedException;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;

import static compiler.lib.generators.Generators.*;

/**
 * @test
 * @bug 8336759
 * @summary test long limits in int counted loops are speculatively converted to int for counted loop
 *         optimizations
 * @requires vm.compiler2.enabled
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   ${test.main.class} testIr
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:-BackgroundCompilation
 *                   ${test.main.class} testDeoptimizations
 */
public class TestIntCountedLoopLongLimit {
    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    // Random longs within int range. Choose small numbers to avoid tests taking too long
    private static final Generator<Long> SMALL_UNIFORMS = G.uniformLongs(0, 1024 * 1024 - 1);

    // Random longs outside the int range. Choose numbers close to the int limits to avoid tests taking too long.
    private static final Generator<Long> LARGE_UNIFORMS = G.uniformLongs((long) Integer.MAX_VALUE + 1, (long) Integer.MAX_VALUE + (1024 * 1024));

    // Use a larger stride to avoid tests taking too long
    private static final int LARGE_STRIDE = Integer.MAX_VALUE / 1024 / 1024;
    private static volatile long SOME_LONG = 42;

    public static void main(String[] args) throws Exception {
        if ((long) WHITE_BOX.getVMFlag("StressLongCountedLoop") != 0 ||
                (long) WHITE_BOX.getVMFlag("PerMethodTrapLimit") < 5) {
            throw new SkippedException("Must disable StressLongCountedLoop and have at least 5 PerMethodTrapLimit");
        }

        switch (args.length > 0 ? args[0] : "") {
            case "testIr":
                TestFramework.run();
                break;
            case "testDeoptimizations":
                testDeoptimizations();
                break;
            default:
                throw new IllegalArgumentException("Unknown test selection. Check @run commands");
        }
    }

    /* Since fuzzers are unlikely to generate int loops with long limits to trigger this optimization, we need to be
     * careful when writing test cases. Currently, these test cases cover:
     *   1. Correctness vs baseline: same trip count as a pure int limit using random small long limits
     *   2. Compare shape and operand order: `limit > i` vs. `i < limit`
     *   3. Other loop opts: combined with loops that are IV-replaced
     *   4. Out-of-int range: using large strides to tests overflow/underflow without excessive test time
     *   5. Negative case: limit reassigned in the loop so the limit is not loop invariant
     *   6. Function call as limit: MemorySegment#byteSize() which is a real-world pattern that could occur
     *   7. Edge values for limits: Integer.MAX_VALUE, Integer.MAX_VALUE + 1L, Integer.MIN_VALUE, Integer.MIN_VALUE - 1L
     *   8. Compilation assertion on deoptimization with the WhiteBox API that traps are working
     *
     * What more to consider:
     *   1. More loop exit patterns: !=, <=, >= (however, should be irrelevant to the optimization code)
     *   2. Large limit with small stride: will lead to unreason loop running time when not C2 compiled
     *
     * Since JDK-8336759 only adds additional traps outside the loops and does little graph modification on the loop
     * itself, these should be sufficient to catch any regression without testing on more complex loop structures.
     */
    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "2" }) // Make sure IR tests can pick up counted loops.
    @IR(failOn = { IRNode.LOOP })
    public static int testControlledCountedLoop(int limit) {
        int sum = 0;
        for (int i = 0; i < limit; i++) {
            sum += i;
        }
        return sum;
    }

    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "2" })
    @IR(failOn = { IRNode.LOOP })
    public static int testCountedLoopWithLongLimit(long limit) {
        int sum = 0;
        for (int i = 0; i < limit; i++) {
            sum += i;
        }
        return sum;
    }

    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "2" })
    @IR(failOn = { IRNode.LOOP })
    public static int testCountedLoopWithSwappedComparisonOperand(long limit) {
        int sum = 0;
        for (int i = 0; limit > i; i++) {
            sum += i;
        }
        return sum;
    }

    // Test counted loops, regardless of limit types, are correctly constructed.
    @Run(test = { "testControlledCountedLoop", "testCountedLoopWithLongLimit",
            "testCountedLoopWithSwappedComparisonOperand" })
    public static void runTestSimpleCountedLoops() {
        long limit = SMALL_UNIFORMS.next();
        int expected = testControlledCountedLoop((int) limit);
        int observed1 = testCountedLoopWithLongLimit(limit);
        int observed2 = testCountedLoopWithSwappedComparisonOperand(limit);

        Asserts.assertEQ(expected, observed1);
        Asserts.assertEQ(expected, observed2);
    }

    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP }) // Eliminated by IR replacement
    public static int testIvReplacedCountedLoop(long limit) {
        int sum = 0;
        for (int i = 0; i < limit; i++) {
            sum += 1;
        }
        return sum;
    }

    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP }) // Eliminated by IR replacement
    public static long testLongIvReplacedCountedLoop(long limit) {
        long sum = 0;
        for (int i = 0; i < limit; i++) {
            sum += 1;
        }
        return sum;
    }

    // Test counted loops with int and long IV types, are corrected constructed, IV replaced, and eliminated.
    @Run(test = { "testIvReplacedCountedLoop", "testLongIvReplacedCountedLoop" })
    public static void runTestIvReplacedCountedLoop() {
        long limit = SMALL_UNIFORMS.next();

        Asserts.assertEQ(limit, (long) testIvReplacedCountedLoop(limit));
        Asserts.assertEQ(limit, testLongIvReplacedCountedLoop(limit));
    }

    // Test counted loop deoptimizes if the long limit falls outside int range.
    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP })
    public static int testCountedLoopWithOverflow(long limit) {
        int sum = 0;
        for (int i = 0; i < limit; i += LARGE_STRIDE) {
            sum += LARGE_STRIDE;

            if (i < 0) {
                return -1; // overflow detected!
            }
        }
        return sum;
    }

    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP })
    public static int testCountedLoopWithUnderflow(long limit) {
        int sum = 0;
        for (int i = 0; i > limit; i -= LARGE_STRIDE) {
            sum -= LARGE_STRIDE;

            if (i > 0) {
                return 1; // underflow detected!
            }
        }
        return sum;
    }

    @Run(test = { "testCountedLoopWithOverflow", "testCountedLoopWithUnderflow" })
    public static void runTestCountedLoopWithOverflow() {
        long limit = SMALL_UNIFORMS.next() * LARGE_STRIDE; // within int range, no over/underflow

        Asserts.assertEQ((int) limit, testCountedLoopWithOverflow(limit));
        Asserts.assertEQ((int) -limit, testCountedLoopWithUnderflow(-limit));

        // See testDeoptimizations for traps on slow path with over/underflows
    }

    // Test optimization is not applied if the limit is not invariant.
    // This is handled by the existing counted loop detection, but we might as well test it here, too.
    @Test
    @IR(counts = { IRNode.CONV_I2L, "1" })
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.CONV_L2I })
    @Arguments(values = { Argument.NUMBER_42 })
    public static int testLimitNotInvariant(long limit) {
        int sum = 0;
        for (int i = 0; i < limit; i++) {
            sum += 1;
            limit = SOME_LONG;
        }
        return sum;
    }

    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "4" })
    @IR(failOn = { IRNode.LOOP })
    public static int testMemorySegmentSizeLimit(MemorySegment segment) {
        int sum = 0;
        for (int i = 0; i < segment.byteSize(); i++) {
            sum += segment.get(ValueLayout.JAVA_BYTE, i);
        }
        return sum;
    }

    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "2" })
    @IR(failOn = { IRNode.LOOP })
    public static int testWithConstantLongLimit() {
        int sum = 0;
        for (int i = 0; i < 1024L; i++) {
            sum += i;
        }
        return sum;
    }

    @Run(test = { "testMemorySegmentSizeLimit" })
    public static void runTestMemorySegmentSizeLimit() {
        MemorySegment segment = Arena.ofAuto().allocate(1024);
        segment.fill((byte) 1);

        Asserts.assertEQ(1024, testMemorySegmentSizeLimit(segment));
    }

    private static void assertIsCompiled(Method m) {
        if (!WHITE_BOX.isMethodCompiled(m) || WHITE_BOX.getMethodCompilationLevel(m) != CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION) {
            throw new AssertionError("should still be compiled");
        }
    }

    private static void assertIsNotCompiled(Method m) {
        if (WHITE_BOX.isMethodCompiled(m) && WHITE_BOX.getMethodCompilationLevel(m) == CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION) {
            throw new AssertionError("should have been deoptimized");
        }
    }

    private static void compile(Method m) {
        WHITE_BOX.enqueueMethodForCompilation(m, CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION);
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
    private static void assertShouldTrap(Method method, long compileArg, long trappingArg) throws Exception {
        Class<?> c = newClassLoader().loadClass(TestIntCountedLoopLongLimit.class.getName());
        Method m = c.getDeclaredMethod(method.getName(), method.getParameterTypes());

        // compile for the fast path
        assertIsNotCompiled(m); // COMP_LEVEL_NONE, interpreter
        m.invoke(null, compileArg); // run once so all classes are loaded, COMP_LEVEL_FULL_PROFILE, C1
        compile(m); // COMP_LEVEL_FULL_OPTIMIZATION, C2

        // observe de-optimization with trapping value
        m.invoke(null, trappingArg); // trapped, COMP_LEVEL_FULL_PROFILE, C1
        assertIsNotCompiled(m); // should deoptimize

        // compile again to make sure trap was properly recorded
        m.invoke(null, trappingArg);
        compile(m); // COMP_LEVEL_FULL_OPTIMIZATION, C2
    }

    private static void testDeoptimizations() throws Exception {
        long compileArg = SMALL_UNIFORMS.next() * LARGE_STRIDE; // compile with a known "good" value that doesn't trap

        Method testCountedLoopWithOverflow = TestIntCountedLoopLongLimit.class.getDeclaredMethod("testCountedLoopWithOverflow", long.class);
        assertShouldTrap(testCountedLoopWithOverflow, compileArg, (long) Integer.MAX_VALUE);
        assertShouldTrap(testCountedLoopWithOverflow, compileArg, (long) Integer.MAX_VALUE + 1L);
        assertShouldTrap(testCountedLoopWithOverflow, compileArg, (long) Integer.MAX_VALUE + compileArg);

        Method testCountedLoopWithUnderflow = TestIntCountedLoopLongLimit.class.getDeclaredMethod("testCountedLoopWithUnderflow", long.class);
        assertShouldTrap(testCountedLoopWithUnderflow, -compileArg, (long) Integer.MIN_VALUE);
        assertShouldTrap(testCountedLoopWithUnderflow, -compileArg, (long) Integer.MIN_VALUE - 1L);
        assertShouldTrap(testCountedLoopWithUnderflow, -compileArg, (long) Integer.MIN_VALUE - compileArg);
    }
}
