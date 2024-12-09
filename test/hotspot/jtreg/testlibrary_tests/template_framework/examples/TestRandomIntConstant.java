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
 * @summary Example test with constant int in Template.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver template_framework.examples.TestRandomIntConstant
 */

package template_framework.examples;

import compiler.lib.compile_framework.*;
import compiler.lib.template_framework.*;

public class TestRandomIntConstant {

    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        comp.addJavaSourceCode("p.xyz.InnerTest", generate());

        // Compile the source file.
        comp.compile();

        // Object ret = p.xyz.InnterTest.test();
        Object ret = comp.invoke("p.xyz.InnerTest", "test", new Object[] {});
        System.out.println("res: " + ret);
    }

    // Generate a source Java file as String
    public static String generate() {
        Template template = new Template("my_example",
            """
            package p.xyz;

            public class InnerTest {
                #open(class)
                public static int test() {
                    #open(method)
                    int $con0 = 123;
                    int $con1 = #{:int_con};
                    int $con2 = #{:int_con(lo=0,hi=100)};
                    int $con3 = #{:int_con(lo=0)};
                    int $con4 = #{:int_con(hi=0)};

                    int $con5 = #{:int_con(lo=min_int)};
                    int $con6 = #{:int_con(lo=max_int)};
                    int $con7 = #{:int_con(hi=max_int)};

                    if ($con0 != 123) {
                        throw new RuntimeException("$con0 was not 123");
                    }
                    if ($con2 < 0 || -$con2 >= 100) {
                        throw new RuntimeException("$con2 was out of range");
                    }
                    if ($con3 < 0) {
                        throw new RuntimeException("$con3 was not positive");
                    }
                    if ($con4 >= 0) {
                        throw new RuntimeException("$con4 was not negative");
                    }

                    return $con0 + $con1 + $con2 + $con3 + $con4;
                    #close(method)
                }
                #close(class)
            }
            """
        );
        return template.instantiate();
    }
}
