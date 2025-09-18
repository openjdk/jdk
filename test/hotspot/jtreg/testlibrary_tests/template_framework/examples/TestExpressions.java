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
 * @summary Demonstrate the use of Expressions from the Template Library.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @compile ../../../compiler/lib/verify/Verify.java
 * @run main template_framework.examples.TestExpressions
 */

package template_framework.examples;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;

import compiler.lib.compile_framework.*;
import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateToken;
import static compiler.lib.template_framework.Template.body;
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
        comp.compile();

        // p.xyz.InnterTest.main(new String[] {});
        comp.invoke("p.xyz.InnerTest", "main", new Object[] {new String[] {}});
    }

    // Generate a Java source file as String
    public static String generate(CompileFramework comp) {
        // Generate a list of test methods.
        List<TemplateToken> tests = new ArrayList<>();

        // Create a test method that executes the expression, with constant arguments.
        var withConstantsTemplate = Template.make("expression", (Expression expression) -> {
            // Create a token: fill the expression with a fixed set of constants.
            // We then use the same token with the same constants, once compiled and once not compiled.
            TemplateToken expressionToken = expression.asToken(expression.argumentTypes.stream().map(t -> t.con()).toList());
            return body(
                let("returnType", expression.returnType),
                """
                @Test
                public static void $primitiveConTest() {
                    #returnType v0 = ${primitiveConTest}_compiled();
                    #returnType v1 = ${primitiveConTest}_reference();
                    Verify.checkEQ(v0, v1);
                }

                @DontInline
                public static #returnType ${primitiveConTest}_compiled() {
                """,
                "return ", expressionToken, ";\n",
                """
                }

                @DontCompile
                public static #returnType ${primitiveConTest}_reference() {
                """,
                "return ", expressionToken, ";\n",
                """
                }
                """
            );
        });

        for (Expression operation : Operations.PRIMITIVE_OPERATIONS) {
            tests.add(withConstantsTemplate.asToken(operation));
        }

        // Create the test class, which runs all tests.
        return TestFrameworkClass.render(
            // package and class name.
            "p.xyz", "InnerTest",
            // Set of imports.
            Set.of("compiler.lib.verify.*"),
            // classpath, so the Test VM has access to the compiled class files.
            comp.getEscapedClassPathOfCompiledClasses(),
            // The list of tests.
            tests);
    }
}
