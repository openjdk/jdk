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
 * @bug 8389659
 * @summary Verify assign op is handled correctly in the constructor prologue
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.file
 *      jdk.compiler/com.sun.tools.javac.main
 *      jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run junit ${test.main.class}
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import toolbox.JavaTask;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class InitAssignOp {

    private final ToolBox tb = new ToolBox();
    private Path base;

    @Test
    void testInitAssignOp() throws Exception {
        record TestCase(String source, List<String> expectedCompilationOutput) {}
        TestCase[] tests = new TestCase[] {
            new TestCase("""
                         public class Test {
                             private int i;

                             public Test() {
                                 ++i;
                                 super();
                             }
                             public static void main(String... args) {
                                 System.out.println(new Test().i);
                             }
                         }
                         """,
                         List.of(
                             "Test.java:5:11: compiler.err.preview.feature.disabled.plural: (compiler.misc.feature.value.classes)",
                             "1 error"
                         )),
            new TestCase("""
                         public class Test {
                             private int i;

                             public Test() {
                                 i++;
                                 super();
                             }
                             public static void main(String... args) {
                                 System.out.println(new Test().i);
                             }
                         }
                         """,
                         List.of(
                             "Test.java:5:9: compiler.err.preview.feature.disabled.plural: (compiler.misc.feature.value.classes)",
                             "1 error"
                         )),
            new TestCase("""
                         public class Test {
                             private int i;

                             public Test() {
                                 i += 1;
                                 super();
                             }
                             public static void main(String... args) {
                                 System.out.println(new Test().i);
                             }
                         }
                         """,
                         List.of(
                             "Test.java:5:9: compiler.err.preview.feature.disabled.plural: (compiler.misc.feature.value.classes)",
                             "1 error"
                         )),
        };
        for (TestCase test : tests) {
            Path classes = base.resolve("classes");
            Files.createDirectories(classes);
            List<String> out;

            out =
                new JavacTask(tb)
                        .options("-XDrawDiagnostics")
                        .outdir(classes)
                        .sources(test.source())
                        .run(Task.Expect.FAIL)
                        .writeAll()
                        .getOutputLines(Task.OutputKind.DIRECT);
            Assertions.assertEquals(test.expectedCompilationOutput(), out);
            new JavacTask(tb)
                    .options("--enable-preview", "--release", System.getProperty("java.specification.version"))
                    .outdir(classes)
                    .sources(test.source())
                    .run()
                    .writeAll();

            out =
                new JavaTask(tb)
                        .vmOptions("--enable-preview")
                        .classpath(classes.toString())
                        .className("Test")
                        .run()
                        .getOutputLines(Task.OutputKind.STDOUT);
            List<String> expectedRunOutput = List.of(
                "1"
            );
            Assertions.assertEquals(expectedRunOutput, out);
        }
    }

    @Test
    void testInitAssignOpErrors() throws Exception {
        record TestCase(String source, List<String> expectedCompilationOutput) {}
        TestCase[] tests = new TestCase[] {
            new TestCase("""
                         public class Test {
                             public Test() {
                                 ++0;
                                 super();
                             }
                         }
                         """,
                         List.of(
                             "Test.java:3:11: compiler.err.unexpected.type: kindname.variable, kindname.value",
                             "1 error"
                         )),
            new TestCase("""
                         public class Test {
                             public Test() {
                                 ++test();
                                 super();
                             }
                             private static int test() { return 1; }
                         }
                         """,
                         List.of(
                             "Test.java:3:15: compiler.err.unexpected.type: kindname.variable, kindname.value",
                             "1 error"
                         )),
            new TestCase("""
                         public class Test {
                             public Test(int i) {
                                 ++(i + 1);
                                 super();
                             }
                         }
                         """,
                         List.of(
                             "Test.java:3:14: compiler.err.unexpected.type: kindname.variable, kindname.value",
                             "1 error"
                         )),

            new TestCase("""
                         public class Test {
                             public Test() {
                                 0++;
                                 super();
                             }
                         }
                         """,
                         List.of(
                             "Test.java:3:9: compiler.err.unexpected.type: kindname.variable, kindname.value",
                             "1 error"
                         )),
            new TestCase("""
                         public class Test {
                             public Test() {
                                 test()++;
                                 super();
                             }
                             private static int test() { return 1; }
                         }
                         """,
                         List.of(
                             "Test.java:3:13: compiler.err.unexpected.type: kindname.variable, kindname.value",
                             "1 error"
                         )),
            new TestCase("""
                         public class Test {
                             public Test(int i) {
                                 (i + 1)++;
                                 super();
                             }
                         }
                         """,
                         List.of(
                             "Test.java:3:12: compiler.err.unexpected.type: kindname.variable, kindname.value",
                             "1 error"
                         )),

            new TestCase("""
                         public class Test {
                             public Test() {
                                 0 += 1;
                                 super();
                             }
                         }
                         """,
                         List.of(
                             "Test.java:3:9: compiler.err.unexpected.type: kindname.variable, kindname.value",
                             "1 error"
                         )),
            new TestCase("""
                         public class Test {
                             public Test() {
                                 test() += 1;
                                 super();
                             }
                             private static int test() { return 1; }
                         }
                         """,
                         List.of(
                             "Test.java:3:13: compiler.err.unexpected.type: kindname.variable, kindname.value",
                             "1 error"
                         )),
            new TestCase("""
                         public class Test {
                             public Test(int i) {
                                 (i + 1) += 1;
                                 super();
                             }
                         }
                         """,
                         List.of(
                             "Test.java:3:12: compiler.err.unexpected.type: kindname.variable, kindname.value",
                             "1 error"
                         )),
        };
        for (TestCase test : tests) {
            Path classes = base.resolve("classes");
            Files.createDirectories(classes);
            List<String> out;

            out =
                new JavacTask(tb)
                        .options("-XDrawDiagnostics",
                                 "--enable-preview", "--release", System.getProperty("java.specification.version"))
                        .outdir(classes)
                        .sources(test.source())
                        .run(Task.Expect.FAIL)
                        .writeAll()
                        .getOutputLines(Task.OutputKind.DIRECT);
            Assertions.assertEquals(test.expectedCompilationOutput(), out);
        }
    }

    @BeforeEach
    public void setUp(TestInfo info) {
        base = Paths.get(".")
                    .resolve(info.getTestMethod()
                                 .orElseThrow()
                                 .getName());
    }

}
