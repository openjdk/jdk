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
            #{:my_code}""", 10
        ));

        codeGenerators.add(new Template("my_int_loop",
            """
            for (int ${i:int:immutable} = 0; $i < 100; $i++) {
                #{:my_code:$i}
            }""", 10
        ));

        codeGenerators.add(new Template("my_if",
            """
            if (#{:my_expr(type=boolean)}) {
                #{:my_code}
            } else {
                #{:my_code}
            }""", 10
        ));

        codeGenerators.add(new Template("my_var", "#{:var(type=#type)}", 5));
        codeGenerators.add(new Template("my_add", "(#{:my_expr(type=#type)} + #{:my_expr(type=#type)})", 5));
        codeGenerators.add(new Template("my_sub", "(#{:my_expr(type=#type)} - #{:my_expr(type=#type)})", 5));
        codeGenerators.add(new Template("my_mul", "(#{:my_expr(type=#type)} * #{:my_expr(type=#type)})", 5));
        codeGenerators.add(new Template("my_and", "(#{:my_expr(type=#type)} & #{:my_expr(type=#type)})", 5));
        codeGenerators.add(new Template("my_or",  "(#{:my_expr(type=#type)} | #{:my_expr(type=#type)})", 5));
        codeGenerators.add(new Template("my_xor", "(#{:my_expr(type=#type)} ^ #{:my_expr(type=#type)})", 5));

        SelectorCodeGenerator.Predicate isNumber = (Scope scope, Parameters parameters) -> {
            String type = parameters.get("type", scope);
            return type.equals("int") || type.equals("long");
        };

        SelectorCodeGenerator expression = new SelectorCodeGenerator("my_expr", "con");
        expression.add("con", 10);
        expression.add("my_var", 20);
        expression.add("my_add", 20, isNumber);
        expression.add("my_sub", 20, isNumber);
        expression.add("my_mul", 20, isNumber);
        expression.add("my_and", 20);
        expression.add("my_or",  20);
        expression.add("my_xor", 20);
        codeGenerators.add(expression);

        codeGenerators.add(new Template("my_assign",
            """
            // Assignment with type #{type:choose(from=int|long|boolean)}
            #{:mutable_var(type=#type)} = #{:my_expr(type=#type)};
            #{:my_code}""", 2
        ));

        codeGenerators.add(new Template("my_def_var",
            """
            // def_var $var with type #{type:choose(from=int|long|boolean)}
            //  value #{value:con(type=#type)}
            // #{:def_var(name=$var,prefix=#type,value=#value,type=#type)} dispatched
            $var = #{:my_expr(type=#type)};
            #{:my_code}""", 2
        ));

        codeGenerators.add(new Template("my_static_prefix", "static #{type}"));
        codeGenerators.add(new Template("my_def_field",
            """
            // def_field $field with type #{type:choose(from=int|long|boolean)}
            //  value #{value:con(type=#type)}
            //  prefix #{prefix:my_static_prefix(type=#type)}
            // #{:def_field(name=$field,prefix=#prefix,value=#value,type=#type)} dispatched
            $field = #{:my_expr(type=#type)};
            #{:my_code}""", 2
        ));

        // This is the core of the random code generator: the selector picks a random template from above,
        // and then those templates may call back recursively to this selector.
        SelectorCodeGenerator selectorForCode = new SelectorCodeGenerator("my_code", "my_empty");
        selectorForCode.add("my_split", 10);
        selectorForCode.add("my_int_loop", 10);
        selectorForCode.add("my_if", 10);
        selectorForCode.add("my_assign", 100);
        selectorForCode.add("my_def_var", 50);
        selectorForCode.add("my_def_field", 50);
        codeGenerators.add(selectorForCode);

        CodeGeneratorLibrary library = new CodeGeneratorLibrary(CodeGeneratorLibrary.standard(), codeGenerators);

        Template template = new Template("my_example",
            """
            package p.xyz;

            public class InnerTest {
                #open(class)
                public static int main() {
                    #open(method)
                    // make sure we have at least 1 mutable variable per type.
                    int ${xi:int} = 0;
                    long ${xl:long} = 0;
                    boolean ${xb:boolean} = false;
                    // Add that variable to available variables, and call my_code.
                    #{:my_code:$xi,$xl,$xb}
                    return #{:my_expr(type=int):$xi,$xl,$xb};
                    #close(method)
                }
                #close(class)
            }"""
        );
        return template.with(library).instantiate();
    }
}
