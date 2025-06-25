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
 * @bug 8344942
 * @summary Test simple use of Templates with the Compile Framework.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run main template_framework.examples.TestSimple
 */

package template_framework.examples;

import compiler.lib.compile_framework.*;
import compiler.lib.template_framework.Template;
import static compiler.lib.template_framework.Template.body;

public class TestSimple {

    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        comp.addJavaSourceCode("p.xyz.InnerTest", generate());

        // Compile the source file.
        comp.compile();

        // Object ret = p.xyz.InnerTest.test();
        Object ret = comp.invoke("p.xyz.InnerTest", "test", new Object[] {});
        System.out.println("res: " + ret);

        // Check that the return value is the sum of the two arguments.
        if ((42 + 7) != (int)ret) {
            throw new RuntimeException("Unexpected result");
        }
    }

    // Generate a source Java file as String
    public static String generate() {
        // Create a Template with two arguments.
        var template = Template.make("arg1", "arg2", (Integer arg1, String arg2) -> body(
            """
            package p.xyz;
            public class InnerTest {
                public static int test() {
                    return #arg1 + #arg2;
                }
            }
            """
        ));

        // Use the template with two arguments, and render it to a String.
        return template.render(42, "7");
    }
}
