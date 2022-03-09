/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8282823
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 *      jdk.jdeps/com.sun.tools.classfile
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main PreviewTest
 */
import com.sun.tools.classfile.ClassFile;
import java.io.InputStream;
import java.nio.file.Files;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.TestRunner;
import toolbox.ToolBox;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class PreviewTest extends TestRunner {

    protected ToolBox tb;

    PreviewTest() {
        super(System.err);
        tb = new ToolBox();
    }

    public static void main(String... args) throws Exception {
        PreviewTest t = new PreviewTest();
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
                          public class Outer {
                              @jdk.internal.javac.PreviewFeature(feature=jdk.internal.javac.PreviewFeature.Feature.TEST)
                              public void test() {}
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

        checkPreviewClassfile(apiClasses.resolve("preview").resolve("api").resolve("Outer.class"),
                              false);

        Path testSrc = base.resolve("test-src");
        tb.writeJavaFiles(testSrc,
                          """
                          package test;
                          import preview.api.Outer;
                          public class Use1 extends Outer {
                          }
                          """,
                          """
                          package test;
                          import preview.api.Outer;
                          public class Use2 extends Outer {
                              public void test() {}
                          }
                          """);
        Path testClasses = base.resolve("test-classes");
        List<String> log = new JavacTask(tb, Task.Mode.CMDLINE)
                .outdir(testClasses)
                .options("--patch-module", "java.base=" + apiClasses.toString(),
                         "--add-exports", "java.base/preview.api=ALL-UNNAMED",
                         "-XDrawDiagnostics")
                .files(tb.findJavaFiles(testSrc))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expected =
                List.of("Use2.java:4:17: compiler.err.is.preview: test()",
                        "1 error");

        if (!log.equals(expected))
            throw new Exception("expected output not found" + log);

        log = new JavacTask(tb, Task.Mode.CMDLINE)
                .outdir(testClasses)
                .options("--patch-module", "java.base=" + apiClasses.toString(),
                         "--add-exports", "java.base/preview.api=ALL-UNNAMED",
                         "--enable-preview",
                         "-Xlint:preview",
                         "-source", String.valueOf(Runtime.version().feature()),
                         "-XDrawDiagnostics")
                .files(tb.findJavaFiles(testSrc))
                .run(Task.Expect.SUCCESS)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        expected =
                List.of("Use2.java:4:17: compiler.warn.is.preview: test()",
                        "1 warning");

        if (!log.equals(expected))
            throw new Exception("expected output not found" + log);

        checkPreviewClassfile(testClasses.resolve("test").resolve("Use1.class"),
                              false);
        checkPreviewClassfile(testClasses.resolve("test").resolve("Use2.class"),
                              true);
    }

    private void checkPreviewClassfile(Path p, boolean preview) throws Exception {
        try (InputStream in = Files.newInputStream(p)) {
            ClassFile cf = ClassFile.read(in);
            if (preview && cf.minor_version != 65535) {
                throw new IllegalStateException("Expected preview class, but got: " + cf.minor_version);
            } else if (!preview && cf.minor_version != 0) {
                throw new IllegalStateException("Expected minor version == 0 but got: " + cf.minor_version);
            }
        }
    }
}
