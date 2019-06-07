/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8223967
 * @summary Unit tests for Text Block language changes
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main TextBlockAPI
 */

import toolbox.JavacTask;
import toolbox.JavaTask;
import toolbox.Task;
import toolbox.ToolBox;

public class TextBlockAPI {
    private static ToolBox TOOLBOX = new ToolBox();
    private final static String JDK_VERSION = Integer.toString(Runtime.version().feature());

    public static void main(String... args) {
        test1();
        test2();
        test3();
        test4();
    }

    /*
     * Check that correct/incorrect syntax is properly detected
     */
    static void test1() {
        for (String lineterminators : new String[] { "\n", "\r", "\r\n" })
        for (String whitespace : new String[] { "", "   ", "\t", "\u2002" })
        for (String content : new String[] { "a", "ab", "abc", "\u2022", "*".repeat(1000), "*".repeat(10000) })  {
            String code =
                    "public class CorrectTest {\n" +
                            "    public static void main(String... args) {\n" +
                            "        String xxx = " +
                            "\"\"\"" + whitespace + lineterminators +
                            content +
                            "\"\"\";\n" +
                            "    }\n" +
                            "}\n";
            compPass(code);
        }
    }

    /*
     * Check that use of \u0022 is properly detected
     */
    static void test2() {
        compPass("public class UnicodeDelimiterTest {\n" +
                "    public static void main(String... args) {\n" +
                "        String xxx = \\u0022\\u0022\\u0022\nabc\n\\u0022\\u0022\\u0022;\n" +
                "    }\n" +
                "}\n");
    }

    /*
     * Check edge cases of text blocks as last token
     */
    static void test3() {
        compFail("public class EndTest {\n" +
                "    public static void main(String... args) {\n" +
                "        String xxx = \"\"\"\nabc\"\"\"");
        compFail("public class TwoQuoteClose {\n" +
                "    public static void main(String... args) {\n" +
                "        String xxx = \"\"\"\nabc\"\"");
        compFail("public class OneQuoteClose {\n" +
                "    public static void main(String... args) {\n" +
                "        String xxx = \"\"\"\nabc\"");
        compFail("public class NoClose {\n" +
                "    public static void main(String... args) {\n" +
                "        String xxx = \"\"\"\nabc");
        compFail("public class ZeroTerminator {\n" +
                "    public static void main(String... args) {\n" +
                "        String xxx = \"\"\"\nabc\\u0000");
        compFail("public class NonBreakingSpace {\n" +
                "    public static void main(String... args) {\n" +
                "        String xxx = \"\"\"\nabc\\u001A");
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
                          "\"\"\"" + terminator +
                          "abc" + terminator +
                          "\"\"\";" + terminator +
                          "        System.out.println(s.equals(\"abc\\n\"));" + terminator +
                          "    }" + terminator +
                          "}" + terminator;
            new JavacTask(TOOLBOX)
                    .sources(code)
                    .classpath(".")
                    .options("--enable-preview", "-source", JDK_VERSION, "-encoding", "utf8")
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
                .options("--enable-preview", "-source", JDK_VERSION, "-encoding", "utf8")
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
                .options("-XDrawDiagnostics", "--enable-preview", "-source", JDK_VERSION, "-encoding", "utf8")
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!errors.contains("compiler.err")) {
            throw new RuntimeException("No error detected");
        }
    }
}
