/*
 * Copyright (c) 2026 IBM Corporation. All rights reserved.
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
 * @test id=main
 * @bug 8351409
 * @summary Test the IR effects of reduction reassociation on long min/max loops
 * @library /test/lib /
 * @compile ../lib/verify/Verify.java
 * @run driver ${test.main.class} main
 */

/**
 * @test id=edge-cases
 * @bug 8351409
 * @summary Test the IR effects of reduction reassociation for edge cases that apply to long min/max loops
 * @library /test/lib /
 * @run driver ${test.main.class} edge-cases
 */

package compiler.loopopts;

import compiler.lib.compile_framework.CompileFramework;
import compiler.lib.generators.Generators;
import compiler.lib.ir_framework.Check;
import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;
import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateToken;
import compiler.lib.template_framework.library.CodeGenerationDataNameType;
import compiler.lib.template_framework.library.PrimitiveType;
import compiler.lib.template_framework.library.TestFrameworkClass;
import compiler.lib.verify.Verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static compiler.lib.template_framework.Template.*;
import static compiler.lib.template_framework.Template.let;

/**
 * For the time being this test only covers Min/Max for Long values,
 * but it has been designed to easily extend it to other associative AddNode
 * implementations. For example, if implementing it for AddI or AddL nodes,
 * a typical loop would generate additional IR nodes. Hence, the test uses
 * a custom while loop with non-inlined helper methods.
 */
public class TestReductionReassociation {
    public static void main(String[] args) {
        switch (args[0]) {
            case "main" -> {
                // Create a new CompileFramework instance.
                CompileFramework comp = new CompileFramework();

                // Add a java source file.
                comp.addJavaSourceCode("compiler.loopopts.templated.ReductionReassociation", generate(comp));

                // Compile the source file.
                comp.compile("--add-modules=jdk.incubator.vector");

                String[] flags = new String[] {
                    "-XX:-UseSuperWord", "-XX:LoopMaxUnroll=0", "-XX:VerifyIterativeGVN=1000",
                    "-XX:CompileCommand=dontinline,*::*dontinline*",
                    "--add-modules=jdk.incubator.vector", "--add-opens", "jdk.incubator.vector/jdk.incubator.vector=ALL-UNNAMED"
                };
                comp.invoke("compiler.loopopts.templated.ReductionReassociation", "main", new Object[] {flags});
            }
            case "edge-cases" -> {
                TestFramework.runWithFlags(
                    "-XX:-UseSuperWord", "-XX:LoopMaxUnroll=0",
                    "-XX:CompileCommand=dontinline,*::*dontinline*", "-XX:VerifyIterativeGVN=1000"
                );
            }
            default -> throw new RuntimeException("unknown run id=" + args[0]);
        }

    }

    public static String generate(CompileFramework comp) {
        List<TemplateToken> testTemplateTokens = new ArrayList<>();

        final int size = 10_000;
        final int batchSize = 4;

        Stream.of(AssociativeAdd.values())
            .map(op -> new TestGenerator(op, batchSize, size).generate())
            .forEach(testTemplateTokens::add);

        // Create the test class, which runs all testTemplateTokens.
        return TestFrameworkClass.render(
            // package and class name.
            "compiler.loopopts.templated", "ReductionReassociation",
            // List of imports.
            Set.of("jdk.incubator.vector.Float16",
                "compiler.lib.generators.*",
                "compiler.lib.verify.*"),
            // classpath, so the Test VM has access to the compiled class files.
            comp.getEscapedClassPathOfCompiledClasses(),
            // The list of tests.
            testTemplateTokens);
    }

    enum AssociativeAdd {
        MIN_L(CodeGenerationDataNameType.longs()),
        MAX_L(CodeGenerationDataNameType.longs());

        final CodeGenerationDataNameType type;

        AssociativeAdd(CodeGenerationDataNameType type) {
            this.type = type;
        }
    }

    record TestGenerator(AssociativeAdd add, int batchSize, int size) {
        public TemplateToken generate() {
            var testTemplate = Template.make(() -> {
                String test = $("test");
                String input = $("input");
                String expected = $("expected");
                String setup = $("setup");
                String check = $("check");
                return scope(
                    """
                    // --- $test start ---

                    """,
                    generateArrayField(input),
                    generateExpectedField(test, expected),
                    generateTest(input, setup, test),
                    generateCheck(test, check, expected),
                    generateAuxMethods(input),
                    """

                    // --- $test end ---
                    """
                );
            });
            return testTemplate.asToken();
        }

        private TemplateToken generateAuxMethods(String input) {
            var template = Template.make(() -> scope(
                let("input", input),
                let("type", add.type.name()),
                """
                static #type getArray_dontinline_#input(int pos, int base) {
                     return #input[pos + base];
                }

                static Object[] asArray_dontinline_#input(#type result, #type result2) {
                    return new Object[]{result, result2};
                }

                static int sum_dontinline_#input(int a, int b) {
                    return a + b;
                }
                """
            ));
            return template.asToken();
        }

        private TemplateToken generateCheck(String test, String check, String expected) {
            var template = Template.make(() -> scope(
                let("test", test),
                let("check", check),
                let("expected", expected),
                """
                @Check(test = "#test")
                public void #check(Object[] results) {
                    Verify.checkEQ(#expected[0], results[0]);
                    Verify.checkEQ(#expected[1], results[1]);
                    Verify.checkEQ(results[0], results[1]);
                }
                """
            ));
            return template.asToken();
        }

        private TemplateToken generateOp(String a, String b) {
            var template = Template.make(() -> scope(
                let("a", a),
                let("b", b),
                switch (add) {
                    case MIN_L -> "Long.min(#a, #b)";
                    case MAX_L -> "Long.max(#a, #b)";
                }
            ));
            return template.asToken();
        }

        private TemplateToken generateResultInit(String resultName) {
            var template = Template.make(() -> scope(
                let("resultName", resultName),
                let("boxedType", getBoxedTypeName()),
                let("type", add.type.name()),
                "#type ", resultName, " = ",
                switch (add) {
                    case MIN_L -> "#boxedType.MAX_VALUE";
                    case MAX_L -> "#boxedType.MIN_VALUE";
                },
                ";\n"
            ));
            return template.asToken();
        }

        private Object getBoxedTypeName() {
            if (add.type instanceof PrimitiveType primitiveType) {
                return primitiveType.boxedTypeName();
            }

            return add.type.name();
        }

        private TemplateToken generateTest(String input, String setup, String test) {
            var template = Template.make(() -> scope(
                let("countsIR", batchSize),
                let("irNodeName", add.name()),
                let("input", input),
                let("setup", setup),
                let("test", test),
                let("type", add.type.name()),
                """
                @Test
                @IR(counts = {IRNode.#irNodeName, "= #countsIR"},
                    phase = CompilePhase.AFTER_LOOP_OPTS)
                public Object[] #test() {
                """,
                generateResultInit("result"),
                generateResultInit("result2"),
                // A manually constructed loop that uses auxiliary methods
                // that doesn't produce associative Add nodes that could get
                // in the way of commoning.
                """
                int i = 0;
                while (i < #input.length) {
                """,
                    IntStream.range(0, batchSize).mapToObj(i ->
                        List.of("#type v", i, " = getArray_dontinline_#input(", i, ", i);\n")).toList(),
                    "#type u0 = ", generateOp("v0", "result"), ";\n",
                    IntStream.range(1, batchSize).mapToObj(i ->
                        List.of("#type u", i, " = ", generateOp("v" + i, "u" + (i - 1)), ";\n")
                    ).toList(),
                    "#type t0 = ", generateOp("v0", "v1"), ";\n",
                    IntStream.range(1, batchSize - 1).mapToObj(i ->
                        List.of("#type t", i, " = ", generateOp("v" + (i + 1), "t" + (i - 1)), ";\n")
                    ).toList(),
                    "#type t", batchSize - 1, " = ", generateOp("result", "t" + (batchSize - 2)), ";\n",
                    "result = u", batchSize - 1,";\n",
                    "result2 = t", batchSize - 1,";\n",
                    "",
                    """
                    i = sum_dontinline_#input(i, #countsIR);
                }
                return asArray_dontinline_#input(result, result2);
                }
                """
            ));
            return template.asToken();
        }

        private TemplateToken generateExpectedField(String test, String expected) {
            var template = Template.make(() -> scope(
                let("size", size),
                let("test", test),
                let("expected", expected),
                """
                private Object[] #expected = #test();
                """
            ));
            return template.asToken();
        }

        private TemplateToken generateArrayField(String input) {
            var template = Template.make(() -> scope(
                let("size", size),
                let("input", input),
                let("type", add.type.name()),
                let("gen", add.type.name().toLowerCase(Locale.ROOT) + "s"),
                """
                private static #type[] #input = new #type[#size];

                static {
                    Generators.G.fill(Generators.G.#gen(), #input);
                }
                """
            ));
            return template.asToken();
        }
    }

    private static long[] input = new long[10000];

    static {
        Generators.G.fill(Generators.G.longs(), input);
    }

    private Object[] expectedNonPowerOfTwoBatch = testNonPowerOfTwoBatch();

    @Test
    @IR(counts = {IRNode.MAX_L, "= 5"},
        phase = CompilePhase.AFTER_LOOP_OPTS)
    public Object[] testNonPowerOfTwoBatch() {
        long result = Long.MIN_VALUE;
        long result2 = Long.MIN_VALUE;
        for (int i = 0; i < input.length; i += 5) {
            long v0 = input[i + 0];
            long v1 = input[i + 1];
            long v2 = input[i + 2];
            long v3 = input[i + 3];
            long v4 = input[i + 4];
            long u0 = Long.max(v0, result);
            long u1 = Long.max(v1, u0);
            long u2 = Long.max(v2, u1);
            long u3 = Long.max(v3, u2);
            long u4 = Long.max(v4, u3);
            long t0 = Long.max(v0, v1);
            long t1 = Long.max(v2, t0);
            long t2 = Long.max(v3, t1);
            long t3 = Long.max(v4, t2);
            long t4 = Long.max(result, t3);
            result = u4;
            result2 = t4;
        }
        return new Object[]{result, result2};
    }

    @Check(test = "testNonPowerOfTwoBatch")
    public void checkNonPowerOfTwoBatch(Object[] results) {
        Verify.checkEQ(expectedNonPowerOfTwoBatch[0], results[0]);
        Verify.checkEQ(expectedNonPowerOfTwoBatch[1], results[1]);
        Verify.checkEQ(results[0], results[1]);
    }

    private Object[] expectedUseIntermediate = testUseIntermediate();

    @Test
    @IR(counts = {IRNode.MAX_L, "= 8"},
        phase = CompilePhase.AFTER_LOOP_OPTS)
    public Object[] testUseIntermediate() {
        long result = Long.MIN_VALUE;
        long result2 = Long.MIN_VALUE;
        for (int i = 0; i < input.length; i += 8) {
            long v0 = input[i + 0];
            long v1 = input[i + 1];
            long v2 = input[i + 2];
            long v3 = input[i + 3];
            long v4 = input[i + 4];
            long v5 = input[i + 5];
            long v6 = input[i + 6];
            long v7 = input[i + 7];

            var u0 = Math.max(v0, result);
            var u1 = Math.max(v1, u0);
            var u2 = Math.max(v2, u1);
            var u3 = Math.max(v3, u2);
            if (u3 == input.hashCode()) {
                System.out.print("");
            }
            var u4 = Math.max(v4, u3);
            var u5 = Math.max(v5, u4);
            var u6 = Math.max(v6, u5);
            var u7 = Math.max(v7, u6);

            long t0 = Long.max(v0, v1);
            long t1 = Long.max(v2, t0);
            long t2 = Long.max(v3, t1);
            long t3 = Long.max(result, t2);
            if (t3 == input.hashCode()) {
                System.out.print("");
            }
            long t4 = Long.max(v4, t3);
            long t5 = Long.max(v5, t4);
            long t6 = Long.max(v6, t5);
            long t7 = Long.max(v7, t6);

            result = u7;
            result2 = t7;
        }
        return new Object[]{result, result2};
    }

    @Check(test = "testUseIntermediate")
    public void checkUseIntermediate(Object[] results) {
        Verify.checkEQ(expectedUseIntermediate[0], results[0]);
        Verify.checkEQ(expectedUseIntermediate[1], results[1]);
        Verify.checkEQ(results[0], results[1]);
    }

    private Object[] expectedSumMax = testSumMax();

    @Test
    @IR(counts = {IRNode.MAX_L, "= 4", IRNode.ADD_L, "= 2"},
        phase = CompilePhase.AFTER_LOOP_OPTS)
    public Object[] testSumMax() {
        long result = Long.MIN_VALUE;
        long result2 = Long.MIN_VALUE;
        for (int i = 0; i < input.length; i += 4) {
            long v0 = getArray_dontinline(i + 0);
            long v1 = getArray_dontinline(i + 1);
            long v2 = getArray_dontinline(i + 2);
            long v3 = getArray_dontinline(i + 3);
            long u0 = Long.max(v0, result);
            long u1 = Long.max(v1, u0);
            long u2 = Long.max(v2, u1);
            long u3 = Long.max(v3, u2);
            long t0 = Long.max(v0, v1);
            long t1 = Long.max(v2, t0);
            long t2 = Long.max(v3, t1);
            long t3 = Long.max(result, t2);
            result += u3;
            result2 += t3;
        }
        return new Object[]{result, result2};
    }

    @Check(test = "testSumMax")
    public void checkSumMax(Object[] results) {
        Verify.checkEQ(expectedSumMax[1], results[1]);
        Verify.checkEQ(expectedSumMax[0], results[0]);
        Verify.checkEQ(results[0], results[1]);
    }

    static long getArray_dontinline(int i) {
        return input[i];
    }
}
