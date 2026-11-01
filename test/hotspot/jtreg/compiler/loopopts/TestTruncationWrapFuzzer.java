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


/*
 * @test
 * @bug 8386597 8385855 8386482 8386591 8386830
 * @summary Fuzz patterns for CountedLoopConverter::has_truncation_wrap
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @compile ../lib/ir_framework/TestFramework.java
 * @compile ../lib/generators/Generators.java
 * @run driver ${test.main.class}
 */

package compiler.loopopts;

import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.Set;

import jdk.test.lib.Utils;

import compiler.lib.compile_framework.*;
import compiler.lib.generators.*;
import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateToken;
import static compiler.lib.template_framework.Template.scope;
import static compiler.lib.template_framework.Template.let;
import static compiler.lib.template_framework.Template.$;

import compiler.lib.template_framework.library.TestFrameworkClass;

/**
 * For more basic examples, see:
 * - TestHasTruncationWrap.java
 * - TestTruncationWrapEmptyType.java
 * - TestTruncationWrapPhiTypeUnion.java
 * - TestTruncationWrapBadCharWrap.java
 *
 * So far, this test does not have IR verification, only result verification.
 *
 * This test generates a wide range of patterns, and will require a lot of
 * runs to find a specific code shape.
 *
 * Features:
 * - Truncation patterns, see TRUNCATIONS and randomIVMutation.
 * - Stride: positive, negative, small and large, see ivMutationWithRandomStride.
 * - Reference (not compiled) vs test (compiled), and result verification.
 * - Loop Shapes: for, while, do-while, see LOOP_SHAPES.
 * - Exit checks: random Comparison, see Comparator and Comparison (signed and unsigned).
 * - For endless loops / loops that would take too long: early exit via opaqueCheck,
 *   Note: it is verified that this does not hinder optimization, see:
 *   TestHasTruncationWrap.java -> testIRShort7.
 * - Interesting loop bounds: init/limit
 *   - constant
 *   - variable, sampled (see getInputTemplate), and modified (no-op, truncate, clamp).
 * - Extra check dominating the loop: compare against constant of limit.
 *   Note: has_truncation_wrap can use such checks to constrain the entry type.
 *   Note2: We've had bugs around this, confusing CmpI/CmpU, see JDK-8385855.
 */
public class TestTruncationWrapFuzzer {
    private static final Random RANDOM = Utils.getRandomInstance();
    private static final RestrictableGenerator<Integer> INT_GEN = Generators.G.ints();

    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        long t0 = System.nanoTime();
        // Add a java source file.
        comp.addJavaSourceCode("compiler.loopopts.templated.Generated", generate(comp));

        long t1 = System.nanoTime();
        // Compile the source file.
        comp.compile();

        long t2 = System.nanoTime();

        // Run the tests without any additional VM flags.
        comp.invoke("compiler.loopopts.templated.Generated", "main", new Object[] {new String[] {}});
        long t3 = System.nanoTime();

        System.out.println("Code Generation:  " + (t1-t0) * 1e-9f);
        System.out.println("Code Compilation: " + (t2-t1) * 1e-9f);
        System.out.println("Running Tests:    " + (t3-t2) * 1e-9f);
    }

    public static String generate(CompileFramework comp) {
        // Create a list to collect all tests.
        List<TemplateToken> testTemplateTokens = new ArrayList<>();

        // Some utilities, to help us get an additional exit, in case the
        // generated loops spin too long, or are infinite loops.
        Template.ZeroArgs utilsTemplate = Template.make(() -> scope(
            """
            private static final Random RANDOM = Utils.getRandomInstance();

            public static int opaqueCounter;
            public static int opaqueCounterMax;

            @DontInline
            public static void opaqueReset() {
                opaqueCounter = 0;
            }

            @DontInline
            public static boolean opaqueCheck() {
                return (opaqueCounter++) > opaqueCounterMax;
            }

            @DontInline
            public static int opaqueSum(int i, int j) {
                return i + j + 1;
            }
            """
        ));
        testTemplateTokens.add(utilsTemplate.asToken());

        for (int i = 0; i < 100; i++) {
            testTemplateTokens.add(generateTest(/* no warmup, like -Xcomp */ 0));
        }
        for (int i = 0; i < 5; i++) {
            testTemplateTokens.add(generateTest(/* with warmup, slower */ 100));
        }

        // Create the test class, which runs all testTemplateTokens.
        return TestFrameworkClass.render(
            // package and class name.
            "compiler.loopopts.templated", "Generated",
            // List of imports.
            Set.of("compiler.lib.generators.*",
                   "java.util.Random",
                   "jdk.test.lib.Utils"),
            // classpath, so the Test VM has access to the compiled class files.
            comp.getEscapedClassPathOfCompiledClasses(),
            // The list of tests.
            testTemplateTokens);
    }

    // This is copied from TestFoldComparesFuzzer.java, and we should
    // refactor this out into the template framework library, in a
    // future RFE.
    enum Comparator {
        ULT(" <  0", false),
        ULE(" <= 0", false),
        UGT(" >  0", false),
        UGE(" >= 0", false),
        UEQ(" == 0", false),
        UNE(" != 0", false),
        LT(" <  ", true),
        LE(" <= ", true),
        GT(" >  ", true),
        GE(" >= ", true),
        EQ(" == ", true),
        NE(" != ", true);

        private final String token;
        private final boolean signed;

        Comparator(String token, boolean signed) {
            this.token = token;
            this.signed = signed;
        }

        public String getToken() {
            return token;
        }

        public boolean isSigned() {
            return signed;
        }

        public Comparator negate() {
            return switch(this) {
                case ULT -> UGE;
                case ULE -> UGT;
                case UGT -> ULE;
                case UGE -> ULT;
                case UEQ -> UNE;
                case UNE -> UEQ;
                case LT -> GE;
                case LE -> GT;
                case GT -> LE;
                case GE -> LT;
                case EQ -> NE;
                case NE -> EQ;
            };
        }

        public Comparator flip() {
            return switch(this) {
                case ULT -> UGT;
                case ULE -> UGE;
                case UGT -> ULT;
                case UGE -> ULE;
                case UEQ -> UEQ;
                case UNE -> UNE;
                case LT -> GT;
                case LE -> GE;
                case GT -> LT;
                case GE -> LE;
                case EQ -> EQ;
                case NE -> NE;
            };
        }

        static Comparator random() {
            return values()[RANDOM.nextInt(values().length)];
        }
    }

    record Comparison(String lhs, Comparator cmp, String rhs, boolean negated) {
        public Comparison(String lhs, Comparator cmp, String rhs) {
            this(lhs, cmp, rhs, false);
        }

        public String toString() {
            return cmp.isSigned()
                ? ((negated ? "!" : "") + "(" + lhs + " "+ cmp.getToken() + " " + rhs + ")")
                : ((negated ? "!" : "") + "(Integer.compareUnsigned(" + lhs + ", " + rhs + ")" + cmp.getToken() + ")");
        }

        // Keep the same semantics of the test, but change its form.
        Comparison permuteRandom() {
            return flipRandom().complementRandom();
        }

        Comparison flipRandom() {
            return RANDOM.nextBoolean() ? this : new Comparison(rhs, cmp.flip(), lhs);
        }

        Comparison complementRandom() {
            return RANDOM.nextBoolean() ? this : new Comparison(lhs, cmp.negate(), rhs, true);
        }
    }

    interface TestMethodGenerator {
        Template.OneArg<String> getTestTemplate();

        default Template.ZeroArgs getInputTemplate() {
            return Template.make(() -> scope(
                switch (RANDOM.nextInt(5)) {
                    case 0 -> """
                              RestrictableGenerator<Integer> gen = Generators.G.ints();
                              int init  = gen.next();
                              int limit = gen.next();
                              """;
                    case 1 -> """
                              int init  = (byte)RANDOM.nextInt();
                              int limit = (byte)RANDOM.nextInt();
                              """;
                    case 2 -> """
                              int init  = (short)RANDOM.nextInt();
                              int limit = (short)RANDOM.nextInt();
                              """;
                    case 3 -> """
                              int init  = (char)RANDOM.nextInt();
                              int limit = (char)RANDOM.nextInt();
                              """;
                    case 4 -> """
                              int e0 = RANDOM.nextInt(32);
                              int e1 = RANDOM.nextInt(32);
                              int r0 = RANDOM.nextInt(32);
                              int r1 = RANDOM.nextInt(32);
                              int init  = (1 << e0) + r0;
                              int limit = (1 << e1) + r1;
                              """;
                    default -> throw new RuntimeException("not expected");
                }
            ));
        };
    }

    private static record Truncation(String s0, String s1) {
        public String ivMutationWithRandomStride() {
            int stride = switch(RANDOM.nextInt(3)) {
                case 0 -> INT_GEN.next();
                case 1 -> RANDOM.nextInt(9) - 4;
                case 2 -> RANDOM.nextInt(129) - 64;
                default -> throw new RuntimeException("not expected");
            };

            return "i = " + s0 + "i + " + stride + s1;
        }

        public String truncate(String val) {
            return val + " = " + s0 + val + s1;
        }
    }

    // Different patterns relevant for triggering truncation/wrap.
    private static final Truncation[] TRUNCATIONS = new Truncation[] {
        new Truncation("", ""),
        new Truncation("(byte)(", ")"),
        new Truncation("(short)(", ")"),
        new Truncation("(char)(", ")"),
        new Truncation("((", ") << 8) >> 8"),
        new Truncation("((", ") << 16) >> 16"),
        new Truncation("((", ") << 24) >> 24"),
        new Truncation("((", ") & 0x7f)"),
        new Truncation("((", ") & 0xff)"),
        new Truncation("((", ") & 0x7fff)"),
        new Truncation("((", ") & 0xffff)")
    };

    private static Truncation randomTruncation() {
        return TRUNCATIONS[RANDOM.nextInt(TRUNCATIONS.length)];
    }

    private static String randomIVMutation() {
        return randomTruncation().ivMutationWithRandomStride();
    }

    private static String randomTruncation(String val) {
        return randomTruncation().truncate(val);
    }

    private static final String[] LOOP_SHAPES = new String[] {
        """
        // Loop Shape: For
        int i;
        for (i = init; #exitCheck; #ivMutation) {
            sum = opaqueSum(sum, #addValue);
            if (opaqueCheck()) { break; }
        }
        """,
        """
        // Loop Shape: While:
        int i = init;
        while (#exitCheck) {
            sum = opaqueSum(sum, #addValue);
            if (opaqueCheck()) { break; }
            #ivMutation;
        }
        """,
        """
        // Loop Shape: Do-While:
        int i = init;
        do {
            sum = opaqueSum(sum, #addValue);
            if (opaqueCheck()) { break; }
            #ivMutation;
        } while (#exitCheck);
        """,
        """
        // Loop Shape: Do-While + pre-loop check.
        int i = init;
        if (!(#exitCheck)) { return sum; }
        do {
            sum = opaqueSum(sum, #addValue);
            if (opaqueCheck()) { break; }
            #ivMutation;
        } while (#exitCheck);
        """
    };

    private static String randomLoopShape() {
        return LOOP_SHAPES[RANDOM.nextInt(LOOP_SHAPES.length)];
    }

    // Loop init/limit are constants.
    static class TestMethodGeneratorConst implements TestMethodGenerator {
        private final int init  = INT_GEN.next();
        private final int limit = INT_GEN.next();

        private final String ivMutation = randomIVMutation();
        private final String loopShape = randomLoopShape();
        private final String addValue = RANDOM.nextBoolean() ? "0" : "i";

        private final Comparison exitCheck = new Comparison("i", Comparator.random(), "limit").permuteRandom();

        private final Template.OneArg<String> testTemplate = Template.make("methodName", (String methodName) -> scope(
            let("init", init),
            let("limit", limit),
            let("ivMutation", ivMutation),
            let("exitCheck", exitCheck),
            let("addValue", addValue),
            """
            static int #methodName(int unused0, int unused1) {
                opaqueReset();
                int init  = #init;
                int limit = #limit;
                int sum = 0;
            """,
            loopShape,
            """
                return sum + #addValue;
            }
            """
        ));

        public Template.OneArg<String> getTestTemplate() { return testTemplate; }
    }

    // Clamp randomly, but not always on both sides.
    private static String randomClamping(String value) {
        String clamp = value;
        if (RANDOM.nextBoolean()) {
            clamp = "Math.max(" + clamp + ", " + INT_GEN.next() + ")";
        }
        if (RANDOM.nextBoolean()) {
            clamp = "Math.min(" + clamp + ", " + INT_GEN.next() + ")";
        }
        return value + " = " + clamp;
    }

    // We want to be able to modify the incoming init/limit.
    // - nothing
    // - truncate
    // - clamp with min/max, maybe even only one-sided
    private static String randomModifyValue(String value) {
        return switch(RANDOM.nextInt(3)) {
            case 0 -> "// Don't modify " + value + "\n";
            case 1 -> randomTruncation(value) + ";\n";
            case 2 -> randomClamping(value) + ";\n";
            default -> throw new RuntimeException("not expected");
        };
    }

    private static String randomExtraCheck() {
        // We can constrain the init value with limit or a constant.
        String other = RANDOM.nextBoolean() ? "limit" : INT_GEN.next().toString();
        Comparison check = new Comparison("init", Comparator.random(), other).permuteRandom();
        return RANDOM.nextBoolean()
               ? "// No extra check.\n"
               : "if (" + check + ") { return -1; }\n";
    }

    // Loop init/limit are variables.
    static class TestMethodGeneratorVars implements TestMethodGenerator {
        private final String ivMutation  = randomIVMutation();
        private final String loopShape   = randomLoopShape();
        private final String addValue = RANDOM.nextBoolean() ? "0" : "i";
        private final String modifyInit  = randomModifyValue("init");
        private final String modifyLimit = randomModifyValue("limit");
        private final String extraCheck  = randomExtraCheck();

        private final Comparison exitCheck = new Comparison("i", Comparator.random(), "limit").permuteRandom();

        private final Template.OneArg<String> testTemplate = Template.make("methodName", (String methodName) -> scope(
            let("ivMutation", ivMutation),
            let("exitCheck", exitCheck),
            let("addValue", addValue),
            """
            static int #methodName(int init, int limit) {
                opaqueReset();
                int sum = 0;
            """,
            modifyInit,  // modify type of init
            modifyLimit, // modify type of limit
            extraCheck,  // extra CmpI/CmpU dominating the loop, might constrain entry value.
            loopShape,
            """
                return sum + #addValue;
            }
            """
        ));

        public Template.OneArg<String> getTestTemplate() { return testTemplate; }
    }
    public static TemplateToken generateTest(int warmup) {
        TestMethodGenerator tg = switch(RANDOM.nextInt(2)) {
            case 0 -> new TestMethodGeneratorConst();
            case 1 -> new TestMethodGeneratorVars();
            default -> throw new RuntimeException("not expected");
        };
        Template.ZeroArgs testInputTemplate = tg.getInputTemplate();
        Template.OneArg<String> testMethodTemplate = tg.getTestTemplate();

        var testTemplate = Template.make(() -> scope(
            let("warmup", warmup),
            """
            // --- $test start ---
            @Run(test = "$test")
            @Warmup(#warmup)
            public static void $run(RunInfo info) {
                int reps = info.isWarmUp() ? 1 : 100;
                for (int i = 0; i < reps; i++) {
                    // Generate random values for init and limit.
                    """,
                    testInputTemplate.asToken(),
                    """

                    // Limit how long we can spin in the loop:
                    opaqueCounterMax = 10_000 + RANDOM.nextInt(1000);

                    // Run test and compare with interpreter results.
                    var result   =      $test(init, limit);
                    var expected = $reference(init, limit);
                    if (result != expected) {
                        throw new RuntimeException("wrong result: " + result + " vs " + expected
                                                   + "\\ninit:  " + init
                                                   + "\\nlimit: " + limit
                                                   + "\\nopaqueCounterMax: " + opaqueCounterMax);
                    }
                }
            }

            @Test
            """,
            testMethodTemplate.asToken($("test")),
            """

            @DontCompile
            """,
            testMethodTemplate.asToken($("reference")),
            """
            // --- $test end   ---
            """
        ));
        return testTemplate.asToken();
    }
}
