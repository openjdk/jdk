/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 * @bug 8387067
 * @summary Chained concats where a replacement removes more nodes than it creates
 *          must not trip the node estimate assert.
 * @library /test/lib /
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,*GeneratedTest::test
 *                   -XX:CompileCommand=dontinline,*StringBuilder::*
 *                   compiler.stringopts.TestManyStackedConcatsTemplated
 */

package compiler.stringopts;

import compiler.lib.compile_framework.CompileFramework;
import compiler.lib.template_framework.Template;

import static compiler.lib.template_framework.Template.*;

public class TestManyStackedConcatsTemplated {
    private static final String GENERATED_CLASS_NAME = "GeneratedTest";

    public static void main(String[] args) {
        CompileFramework compileFramework = new CompileFramework();
        compileFramework.addJavaSourceCode(GENERATED_CLASS_NAME, generate());
        compileFramework.compile();
        compileFramework.invoke(GENERATED_CLASS_NAME, "main", new Object[] {new String[] {}});
    }

    static String generate() {
        return Template.make(() -> scope(
            let("className", GENERATED_CLASS_NAME),
            """
            public class #className {
                public static void main(String[] args) {
                    new StringBuilder().toString();
                    test();
                }

                public static String test() {
                    String s = new StringBuilder().toString();
            """,
            repeat(120, scope(
                "s = new StringBuilder().append(s).toString();\n")),
            """
                    return s;
                }
            }
            """
        )).render();
    }
}
