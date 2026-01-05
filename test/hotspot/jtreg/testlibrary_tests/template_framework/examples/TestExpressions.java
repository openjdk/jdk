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
 * @bug 8359412 8370922
 * @summary Demonstrate the use of Expressions from the Template Library.
 * @modules java.base/jdk.internal.misc
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @compile ../../../compiler/lib/verify/Verify.java
 * @run main ${test.main.class}
 */

package template_framework.examples;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;

import compiler.lib.compile_framework.*;
import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateToken;
import static compiler.lib.template_framework.Template.scope;
import static compiler.lib.template_framework.Template.let;
import compiler.lib.template_framework.library.Expression;
import compiler.lib.template_framework.library.Operations;
import compiler.lib.template_framework.library.TestFrameworkClass;

public class TestExpressions {
    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        comp.addJavaSourceCode("p.xyz.InnerTest", generate(comp));

        // Compile the source file.
        comp.compile("--add-modules=jdk.incubator.vector");

        // p.xyz.InnterTest.main(new String[] {});
        comp.invoke("p.xyz.InnerTest", "main", new Object[] {new String[] {
            "--add-modules=jdk.incubator.vector",
            "--add-opens", "jdk.incubator.vector/jdk.incubator.vector=ALL-UNNAMED",
            "--add-opens", "java.base/java.lang=ALL-UNNAMED"
        }});
    }

    // Generate a Java source file as String
    public static String generate(CompileFramework comp) {
        // Generate a list of test methods.
        List<TemplateToken> tests = new ArrayList<>();

        // Create a test method that executes the expression, with constant arguments.
        var withConstantsTemplate = Template.make("expression", (Expression expression) -> {
            // Create a token: fill the expression with a fixed set of constants.
            // We then use the same token with the same constants, once compiled and once not compiled.
            //
            // Some expressions can throw Exceptions. We have to catch them. In such a case, we return
            // the Exception instead of the value from the expression, and compare the Exceptions.
            //
            // Some Expressions do not have a deterministic result. For example, different NaN or
            // precision results from some operators. We only compare the results if we know that the
            // result is deterministically the same.
            TemplateToken expressionToken = expression.asToken(expression.argumentTypes.stream().map(t -> t.con()).toList());
            return scope(
                let("returnType", expression.returnType),
                """
                @Test
                public static void $primitiveConTest() {
                    Object v0 = ${primitiveConTest}_compiled();
                    Object v1 = ${primitiveConTest}_reference();
                """,
                expression.info.isResultDeterministic ? "Verify.checkEQ(v0, v1);\n" : "",
                """
                }

                @DontInline
                public static Object ${primitiveConTest}_compiled() {
                try {
                """,
                    "return ", expressionToken, ";\n",
                    expression.info.exceptions.stream().map(exception ->
                        "} catch (" + exception + " e) { return e;\n"
                    ).toList(),
                """
                    } finally {
                        // Just so that javac is happy if there are no exceptions to catch.
                    }
                }

                @DontCompile
                public static Object ${primitiveConTest}_reference() {
                try {
                """,
                    "return ", expressionToken, ";\n",
                    expression.info.exceptions.stream().map(exception ->
                        "} catch (" + exception + " e) { return e;\n"
                    ).toList(),
                """
                    } finally {
                        // Just so that javac is happy if there are no exceptions to catch.
                    }
                }
                """
            );
        });

        for (Expression operation : Operations.SCALAR_NUMERIC_OPERATIONS) {
            tests.add(withConstantsTemplate.asToken(operation));
        }

        // Create the test class, which runs all tests.
        return TestFrameworkClass.render(
            // package and class name.
            "p.xyz", "InnerTest",
            // Set of imports.
            Set.of("compiler.lib.verify.*", "jdk.incubator.vector.Float16"),
            // classpath, so the Test VM has access to the compiled class files.
            comp.getEscapedClassPathOfCompiledClasses(),
            // The list of tests.
            tests);
    }
}
