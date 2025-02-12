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

import java.util.List;

import compiler.lib.compile_framework.*;
import compiler.lib.template_framework.Template;
import static compiler.lib.template_framework.Template.body;
import static compiler.lib.template_framework.Template.let;
import static compiler.lib.template_framework.Template.$;

public class TestAdvanced {

    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add java source files.
        comp.addJavaSourceCode("p.xyz.InnerTest1", generate1());
        comp.addJavaSourceCode("p.xyz.InnerTest2", generate2());
        comp.addJavaSourceCode("p.xyz.InnerTest3", generate3());

        // Compile the source files.
        comp.compile();

        // Object ret = p.xyz.InnterTest1.main();
        comp.invoke("p.xyz.InnerTest1", "main", new Object[] {});
        comp.invoke("p.xyz.InnerTest2", "main", new Object[] {});
        comp.invoke("p.xyz.InnerTest3", "main", new Object[] {});
    }

    // This example shows the use of various Tokens.
    public static String generate1() {
        // A Template is essencially a function / lambda that produces a
        // token body, which is a list of Tokens that are concatenated.
        var templateClass = Template.make(() -> body(
            // The "body" method is filled by a sequence of "Tokens".
            // This can be Strings and multi-line Strings, but also
            // boxed primitives.
            """
            package p.xyz;

            public class InnerTest1 {
                public static void main() {
                    System.out.println("Hello World!");
            """,
            "int a = ", Integer.valueOf(1), ";\n",
            "float b = ", Float.valueOf(1.5f), ";\n",
            // Special Float values are "smartly" formatted!
            "float nan = ", Float.valueOf(Float.POSITIVE_INFINITY), ";\n",
            "boolean c = ", Boolean.valueOf(true), ";\n",
            // Lists of Tokens are also allowed:
            List.of("int ", "d = 5", ";\n"),
            // That can be great for streaming / mapping over an existing list:
            List.of(3, 5, 7, 11).stream().map(i -> "System.out.println(" + i + ");\n").toList(),
            """
                    System.out.println(a + " " + b + " " + nan + " " + c + " " + d);
                }
            }
            """
        ));

        // Render templateClass to String.
        return templateClass.withArgs().render();
    }

    // This example shows the use of Templates, with and without arguments.
    public static String generate2() {
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

            public class InnerTest2 {
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

    // Example with hashtag replacements (arguments and let), and $-name renamings.
    // Note: hashtag replacements are a workaround for the missing string templates.
    //       If we had string templates, we could just capture the typed lambda
    //       arguments, and use them directly in the String via string templating.
    public static String generate3() {
        var template1 = Template.make("x", (Integer x) -> body(
            // We have the "#x" hashtag replacement from the argument capture above.
            // Additionally, we can define "#con" as a hashtag replacement from let:
            let("con", 3 * x),
            // In the code below, we use "var" as a local variable. But if we were
            // to instantiate this template twice, the names could conflict. Hence,
            // we automatically rename the names that have a $ prepended.
            """
            int $var = #con;
            System.out.println("T1: #x, #con, " + $var);
            """
        ));

        var template2 = Template.make("x", (Integer x) ->
            // Sometimes it can be helpful to not just create a hashtag replacement
            // with let, but also to capture the variable.
            let("y", 11 * x, y ->
                body(
                    """
                    System.out.println("T2: #x, #y");
                    """,
                    template1.withArgs(y)
                )
            )
        );

        // This template generates an int variable and assigns it a value.
        // Together with template4, we see that each template has a unique renaming
        // for a $-name replacement.
        var template3 = Template.make("name", "value", (String name, Integer value) -> body(
            """
            int #name = #value; // Note: $var is not #name
            """
        ));

        var template4 = Template.make(() -> body(
            """
            // We will define the variable $var:
            """,
            // We can capture the $-name programmatically, and pass it to other templates:
            template3.withArgs($("var"), 42),
            """
            if ($var != 42) { throw new RuntimeException("Wrong value!"); }
            """
        ));

        var templateClass = Template.make(() -> body(
            """
            package p.xyz;

            public class InnerTest3 {
                public static void main() {
            """,
                    template1.withArgs(1),
                    template1.withArgs(7),
                    template2.withArgs(2),
                    template2.withArgs(5),
                    template4.withArgs(),
            """
                }
            }
            """
        ));

        // Render templateClass to String.
        return templateClass.withArgs().render();
    }
}
