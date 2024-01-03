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
 * @enablePreview
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 *      java.base/jdk.internal.classfile.impl
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main PreviewTest
 */
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassFile;
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
                          public class UseSubClass11 extends UseClass1 {
                          }
                          """,
                          """
                          package test;
                          public class UseSubClass12P extends UseClass1 {
                              public void test() {}
                          }
                          """,
                          """
                          package test;
                          import preview.api.OuterClass;
                          public class UseClass2P extends OuterClass {
                              public void test() {}
                          }
                          """,
                          """
                          package test;
                          public class UseSubClass21 extends UseClass2P {
                          }
                          """,
                          """
                          package test;
                          public class UseSubClass22 extends UseClass2P {
                              public void test() {}
                          }
                          """,
                          """
                          package test;
                          import preview.api.OuterIntf;
                          public class UseIntf2P implements OuterIntf {
                              public void test() {}
                          }
                          """,
                          """
                          package test;
                          public class UseSubIntf21 extends UseIntf2P {
                          }
                          """,
                          """
                          package test;
                          public class UseSubIntf22 extends UseIntf2P {
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
                          public class UseSubIntfDef11 extends UseIntfDef1 {
                          }
                          """,
                          """
                          package test;
                          public class UseSubIntfDef12P extends UseIntfDef1 {
                              public void test() {}
                          }
                          """,
                          """
                          package test;
                          import preview.api.OuterIntfDef;
                          public class UseIntfDef2P implements OuterIntfDef {
                              public void test() {}
                          }
                          """,
                          """
                          package test;
                          public class UseSubIntfDef21 extends UseIntfDef2P {
                          }
                          """,
                          """
                          package test;
                          public class UseSubIntfDef22 extends UseIntfDef2P {
                              public void test() {}
                          }
                          """,
                          """
                          package test;
                          import preview.api.OuterIntf;
                          public interface IUseIntf1 extends OuterIntf {
                          }
                          """,
                          """
                          package test;
                          import preview.api.OuterIntf;
                          public interface IUseIntf2P extends OuterIntf {
                              public default void test() {}
                          }
                          """,
                          """
                          package test;
                          import preview.api.OuterIntfDef;
                          public interface IUseIntfDef1 extends OuterIntfDef {
                          }
                          """,
                          """
                          package test;
                          import preview.api.OuterIntfDef;
                          public interface IUseIntfDef2P extends OuterIntfDef {
                              public default void test() {}
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
                List.of("IUseIntf2P.java:3:8: compiler.err.is.preview: test()",
                        "IUseIntfDef2P.java:3:8: compiler.err.is.preview: test()",
                        "UseClass2P.java:4:17: compiler.err.is.preview: test()",
                        "UseIntf2P.java:3:8: compiler.err.is.preview: test()",
                        "UseIntfDef2P.java:3:8: compiler.err.is.preview: test()",
                        "UseSubClass12P.java:3:17: compiler.err.is.preview: test()",
                        "UseSubIntfDef12P.java:2:8: compiler.err.is.preview: test()",
                        "7 errors");

        if (!log.equals(expected))
            throw new Exception("expected output not found" + log);

        Path[] sources = tb.findJavaFiles(testSrc);

        log = new JavacTask(tb, Task.Mode.CMDLINE)
                .outdir(testClasses)
                .options("--patch-module", "java.base=" + apiClasses.toString(),
                         "--add-exports", "java.base/preview.api=ALL-UNNAMED",
                         "--enable-preview",
                         "-Xlint:preview",
                         "-source", String.valueOf(Runtime.version().feature()),
                         "-XDrawDiagnostics")
                .files(sources)
                .run(Task.Expect.SUCCESS)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        expected =
                List.of("IUseIntf2P.java:3:8: compiler.warn.is.preview: test()",
                        "IUseIntfDef2P.java:3:8: compiler.warn.is.preview: test()",
                        "UseClass2P.java:4:17: compiler.warn.is.preview: test()",
                        "UseIntf2P.java:3:8: compiler.warn.is.preview: test()",
                        "UseIntfDef2P.java:3:8: compiler.warn.is.preview: test()",
                        "UseSubClass12P.java:3:17: compiler.warn.is.preview: test()",
                        "UseSubIntfDef12P.java:2:8: compiler.warn.is.preview: test()",
                        "7 warnings");

        if (!log.equals(expected))
            throw new Exception("expected output not found" + log);

        int classfileCount = verifyPreviewClassfiles(testClasses);

        if (sources.length != classfileCount) {
            throw new IllegalStateException("Unexpected number of classfiles: " + classfileCount + ", number of source files: " + sources.length);
        }

        for (Path source : sources) {
            log = new JavacTask(tb, Task.Mode.CMDLINE)
                    .classpath(testClasses)
                    .outdir(testClasses)
                    .options("--patch-module", "java.base=" + apiClasses.toString(),
                             "--add-exports", "java.base/preview.api=ALL-UNNAMED",
                             "--enable-preview",
                             "-Xlint:preview",
                             "-source", String.valueOf(Runtime.version().feature()),
                             "-XDrawDiagnostics")
                    .files(source)
                    .run(Task.Expect.SUCCESS)
                    .writeAll()
                    .getOutputLines(Task.OutputKind.DIRECT);

            boolean preview = source.getFileName().toString().contains("P.");
            boolean hasWarning = false;
            for (String line : log) {
                hasWarning |= line.contains(source.getFileName().toString()) &&
                              line.contains("compiler.warn.is.preview: test()");
            }

            if (preview != hasWarning)
                throw new Exception("expected " + (preview ? "preview" : "not preview") +
                                    "but got " + (hasWarning ? "warning" : "no warning") +
                                    "in: " + log);

            classfileCount = verifyPreviewClassfiles(testClasses);

            if (sources.length != classfileCount) {
                throw new IllegalStateException("Unexpected number of classfiles: " + classfileCount + ", number of source files: " + sources.length);
            }
        }
    }

    @Test
    public void previewAPIAbstractReAbstract(Path base) throws Exception {
        Path apiSrc = base.resolve("api-src");
        tb.writeJavaFiles(apiSrc,
                          """
                          package preview.api;
                          public class Concrete {
                              @jdk.internal.javac.PreviewFeature(feature=jdk.internal.javac.PreviewFeature.Feature.TEST)
                              public void test() {}
                          }
                          """,
                          """
                          package preview.api;
                          public abstract class Abstract {
                              @jdk.internal.javac.PreviewFeature(feature=jdk.internal.javac.PreviewFeature.Feature.TEST)
                              public abstract void test();
                          }
                          """);
        Path apiClasses = base.resolve("api-classes");

        new JavacTask(tb, Task.Mode.CMDLINE)
                .outdir(apiClasses)
                .options("-XDrawDiagnostics", "-doe",
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
                          import preview.api.Concrete;
                          public abstract class ReabstractP extends Concrete {
                              public abstract void test();
                          }
                          """,
                          """
                          package test;
                          public abstract class ReabstractSubclass1 extends ReabstractP {
                              public abstract void test();
                          }
                          """,
                          """
                          package test;
                          public class ReabstractSubclass2 extends ReabstractP {
                              public void test() {}
                          }
                          """,
                          """
                          package test;
                          import preview.api.Abstract;
                          public abstract class AbstractP extends Abstract {
                              public abstract void test();
                          }
                          """,
                          """
                          package test;
                          public abstract class AbstractSubclass1 extends AbstractP {
                              public abstract void test();
                          }
                          """,
                          """
                          package test;
                          public class AbstractSubclass2 extends AbstractP {
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
                List.of("AbstractP.java:3:17: compiler.err.is.preview: test()",
                        "ReabstractP.java:4:26: compiler.err.is.preview: test()",
                        "2 errors");

        if (!log.equals(expected))
            throw new Exception("expected output not found" + log);

        Path[] sources = tb.findJavaFiles(testSrc);

        log = new JavacTask(tb, Task.Mode.CMDLINE)
                .outdir(testClasses)
                .options("--patch-module", "java.base=" + apiClasses.toString(),
                         "--add-exports", "java.base/preview.api=ALL-UNNAMED",
                         "--enable-preview",
                         "-Xlint:preview",
                         "-source", String.valueOf(Runtime.version().feature()),
                         "-XDrawDiagnostics")
                .files(sources)
                .run(Task.Expect.SUCCESS)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        expected =
                List.of("AbstractP.java:3:17: compiler.warn.is.preview: test()",
                        "ReabstractP.java:4:26: compiler.warn.is.preview: test()",
                        "2 warnings");

        if (!log.equals(expected))
            throw new Exception("expected output not found" + log);

        int classfileCount = verifyPreviewClassfiles(testClasses);

        if (sources.length != classfileCount) {
            throw new IllegalStateException("Unexpected number of classfiles: " + classfileCount + ", number of source files: " + sources.length);
        }

        for (Path source : sources) {
            log = new JavacTask(tb, Task.Mode.CMDLINE)
                    .classpath(testClasses)
                    .outdir(testClasses)
                    .options("--patch-module", "java.base=" + apiClasses.toString(),
                             "--add-exports", "java.base/preview.api=ALL-UNNAMED",
                             "--enable-preview",
                             "-Xlint:preview",
                             "-source", String.valueOf(Runtime.version().feature()),
                             "-XDrawDiagnostics")
                    .files(source)
                    .run(Task.Expect.SUCCESS)
                    .writeAll()
                    .getOutputLines(Task.OutputKind.DIRECT);

            boolean preview = source.getFileName().toString().contains("P.");
            boolean hasWarning = false;
            for (String line : log) {
                hasWarning |= line.contains(source.getFileName().toString()) &&
                              line.contains("compiler.warn.is.preview: test()");
            }

            if (preview != hasWarning)
                throw new Exception("expected " + (preview ? "preview" : "not preview") +
                                    "but got " + (hasWarning ? "warning" : "no warning") +
                                    "in: " + log);

            classfileCount = verifyPreviewClassfiles(testClasses);

            if (sources.length != classfileCount) {
                throw new IllegalStateException("Unexpected number of classfiles: " + classfileCount + ", number of source files: " + sources.length);
            }
        }
    }

    private int verifyPreviewClassfiles(Path directory) throws Exception {
        Path[] classfiles = tb.findFiles("class", directory);

        for (Path classfile : classfiles) {
            boolean preview = classfile.getFileName().toString().contains("P.");

            checkPreviewClassfile(classfile, preview);
        }

        return classfiles.length;
    }

    private void checkPreviewClassfile(Path p, boolean preview) throws Exception {
        try (InputStream in = Files.newInputStream(p)) {
            ClassModel cf = ClassFile.of().parse(in.readAllBytes());
            if (preview && cf.minorVersion() != 65535) {
                throw new IllegalStateException("Expected preview class, but got: " + cf.minorVersion() + " for: " + p.toString());
            } else if (!preview && cf.minorVersion() != 0) {
                throw new IllegalStateException("Expected minor version == 0 but got: " + cf.minorVersion() + " for: " + p.toString());
            }
        }
    }
}
