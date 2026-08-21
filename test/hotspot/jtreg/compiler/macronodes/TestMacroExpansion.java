/*
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
 * @bug 8390550
 * @key stress
 * @requires vm.compiler2.enabled
 * @summary Test scalarized calls and entry points around calling convention limits.
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

package compiler.macronodes;

import compiler.lib.compile_framework.CompileFramework;
import compiler.lib.template_framework.Template;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import static compiler.lib.template_framework.Template.*;

public class TestMacroExpansion {
    private static final String GENERATED_CLASS_NAME = "GeneratedTest";
    private static final String PACKAGE_NAME = "compiler.macronodes";
    private static final String QUALIFIED_NAME = PACKAGE_NAME + "." + GENERATED_CLASS_NAME;

    public static void main(String[] args) throws Exception {
        CompileFramework compileFramework = new CompileFramework();
        compileFramework.addJavaSourceCode(GENERATED_CLASS_NAME, generate());
        compileFramework.compile();
        String[] command = {
                "-classpath",
                compileFramework.getEscapedClassPathOfCompiledClasses(),
                "-Xcomp",
                "-XX:-TieredCompilation",
                "-XX:CompileCommand=compileonly," + QUALIFIED_NAME + "::test",
                "-XX:+IgnoreUnrecognizedVMOptions",
                "-XX:VerifyIterativeGVN=1110",
                QUALIFIED_NAME
        };

        OutputAnalyzer analyzer = ProcessTools.executeTestJava(command);
        analyzer.shouldHaveExitValue(0);
    }

    static String generate() {
        final int rows = 140;
        final int args = 9;
        return Template.make(() -> scope(
                let("className", GENERATED_CLASS_NAME),
                let("packageName", PACKAGE_NAME),
                """
                package #packageName;

                public class #className {
                    interface I {
                    }

                    public static void main(String[] args) {
                        test();
                    }

                    static class A implements I {
                    }

                    static A a = new A();

                    static Object[] arr;

                """,
                "    static void init(", repeatAndJoin(args, ", ", i -> scope("I i" + i)), ") {\n",
                "        arr = new Object[] {", repeatAndJoin(args, ", ", i -> scope("i" + i)), "};\n",
                """
                    }

                """,
                repeatAndJoin(rows, "\n", rowIndex -> scope(
               "    static Object ", repeatAndJoin(args, ", ", index -> scope( "o" + (index + rowIndex * args))), ";"
            )), "\n",
                """

                    static void test() {
                """,
                repeatAndJoin(rows, "\n", rowIndex -> scope(
               "        init(", repeatAndJoin(args, ", ", index -> scope( "(I)o" + (index + rowIndex * args))), ");"
            )), "\n",
                """
                    }
                }
                """
        )).render();
    }
}
