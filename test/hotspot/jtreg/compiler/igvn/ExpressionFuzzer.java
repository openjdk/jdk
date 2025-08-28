/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8359412
 * @summary Use the template framework library to generate random expressions.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @compile ../lib/verify/Verify.java
 * @run main compiler.igvn.ExpressionFuzzer
 */

package compiler.igvn;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.Random;
import jdk.test.lib.Utils;
import java.util.stream.Collectors;

import compiler.lib.compile_framework.*;
import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateToken;
import static compiler.lib.template_framework.Template.body;
import static compiler.lib.template_framework.Template.let;
import static compiler.lib.template_framework.Template.$;
import compiler.lib.template_framework.library.CodeGenerationDataNameType;
import compiler.lib.template_framework.library.Expression;
import compiler.lib.template_framework.library.Operations;
import compiler.lib.template_framework.library.PrimitiveType;
import compiler.lib.template_framework.library.TestFrameworkClass;
import static compiler.lib.template_framework.library.CodeGenerationDataNameType.PRIMITIVE_TYPES;

public class ExpressionFuzzer {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static record MethodArgument(String name, CodeGenerationDataNameType type) {}

    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        comp.addJavaSourceCode("compiler.igvn.templated.ExpressionFuzzerInnerTest", generate(comp));

        // Compile the source file.
        comp.compile();

        // compiler.igvn.templated.InnterTest.main(new String[] {});
        comp.invoke("compiler.igvn.templated.ExpressionFuzzerInnerTest", "main", new Object[] {new String[] {}});
    }

    // Generate a Java source file as String
    public static String generate(CompileFramework comp) {
        // Generate a list of test methods.
        List<TemplateToken> tests = new ArrayList<>();

        var bodyTemplate = Template.make("expression", "arguments", (Expression expression, List<Object> arguments) -> body(
                """
                try {
                """,
                "return ", expression.asToken(arguments), ";\n",
                expression.info.exceptions.stream().map(exception ->
                    "} catch (" + exception + " e) { return e;\n"
                ).toList(),
                """
                } finally {
                    // Just so that javac is happy if there are no exceptions to catch.
                }
                """
        ));

        var valueTemplate = Template.make("name", "type", (String name, CodeGenerationDataNameType type) -> body(
            "#type #name = ", type.con(), ";\n"
            // TODO: randomize!
        ));

        var testTemplate = Template.make("expression", (Expression expression) -> {
            // Fix the arguments for both the compiled and reference method.
            List<MethodArgument> methodArguments = new ArrayList<>();
            List<Object> expressionArguments = new ArrayList<>();
            for (CodeGenerationDataNameType type : expression.argumentTypes) {
                switch (RANDOM.nextInt(2)) {
                    case 0 -> {
                        String name = $("arg" + methodArguments.size());
                        methodArguments.add(new MethodArgument(name, type));
                        expressionArguments.add(name);
                    }
                    default -> {
                        expressionArguments.add(type.con());
                    }
                }
            }
            // TODO: fix with Values
            // We need to have a way to get values with type constraints
            // And we need to "load" them once in compiled and once in reference
            // Could do mix of method args, fields and constants.
            // And even computations in the method? Not sure
            // Args and fields can then be "constrained" in the method before use.
            // And the result should be returned, but also some "checksum" to
            // detect range and bits.
            return body(
                let("methodArguments",
                    methodArguments.stream().map(ma -> ma.name).collect(Collectors.joining(", "))),
                let("methodArgumentsWithTypes",
                    methodArguments.stream().map(ma -> ma.type + " " + ma.name).collect(Collectors.joining(", "))),
                """
                @Test
                public static void $primitiveConTest() {
                """,
                methodArguments.stream().map(ma -> valueTemplate.asToken(ma.name, ma.type)).toList(),
                """
                    Object v0 = ${primitiveConTest}_compiled(#methodArguments);
                    Object v1 = ${primitiveConTest}_reference(#methodArguments);
                    Verify.checkEQ(v0, v1);
                }

                @DontInline
                public static Object ${primitiveConTest}_compiled(#methodArgumentsWithTypes) {
                """,
                bodyTemplate.asToken(expression, expressionArguments),
                """
                }

                @DontCompile
                public static Object ${primitiveConTest}_reference(#methodArgumentsWithTypes) {
                """,
                bodyTemplate.asToken(expression, expressionArguments),
                """
                }
                """
            );
        });

        for (PrimitiveType type : PRIMITIVE_TYPES) {
            for (int i = 0; i < 10; i++) {
                // TODO: have a range of depths?
                Expression expression = Expression.nestRandomly(type, Operations.PRIMITIVE_OPERATIONS, 10);
                tests.add(testTemplate.asToken(expression));
            }
        }

        // Create the test class, which runs all tests.
        return TestFrameworkClass.render(
            // package and class name.
            "compiler.igvn.templated", "ExpressionFuzzerInnerTest",
            // Set of imports.
            Set.of("compiler.lib.verify.*"),
            // classpath, so the Test VM has access to the compiled class files.
            comp.getEscapedClassPathOfCompiledClasses(),
            // The list of tests.
            tests);
    }
}
