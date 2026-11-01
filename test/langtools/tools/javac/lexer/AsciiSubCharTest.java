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

/**
 * @test
 * @bug 8371873
 * @summary Check for proper handling of trailing ASCII SUB character
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run junit AsciiSubCharTest
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class AsciiSubCharTest {

    ToolBox tb = new ToolBox();
    Path base;

    @Test
    public void testTrailingAsciiSubIsIgnored() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
                .options("-d", classes.toString())
                .sources("""
                         public class Test {
                            void main(String... args) { IO.println("\u001A"); }
                         }
                         \u001A""")
                .run()
                .writeAll();
    }

    @Test
    public void testMultipleTrailingAsciiSubAreReported() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        List<String> out = new JavacTask(tb)
                .options("-d", classes.toString(), "-XDrawDiagnostics", "-nowarn")
                .sources("""
                         public class Test {
                            void main(String... args) { IO.println("\u001A"); }
                         }
                         \u001A\u001A""")
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        tb.checkEqual(out, List.of(
                "Test.java:4:1: compiler.err.illegal.char: \\u001a",
                "Test.java:4:2: compiler.err.premature.eof",
                "2 errors"));
    }

    @Test
    public void test8371873() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        List<String> out = new JavacTask(tb)
                .options("-d", classes.toString(), "-XDrawDiagnostics", "-nowarn")
                .sources("""
                         public class Test {
                            void main(String... args) { IO.println("\u001A"); }
                         }
                         \u001A\u0001""")
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        tb.checkEqual(out, List.of(
                "Test.java:4:1: compiler.err.illegal.char: \\u001a",
                "Test.java:4:2: compiler.err.illegal.char: \\u0001",
                "Test.java:4:3: compiler.err.premature.eof",
                "3 errors"));
    }

    @BeforeEach
    public void setUp(TestInfo info) {
        base = Paths.get(".")
                    .resolve(info.getTestMethod()
                                 .orElseThrow()
                                 .getName());
    }
}
