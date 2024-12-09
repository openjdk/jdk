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
 * @summary All sorts of random things.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver template_framework.examples.TestInstantiationOfManyTests
 */

package template_framework.examples;

import java.util.Arrays;

import compiler.lib.compile_framework.*;
import compiler.lib.template_framework.*;

public class TestInstantiationOfManyTests {

    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        comp.addJavaSourceCode("p.xyz.InnerTest", generate());

        // Compile the source file.
        comp.compile();

        // Object ret = p.xyz.InnerTest.main();
        Object ret = comp.invoke("p.xyz.InnerTest", "main", new Object[] {new String[0]});
        System.out.println("res: " + ret);
    }

    // Generate a source Java file as String
    public static String generate() {
        TestClassInstantiator instantiator = new TestClassInstantiator("p.xyz", "InnerTest");

        Template staticsTemplate = new Template("my_example_statics",
            """
            // $statics
            private static String $PARAM1 = "#{param1}";
            """
        );
        Template mainTemplate = new Template("my_example_main",
            """
            // $main
            $test("#{param2}");
            """
        );
        Template testTemplate = new Template("my_example_test",
            """
            // $test
            public static void $test(String param2) {
                System.out.println("$test #{param1} #{param2}");
                System.out.println("$test " + $PARAM1 + " " + param2);
                if (!$PARAM1.equals("#{param1}") || !param2.equals("#{param2}")) {
                    throw new RuntimeException("Strings mismatched");
                }
            }
            """
        );

        // 2 individual instantiations:
        instantiator.where("param1", "abc").where("param2", "xyz").add(staticsTemplate, mainTemplate, testTemplate);
        instantiator.where("param1", "def").where("param2", "pqr").add(staticsTemplate, mainTemplate, testTemplate);

        // Cross product with parameters, produces 9 individual instantiations:
        instantiator.where("param1", Arrays.asList("aaa", "bbb", "ccc"))
                    .where("param2", Arrays.asList("xxx", "yyy", "zzz"))
                    .add(staticsTemplate, mainTemplate, testTemplate);

        return instantiator.instantiate();
    }
}
