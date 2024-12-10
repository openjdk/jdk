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
        instantiator.where("param1", "abc")
                    .where("param2", "xyz")
                    .add(staticsTemplate, mainTemplate, testTemplate);
        instantiator.where("param1", "def")
                    .where("param2", "pqr")
                    .add(staticsTemplate, mainTemplate, testTemplate);

        // Cross product with parameters, produces 9 individual instantiations:
        instantiator.where("param1", Arrays.asList("aaa", "bbb", "ccc"))
                    .where("param2", Arrays.asList("xxx", "yyy", "zzz"))
                    .add(staticsTemplate, mainTemplate, testTemplate);

        // But we do not have to provide all 3 templates, we can also provide them
        // selectively.
        Template helloStaticTemplate = new Template("my_example_hello_static",
            """
            // $hello_static
            static {
                System.out.println("$hello_static");
            }
            """
        );
        instantiator.add(helloStaticTemplate, null, null);

        Template helloMainTemplate = new Template("my_example_hello_main",
            """
            // $hello_main
            System.out.println("$hello_main");
            """
        );
        instantiator.add(null, helloMainTemplate, null);

        // Define some method/function we would like to call from a few templates later:
        Template helloFunctionTemplate = new Template("my_example_hello_function",
            """
            public static void helloFunction(String txt) {
                System.out.println("helloFunction: " + txt);
            }
            """
        );
        instantiator.add(null, null, helloFunctionTemplate);

        // Invoke that function with 3 different arguments.
        Template callFunctionTemplate = new Template("my_example_call_function",
            """
            // $call_function
            helloFunction("#{param}");
            """
        );
        instantiator.where("param", Arrays.asList("hello A", "hello B", "hello C"))
                    .add(null, callFunctionTemplate, null);

        // Replacements are shared between templates of the same instantiation:
        Template staticsTemplate2 = new Template("my_example_statics_2",
            """
            // $statics2
            private static int $MY_CON = #{my_con:int_con};
            """
        );
        Template mainTemplate2 = new Template("my_example_main_2",
            """
            // $main2
            $test(#{my_con});
            """
        );
        Template testTemplate2 = new Template("my_example_test_2",
            """
            // $test2
            public static void $test(int myCon) {
                if (myCon != #{my_con}) {
                    throw new RuntimeException("Replacements mismatch.");
                }
            }
            """
        );
        // We instantiate these templates in 3 sets. Every set internally shares the
        // replacements and variables.
        instantiator.repeat(3).add(staticsTemplate2, mainTemplate2, testTemplate2);

        // Collect everything into a String.
        return instantiator.instantiate();
    }
}
