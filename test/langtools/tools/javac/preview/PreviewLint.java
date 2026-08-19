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
 * @bug 8388373
 * @summary Verify -Xlint works as expected in relation to --enable-preview.
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main PreviewLint
 */
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.TestRunner;
import toolbox.ToolBox;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class PreviewLint extends TestRunner {

    protected ToolBox tb;

    PreviewLint() {
        super(System.err);
        tb = new ToolBox();
    }

    public static void main(String... args) throws Exception {
        PreviewLint t = new PreviewLint();
        t.runTests();
    }

    protected void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test
    public void previewAPI(Path base) throws Exception {
        Path apiSrc = base.resolve("api-src");
        tb.writeJavaFiles(apiSrc,
                          """
                          package preview.api;
                          @jdk.internal.javac.PreviewFeature(feature=jdk.internal.javac.PreviewFeature.Feature.TEST)
                          public class Preview {
                          }
                          """,
                          """
                          package preview.api;
                          @jdk.internal.javac.PreviewFeature(feature=jdk.internal.javac.PreviewFeature.Feature.TEST, reflective=true)
                          public class ReflectivePreview {
                          }
                          """);
        Path apiClasses = base.resolve("api-classes");

        new JavacTask(tb, Task.Mode.CMDLINE)
                .outdir(apiClasses)
                .options("-XDrawDiagnostics",
                         "--patch-module", "java.base=" + apiSrc.toString(),
                         "-Werror")
                .files(tb.findJavaFiles(apiSrc))
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        Path testSrc = base.resolve("test-src");
        tb.writeJavaFiles(testSrc,
                          """
                          package test;
                          import preview.api.Preview;
                          import preview.api.ReflectivePreview;
                          public class UseClass {
                              Preview f1;
                              ReflectivePreview f2;
                          }
                          """);
        Path testClasses = base.resolve("test-classes");
        List<String> log;
        List<String> expected;

        log = new JavacTask(tb, Task.Mode.CMDLINE)
                .outdir(testClasses)
                .options("--patch-module", "java.base=" + apiClasses.toString(),
                        "--add-exports", "java.base/preview.api=ALL-UNNAMED",
                        "-XDrawDiagnostics")
                .files(tb.findJavaFiles(testSrc))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        expected = List.of(
                "UseClass.java:2:19: compiler.err.is.preview: preview.api.Preview",
                "UseClass.java:5:5: compiler.err.is.preview: preview.api.Preview",
                "UseClass.java:6:5: compiler.warn.is.preview.reflective: preview.api.ReflectivePreview",
                "2 errors",
                "1 warning");

        tb.checkEqual(expected, log);

        log = new JavacTask(tb, Task.Mode.CMDLINE)
                .outdir(testClasses)
                .options("--patch-module", "java.base=" + apiClasses.toString(),
                        "--add-exports", "java.base/preview.api=ALL-UNNAMED",
                        "-Xlint",
                        "-XDrawDiagnostics")
                .files(tb.findJavaFiles(testSrc))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        expected = List.of(
                "UseClass.java:2:19: compiler.err.is.preview: preview.api.Preview",
                "UseClass.java:5:5: compiler.err.is.preview: preview.api.Preview",
                "UseClass.java:6:5: compiler.warn.is.preview.reflective: preview.api.ReflectivePreview",
                "2 errors",
                "1 warning");

        tb.checkEqual(expected, log);

        log = new JavacTask(tb, Task.Mode.CMDLINE)
                .outdir(testClasses)
                .options("--patch-module", "java.base=" + apiClasses.toString(),
                        "--add-exports", "java.base/preview.api=ALL-UNNAMED",
                        "--enable-preview", "--source", System.getProperty("java.specification.version"),
                        "-XDrawDiagnostics")
                .files(tb.findJavaFiles(testSrc))
                .run(Task.Expect.SUCCESS)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        expected = List.of(
                "- compiler.note.preview.filename: UseClass.java, DEFAULT",
                "- compiler.note.preview.recompile");

        tb.checkEqual(expected, log);

        log = new JavacTask(tb, Task.Mode.CMDLINE)
                .outdir(testClasses)
                .options("--patch-module", "java.base=" + apiClasses.toString(),
                        "--add-exports", "java.base/preview.api=ALL-UNNAMED",
                        "--enable-preview", "--source", System.getProperty("java.specification.version"),
                        "-Xlint",
                        "-XDrawDiagnostics")
                .files(tb.findJavaFiles(testSrc))
                .run(Task.Expect.SUCCESS)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        expected = List.of(
                "- compiler.note.preview.filename: UseClass.java, DEFAULT",
                "- compiler.note.preview.recompile");

        tb.checkEqual(expected, log);

        log = new JavacTask(tb, Task.Mode.CMDLINE)
                .outdir(testClasses)
                .options("--patch-module", "java.base=" + apiClasses.toString(),
                        "--add-exports", "java.base/preview.api=ALL-UNNAMED",
                        "--enable-preview", "--source", System.getProperty("java.specification.version"),
                        "-Xlint:preview",
                        "-XDrawDiagnostics")
                .files(tb.findJavaFiles(testSrc))
                .run(Task.Expect.SUCCESS)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        expected = List.of(
                "UseClass.java:5:5: compiler.warn.is.preview: preview.api.Preview",
                "UseClass.java:6:5: compiler.warn.is.preview.reflective: preview.api.ReflectivePreview",
                "2 warnings");

        tb.checkEqual(expected, log);

        log = new JavacTask(tb, Task.Mode.CMDLINE)
                .outdir(testClasses)
                .options("--patch-module", "java.base=" + apiClasses.toString(),
                        "--add-exports", "java.base/preview.api=ALL-UNNAMED",
                        "--enable-preview", "--source", System.getProperty("java.specification.version"),
                        "-Xlint:all",
                        "-XDrawDiagnostics")
                .files(tb.findJavaFiles(testSrc))
                .run(Task.Expect.SUCCESS)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        expected = List.of(
                "UseClass.java:5:5: compiler.warn.is.preview: preview.api.Preview",
                "UseClass.java:6:5: compiler.warn.is.preview.reflective: preview.api.ReflectivePreview",
                "2 warnings");

        tb.checkEqual(expected, log);

        //combine -Xlint and -Xlint:all
        log = new JavacTask(tb, Task.Mode.CMDLINE)
                .outdir(testClasses)
                .options("--patch-module", "java.base=" + apiClasses.toString(),
                        "--add-exports", "java.base/preview.api=ALL-UNNAMED",
                        "--enable-preview", "--source", System.getProperty("java.specification.version"),
                        "-Xlint:all", "-Xlint",
                        "-XDrawDiagnostics")
                .files(tb.findJavaFiles(testSrc))
                .run(Task.Expect.SUCCESS)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        expected = List.of(
                "UseClass.java:5:5: compiler.warn.is.preview: preview.api.Preview",
                "UseClass.java:6:5: compiler.warn.is.preview.reflective: preview.api.ReflectivePreview",
                "2 warnings");

        tb.checkEqual(expected, log);

        log = new JavacTask(tb, Task.Mode.CMDLINE)
                .outdir(testClasses)
                .options("--patch-module", "java.base=" + apiClasses.toString(),
                        "--add-exports", "java.base/preview.api=ALL-UNNAMED",
                        "--enable-preview", "--source", System.getProperty("java.specification.version"),
                        "-Xlint", "-Xlint:all",
                        "-XDrawDiagnostics")
                .files(tb.findJavaFiles(testSrc))
                .run(Task.Expect.SUCCESS)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        expected = List.of(
                "UseClass.java:5:5: compiler.warn.is.preview: preview.api.Preview",
                "UseClass.java:6:5: compiler.warn.is.preview.reflective: preview.api.ReflectivePreview",
                "2 warnings");

        tb.checkEqual(expected, log);
    }

}
