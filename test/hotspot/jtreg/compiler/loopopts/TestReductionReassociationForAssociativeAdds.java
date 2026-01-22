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
 * @summary Test the IR effects of reduction reassociation on all associative add operations
 * @library /test/lib /
 * @compile ../lib/verify/Verify.java
 * @run driver ${test.main.class}
 */

package compiler.loopopts;

import compiler.lib.compile_framework.CompileFramework;
import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateToken;
import compiler.lib.template_framework.library.CodeGenerationDataNameType;
import compiler.lib.template_framework.library.PrimitiveType;
import compiler.lib.template_framework.library.TestFrameworkClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static compiler.lib.template_framework.Template.*;
import static compiler.lib.template_framework.Template.let;

public class TestReductionReassociationForAssociativeAdds {
    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        comp.addJavaSourceCode("compiler.loopopts.templated.ReductionReassociationForAssociativeAdds", generate(comp));

        // Compile the source file.
        comp.compile("--add-modules=jdk.incubator.vector");

        String[] flags = new String[] {
            "-XX:-UseSuperWord", "-XX:LoopMaxUnroll=0", "-XX:VerifyIterativeGVN=1000",
            "-XX:CompileCommand=dontinline,*::*dontinline*",
            "--add-modules=jdk.incubator.vector", "--add-opens", "jdk.incubator.vector/jdk.incubator.vector=ALL-UNNAMED"
        };
        comp.invoke("compiler.loopopts.templated.ReductionReassociationForAssociativeAdds", "main", new Object[] {flags});
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
            "compiler.loopopts.templated", "ReductionReassociationForAssociativeAdds",
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
        ADD_I(CodeGenerationDataNameType.ints()),
        ADD_L(CodeGenerationDataNameType.longs()),
        MIN_D(CodeGenerationDataNameType.doubles()),
        MAX_D(CodeGenerationDataNameType.doubles()),
//        MIN_HF(CodeGenerationDataNameType.float16()),
//        MAX_HF(CodeGenerationDataNameType.float16()),
        MIN_F(CodeGenerationDataNameType.floats()),
        MAX_F(CodeGenerationDataNameType.floats()),
        MIN_I(CodeGenerationDataNameType.ints()),
        MAX_I(CodeGenerationDataNameType.ints()),
        MIN_L(CodeGenerationDataNameType.longs()),
        MAX_L(CodeGenerationDataNameType.longs()),
        OR_I(CodeGenerationDataNameType.ints()),
        OR_L(CodeGenerationDataNameType.longs()),
        XOR_I(CodeGenerationDataNameType.ints()),
        XOR_L(CodeGenerationDataNameType.longs());

        final CodeGenerationDataNameType type;

        AssociativeAdd(CodeGenerationDataNameType type) {
            this.type = type;
        }

//        boolean isFloat16() {
//            return MIN_HF == this || MAX_HF == this;
//        }
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
                    case ADD_I, ADD_L -> "#a + #b";
                    case MIN_D -> "Double.min(#a, #b)";
                    case MAX_D -> "Double.max(#a, #b)";
                    case MIN_F -> "Float.min(#a, #b)";
                    case MAX_F -> "Float.max(#a, #b)";
                    case MIN_I -> "Integer.min(#a, #b)";
                    case MAX_I -> "Integer.max(#a, #b)";
                    case MIN_L -> "Long.min(#a, #b)";
                    case MAX_L -> "Long.max(#a, #b)";
                    case OR_I, OR_L -> "#a | #b";
                    case XOR_I, XOR_L -> "#a ^ #b";
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
                    case MIN_D, MIN_F, MIN_I, MIN_L -> "#boxedType.MAX_VALUE";
                    case ADD_I, ADD_L, MAX_D, MAX_F, MAX_I, MAX_L -> "#boxedType.MIN_VALUE";
                    case OR_I, OR_L, XOR_I, XOR_L -> "0";
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
}
