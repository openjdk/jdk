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
 * @test
 * @bug 8351409
 * @summary Test the IR effects of reduction reassociation
 * @library /test/lib /
 * @run driver compiler.loopopts.TestReductionReassociation
 */

package compiler.loopopts;

import compiler.igvn.TestMinMaxIdeal;
import compiler.lib.compile_framework.CompileFramework;
import compiler.lib.generators.Generator;
import compiler.lib.ir_framework.*;
import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateToken;
import compiler.lib.template_framework.library.CodeGenerationDataNameType;
import compiler.lib.template_framework.library.PrimitiveType;
import compiler.lib.template_framework.library.TestFrameworkClass;
import compiler.lib.verify.Verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static compiler.lib.generators.Generators.G;
import static compiler.lib.template_framework.Template.*;
import static compiler.lib.template_framework.Template.let;

public class TestReductionReassociation {
    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        comp.addJavaSourceCode("compiler.loopopts.templated.ReductionReassociation", generate(comp));

        // Compile the source file.
        comp.compile();

        String[] flags = new String[] {"-XX:-UseSuperWord", "-XX:LoopMaxUnroll=0", "-XX:VerifyIterativeGVN=1000"};
        comp.invoke("compiler.loopopts.templated.ReductionReassociation", "main", new Object[] {flags});
    }

    public static String generate(CompileFramework comp) {
        List<TemplateToken> testTemplateTokens = new ArrayList<>();

        final int size = 10_000;
        final int batchSize = 4;

        Stream.of(TestReductionReassociation.AddOp.values())
            .map(op -> new TestGenerator(op, batchSize, false, size).generate())
            .forEach(testTemplateTokens::add);

        // A single test to test a non-power-of-2 value
        testTemplateTokens.add(new TestGenerator(AddOp.MAX_L, 5, false, size).generate());
        // A single test where an intermediate value is used some other way
        testTemplateTokens.add(new TestGenerator(AddOp.MAX_L, batchSize, true, size).generate());

        // Create the test class, which runs all testTemplateTokens.
        return TestFrameworkClass.render(
            // package and class name.
            "compiler.loopopts.templated", "ReductionReassociation",
            // List of imports.
            Set.of("compiler.lib.generators.*",
                "compiler.lib.verify.*"),
            // classpath, so the Test VM has access to the compiled class files.
            comp.getEscapedClassPathOfCompiledClasses(),
            // The list of tests.
            testTemplateTokens);
    }

    enum AddOp {
        MIN_L(CodeGenerationDataNameType.longs()),
        MAX_L(CodeGenerationDataNameType.longs());

        final PrimitiveType type;

        AddOp(PrimitiveType type) {
            this.type = type;
        }
    }

    record TestGenerator(AddOp add, int batchSize, boolean useIntermediate, int size) {
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
                    // generateSetup(setup, input),
                    generateTest(input, setup, test),
                    generateCheck(test, check, expected),
                    """

                    // --- $test end ---
                    """
                );
            });
            return testTemplate.asToken();
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

        private TemplateToken generateTest(String input, String setup, String test) {
            var template = Template.make(() -> scope(
                let("irNodeName", add.name()),
                let("input", input),
                let("setup", setup),
                let("test", test),
                let("type", add.type.name()),
                """
                @Test
                """,
                "@IR(counts = {IRNode.#irNodeName, \"= ",
                useIntermediate ? batchSize * 2 : batchSize,
                "\"}, phase = CompilePhase.AFTER_LOOP_OPTS)",
                """
                public Object[] #test() {
                    #type result = Integer.MIN_VALUE;
                    #type result2 = Integer.MIN_VALUE;
                """,
                "for (int i = 0; i < #input.length; i += ", batchSize, ") {",
                IntStream.range(0, batchSize).mapToObj(i ->
                    List.of("long v", i, " = #input[i + ", i, "];\n")
                ).toList(),
                useIntermediate ? List.of("if (v", batchSize - 1," == #input.hashCode()) { System.out.print(\"\"); }") : "",
                "long u0 = ", generateOp("v0", "result"), ";",
                IntStream.range(1, batchSize).mapToObj(i ->
                    List.of("long u", i, " = ", generateOp("v" + i, "u" + (i - 1)), ";\n")
                ).toList(),
                "result = u", batchSize - 1,";",
                "long t0 = ", generateOp("v0", "v1"), ";",
                IntStream.range(1, batchSize - 1).mapToObj(i ->
                    List.of("long t", i, " = ", generateOp("v" + (i + 1), "t" + (i - 1)), ";\n")
                ).toList(),
                "long t", batchSize - 1, " = ", generateOp("result", "t" + (batchSize - 2)), ";",
                "result2 = t", batchSize - 1,";",
                """
                    }
                    return new Object[]{result, result2};
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
                let("gen", add.type.name() + "s"),
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
}
