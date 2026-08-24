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
 * @bug 8389987
 * @summary Verify correct handling of synchronized
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run junit Synchronized
*/

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class Synchronized {

    private Path base;
    private ToolBox tb = new ToolBox();

    @Test
    public void testSynchronizedNull() throws Exception {
        Path src = base.resolve("src");
        Path classes = base.resolve("classes");
        tb.writeJavaFiles(src,
                          """
                          public class Test {
                              void t() {
                                  synchronized (null) {}
                              }
                          }
                          """);

        Files.createDirectories(classes);

        List<String> log;
        List<String> expected;

        log = new JavacTask(tb)
            .options("-XDrawDiagnostics")
            .outdir(classes)
            .files(tb.findJavaFiles(src))
            .run(Task.Expect.FAIL)
            .writeAll()
            .getOutputLines(Task.OutputKind.DIRECT);

        expected = List.of(
            "Test.java:3:9: compiler.err.type.found.req: compiler.misc.type.null, (compiler.misc.type.req.ref)",
            "1 error"
        );

        assertEquals(expected, log);

        log = new JavacTask(tb)
            .options("-XDrawDiagnostics",
                     "--enable-preview", "--release", System.getProperty("java.specification.version"))
            .outdir(classes)
            .files(tb.findJavaFiles(src))
            .run(Task.Expect.FAIL)
            .writeAll()
            .getOutputLines(Task.OutputKind.DIRECT);

        expected = List.of(
            "Test.java:3:9: compiler.err.type.found.req: compiler.misc.type.null, (compiler.misc.type.req.identity)",
            "1 error"
        );

        assertEquals(expected, log);
    }

    @Test
    public void testSynchronizedValueClassValueClassesDisabled() throws Exception {
        Path src = base.resolve("src");
        Path classes = base.resolve("classes");
        tb.writeJavaFiles(src,
                          """
                          public value class Test {
                              void t() {
                                  synchronized (new Test()) {}
                              }
                          }
                          """);

        Files.createDirectories(classes);

        List<String> log;
        List<String> expected;

        log = new JavacTask(tb)
            .options("-XDrawDiagnostics",
                     "-XDshould-stop.at=WARN")
            .outdir(classes)
            .files(tb.findJavaFiles(src))
            .run(Task.Expect.FAIL)
            .writeAll()
            .getOutputLines(Task.OutputKind.DIRECT);

        expected = List.of(
            "Test.java:1:8: compiler.err.preview.feature.disabled.plural: (compiler.misc.feature.value.classes)",
            "Test.java:3:9: compiler.err.type.found.req: Test, (compiler.misc.type.req.ref)",
            "2 errors"
        );

        assertEquals(expected, log);
    }

    @BeforeEach
    public void setUp(TestInfo info) {
        base = Paths.get(".")
                    .resolve(info.getTestMethod()
                                 .orElseThrow()
                                 .getName());
    }
}
