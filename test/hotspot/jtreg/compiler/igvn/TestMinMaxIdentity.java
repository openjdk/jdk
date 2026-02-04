/*
 * Copyright (c) 2025, 2026 IBM Corporation. All rights reserved.
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
 * @bug 8373134
 * @summary Verify that min/max add identity optimizations get applied correctly
 * @modules java.base/jdk.internal.misc
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

package compiler.igvn;

import compiler.lib.compile_framework.CompileFramework;
import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateToken;
import compiler.lib.template_framework.library.CodeGenerationDataNameType;
import compiler.lib.template_framework.library.PrimitiveType;
import compiler.lib.template_framework.library.TestFrameworkClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static compiler.lib.template_framework.Template.let;
import static compiler.lib.template_framework.Template.scope;

public class TestMinMaxIdentity {
    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        comp.addJavaSourceCode("compiler.igvn.templated.MinMaxIdentity", generate(comp));

        // Compile the source file.
        comp.compile("--add-modules=jdk.incubator.vector");

        comp.invoke("compiler.igvn.templated.MinMaxIdentity", "main", new Object[] {new String[] {
            "--add-modules=jdk.incubator.vector",
            "--add-opens", "jdk.incubator.vector/jdk.incubator.vector=ALL-UNNAMED"
        }});
    }

    private static String generate(CompileFramework comp) {
        // Create a list to collect all tests.
        List<TemplateToken> testTemplateTokens = new ArrayList<>();

        Stream.of(MinMaxOp.values())
            .flatMap(MinMaxOp::generate)
            .forEach(testTemplateTokens::add);

        Stream.of(Fp16MinMaxOp.values())
            .flatMap(Fp16MinMaxOp::generate)
            .forEach(testTemplateTokens::add);

        // Create the test class, which runs all testTemplateTokens.
        return TestFrameworkClass.render(
            // package and class name.
            "compiler.igvn.templated", "MinMaxIdentity",
            // List of imports.
            Set.of("jdk.incubator.vector.Float16"),
            // classpath, so the Test VM has access to the compiled class files.
            comp.getEscapedClassPathOfCompiledClasses(),
            // The list of tests.
            testTemplateTokens);
    }

    enum MinMaxOp {
        MIN_D("min", CodeGenerationDataNameType.doubles()),
        MAX_D("max", CodeGenerationDataNameType.doubles()),
        MIN_F("min", CodeGenerationDataNameType.floats()),
        MAX_F("max", CodeGenerationDataNameType.floats()),
        MIN_I("min", CodeGenerationDataNameType.ints()),
        MAX_I("max", CodeGenerationDataNameType.ints()),
        MIN_L("min", CodeGenerationDataNameType.longs()),
        MAX_L("max", CodeGenerationDataNameType.longs());

        final String functionName;
        final PrimitiveType type;

        MinMaxOp(String functionName, PrimitiveType type) {
            this.functionName = functionName;
            this.type = type;
        }

        Stream<TemplateToken> generate() {
            return Stream.of(template("a", "b"), template("b", "a")).
                map(Template.ZeroArgs::asToken);
        }

        private Template.ZeroArgs template(String arg1, String arg2) {
            return Template.make(() -> scope(
                let("boxedTypeName", type.boxedTypeName()),
                let("op", name()),
                let("type", type.name()),
                let("functionName", functionName),
                let("arg1", arg1),
                let("arg2", arg2),
                """
                @Test
                """,
                type.isFloating() ?
                    """
                    @IR(counts = {IRNode.#op, "= 1"},
                        phase = CompilePhase.BEFORE_MACRO_EXPANSION,
                        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
                    @IR(counts = {IRNode.#op, "= 1"},
                        phase = CompilePhase.BEFORE_MACRO_EXPANSION,
                        applyIfPlatform = {"riscv64", "true"})
                    """ :
                    """
                    @IR(counts = {IRNode.#op, "= 1"},
                        phase = CompilePhase.BEFORE_MACRO_EXPANSION)
                    """,
                """
                @Arguments(values = {Argument.NUMBER_42, Argument.NUMBER_42})
                public #type $test(#type #arg1, #type #arg2) {
                    int i;
                    for (i = -10; i < 1; i++) {
                    }
                    #type c = a * i;
                    return #boxedTypeName.#functionName(a, #boxedTypeName.#functionName(b, c));
                }
                """
            ));
        }
    }

    enum Fp16MinMaxOp {
        MAX_HF("max"),
        MIN_HF("min");

        final String functionName;

        Fp16MinMaxOp(String functionName) {
            this.functionName = functionName;
        }

        Stream<TemplateToken> generate() {
            return Stream.of(template("a", "b"), template("b", "a")).
                map(Template.ZeroArgs::asToken);
        }

        private Template.ZeroArgs template(String arg1, String arg2) {
            return Template.make(() -> scope(
                let("op", name()),
                let("functionName", functionName),
                let("arg1", arg1),
                let("arg2", arg2),
                """
                @Setup
                private static Object[] $setup() {
                    return new Object[] {Float16.valueOf(42), Float16.valueOf(42)};
                }

                @Test
                @IR(counts = {IRNode.#op, "= 1"},
                    phase = CompilePhase.BEFORE_MACRO_EXPANSION,
                    applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
                @IR(counts = {IRNode.#op, "= 1"},
                    phase = CompilePhase.BEFORE_MACRO_EXPANSION,
                    applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
                @Arguments(setup = "$setup")
                public Float16 $test(Float16 #arg1, Float16 #arg2) {
                    int i;
                    for (i = -10; i < 1; i++) {
                    }
                    Float16 c = Float16.multiply(a, Float16.valueOf(i));
                    return Float16.#functionName(a, Float16.#functionName(b, c));
                }
                """
            ));
        }
    }
}
