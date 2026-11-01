/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary C2 should not crash with node limit exceeded for large int string concatenations
 * @library /test/lib /
 * @run main/othervm -Xbatch compiler.stringopts.TestLargeIntConcat
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,*GeneratedTest::test*
 *                   compiler.stringopts.TestLargeIntConcat
 */

package compiler.stringopts;

import java.util.List;

import compiler.lib.compile_framework.CompileFramework;
import compiler.lib.template_framework.Template;

import static compiler.lib.template_framework.Template.*;

public class TestLargeIntConcat {
    private static final String GENERATED_CLASS_NAME = "GeneratedTest";

    // Number of int appends per concat. Small counts are expanded by stringopts,
    // large ones exceed the estimated node budget and must bail out gracefully.
    // The appends must form a single fluent chain to be recognized as one concat,
    // and javac cannot compile chains much longer than this.
    private static final List<Integer> APPEND_COUNTS = List.of(1, 10, 100, 200, 500);

    public static void main(String[] args) {
        CompileFramework compileFramework = new CompileFramework();
        compileFramework.addJavaSourceCode(GENERATED_CLASS_NAME, generate());
        compileFramework.compile();
        compileFramework.invoke(GENERATED_CLASS_NAME, "main", new Object[] {new String[] {}});
    }

    static String generate() {
        var testTemplate = Template.make("count", (Integer count) -> scope(
            """
                public static String test#count(int i) {
                    return new StringBuilder()
            """,
            repeat(count, scope("            .append(i)\n")),
            """
                        .toString();
                }

                static void run#count() {
                    for (int i = 0; i < 20_000; i++) {
                        String expected = String.valueOf(i).repeat(#count);
                        String actual = test#count(i);
                        if (!expected.equals(actual)) {
                            throw new RuntimeException("test#count(" + i + ") returned wrong result: " + actual);
                        }
                    }
                }

            """
        ));

        var classTemplate = Template.make(() -> scope(
            let("className", GENERATED_CLASS_NAME),
            """
            public class #className {
                public static void main(String[] args) {
            """,
            APPEND_COUNTS.stream().map(n -> "        run" + n + "();\n").toList(),
            """
                }

            """,
            APPEND_COUNTS.stream().map(n -> testTemplate.asToken(n)).toList(),
            """
            }
            """
        ));
        return classTemplate.render();
    }
}
