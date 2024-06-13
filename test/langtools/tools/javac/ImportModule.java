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
 * @bug 8328481 8332236 8332890
 * @summary Check behavior of module imports.
 * @library /tools/lib
 * @modules java.logging
 *          java.sql
 *          jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main ImportModule
*/

import com.sun.source.tree.Tree;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskEvent.Kind;
import com.sun.source.util.TaskListener;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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

        {//with --release:
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

        {//with --source:
            new JavacTask(tb)
                .options("--enable-preview", "--source", SOURCE_VERSION)
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

        tb.writeJavaFiles(src,
                          """
                          package test;
                          import module java.logging;
                          import java.lang.System.*;
                          public class Test {
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
                          import module java.base;
                          import module java.sql;
                          public class Test {
                              Date d;
                          }
                          """);

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
                "Test.java:5:5: compiler.err.ref.ambiguous: Date, kindname.class, java.sql.Date, java.sql, kindname.class, java.util.Date, java.util",
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
                          import module java.base;
                          import module java.sql;
                          import java.util.Date;
                          public class Test {
                              Date d;
                          }
                          """);

        new JavacTask(tb)
            .options("--enable-preview", "--release", SOURCE_VERSION)
            .outdir(classes)
            .files(tb.findJavaFiles(src))
            .run()
            .writeAll();
    }

    @Test
    public void testNoQualifiedExports(Path base) throws Exception {
        Path current = base.resolve(".");

        Path lib = current.resolve("lib");
        Path libSrc = lib.resolve("src");
        Path libClasses = lib.resolve("classes");
        tb.writeJavaFiles(libSrc,
                          """
                          module lib {
                              exports api;
                              exports impl to use;
                          }
                          """,
                          """
                          package api;
                          public class Api {
                          }
                          """,
                          """
                          package impl;
                          public class Impl {
                          }
                          """);

        Files.createDirectories(libClasses);

        new JavacTask(tb)
            .outdir(libClasses)
            .files(tb.findJavaFiles(libSrc))
            .run()
            .writeAll();

        Path src = current.resolve("src");
        Path classes = current.resolve("classes");

        tb.writeJavaFiles(src,
                          """
                          package test;
                          import module lib;
                          public class Test {
                              public static void main(String... args) {
                                  Api a;
                                  Impl i;
                              }
                          }
                          """);

        Files.createDirectories(classes);

        List<String> actualErrors;
        List<String> expectedErrors;

        actualErrors =
                new JavacTask(tb)
                    .options("--enable-preview", "--release", SOURCE_VERSION,
                             "-p", libClasses.toString(),
                             "--add-modules", "lib",
                             "-XDrawDiagnostics")
                    .outdir(classes)
                    .files(tb.findJavaFiles(src))
                    .run(Task.Expect.FAIL)
                    .writeAll()
                    .getOutputLines(Task.OutputKind.DIRECT);

        expectedErrors = List.of(
                "Test.java:6:9: compiler.err.cant.resolve.location: kindname.class, Impl, , , (compiler.misc.location: kindname.class, test.Test, null)",
                "- compiler.note.preview.filename: Test.java, DEFAULT",
                "- compiler.note.preview.recompile",
                "1 error"
        );

        if (!Objects.equals(expectedErrors, actualErrors)) {
            throw new AssertionError("Incorrect Output, expected: " + expectedErrors +
                                      ", actual: " + out);

        }

        actualErrors =
                new JavacTask(tb)
                    .options("--enable-preview", "--release", SOURCE_VERSION,
                             "-p", libClasses.toString(),
                             "-XDdev",
                             "-XDrawDiagnostics")
                    .outdir(classes)
                    .files(tb.findJavaFiles(src))
                    .run(Task.Expect.FAIL)
                    .writeAll()
                    .getOutputLines(Task.OutputKind.DIRECT);

        expectedErrors = List.of(
                "Test.java:2:1: compiler.err.import.module.does.not.read.unnamed: lib",
                "Test.java:6:9: compiler.err.cant.resolve.location: kindname.class, Impl, , , (compiler.misc.location: kindname.class, test.Test, null)",
                "- compiler.note.preview.filename: Test.java, DEFAULT",
                "- compiler.note.preview.recompile",
                "2 errors"
        );

        if (!Objects.equals(expectedErrors, actualErrors)) {
            throw new AssertionError("Incorrect Output, expected: " + expectedErrors +
                                      ", actual: " + out);

        }

        tb.writeJavaFiles(src,
                          """
                          module test.module {
                          }
                          """);

        actualErrors =
                new JavacTask(tb)
                    .options("--enable-preview", "--release", SOURCE_VERSION,
                             "-p", libClasses.toString(),
                             "-XDdev",
                             "-XDrawDiagnostics")
                    .outdir(classes)
                    .files(tb.findJavaFiles(src))
                    .run(Task.Expect.FAIL)
                    .writeAll()
                    .getOutputLines(Task.OutputKind.DIRECT);

        expectedErrors = List.of(
                "Test.java:2:1: compiler.err.import.module.does.not.read: test.module, lib",
                "Test.java:6:9: compiler.err.cant.resolve.location: kindname.class, Impl, , , (compiler.misc.location: kindname.class, test.Test, null)",
                "- compiler.note.preview.filename: Test.java, DEFAULT",
                "- compiler.note.preview.recompile",
                "2 errors"
        );

        if (!Objects.equals(expectedErrors, actualErrors)) {
            throw new AssertionError("Incorrect Output, expected: " + expectedErrors +
                                      ", actual: " + out);

        }
    }

    @Test
    public void testTransitiveDependencies(Path base) throws Exception {
        Path current = base.resolve(".");
        Path lib = current.resolve("lib");
        Path libSrc = lib.resolve("src");
        Path libM1 = libSrc.resolve("m1");
        tb.writeJavaFiles(libM1,
                          """
                          module m1 {
                              requires transitive m2;
                              exports api1;
                              exports api2 to test;
                              exports api3 to m3;
                          }
                          """,
                          """
                          package api1;
                          public class Api1 {
                          }
                          """,
                          """
                          package api2;
                          public class Api2 {
                          }
                          """,
                          """
                          package api3;
                          public class Api3 {
                          }
                          """,
                          """
                          package impl1;
                          public class Impl1 {
                          }
                          """);

        Path libM2 = libSrc.resolve("m2");
        tb.writeJavaFiles(libM2,
                          """
                          module m2 {
                              exports api4;
                              exports api5 to test;
                              exports api6 to m3;
                          }
                          """,
                          """
                          package api4;
                          public class Api4 {
                          }
                          """,
                          """
                          package api5;
                          public class Api5 {
                          }
                          """,
                          """
                          package api6;
                          public class Api6 {
                          }
                          """,
                          """
                          package impl2;
                          public class Impl2 {
                          }
                          """);

        Path libClasses = lib.resolve("classes");
        Files.createDirectories(libClasses);

        new JavacTask(tb)
            .options("--enable-preview", "--release", SOURCE_VERSION,
                     "--module-source-path", libSrc.toString(),
                     "-XDrawDiagnostics")
            .outdir(libClasses)
            .files(tb.findJavaFiles(libSrc))
            .run()
            .writeAll();

        Path src = current.resolve("src");
        tb.writeJavaFiles(src,
                          """
                          module test {
                              requires m1;
                          }
                          """,
                          """
                          package test;
                          import module m1;
                          public class Test1 {
                              Api1 a1;
                              Api2 a2;
                              Api3 a3;
                              Impl1 i1;
                              Api4 a4;
                              Api5 a5;
                              Api6 a6;
                              Impl2 i2;
                          }
                          """,
                          """
                          package test;
                          import module m2;
                          public class Test2 {
                              Api1 a1;
                              Api2 a2;
                              Api3 a3;
                              Impl1 i1;
                              Api4 a4;
                              Api5 a5;
                              Api6 a6;
                              Impl2 i2;
                          }
                          """);

        Path classes = current.resolve("classes");
        Files.createDirectories(classes);

        List<String> actualErrors;
        List<String> expectedErrors;

        actualErrors =
                new JavacTask(tb)
                    .options("--enable-preview", "--release", SOURCE_VERSION,
                             "--module-path", libClasses.toString(),
                             "-XDrawDiagnostics")
                    .outdir(classes)
                    .files(tb.findJavaFiles(src))
                    .run(Task.Expect.FAIL)
                    .writeAll()
                    .getOutputLines(Task.OutputKind.DIRECT);

        expectedErrors = List.of(
                "Test1.java:6:5: compiler.err.cant.resolve.location: kindname.class, Api3, , , (compiler.misc.location: kindname.class, test.Test1, null)",
                "Test1.java:7:5: compiler.err.cant.resolve.location: kindname.class, Impl1, , , (compiler.misc.location: kindname.class, test.Test1, null)",
                "Test1.java:10:5: compiler.err.cant.resolve.location: kindname.class, Api6, , , (compiler.misc.location: kindname.class, test.Test1, null)",
                "Test1.java:11:5: compiler.err.cant.resolve.location: kindname.class, Impl2, , , (compiler.misc.location: kindname.class, test.Test1, null)",
                "Test2.java:4:5: compiler.err.cant.resolve.location: kindname.class, Api1, , , (compiler.misc.location: kindname.class, test.Test2, null)",
                "Test2.java:5:5: compiler.err.cant.resolve.location: kindname.class, Api2, , , (compiler.misc.location: kindname.class, test.Test2, null)",
                "Test2.java:6:5: compiler.err.cant.resolve.location: kindname.class, Api3, , , (compiler.misc.location: kindname.class, test.Test2, null)",
                "Test2.java:7:5: compiler.err.cant.resolve.location: kindname.class, Impl1, , , (compiler.misc.location: kindname.class, test.Test2, null)",
                "Test2.java:10:5: compiler.err.cant.resolve.location: kindname.class, Api6, , , (compiler.misc.location: kindname.class, test.Test2, null)",
                "Test2.java:11:5: compiler.err.cant.resolve.location: kindname.class, Impl2, , , (compiler.misc.location: kindname.class, test.Test2, null)",
                "- compiler.note.preview.plural: DEFAULT",
                "- compiler.note.preview.recompile",
                "10 errors"
        );

        if (!Objects.equals(expectedErrors, actualErrors)) {
            throw new AssertionError("Incorrect Output, expected: " + expectedErrors +
                                      ", actual: " + out);

        }
    }

    @Test
    public void testModel(Path base) throws Exception {
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
        List<String> kinds = new ArrayList<>();

        new JavacTask(tb)
            .options("--enable-preview", "--release", SOURCE_VERSION)
            .outdir(classes)
            .callback(task -> {
                task.addTaskListener(new TaskListener() {
                    @Override
                    public void finished(TaskEvent e) {
                        if (e.getKind() == Kind.ANALYZE) {
                            for (Tree t : e.getCompilationUnit().getTypeDecls()) {
                                kinds.add(t.getKind().name());
                            }
                        }
                    }
                });
            })
            .files(tb.findJavaFiles(src))
            .run(Task.Expect.SUCCESS)
            .writeAll();

        List<String> expectedKinds = List.of(
            "CLASS"
        );

        if (!Objects.equals(expectedKinds, kinds)) {
            throw new AssertionError("Incorrect Output, expected: " + expectedKinds +
                                      ", actual: " + kinds);

        }
    }

    @Test
    public void testModelDisambiguation(Path base) throws Exception {
        Path current = base.resolve(".");
        Path src = current.resolve("src");
        Path classes = current.resolve("classes");
        tb.writeJavaFiles(src,
                          """
                          package test;
                          import module.*;
                          import module.ModuleClass;
                          import module.module.*;
                          import module.module.ModuleModuleClass;
                          public class Test {
                          }
                          """,
                          """
                          package module;
                          public class ModuleClass{
                          }
                          """,
                          """
                          package module.module;
                          public class ModuleModuleClass {
                          }
                          """);

        Files.createDirectories(classes);
        List<String> kinds = new ArrayList<>();

        new JavacTask(tb)
            .options("--enable-preview", "--release", SOURCE_VERSION)
            .outdir(classes)
            .files(tb.findJavaFiles(src))
            .run(Task.Expect.SUCCESS)
            .writeAll();
    }

    @Test
    public void testImplicitlyDeclaredClass(Path base) throws Exception {
        Path current = base.resolve(".");
        Path src = current.resolve("src");
        Path classes = current.resolve("classes");
        tb.writeFile(src.resolve("Test.java"),
                     """
                     import module java.base;
                     void main() {
                     }
                     """);

        Files.createDirectories(classes);

        new JavacTask(tb)
            .options("--enable-preview", "--release", SOURCE_VERSION)
            .outdir(classes)
            .files(tb.findJavaFiles(src))
            .run(Task.Expect.SUCCESS)
            .writeAll();
    }

    @Test //JDK-8332890
    public void testModuleInfoSelfImport(Path base) throws Exception {
        Path current = base.resolve(".");
        Path src = current.resolve("src");
        Path classes = current.resolve("classes");
        tb.writeJavaFiles(src,
                          """
                          import module M;
                          module M {
                             exports p1 to M1;
                             exports p2;
                             exports p3 to M;
                             uses A;
                             uses B;
                             uses C;
                          }
                          """,
                          """
                          package p1;
                          public class A {}
                          """,
                          """
                          package p2;
                          public class B {}
                          """,
                          """
                          package p3;
                          public class C {}
                          """);

        Files.createDirectories(classes);

        List<String> actualErrors = new JavacTask(tb)
                .options("-XDrawDiagnostics",
                        "--enable-preview", "--release", SOURCE_VERSION)
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expectedErrors = List.of(
                "module-info.java:3:18: compiler.warn.module.not.found: M1",
                "module-info.java:6:9: compiler.err.cant.resolve: kindname.class, A, , ",
                "- compiler.note.preview.filename: module-info.java, DEFAULT",
                "- compiler.note.preview.recompile",
                "1 error",
                "1 warning"
        );

        if (!Objects.equals(expectedErrors, actualErrors)) {
            throw new AssertionError("Incorrect Output, expected: " + expectedErrors +
                                      ", actual: " + out);

        }
    }
}
