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

import compiler.lib.compile_framework.CompileFramework;
import compiler.lib.template_framework.*;
import compiler.lib.template_framework.library.TestFrameworkClass;

import java.util.*;

import static compiler.lib.generators.Generators.*;
import static compiler.lib.template_framework.Template.*;

/*
 * @test id=default
 * @bug 8336759
 * @key randomness
 * @summary Test long limits in int counted loops are speculatively converted
 *          to int for counted loop optimizations - templated correctness tests
 *          covering all comparison operators, swapped operands, multiple strides,
 *          random init values, boundary limits, and zero/one-iteration edge cases.
 * @requires vm.compiler2.enabled
 * @library /test/lib /
 * @run driver/timeout=600 compiler.loopopts.TestIntCountedLoopLongLimitTemplated default
 */

/*
 * @test id=stress
 * @bug 8336759
 * @key randomness stress
 * @summary Test long limits in int counted loops are speculatively converted
 *          to int for counted loop optimizations - stress test with
 *          StressLongCountedLoop to exercise the int -> long -> speculatively
 *          narrow back to int -> deopt -> recompile cycle.
 * @requires vm.compiler2.enabled & vm.debug
 * @library /test/lib /
 * @run driver/timeout=600 compiler.loopopts.TestIntCountedLoopLongLimitTemplated stress
 */
public class TestIntCountedLoopLongLimitTemplated {

    public static void main(String[] args) {
        switch (args.length > 0 ? args[0] : "") {
            case "default":
                testTemplated(new String[]{});
                break;
            case "stress":
                testTemplated(new String[]{
                        "-XX:StressLongCountedLoop=1",
                        "-XX:+StressCountedLoop",
                        "-XX:+StressShortRunningLongLoop",
                        "-XX:+StressIGVN",
                        "-XX:+StressCCP",
                        "-XX:+StressLoopLimitSpeculativeNarrowing"
                });
                break;
            default:
                throw new IllegalArgumentException("Unknown test selection. Check @run commands");
        }
    }

    // Comparison operator != is excluded: with a long limit outside int range, int i can never equal it, so the loop
    // runs until MAX_ITERATIONS is triggered; in which case, the test would not be meaningful since it only tests the
    // guard. For limits within int range, != behaves like < or > and is already covered.
    //
    // In total, 24 generated tests per run: 4 ops x 2 swaps x 3 strides.
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
