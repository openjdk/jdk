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

        // Object ret = XYZ.test(5);
        Object ret = comp.invoke("p.xyz.InnerTest", "test", new Object[] {});
        System.out.println("res: " + ret);

        // // Extract return value of invocation, verify its value.
        // int i = (int) ret;
        // System.out.println("Result of call: " + i);
        // if (i != 10) {
        //     throw new RuntimeException("wrong value: " + i);
        // }
    }

    // Generate a source Java file as String
    public static String generate() {
        ClassScope scope = new ClassScope("p.xyz", "InnerTest");
        Parameters parameters = new Parameters();

        Template template = new Template(
            """
            public static int test() {
                int $con1 = #{conx:int_con};
                int ${con2:int} = #{cony};
                int $con2 = #{conz:int_con(3,11):con1};
                #{:code:con1,con2}
                return $con1 + $con2;
            }
            """
        );
        template.instantiate(scope, parameters);
        return scope.toString();
    }
}
