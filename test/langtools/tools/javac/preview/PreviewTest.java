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
                          public class OuterClass {
                              @jdk.internal.javac.PreviewFeature(feature=jdk.internal.javac.PreviewFeature.Feature.TEST)
                              public void test() {}
                          }
                          """,
                          """
                          package preview.api;
                          public interface OuterIntf {
                              @jdk.internal.javac.PreviewFeature(feature=jdk.internal.javac.PreviewFeature.Feature.TEST)
                              public void test();
                          }
                          """,
                          """
                          package preview.api;
                          public interface OuterIntfDef {
                              @jdk.internal.javac.PreviewFeature(feature=jdk.internal.javac.PreviewFeature.Feature.TEST)
                              public default void test() {};
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
                          import preview.api.OuterClass;
                          public class UseClass1 extends OuterClass {
                          }
                          """,
                          """
                          package test;
                          import preview.api.OuterClass;
                          public class UseClass2 extends OuterClass {
                              public void test() {}
                          }
                          """,
                          """
                          package test;
                          import preview.api.OuterIntf;
                          public class UseIntf2 implements OuterIntf {
                              public void test() {}
                          }
                          """,
                          """
                          package test;
                          import preview.api.OuterIntfDef;
                          public class UseIntfDef1 implements OuterIntfDef {
                          }
                          """,
                          """
                          package test;
                          import preview.api.OuterIntfDef;
                          public class UseIntfDef2 implements OuterIntfDef {
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
                List.of("UseClass2.java:4:17: compiler.err.is.preview: test()",
                        "UseIntf2.java:3:8: compiler.err.is.preview: test()",
                        "UseIntfDef2.java:3:8: compiler.err.is.preview: test()",
                        "3 errors");

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
                List.of("UseClass2.java:4:17: compiler.warn.is.preview: test()",
                        "UseIntf2.java:3:8: compiler.warn.is.preview: test()",
                        "UseIntfDef2.java:3:8: compiler.warn.is.preview: test()",
                        "3 warnings");

        if (!log.equals(expected))
            throw new Exception("expected output not found" + log);

        checkPreviewClassfile(testClasses.resolve("test").resolve("UseClass1.class"),
                              false);
        checkPreviewClassfile(testClasses.resolve("test").resolve("UseClass2.class"),
                              true);
        checkPreviewClassfile(testClasses.resolve("test").resolve("UseIntf2.class"),
                              true);
        checkPreviewClassfile(testClasses.resolve("test").resolve("UseIntfDef1.class"),
                              false);
        checkPreviewClassfile(testClasses.resolve("test").resolve("UseIntfDef2.class"),
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
