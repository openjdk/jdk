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

import compiler.lib.compile_framework.CompileFramework;
import compiler.lib.generators.Generator;
import compiler.lib.ir_framework.*;
import compiler.lib.template_framework.*;
import compiler.lib.template_framework.library.TestFrameworkClass;
import compiler.whitebox.CompilerWhiteBoxTest;
import jdk.test.lib.*;
import jdk.test.whitebox.WhiteBox;
import jtreg.SkippedException;

import java.lang.foreign.*;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.file.Paths;
import java.util.*;

import static compiler.lib.generators.Generators.*;
import static compiler.lib.template_framework.Template.*;

/**
 * @test
 * @bug 8336759
 * @summary test long limits in int counted loops are speculatively converted to int for counted loop
 * optimizations
 * @requires vm.compiler2.enabled
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 * ${test.main.class} testIr
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:-BackgroundCompilation
 * ${test.main.class} testDeoptimizations
 * @run driver/timeout=600 ${test.main.class} testTemplated
 * @run driver/timeout=600 ${test.main.class} testTemplatedStress
 */
public class TestIntCountedLoopLongLimit {

    // Random longs within int range. Choose small numbers to avoid tests taking too long
    private static final Generator<Long> SMALL_UNIFORMS = G.uniformLongs(0, 1024 * 1024 - 1);

    // Use a larger stride to avoid tests taking too long
    private static final int LARGE_STRIDE = Integer.MAX_VALUE / 1024 / 1024;
    private static volatile long SOME_LONG = 42;

    public static void main(String[] args) throws Exception {
        switch (args.length > 0 ? args[0] : "") {
            case "testIr":
                checkWhiteBoxPreconditions();
                TestFramework.run();
                break;
            case "testDeoptimizations":
                checkWhiteBoxPreconditions();
                testDeoptimizations();
                break;
            case "testTemplated":
                testTemplated(new String[]{});
                break;
            case "testTemplatedStress":
                if (!Platform.isDebugBuild()) {
                    throw new SkippedException("Stress flags used here are debug-only");
                }
                testTemplated(new String[]{
                        "-XX:StressLongCountedLoop=1",
                        "-XX:+StressCountedLoop",
                        "-XX:+StressShortRunningLongLoop",
                        "-XX:+StressIGVN",
                        "-XX:+StressCCP"
                });
                break;
            default:
                throw new IllegalArgumentException("Unknown test selection. Check @run commands");
        }
    }

    private static void checkWhiteBoxPreconditions() {
        if ((long) WhiteBox.getWhiteBox().getVMFlag("StressLongCountedLoop") != 0 ||
                (long) WhiteBox.getWhiteBox().getVMFlag("PerMethodTrapLimit") < 5) {
            throw new SkippedException("Must disable StressLongCountedLoop and have at least 5 PerMethodTrapLimit");
        }
    }

    /* Since fuzzers are unlikely to generate int loops with long limits to trigger this optimization, we need to be
     * careful when writing test cases. The hand-written tests below cover IR shape verification and deoptimization
     * traps. The templated tests (testTemplated, testTemplatedStress) cover correctness across all comparison
     * operators (<, <=, >, >=), swapped operands, multiple strides, random init values, boundary limits
     * (Integer.MAX_VALUE, MIN_VALUE, and slightly out-of-range), and zero/one-iteration edge cases.
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
    @IR(counts = { IRNode.COUNTED_LOOP, "2" })
    @IR(failOn = { IRNode.LOOP })
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

    @Test
    @IR(counts = { IRNode.COUNTED_LOOP, "2" })
    @IR(failOn = { IRNode.LOOP })
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

    @Run(test = { "testCountedLoopWithOverflow", "testCountedLoopWithUnderflow" })
    public static void runTestCountedLoopWithOverflow() {
        long trips = SMALL_UNIFORMS.next();
        int init = G.uniformInts(0, 10).next() * LARGE_STRIDE;
        long limit = init + trips * LARGE_STRIDE; // within int range, no over/underflow

        Asserts.assertEQ((int) (trips * LARGE_STRIDE), testCountedLoopWithOverflow(init, limit));
        Asserts.assertEQ((int) -(trips * LARGE_STRIDE), testCountedLoopWithUnderflow(-init, -limit));

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
    @IR(counts = { IRNode.COUNTED_LOOP, ">=2" })
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
        Class<?> c = newClassLoader().loadClass(TestIntCountedLoopLongLimit.class.getName());
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
        long compileArg = (SMALL_UNIFORMS.next() + 1) * LARGE_STRIDE; // compile with a known "good" value that doesn't trap
        int init = G.uniformInts(0, 10).next();

        Method testCountedLoopWithOverflow = TestIntCountedLoopLongLimit.class.getDeclaredMethod("testCountedLoopWithOverflow", int.class, long.class);
        // Although Integer.MAX_VALUE is within int range, it still always traps. LARGE_STRIDE causes the IV to overflow past it.
        assertShouldTrap(testCountedLoopWithOverflow, new Object[]{ init, compileArg }, new Object[]{ init, (long) Integer.MAX_VALUE }, (int) compileArg, -1);
        assertShouldTrap(testCountedLoopWithOverflow, new Object[]{ init, compileArg }, new Object[]{ init, (long) Integer.MAX_VALUE + 1L }, (int) compileArg, -1);
        assertShouldTrap(testCountedLoopWithOverflow, new Object[]{ init, compileArg }, new Object[]{ init, (long) Integer.MAX_VALUE + compileArg }, (int) compileArg, -1);

        Method testCountedLoopWithUnderflow = TestIntCountedLoopLongLimit.class.getDeclaredMethod("testCountedLoopWithUnderflow", int.class, long.class);
        // Similar, Integer.MIN_VALUE always traps with IV underflow.
        assertShouldTrap(testCountedLoopWithUnderflow, new Object[]{ -init, -compileArg }, new Object[]{ -init, (long) Integer.MIN_VALUE }, (int) -compileArg, 1);
        assertShouldTrap(testCountedLoopWithUnderflow, new Object[]{ -init, -compileArg }, new Object[]{ -init, (long) Integer.MIN_VALUE - 1L }, (int) -compileArg, 1);
        assertShouldTrap(testCountedLoopWithUnderflow, new Object[]{ -init, -compileArg }, new Object[]{ -init, (long) Integer.MIN_VALUE - compileArg }, (int) -compileArg, 1);
    }

    // Templated and randomized tests cover the following:
    //   - All comparison operators: <, <=, >, >=
    //   - Swapped operands: (long) i < limit vs. limit > (long) i
    //   - Multiple strides: 1, 3, and a random
    //   - Random init values
    //   - Boundary limits: Integer.MAX_VALUE, MIN_VALUE, and slightly out-of-range
    //   - Zero-iteration and one-iteration edge cases
    //   - StressLongCountedLoop=1 stress testing (testTemplatedStress)
    //
    // Comparison operator != is excluded: with a long limit outside int range, int i can never equal it, so the loop
    // runs until MAX_ITERATIONS is triggered; in which case, the test would not be meaningful since it only tests the
    // guard. For limits within int range, != behaves like < or > and is already covered.
    //
    // In total, 24 generated tests are per run: 4 ops x 2 swaps x 3 strides.
    enum CmpOp {
        LT("<", ">", true),
        LE("<=", ">=", true),
        GT(">", "<", false),
        GE(">=", "<=", false);

        final String symbol;
        final String mirror;
        final boolean countingUp;

        CmpOp(String symbol, String mirror, boolean countingUp) {
            this.symbol = symbol;
            this.mirror = mirror;
            this.countingUp = countingUp;
        }
    }

    private static void testTemplated(String[] vmFlags) {
        CompileFramework comp = new CompileFramework();
        comp.addJavaSourceCode("compiler.loopopts.templated.IntCountedLoopLongLimit", generate(comp));
        comp.compile();
        comp.invoke("compiler.loopopts.templated.IntCountedLoopLongLimit", "main", new Object[]{vmFlags});
    }

    private static String generate(CompileFramework comp) {
        List<TemplateToken> tests = new ArrayList<>();

        var sharedFields = Template.make(() -> scope(
                """
                private static final Generators G = Generators.G;
                """));
        tests.add(sharedFields.asToken());

        int randomStride = G.uniformInts(2, 100).next();
        for (CmpOp op : CmpOp.values()) {
            for (boolean swap : new boolean[]{false, true}) {
                for (int stride : new int[]{1, 3, randomStride}) {
                    tests.add(generateLoopTest(op, swap, stride));
                }
            }
        }

        return TestFrameworkClass.render(
                "compiler.loopopts.templated", "IntCountedLoopLongLimit",
                Set.of("java.util.Arrays",
                        "compiler.lib.generators.Generators"),
                comp.getEscapedClassPathOfCompiledClasses(),
                tests);
    }

    private static TemplateToken generateLoopTest(CmpOp op, boolean swap, int stride) {
        int actualStride = op.countingUp ? stride : -stride;
        String cmp = swap
                ? "limit " + op.mirror + " (long) i"
                : "(long) i " + op.symbol + " limit";

        var template = Template.make(() -> {
            String test = $("test");
            String ref = $("ref");
            String run = $("run");
            return scope(
                    let("cmp", cmp),
                    let("stride", actualStride),
                    let("test", test),
                    let("ref", ref),
                    let("run", run),
                    generateTestMethod(test, cmp, actualStride, false),
                    generateTestMethod(ref, cmp, actualStride, true),
                    generateRunMethod(op, run, test, ref));
        });
        return template.asToken();
    }

    private static final int MAX_ITERATIONS = 10_000;

    private static TemplateToken generateTestMethod(String methodName, String cmp, int stride, boolean dontCompile) {
        var template = Template.make(() -> scope(
                let("methodName", methodName),
                let("cmp", cmp),
                let("stride", stride),
                let("maxIter", MAX_ITERATIONS),
                dontCompile
                        ? "@DontCompile\n"
                        : "",
                !dontCompile
                        ? "@Test\n"
                        : "",
                """
                public static long[] #methodName(int init, long limit) {
                    long sum = 0;
                    int count = 0;
                    for (int i = init; #cmp; i += #stride) {
                        sum += i;
                        count++;
                        if (count > #maxIter) break;
                    }
                    return new long[] { count, sum };
                }
                """));
        return template.asToken();
    }

    private static TemplateToken generateRunMethod(CmpOp op, String run, String test, String ref) {
        var template = Template.make(() -> scope(
                let("run", run),
                let("test", test),
                let("ref", ref),
                """
                @Run(test = "#test")
                @Warmup(100)
                public static void #run() {
                    int init;
                    long limit;
                """,
                op.countingUp
                        ? generateRunBodyCountingUp()
                        : generateRunBodyCountingDown(),
                """
                    long[] actual = #test(init, limit);
                    long[] expected = #ref(init, limit);
                    if (!Arrays.equals(actual, expected)) {
                        throw new RuntimeException("#test(init=" + init + ", limit=" + limit + "): " +
                            "expected " + Arrays.toString(expected) + " but got " + Arrays.toString(actual));
                    }
                }
                """));
        return template.asToken();
    }

    private static TemplateToken generateRunBodyCountingUp() {
        var template = Template.make(() -> scope(
                """
                    switch (G.uniformInts(0, 7).next()) {
                        case 0 -> {
                            init = G.uniformInts(-1000, 999).next();
                            limit = G.uniformLongs(-1000, 999).next();
                        }
                        case 1 -> {
                            init = Integer.MAX_VALUE - G.uniformInts(1, 999).next();
                            limit = (long) Integer.MAX_VALUE;
                        }
                        case 2 -> {
                            init = Integer.MAX_VALUE - G.uniformInts(1, 999).next();
                            limit = (long) Integer.MAX_VALUE + 1L;
                        }
                        case 3 -> {
                            init = Integer.MAX_VALUE - G.uniformInts(1, 999).next();
                            limit = (long) Integer.MAX_VALUE + G.uniformInts(1, 99).next();
                        }
                        case 4 -> {
                            init = Integer.MIN_VALUE + G.uniformInts(0, 999).next();
                            limit = (long) Integer.MIN_VALUE;
                        }
                        case 5 -> {
                            init = Integer.MIN_VALUE + G.uniformInts(0, 999).next();
                            limit = (long) Integer.MIN_VALUE - 1L;
                        }
                        case 6 -> {
                            init = G.uniformInts(0, 999).next();
                            limit = G.uniformInts(-1000, init - 1).next();
                        }
                        default -> {
                            init = G.uniformInts(0, 999).next();
                            limit = init + G.uniformInts(0, 1).next();
                        }
                    }
                """));
        return template.asToken();
    }

    private static TemplateToken generateRunBodyCountingDown() {
        var template = Template.make(() -> scope(
                """
                    switch (G.uniformInts(0, 7).next()) {
                        case 0 -> {
                            init = G.uniformInts(-1000, 999).next();
                            limit = G.uniformLongs(-1000, 999).next();
                        }
                        case 1 -> {
                            init = Integer.MIN_VALUE + G.uniformInts(1, 999).next();
                            limit = (long) Integer.MIN_VALUE;
                        }
                        case 2 -> {
                            init = Integer.MIN_VALUE + G.uniformInts(1, 999).next();
                            limit = (long) Integer.MIN_VALUE - 1L;
                        }
                        case 3 -> {
                            init = Integer.MIN_VALUE + G.uniformInts(1, 999).next();
                            limit = (long) Integer.MIN_VALUE - G.uniformInts(1, 99).next();
                        }
                        case 4 -> {
                            init = Integer.MAX_VALUE - G.uniformInts(0, 999).next();
                            limit = (long) Integer.MAX_VALUE;
                        }
                        case 5 -> {
                            init = Integer.MAX_VALUE - G.uniformInts(0, 999).next();
                            limit = (long) Integer.MAX_VALUE + 1L;
                        }
                        case 6 -> {
                            init = G.uniformInts(-1000, -1).next();
                            limit = G.uniformInts(init + 1, 999).next();
                        }
                        default -> {
                            init = G.uniformInts(-1000, -1).next();
                            limit = init - G.uniformInts(0, 1).next();
                        }
                    }
                """));
        return template.asToken();
    }
}
