/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8338301
 * @summary Verify error recovery and reporting related to implicitly declared classes
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main ErrorRecovery
*/

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

import toolbox.TestRunner;
import toolbox.JavacTask;
import toolbox.JavaTask;
import toolbox.Task;
import toolbox.Task.OutputKind;
import toolbox.ToolBox;

public class ErrorRecovery extends TestRunner {

    private static final String SOURCE_VERSION = System.getProperty("java.specification.version");
    private ToolBox tb;

    public static void main(String... args) throws Exception {
        new ErrorRecovery().runTests();
    }

    ErrorRecovery() {
        super(System.err);
        tb = new ToolBox();
    }

    public void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test
    public void testMethodNoReturnType(Path base) throws Exception {
        Path current = base.resolve(".");
        Path src = current.resolve("src");
        Path classes = current.resolve("classes");
        tb.writeFile(src.resolve("Test.java"),
                     """
                     main() {}
                     """);

        Files.createDirectories(classes);

        List<String> log = new JavacTask(tb)
            .options("-XDrawDiagnostics",
                     "--enable-preview", "--release", SOURCE_VERSION)
            .outdir(classes)
            .files(tb.findJavaFiles(src))
            .run(Task.Expect.FAIL)
            .writeAll()
            .getOutputLines(OutputKind.DIRECT);
        List<String> expected = List.of(
            "Test.java:1:1: compiler.err.invalid.meth.decl.ret.type.req",
            "- compiler.note.preview.filename: Test.java, DEFAULT",
            "- compiler.note.preview.recompile",
            "1 error"
        );
        if (!Objects.equals(expected, log)) {
            throw new AssertionError("Unexpected output: " + log +
                                     ", while expecting: " + expected);
        }
    }

    @Test
    public void testBrokenVariable(Path base) throws Exception {
        Path current = base.resolve(".");
        Path src = current.resolve("src");
        Path classes = current.resolve("classes");
        tb.writeFile(src.resolve("Test.java"),
                     """
                     if (true) ;
                     """);

        Files.createDirectories(classes);

        List<String> log = new JavacTask(tb)
            .options("-XDrawDiagnostics",
                     "--enable-preview", "--release", SOURCE_VERSION)
            .outdir(classes)
            .files(tb.findJavaFiles(src))
            .run(Task.Expect.FAIL)
            .writeAll()
            .getOutputLines(OutputKind.DIRECT);
        List<String> expected = List.of(
            "Test.java:1:1: compiler.err.statement.not.expected",
            "1 error"
        );
        if (!Objects.equals(expected, log)) {
            throw new AssertionError("Unexpected output: " + log +
                                     ", while expecting: " + expected);
        }
    }
}
