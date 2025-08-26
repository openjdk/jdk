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
 * @summary Demonstrate the use of Expressions form the Template Library.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @compile ../../../compiler/lib/verify/Verify.java
 * @run main template_framework.examples.TestExpressions
 */

package template_framework.examples;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import compiler.lib.compile_framework.*;
import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateToken;
import static compiler.lib.template_framework.Template.body;
import static compiler.lib.template_framework.Template.let;
import compiler.lib.template_framework.library.Expression;
import compiler.lib.template_framework.library.Operations;

public class TestExpressions {
    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        comp.addJavaSourceCode("p.xyz.InnerTest", generate());

        // Compile the source file.
        comp.compile();

        // p.xyz.InnerTest.main();
        comp.invoke("p.xyz.InnerTest", "main", new Object[] {});
    }

    // Generate a Java source file as String
    public static String generate() {
        // Generate a list of test methods.
        Map<String, TemplateToken> tests = new HashMap<>();

        // Create a test method that executes the expression, with constant arguments.
        var withConstantsTemplate = Template.make("name", "expression", (String name, Expression expression) -> body(
            //let("CON1", type.con()),
            //let("CON2", type.con()),
            let("returnType", expression.returnType),
            """
            public static #returnType #name() {
            """,
            "return ", expression.asToken(expression.argumentTypes.stream().map(t -> t.con()).toList()), ";\n",
            """
            }
            """
        ));

        int idx = 0;
        for (Expression operation : Operations.PRIMITIVE_OPERATIONS) {
            String name = "test" + (idx++);
            tests.put(name, withConstantsTemplate.asToken(name, operation));
        }

        // Finally, put all the tests together in a class, and invoke all
        // tests from the main method.
        var template = Template.make(() -> body(
            """
            package p.xyz;

            import compiler.lib.verify.*;
            import java.lang.foreign.MemorySegment;

            public class InnerTest {
                public static void main() {
            """,
            // Call all test methods from main.
            tests.keySet().stream().map(
                n -> List.of(n, "();\n")
            ).toList(),
            """
                }
            """,
            // Now add all the test methods.
            tests.values().stream().toList(),
            """
            }
            """
        ));

        // Render the template to a String.
        return template.render();
    }
}
