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
 * @bug 9999999
 * @summary Check behavior of module imports.
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main ImportModule
*/

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

import toolbox.TestRunner;
import toolbox.JavacTask;
import toolbox.JavaTask;
import toolbox.Task;
import toolbox.ToolBox;

public class ImportModule extends TestRunner {

    private static final String SOURCE_VERSION = System.getProperty("java.specification.version");
    private ToolBox tb;

    public static void main(String... args) throws Exception {
        new ImportModule().runTests();
    }

    ImportModule() {
        super(System.err);
        tb = new ToolBox();
    }

    public void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test
    public void testImportJavaBase(Path base) throws Exception {
        Path current = base.resolve(".");
        Path src = current.resolve("src");
        Path classes = current.resolve("classes");
        tb.writeJavaFiles(src,
                          """
                          package test;
                          import module java.base;
                          public class Test {
                              public static void main(String... args) {
                                  List<String> l = new ArrayList<>();
                                  System.out.println(l.getClass().getName());
                              }
                          }
                          """);

        Files.createDirectories(classes);

        new JavacTask(tb)
            .options("--enable-preview", "--release", SOURCE_VERSION)
            .outdir(classes)
            .files(tb.findJavaFiles(src))
            .run(Task.Expect.SUCCESS)
            .writeAll();

        var out = new JavaTask(tb)
                .classpath(classes.toString())
                .className("test.Test")
                .vmOptions("--enable-preview")
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.STDOUT);

        var expectedOut = List.of("java.util.ArrayList");

        if (!Objects.equals(expectedOut, out)) {
            throw new AssertionError("Incorrect Output, expected: " + expectedOut +
                                      ", actual: " + out);

        }
    }

    @Test
    public void testVerifySourceLevelCheck(Path base) throws Exception {
        Path current = base.resolve(".");
        Path src = current.resolve("src");
        Path classes = current.resolve("classes");
        tb.writeJavaFiles(src,
                          """
                          package test;
                          import module java.base;
                          public class Test {
                          }
                          """);

        Files.createDirectories(classes);

        List<String> actualErrors;
        List<String> expectedErrors;

        actualErrors =
                new JavacTask(tb)
                    .options("--release", "21", "-XDrawDiagnostics")
                    .outdir(classes)
                    .files(tb.findJavaFiles(src))
                    .run(Task.Expect.FAIL)
                    .writeAll()
                    .getOutputLines(Task.OutputKind.DIRECT);

        expectedErrors = List.of(
                "Test.java:2:8: compiler.err.preview.feature.disabled.plural: (compiler.misc.feature.module.imports)",
                "1 error"
        );

        if (!Objects.equals(expectedErrors, actualErrors)) {
            throw new AssertionError("Incorrect Output, expected: " + expectedErrors +
                                      ", actual: " + out);

        }
        actualErrors =
                new JavacTask(tb)
                    .options("-XDrawDiagnostics")
                    .outdir(classes)
                    .files(tb.findJavaFiles(src))
                    .run(Task.Expect.FAIL)
                    .writeAll()
                    .getOutputLines(Task.OutputKind.DIRECT);

        expectedErrors = List.of(
                "Test.java:2:8: compiler.err.preview.feature.disabled.plural: (compiler.misc.feature.module.imports)",
                "1 error"
        );

        if (!Objects.equals(expectedErrors, actualErrors)) {
            throw new AssertionError("Incorrect Output, expected: " + expectedErrors +
                                      ", actual: " + out);

        }
    }

    @Test
    public void testConflicts(Path base) throws Exception {
        Path current = base.resolve(".");
        Path src = current.resolve("src");
        Path classes = current.resolve("classes");
        tb.writeJavaFiles(src,
                          """
                          package test;
                          import module java.logging;
                          import java.lang.System.*;
                          public class Test {
                              Logger l;
                          }
                          """);

        Files.createDirectories(classes);

        List<String> actualErrors;
        List<String> expectedErrors;

        actualErrors =
                new JavacTask(tb)
                    .options("--enable-preview", "--release", SOURCE_VERSION,
                             "-XDrawDiagnostics")
                    .outdir(classes)
                    .files(tb.findJavaFiles(src))
                    .run(Task.Expect.FAIL)
                    .writeAll()
                    .getOutputLines(Task.OutputKind.DIRECT);

        expectedErrors = List.of(
                "Test.java:5:5: compiler.err.ref.ambiguous: Logger, kindname.interface, java.lang.System.Logger, java.lang.System, kindname.class, java.util.logging.Logger, java.util.logging",
                "- compiler.note.preview.filename: Test.java, DEFAULT",
                "- compiler.note.preview.recompile",
                "1 error"
        );

        if (!Objects.equals(expectedErrors, actualErrors)) {
            throw new AssertionError("Incorrect Output, expected: " + expectedErrors +
                                      ", actual: " + out);

        }

        tb.writeJavaFiles(src,
                          """
                          package test;
                          import module java.logging;
                          import java.lang.System.*;
                          import java.lang.System.Logger;
                          public class Test {
                              Logger l;
                          }
                          """);

        new JavacTask(tb)
            .options("--enable-preview", "--release", SOURCE_VERSION)
            .outdir(classes)
            .files(tb.findJavaFiles(src))
            .run()
            .writeAll();

        tb.writeJavaFiles(src,
                          """
                          package test;
                          import module java.logging;
                          import java.lang.System.*;
                          import java.util.logging.Logger;
                          public class Test {
                              Logger l;
                          }
                          """);

        new JavacTask(tb)
            .options("--enable-preview", "--release", SOURCE_VERSION)
            .outdir(classes)
            .files(tb.findJavaFiles(src))
            .run()
            .writeAll();

    }
}
