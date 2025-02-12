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
 * @summary Test advanced use of Templates with the Compile Framework.
 *          It displays the use of most features in the Template Framework.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver template_framework.examples.TestAdvanced
 */

package template_framework.examples;

import compiler.lib.compile_framework.*;
import compiler.lib.template_framework.Template;
import static compiler.lib.template_framework.Template.body;

public class TestAdvanced {

    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        comp.addJavaSourceCode("p.xyz.InnerTest1", generate1());

        // Compile the source file.
        comp.compile();

        // Object ret = p.xyz.InnterTest1.main();
        comp.invoke("p.xyz.InnerTest1", "main", new Object[] {});
    }

    // Generate a source Java file as String
    public static String generate1() {
        // A Template with no arguments.
        var templateHello = Template.make(() -> body(
            """
            System.out.println("Hello");
            """
        ));

        // A Template with a single Integer argument.
        var templateCompare = Template.make("arg", (Integer arg) -> body(
            "System.out.println(", arg, ");\n",  // capture arg via lambda argument
            "System.out.println(#arg);\n",       // capture arg via hashtag replacement
            "if (#arg != ", arg, ") { throw new RuntimeException(\"mismatch\"); }\n"
        ));

        // A Template that creates the body of the Class and main method, and then
        // uses the two Templates above inside it.
        var templateClass = Template.make(() -> body(
            """
            package p.xyz;

            public class InnerTest1 {
                public static void main() {
            """,
                    templateHello.withArgs(),
                    templateCompare.withArgs(7),
                    templateCompare.withArgs(42),
            """
                }
            }
            """
        ));

        // Render templateClass to String.
        return templateClass.withArgs().render();
    }
}
