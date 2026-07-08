/*
 * Copyright (c) 2025 IBM Corporation. All rights reserved.
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
 * @bug 8373396
 * @summary Verify that min/max add ideal optimizations get applied correctly
 * @modules java.base/jdk.internal.misc
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

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static compiler.lib.template_framework.Template.let;
import static compiler.lib.template_framework.Template.scope;

public class TestMinMaxIdeal {
    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        comp.addJavaSourceCode("compiler.igvn.templated.MinMaxIdeal", generate(comp));

        // Compile the source file.
        comp.compile();

        comp.invoke("compiler.igvn.templated.MinMaxIdeal", "main", new Object[] {new String[] {}});
    }

    private static String generate(CompileFramework comp) {
        // Create a list to collect all tests.
        List<TemplateToken> testTemplateTokens = Stream.of(Op.values())
            .map(op -> new TestGenerator(op).generate())
            .toList();

        // Create the test class, which runs all testTemplateTokens.
        return TestFrameworkClass.render(
            // package and class name.
            "compiler.igvn.templated", "MinMaxIdeal",
            // List of imports.
            Collections.emptySet(),
            // classpath, so the Test VM has access to the compiled class files.
            comp.getEscapedClassPathOfCompiledClasses(),
            // The list of tests.
            testTemplateTokens);
    }

    enum Op {
        MIN_I("min", CodeGenerationDataNameType.ints()),
        MAX_I("max", CodeGenerationDataNameType.ints()),
        MIN_L("min", CodeGenerationDataNameType.longs()),
        MAX_L("max", CodeGenerationDataNameType.longs());

        final String functionName;
        final PrimitiveType type;

        Op(String functionName, PrimitiveType type) {
            this.functionName = functionName;
            this.type = type;
        }
    }

    record TestGenerator(Op op) {
        TemplateToken generate() {
            var template = Template.make(() -> scope(
                let("boxedTypeName", op.type.boxedTypeName()),
                let("op", op.name()),
                let("type", op.type.name()),
                let("functionName", op.functionName),
                """
                @Test
                @IR(counts = {IRNode.#op, "= 1"},
                    phase = CompilePhase.BEFORE_MACRO_EXPANSION)
                @Arguments(values = {Argument.NUMBER_42, Argument.NUMBER_42})
                public #type test_commute_#op(#type a, #type b) {
                    return #boxedTypeName.#functionName(a, b) + #boxedTypeName.#functionName(b, a);
                }

                @Test
                @IR(counts = {IRNode.#op, "= 1"},
                    phase = CompilePhase.BEFORE_MACRO_EXPANSION)
                @Arguments(values = {Argument.NUMBER_42})
                public #type test_flatten_#op(#type a) {
                    return #boxedTypeName.#functionName(#boxedTypeName.#functionName(a, 1), 2);
                }

                @Test
                @IR(counts = {IRNode.#op, "= 2"},
                    phase = CompilePhase.BEFORE_MACRO_EXPANSION)
                @Arguments(values = {Argument.NUMBER_42, Argument.NUMBER_42})
                public #type test_push_constant_left_#op(#type a, #type b) {
                    return #boxedTypeName.#functionName(#boxedTypeName.#functionName(a, 1), b) + #boxedTypeName.#functionName(b, a);
                }

                @Test
                @IR(counts = {IRNode.#op, "= 2"},
                    phase = CompilePhase.BEFORE_MACRO_EXPANSION)
                @Arguments(values = {Argument.NUMBER_42, Argument.NUMBER_42})
                public #type test_push_constant_right_#op(#type a, #type b) {
                    return #boxedTypeName.#functionName(a, #boxedTypeName.#functionName(b, 1)) + #boxedTypeName.#functionName(b, a);
                }
                """
            ));
            return template.asToken();
        }
    }
}
