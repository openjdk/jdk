/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Generate random code with the library, demonstrating a simple Fuzzer.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver template_framework.examples.TestRandomCode
 */

package template_framework.examples;

import java.util.HashSet;

import compiler.lib.compile_framework.*;
import compiler.lib.template_framework.*;

public class TestRandomCode {

    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        comp.addJavaSourceCode("p.xyz.InnerTest", generate());

        // Compile the source file.
        comp.compile();

        // InnerTest.main();
        int res = (int)comp.invoke("p.xyz.InnerTest", "main", new Object[] {});
        System.out.println("res: " + res);
    }

    // Generate a source Java file as String
    public static String generate() {
        // Add some extra methods to the library.
        HashSet<CodeGenerator> codeGenerators = new HashSet<CodeGenerator>();

        codeGenerators.add(new Template("my_empty","/* empty */", 0));

        codeGenerators.add(new Template("my_split",
            """
            #{:my_code}
            #{:my_code}
            """
        ));

        codeGenerators.add(new Template("my_loop",
            """
            for (int ${i:int:immutable} = 0; $i < 100; $i++) {
                #{:my_code:$i}
            }
            """
        ));

        codeGenerators.add(new Template("my_var", "#{:var(type=int)}", 5));
        codeGenerators.add(new Template("my_add", "(#{:my_expression} + #{:my_expression})", 5));
        codeGenerators.add(new Template("my_sub", "(#{:my_expression} - #{:my_expression})", 5));
        codeGenerators.add(new Template("my_mul", "(#{:my_expression} * #{:my_expression})", 5));
        codeGenerators.add(new Template("my_and", "(#{:my_expression} & #{:my_expression})", 5));
        codeGenerators.add(new Template("my_or",  "(#{:my_expression} | #{:my_expression})", 5));
        codeGenerators.add(new Template("my_xor", "(#{:my_expression} ^ #{:my_expression})", 5));

        SelectorCodeGenerator expression = new SelectorCodeGenerator("my_expression", "int_con");
        expression.add("int_con", 20);
        expression.add("my_var", 20);
        expression.add("my_add", 20);
        expression.add("my_sub", 20);
        expression.add("my_mul", 20);
        expression.add("my_and", 20);
        expression.add("my_or",  20);
        expression.add("my_xor", 20);
        codeGenerators.add(expression);

        codeGenerators.add(new Template("my_assign_expression",
            """
            #{:mutable_var(type=int)} = #{:my_expression};
            #{:my_code}
            """, 2
        ));

        // TODO some random if, while, try/catch, random variables, etc

        // This is the core of the random code generator: the selector picks a random template from above,
        // and then those templates may call back recursively to this selector.
        SelectorCodeGenerator selectorForCode = new SelectorCodeGenerator("my_code", "my_empty");
        selectorForCode.add("my_split", 50);
        selectorForCode.add("my_loop", 50);
        selectorForCode.add("my_assign_expression", 100);
        // TODO add more
        codeGenerators.add(selectorForCode);

        CodeGeneratorLibrary library = new CodeGeneratorLibrary(CodeGeneratorLibrary.standard(), codeGenerators);

        Template template = new Template("my_example",
            """
            package p.xyz;

            public class InnerTest {
                #open(class)
                public static int main() {
                    #open(method)
                    // make sure we have at least 1 mutable int variable.
                    int ${x:int} = 0;
                    // Add that variable to available variables, and call my_code.
                    #{:my_code:$x}
                    return $x;
                    #close(method)
                }
                #close(class)
            }
            """
        );
        return template.with(library).instantiate();
    }
}
