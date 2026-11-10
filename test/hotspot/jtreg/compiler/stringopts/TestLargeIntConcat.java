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
 * @key randomness
 * @library /test/lib /
 * @run main/othervm -Xbatch compiler.stringopts.TestLargeIntConcat
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,*GeneratedTest::test*
 *                   compiler.stringopts.TestLargeIntConcat
 */

package compiler.stringopts;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import compiler.lib.compile_framework.CompileFramework;
import compiler.lib.template_framework.Template;
import jdk.test.lib.Utils;

import static compiler.lib.template_framework.Template.*;

public class TestLargeIntConcat {
    private static final String GENERATED_CLASS_NAME = "GeneratedTest";

    // The node limit is hit somewhere in this range. javac cannot compile
    // much longer append chains.
    private static final int MIN_RANDOM_COUNT = 100;
    private static final int MAX_RANDOM_COUNT = 500;

    public static void main(String[] args) {
        Random random = Utils.getRandomInstance();
        // MAX_RANDOM_COUNT always exceeds the node limit without the fix.
        List<Integer> counts = List.of(MAX_RANDOM_COUNT,
                                       MIN_RANDOM_COUNT + random.nextInt(MAX_RANDOM_COUNT - MIN_RANDOM_COUNT));
        System.out.println("Append counts: " + counts);

        CompileFramework compileFramework = new CompileFramework();
        compileFramework.addJavaSourceCode(GENERATED_CLASS_NAME, generate(counts));
        compileFramework.compile();
        compileFramework.invoke(GENERATED_CLASS_NAME, "main", new Object[] {new String[] {}});
    }

    static String generate(List<Integer> counts) {
        // The prefix is not a constant, so the coder of the result is unknown at
        // compile time and every int argument takes the most expensive path.
        var testTemplate = Template.make("index", "count", (Integer index, Integer count) -> scope(
            """
                public static String test#index(String prefix, int i) {
                    return new StringBuilder()
                        .append(prefix)
            """,
            repeat(count, scope("            .append(i)\n")),
            """
                        .toString();
                }

                static void run#index(String prefix) {
                    for (int i = 0; i < 20_000; i++) {
                        String expected = prefix + String.valueOf(i).repeat(#count);
                        String actual = test#index(prefix, i);
                        if (!expected.equals(actual)) {
                            throw new RuntimeException("test#index with #count appends returned wrong result for " + i);
                        }
                    }
                }

            """
        ));

        List<Object> calls = new ArrayList<>();
        List<Object> methods = new ArrayList<>();
        for (int i = 0; i < counts.size(); i++) {
            calls.add("        run" + i + "(prefix);\n");
            methods.add(testTemplate.asToken(i, counts.get(i)));
        }

        var classTemplate = Template.make(() -> scope(
            let("className", GENERATED_CLASS_NAME),
            """
            public class #className {
                public static void main(String[] args) {
                    String prefix = args.length > 0 ? args[0] : "x";
            """,
            calls,
            """
                }

            """,
            methods,
            """
            }
            """
        ));
        return classTemplate.render();
    }
}
