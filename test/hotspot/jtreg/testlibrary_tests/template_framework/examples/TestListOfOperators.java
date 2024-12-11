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
 * @summary Example to test lists of operators.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver template_framework.examples.TestListOfOperators
 */

package template_framework.examples;

import java.util.Arrays;

import compiler.lib.compile_framework.*;
import compiler.lib.template_framework.*;

public class TestListOfOperators {

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
            private static int $GOLD1 = $test(#{in1:int_con});
            private static int $GOLD2 = $test(#{in2:int_con});
            private static int $GOLD3 = $test(#{in3:int_con});
            """
        );
        Template mainTemplate = new Template("my_example_main",
            """
            // $main
            for (int i = 0; i < 10_000; i++) {
                int $res1 = $test(#{in1});
                int $res2 = $test(#{in2});
                int $res3 = $test(#{in3});
                if ($res1 != $GOLD1 ||
                    $res2 != $GOLD2 ||
                    $res3 != $GOLD3) {
                    throw new RuntimeException("wrong result for $test");
                }
            }
            """
        );
        Template testTemplate = new Template("my_example_test",
            """
            // $test
            public static int $test(int in) {
                return in #{OP} #{:int_con};
            }
            """
        );

        // Instantiate a set of the 3 templates for every operator.
        instantiator.where("OP", Arrays.asList("+", "-", "*", "&", "|"))
                    .add(staticsTemplate, mainTemplate, testTemplate);

        // Collect everything into a String.
        return instantiator.instantiate();
    }
}
