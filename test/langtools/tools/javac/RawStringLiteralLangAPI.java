/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Unit tests for Raw String Literal language changes
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main RawStringLiteralLangAPI
 */

import toolbox.JavacTask;
import toolbox.JavaTask;
import toolbox.Task;
import toolbox.ToolBox;

public class RawStringLiteralLangAPI {
    private static ToolBox TOOLBOX = new ToolBox();

    public static void main(String... args) {
        test1();
        test2();
        test3();
        test4();
    }

    /*
     * Check that correct/incorrect syntax is properly detected
     */
    enum Test {
        t0(false, "`*`*`"),
        t1(false, "`\\u2022`\\u2022`"),
        t2(false, "``*`*`"),
        t3(false, "``\\u2022`\\u2022`"),
        t4(false, "`*`*``"),
        t5(false, "`\\u2022`\\u2022``"),
        t6(true, "``*`*``"),
        t7(true, "``\\u2022`\\u2022``"),
        t8(true, "`*``*`"),
        t9(true, "`\\u2022``\\u2022`"),
        ;

        Test(boolean pass, String string) {
            this.pass = pass;
            this.string = string;
        }

        boolean pass;
        String string;
    }
    static void test1() {
        for (Test t : Test.values()) {
            String code =
                    "public class RawStringLiteralTest {\n" +
                            "    public static void main(String... args) {\n" +
                            "        String xxx = " + t.string + ";\n" +
                            "    }\n" +
                            "}\n";
            if (t.pass) {
                compPass(code);
            } else {
                compFail(code);
            }
        }
    }

    /*
     * Check that misuse of \u0060 is properly detected
     */
    static void test2() {
        compFail("public class BadDelimiter {\n" +
                "    public static void main(String... args) {\n" +
                "        String xxx = \\u0060`abc`;\n" +
                "    }\n" +
                "}\n");
    }

    /*
     * Check edge cases of raw string literal as last token
     */
    static void test3() {
        compFail("public class RawStringLiteralTest {\n" +
                "    public static void main(String... args) {\n" +
                "        String xxx = `abc`");
        compFail("public class RawStringLiteralTest {\n" +
                "    public static void main(String... args) {\n" +
                "        String xxx = `abc");
        compFail("public class RawStringLiteralTest {\n" +
                "    public static void main(String... args) {\n" +
                "        String xxx = `abc\u0000");
    }

    /*
     * Check line terminator translation
     */
    static void test4() {
        String[] terminators = new String[] { "\n", "\r\n", "\r" };
        for (String terminator : terminators) {
            String code = "public class LineTerminatorTest {" + terminator +
                          "    public static void main(String... args) {" + terminator +
                          "        String s =" + terminator +
                          "`" + terminator +
                          "abc" + terminator +
                          "`;" + terminator +
                          "        System.out.println(s.equals(\"\\nabc\\n\"));" + terminator +
                          "    }" + terminator +
                          "}" + terminator;
            new JavacTask(TOOLBOX)
                    .sources(code)
                    .classpath(".")
                    .options("--enable-preview", "-source", "12")
                    .run();
            String output = new JavaTask(TOOLBOX)
                    .vmOptions("--enable-preview")
                    .classpath(".")
                    .classArgs("LineTerminatorTest")
                    .run()
                    .writeAll()
                    .getOutput(Task.OutputKind.STDOUT);

            if (!output.contains("true")) {
                throw new RuntimeException("Error detected");
            }
        }
    }

    /*
     * Test source for successful compile.
     */
    static void compPass(String source) {
        String output = new JavacTask(TOOLBOX)
                .sources(source)
                .classpath(".")
                .options("--enable-preview", "-source", "12", "-encoding", "utf8")
                .run()
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (output.contains("compiler.err")) {
            throw new RuntimeException("Error detected");
        }
    }

    /*
     * Test source for unsuccessful compile and specific error.
     */
    static void compFail(String source)  {
        String errors = new JavacTask(TOOLBOX)
                .sources(source)
                .classpath(".")
                .options("-XDrawDiagnostics", "--enable-preview", "-source", "12", "-encoding", "utf8")
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!errors.contains("compiler.err")) {
            throw new RuntimeException("No error detected");
        }
    }
}
