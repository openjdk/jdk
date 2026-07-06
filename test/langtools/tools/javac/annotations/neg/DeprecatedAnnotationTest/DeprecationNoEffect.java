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
 * @bug 8381403
 * @summary Check that Deprecated annotation has no effect warnings are correctly reported.
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run junit ${test.main.class}
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import toolbox.ToolBox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import toolbox.JavacTask;
import toolbox.Task;

public class DeprecationNoEffect {

    Path base;
    ToolBox tb = new ToolBox();

    @Test
    void testDeprecatedRecordComponent() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        List<String> out = new JavacTask(tb)
                .options("-d", classes.toString(), "-XDrawDiagnostics", "-Xlint:deprecation")
                .sources("""
                         class Test {
                             void f(R r) {
                                 System.err.println(r.x());
                             }
                         }

                         record R(@Deprecated int x) {}
                         """)
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        tb.checkEqual(out, List.of(
                "Test.java:3:29: compiler.warn.has.been.deprecated: x(), R",
                "1 warning"));
    }

    @Test
    void testDeprecatedVarInRecordMethod() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        List<String> out = new JavacTask(tb)
                .options("-d", classes.toString(), "-XDrawDiagnostics", "-Xlint:deprecation")
                .sources("""
                         record R(int x) {
                             static void op () {
                                 @Deprecated int y = 0;
                             }
                         }
                         """)
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        tb.checkEqual(out, List.of(
                "R.java:3:25: compiler.warn.deprecated.annotation.has.no.effect: kindname.variable",
                "1 warning"));
    }

    @Test
    void testDeprecatedParamInRecordConstructor() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        List<String> out = new JavacTask(tb)
                .options("-d", classes.toString(), "-XDrawDiagnostics", "-Xlint:deprecation")
                .sources("""
                         record R(int x) {
                             R (@Deprecated int x) {
                                 this.x = x;
                             }
                             R (@Deprecated String s) {
                                 this(s.length());
                             }
                         }
                         """)
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        tb.checkEqual(out, List.of(
                "R.java:2:24: compiler.warn.deprecated.annotation.has.no.effect: kindname.variable",
                "R.java:5:27: compiler.warn.deprecated.annotation.has.no.effect: kindname.variable",
                "2 warnings"));
    }

    @BeforeEach
    public void setUp(TestInfo info) {
        base = Paths.get(".")
                    .resolve(info.getTestMethod()
                                 .orElseThrow()
                                 .getName());
    }
}
