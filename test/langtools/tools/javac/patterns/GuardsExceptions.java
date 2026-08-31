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
 * @bug 8390759
 * @summary Check that exceptions thrown from a guard propagate out of the switch.
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run junit GuardsExceptions
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

public class GuardsExceptions {

    Path base;
    ToolBox tb = new ToolBox();

    @Test
    void testUnreportedExceptionReported() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        List<String> out = new JavacTask(tb)
                .options("-d", classes.toString(), "-XDrawDiagnostics", "-nowarn")
                .sources("""
                         package test;

                         import java.io.IOException;

                         public class Test {
                             private static boolean guard() throws IOException {
                                 throw new IOException();
                             }

                             public static void test(Object o) {
                                 switch (o) {
                                     case String s when guard() -> {}
                                     default -> {}
                                 }
                             }
                         }
                         """)
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        tb.checkEqual(out, List.of(
                "Test.java:12:37: compiler.err.unreported.exception.need.to.catch.or.throw: java.io.IOException",
                "1 error"));
    }

    @Test
    void testThrownExceptionHandled() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
                .options("-d", classes.toString(), "-XDrawDiagnostics", "-nowarn")
                .sources("""
                         package test;

                         import java.io.IOException;

                         public class Test {
                             private static boolean guard() throws IOException {
                                 throw new IOException();
                             }

                             public static void test(Object o) {
                                 try {
                                     switch (o) {
                                         case String s when guard() -> {}
                                         default -> {}
                                     }
                                 } catch (IOException e) {}
                             }
                         }
                         """)
                .run()
                .writeAll();
    }

    @Test
    void testUnreachableStatementReported() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        List<String> out = new JavacTask(tb)
                .options("-d", classes.toString(), "-XDrawDiagnostics", "-nowarn")
                .sources("""
                         package test;

                         import java.io.IOException;

                         public class Test {
                             public static void test(Object o) {
                                 switch (o) {
                                     case String s when switch(s.length()) {
                                         case 0 -> { yield false; System.out.println(); }
                                         default -> true;
                                     } -> {}
                                     default -> {}
                                 }
                             }
                         }
                         """)
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        tb.checkEqual(out, List.of(
                "Test.java:9:42: compiler.err.unreachable.stmt",
                "1 error"));
    }

    @BeforeEach
    public void setUp(TestInfo info) {
        base = Paths.get(".")
                    .resolve(info.getTestMethod()
                                 .orElseThrow()
                                 .getName());
    }
}
