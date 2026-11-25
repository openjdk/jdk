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

/**
 * @test
 * @bug 8366138
 * @summary C2: jump_switch_ranges could cause stack overflow when compiling
 *          huge switch statement with zero-count profiling data.
 * @library /test/lib /
 * @run main/othervm ${test.main.class}
 * @run main/othervm -Xbatch -XX:CompileOnly=Test::test -XX:-DontCompileHugeMethods -XX:CompilerThreadStackSize=512 ${test.main.class}
 * @run main/othervm -Xbatch -XX:CompileOnly=Test::test -XX:+DontCompileHugeMethods -XX:CompilerThreadStackSize=512 ${test.main.class}
 */

package compiler.c2;

import java.util.stream.Stream;

import compiler.lib.compile_framework.CompileFramework;
import compiler.lib.template_framework.Template;
import static compiler.lib.template_framework.Template.scope;

public class TestSwitchStackOverflow {
    // Template method exceeds HugeMethodLimit, hence -XX:-DontCompileHugeMethods.
    // 5000 cases + CompilerThreadStackSize=512 reliably overflows without the fix.
    static final int NUM_CASES = 5000;

    // Generate a class with a large tableswitch method and a main that
    // triggers recompilation with zero-count profiling data:
    //   Phase 1: warm up with default-only path -> C2 compiles
    //   Phase 2: hit a case -> triggers unstable_if deopt
    //   Phase 3: recompile with all counts zero (except the default case)
    static String generate() {
        var caseTemplate = Template.make("i", (Integer i) -> scope(
            """
            case #i:
                return #i;
            """
        ));
        var test = Template.make(() -> scope(
            """
            public class Test {
                static int test(int x) {
                    switch (x) {
                    """,
                    Stream.iterate(0, i -> i + 1)
                          .limit(NUM_CASES)
                          .map(i -> caseTemplate.asToken(i))
                          .toList(),
                    """
                        default: return -1;
                    }
                }

                public static void main(String[] args) {
                    for (int i = 0; i < 10_000; i++) {
                        test(-1);
                    }
                    test(42);
                    for (int i = 0; i < 10_000; i++) {
                        test(-1);
                    }
                    System.out.println("Done");
                }
            }
            """
        ));

        return test.render();
    }

    public static void main(String[] args) {
        CompileFramework comp = new CompileFramework();
        comp.addJavaSourceCode("Test", generate());
        comp.compile();
        comp.invoke("Test", "main", new Object[] { new String[0] });
    }
}
