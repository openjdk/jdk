/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 0000000
 * @summary Exercise javac handing of templated strings.
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main Basic
 */


import toolbox.JavacTask;
import toolbox.JavaTask;
import toolbox.Task;
import toolbox.ToolBox;

public class Basic {
    private static ToolBox TOOLBOX = new ToolBox();
    private static final String JAVA_VERSION = System.getProperty("java.specification.version");

    public static void main(String... arg) {
        primitivesTest();
        missingPartsTest();
        expressionsTest();
        invalidExpressionsTest();
        processorTest();
    }

    /*
     * Primitive types test.
     */
    static void primitivesTest() {
        for (String type : new String[] {
            "byte",
            "short",
            "int",
            "long",
            "float",
            "double"
        }) {
            compPass(type + " x = 10; " + type + "  y = 20; StringTemplate result = RAW.\"\\{x} + \\{y} = \\{x + y}\";");
        }
    }

    /*
     * Missing parts test.
     */
    static void missingPartsTest() {
        compFail("""
            int x = 10;
            StringTemplate result = RAW."\\{x";
        """);
        compFail("""
            int x = 10;
            StringTemplate result = RAW."\\{{x}";
        """);
        compFail("""
            int x = 10;
            StringTemplate result = RAW."\\{x + }";
        """);
        compFail("""
            int x = 10;
            StringTemplate result = RAW."\\{ * x }";
        """);
        compFail("""
            int x = 10;
            StringTemplate result = RAW."\\{ (x + x }";
        """);
    }

    /*
     * Expressions test.
     */
    static void expressionsTest() {
        compPass("""
            int x = 10;
            int[] y = new int[] { 10, 20, 30 };
            StringTemplate result1 = RAW."\\{x + 1}";
            StringTemplate result2 = RAW."\\{x + x}";
            StringTemplate result3 = RAW."\\{x - x}";
            StringTemplate result4 = RAW."\\{x * x}";
            StringTemplate result5 = RAW."\\{x / x}";
            StringTemplate result6 = RAW."\\{x % x}";
            StringTemplate result7 = RAW."\\{x + (x + x)}";
            StringTemplate result8 = RAW."\\{y[x - 9]}";
            StringTemplate result9 = RAW."\\{System.out}";
            StringTemplate result10 = RAW.\"""
                    \\{ "a string" }
                    \""";
                    """);
        compPass("""
            StringTemplate result = RAW.\"""
                 \\{
                     new Collection<String>() {
                          @Override public int size() { return 0; }
                          @Override public boolean isEmpty() { return false; }
                          @Override public boolean contains(Object o) { return false; }
                          @Override public Iterator<String> iterator() { return null; }
                          @Override public Object[] toArray() { return new Object[0]; }
                          @Override public <T> T[] toArray(T[] a) { return null; }
                          @Override public boolean add(String s) { return false; }
                          @Override public boolean remove(Object o) { return false; }
                          @Override public boolean containsAll(Collection<?> c) { return false; }
                          @Override public boolean addAll(Collection<? extends String> c) { return false; }
                          @Override public boolean removeAll(Collection<?> c) { return false; }
                          @Override public boolean retainAll(Collection<?> c) { return false; }
                          @Override public void clear() { }
                      }
                 }
                 \""";
         """);
    }

    /*
     * Invalid expressions test.
     */
    static void invalidExpressionsTest() {
        compFail("""
            int x = 10;
            StringTemplate result = RAW."\\{ (x == x }";
        """);
        compFail("""
            int x = 10;
            StringTemplate result = RAW."\\{ true ?  : x - 1 }";
        """);
        compFail("""
             String result = RAW."\\{ 'a }";
        """);
        compFail("""
            int x = 10;
            StringTemplate result = RAW."\\{ Math.min(, x - 1) }";
        """);
        compFail("""
            int x = 10;
            StringTemplate result = RAW."\\{ \\tx }";
        """);
    }

    /*
     * Processor test.
     */
    static void processorTest() {
        compPass("""
         int x = 10, y = 20;
         String string = STR."\\{x} + \\{y} = \\{x + y}";
         """);
        compFail("""
         int x = 10, y = 20;
         String processor = "abc";
         String string = processor."\\{x} + \\{y} = \\{x + y}";
         """);
        compFail("""
         int x = 10, y = 20;
         long processor = 100;
         String string = processor."\\{x} + \\{y} = \\{x + y}";
         """);
    }

    /*
     * Test source for successful compile.
     */
    static void compPass(String code) {
        String source = """
            import java.lang.*;
            import java.util.*;
            import static java.lang.StringTemplate.RAW;
            public class TEST {
                public static void main(String... arg) {
            """ +
            code.indent(8) +
            """
                }
            }
            """;
        String output = new JavacTask(TOOLBOX)
                .sources(source)
                .classpath(".")
                .options("-encoding", "utf8", "--enable-preview", "-source", JAVA_VERSION)
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
    static void compFail(String code) {
        String source = """
            import java.lang.*;
            import java.util.*;
            import static java.lang.StringTemplate.RAW;
            public class TEST {
                public static void main(String... arg) {
            """ +
            code.indent(8) +
            """
                }
            }
            """;
        String errors = new JavacTask(TOOLBOX)
                .sources(source)
                .classpath(".")
                .options("-XDrawDiagnostics", "-encoding", "utf8", "--enable-preview", "-source", JAVA_VERSION)
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!errors.contains("compiler.err")) {
            throw new RuntimeException("No error detected");
        }
    }
}
