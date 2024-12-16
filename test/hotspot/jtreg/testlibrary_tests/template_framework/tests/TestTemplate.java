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
 * @summary Test some basic Template instantiations.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver template_framework.tests.TestTemplate
 */

package template_framework.tests;

import java.util.HashSet;

import compiler.lib.template_framework.*;

public class TestTemplate {

    public static void main(String[] args) {
        test1();
        test2();
        test3();
        test4();
    }

    public static void test1() {
        Template template = new Template("my_template","Hello World!");
        String code = template.instantiate();
        checkEQ(code, "Hello World!");
    }

    public static void test2() {
        Template template = new Template("my_template",
            """
            Code on more
            than a single line
            """
        );
        String code = template.instantiate();
        String expected = 
            """
            Code on more
            than a single line
            """;
        checkEQ(code, expected);
    }

    public static void test3() {
        Template template = new Template("my_template","start #{p1} #{p2} end");
        checkEQ(template.where("p1", "x").where("p2", "y").instantiate(), "start x y end");
        checkEQ(template.where("p1", "a").where("p2", "b").instantiate(), "start a b end");
        checkEQ(template.where("p1", "").where("p2", "").instantiate(), "start   end");
    }

    public static void test4() {
        HashSet<CodeGenerator> codeGenerators = new HashSet<CodeGenerator>();

        codeGenerators.add(new Template("my_generator_1", "proton"));

        codeGenerators.add(new Template("my_generator_2",
            """
            electron #{param1}
            neutron #{param2}
            """
        ));

        codeGenerators.add(new Template("my_generator_3",
            """
            Universe @{:my_generator_1} {
                #{:my_generator_2(param1=up,param2=down)}
                #{:my_generator_2(param1=#param1,param2=#param2)}
            }
            """
        ));
        CodeGeneratorLibrary library = new CodeGeneratorLibrary(null, codeGenerators);

        Template template = new Template("my_template",
            """
            #{:my_generator_3(param1=low,param2=high)}
            {
                #{:my_generator_3(param1=42,param2=24)}
            }
            """
        );

        // TODO instantiate with library and compare???
    }

    public static void checkEQ(String code, String expected) {
        if (!code.equals(expected)) {
            System.out.println(code);
            System.out.println(expected);
            throw new RuntimeException("Template instantiation mismatch!");
        }
    }
}
