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
        comp.invoke("p.xyz.InnerTest", "main", new Object[] {});
    }

    // Generate a source Java file as String
    public static String generate() {
        // Add some extra methods to the library.
        HashSet<CodeGenerator> codeGenerators = new HashSet<CodeGenerator>();

        codeGenerators.add(new Template("my_empty","/* empty */", 0));

        codeGenerators.add(new Template("my_method_code_split",
            """
            #{:my_method_code}
            #{:my_method_code}
            """
        ));

        // TODO some random if, loops, while, try/catch, random variables, etc

        // This is the core of the random code generator: the selector picks a random template from above,
        // and then those templates may call back recursively to this selector.
        SelectorCodeGenerator selectorForCode = new SelectorCodeGenerator("my_method_code", "my_empty");
        selectorForCode.add("my_method_code_split", 100);
        // TODO add more
        codeGenerators.add(selectorForCode);

        CodeGeneratorLibrary library = new CodeGeneratorLibrary(CodeGeneratorLibrary.standard(), codeGenerators);

        Template template = new Template("my_example",
            """
            package p.xyz;

            public class InnerTest {
                #open(class)
                public static void main() {
                    #open(method)
                    #{:my_method_code}
                    #close(method)
                }
                #close(class)
            }
            """
        );
        return template.with(library).instantiate();
    }
}
