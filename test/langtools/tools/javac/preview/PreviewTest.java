/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8282823 8343540
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
                List.of("IUseIntf2P.java:4:25: compiler.err.is.preview: test()",
                        "IUseIntfDef2P.java:4:25: compiler.err.is.preview: test()",
                        "UseClass2P.java:4:17: compiler.err.is.preview: test()",
                        "UseIntf2P.java:4:17: compiler.err.is.preview: test()",
                        "UseIntfDef2P.java:4:17: compiler.err.is.preview: test()",
                        "UseSubClass12P.java:3:17: compiler.err.is.preview: test()",
                        "UseSubIntfDef12P.java:3:17: compiler.err.is.preview: test()",
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
                List.of("IUseIntf2P.java:4:25: compiler.warn.is.preview: test()",
                        "IUseIntfDef2P.java:4:25: compiler.warn.is.preview: test()",
                        "UseClass2P.java:4:17: compiler.warn.is.preview: test()",
                        "UseIntf2P.java:4:17: compiler.warn.is.preview: test()",
                        "UseIntfDef2P.java:4:17: compiler.warn.is.preview: test()",
                        "UseSubClass12P.java:3:17: compiler.warn.is.preview: test()",
                        "UseSubIntfDef12P.java:3:17: compiler.warn.is.preview: test()",
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
                List.of("AbstractP.java:4:26: compiler.err.is.preview: test()",
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
                List.of("AbstractP.java:4:26: compiler.warn.is.preview: test()",
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

    @Test //JDK-8343540:
    public void nonPreviewImplementsPreview(Path base) throws Exception {
        Path apiSrc = base.resolve("api-src");
        tb.writeJavaFiles(apiSrc,
                          """
                          package preview.api;
                          @jdk.internal.javac.PreviewFeature(feature=jdk.internal.javac.PreviewFeature.Feature.TEST)
                          public interface Preview {
                              public static final int FIELD = 0;
                              public default void test() {}
                          }
                          """,
                          """
                          package preview.api;
                          @jdk.internal.javac.PreviewFeature(feature=jdk.internal.javac.PreviewFeature.Feature.TEST,
                                                             reflective=true)
                          public interface ReflectivePreview {
                              public default void test() {}
                          }
                          """,
                          """
                          package preview.api;
                          public interface NonPreviewIntf extends Preview {
                          }
                          """,
                          """
                          package preview.api;
                          public class NonPreview implements Preview {
                          }
                          """,
                          """
                          package preview.api;
                          public class ReflectiveNonPreview implements ReflectivePreview {
                          }
                          """);
        Path apiClasses = base.resolve("api-classes");

        new JavacTask(tb, Task.Mode.CMDLINE)
                .outdir(apiClasses)
                .options("--patch-module", "java.base=" + apiSrc.toString(),
                         "-Werror")
                .files(tb.findJavaFiles(apiSrc))
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        Path testSrc = base.resolve("test-src");
        tb.writeJavaFiles(testSrc,
                          """
                          package test;
                          import preview.api.NonPreview;
                          import preview.api.NonPreviewIntf;
                          import preview.api.Preview;
                          import preview.api.ReflectiveNonPreview;
                          public class Test {
                              public void test(NonPreview np,
                                               Produce<? extends NonPreview> prod) {
                                  np.test();
                                  acceptRunnable(np::test);
                                  accept(NonPreview::test);
                                  prod.produce().test();
                                  acceptRunnable(prod.produce()::test);
                                  int i = np.FIELD;
                              }
                              public <T1 extends NonPreview,
                                      T2 extends Test & NonPreviewIntf,
                                      T3 extends T2> void test(T1 t1, T2 t2, T3 t3) {
                                  t1.test();
                                  t2.test();
                                  t3.test();
                              }
                              public void test(ReflectiveNonPreview np) {
                                  np.test();
                              }
                              public void test(Preview p) {
                                  p.test();
                                  acceptRunnable(p::test);
                                  accept(Preview::test);
                              }
                              private static class ExtendsNonPreview extends NonPreview {
                                  public void test() {} //error/warning here:
                              }
                              private static class ImplementsPreview implements Preview {
                                  //no error/warning (already was on Preview after implements)
                                  public void test() {}
                              }
                              private static class ImplicitReceiver extends NonPreview {
                                  public void g() {
                                      test(); //implicit this - error/warning
                                      int i = FIELD; //implicit this - error/warning
                                  }
                              }
                              private void acceptRunnable(Runnable r) {}
                              private void accept(Accept<NonPreview> accept) {}
                              interface Accept<T> {
                                  public void accept(T t);
                              }
                              interface Produce<T> {
                                  public T produce();
                              }
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
                List.of("Test.java:4:19: compiler.err.is.preview: preview.api.Preview",
                        "Test.java:26:22: compiler.err.is.preview: preview.api.Preview",
                        "Test.java:34:55: compiler.err.is.preview: preview.api.Preview",
                        "Test.java:9:11: compiler.err.is.preview: test()",
                        "Test.java:10:24: compiler.err.is.preview: test()",
                        "Test.java:11:16: compiler.err.is.preview: test()",
                        "Test.java:12:23: compiler.err.is.preview: test()",
                        "Test.java:13:24: compiler.err.is.preview: test()",
                        "Test.java:14:19: compiler.err.is.preview: FIELD",
                        "Test.java:19:11: compiler.err.is.preview: test()",
                        "Test.java:20:11: compiler.err.is.preview: test()",
                        "Test.java:21:11: compiler.err.is.preview: test()",
                        "Test.java:24:11: compiler.warn.is.preview.reflective: test()",
                        "Test.java:29:16: compiler.err.is.preview: preview.api.Preview",
                        "Test.java:32:21: compiler.err.is.preview: test()",
                        "Test.java:36:21: compiler.err.is.preview: test()",
                        "Test.java:40:13: compiler.err.is.preview: test()",
                        "Test.java:41:21: compiler.err.is.preview: FIELD",
                        "17 errors",
                        "1 warning");

        if (!log.equals(expected))
            throw new Exception("expected output not found" + log);
    }

    @Test //JDK-8343540:
    public void nonPreviewImplementsPreview2(Path base) throws Exception {
        Path apiSrc = base.resolve("api-src");
        tb.writeJavaFiles(apiSrc,
                          """
                          package preview.api;
                          @jdk.internal.javac.PreviewFeature(feature=jdk.internal.javac.PreviewFeature.Feature.TEST)
                          public interface Preview {
                              public default void test() {}
                          }
                          """,
                          """
                          package preview.api;
                          public interface NonPreviewIntf extends Preview {
                              public default void test() {}
                          }
                          """,
                          """
                          package preview.api;
                          public class NonPreview implements Preview {
                              public void test() {}
                          }
                          """);
        Path apiClasses = base.resolve("api-classes");

        new JavacTask(tb, Task.Mode.CMDLINE)
                .outdir(apiClasses)
                .options("--patch-module", "java.base=" + apiSrc.toString(),
                         "-Werror")
                .files(tb.findJavaFiles(apiSrc))
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        Path testSrc = base.resolve("test-src");
        tb.writeJavaFiles(testSrc,
                          """
                          package test;
                          import preview.api.NonPreview;
                          import preview.api.NonPreviewIntf;
                          public class Test {
                              public void test(NonPreview np1,
                                               NonPreviewIntf np2) {
                                  np1.test();
                                  np2.test();
                              }
                          }
                          """);
        Path testClasses = base.resolve("test-classes");
        List<String> log = new JavacTask(tb, Task.Mode.CMDLINE)
                .outdir(testClasses)
                .options("--patch-module", "java.base=" + apiClasses.toString(),
                         "--add-exports", "java.base/preview.api=ALL-UNNAMED",
                         "-XDrawDiagnostics")
                .files(tb.findJavaFiles(testSrc))
                .run(Task.Expect.SUCCESS)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
    }

    @Test //JDK-8343540:
    public void nonPreviewImplementsPreview3(Path base) throws Exception {
        Path apiSrc = base.resolve("api-src");
        tb.writeJavaFiles(apiSrc,
                          """
                          package preview.api;
                          @jdk.internal.javac.PreviewFeature(feature=jdk.internal.javac.PreviewFeature.Feature.TEST)
                          public class Preview {
                              public int field;
                              public static void test() {}
                          }
                          """,
                          """
                          package preview.api;
                          public class NonPreview extends Preview {
                          }
                          """);
        Path apiClasses = base.resolve("api-classes");

        new JavacTask(tb, Task.Mode.CMDLINE)
                .outdir(apiClasses)
                .options("--patch-module", "java.base=" + apiSrc.toString(),
                         "-Werror")
                .files(tb.findJavaFiles(apiSrc))
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        Path testSrc = base.resolve("test-src");
        tb.writeJavaFiles(testSrc,
                          """
                          package test;
                          import preview.api.NonPreview;
                          import preview.api.Preview;
                          public class Test {
                              public void test(NonPreview np, Preview p) {
                                  NonPreview.test();
                                  Preview.test();
                                  int i1 = np.field;
                                  int i2 = p.field;
                              }
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
                List.of("Test.java:3:19: compiler.err.is.preview: preview.api.Preview",
                        "Test.java:5:37: compiler.err.is.preview: preview.api.Preview",
                        "Test.java:6:19: compiler.err.is.preview: test()",
                        "Test.java:7:9: compiler.err.is.preview: preview.api.Preview",
                        "Test.java:8:20: compiler.err.is.preview: field",
                        "5 errors");

        if (!log.equals(expected))
            throw new Exception("expected output not found" + log);
    }

    @Test //JDK-8343540:
    public void nonPreviewImplementsPreview4(Path base) throws Exception {
        Path apiSrc = base.resolve("api-src");
        tb.writeJavaFiles(apiSrc,
                          """
                          package preview.api;
                          @jdk.internal.javac.PreviewFeature(feature=jdk.internal.javac.PreviewFeature.Feature.TEST)
                          public class Preview {
                              public int field;
                              public static void test() {}
                          }
                          """,
                          """
                          package preview.api;
                          public class NonPreview extends Preview {
                              public int field;
                              public static void test() {}
                          }
                          """);
        Path apiClasses = base.resolve("api-classes");

        new JavacTask(tb, Task.Mode.CMDLINE)
                .outdir(apiClasses)
                .options("--patch-module", "java.base=" + apiSrc.toString(),
                         "-Werror")
                .files(tb.findJavaFiles(apiSrc))
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        Path testSrc = base.resolve("test-src");
        tb.writeJavaFiles(testSrc,
                          """
                          package test;
                          import preview.api.NonPreview;
                          import preview.api.Preview;
                          public class Test {
                              public void test(NonPreview np, Preview p) {
                                  NonPreview.test();
                                  Preview.test();
                                  int i1 = np.field;
                                  int i2 = p.field;
                              }
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
                List.of("Test.java:3:19: compiler.err.is.preview: preview.api.Preview",
                        "Test.java:5:37: compiler.err.is.preview: preview.api.Preview",
                        "Test.java:7:9: compiler.err.is.preview: preview.api.Preview",
                        "3 errors");

        if (!log.equals(expected))
            throw new Exception("expected output not found" + log);
    }

    @Test //JDK-8343540:
    public void nonPreviewImplementsPreview5(Path base) throws Exception {
        Path apiSrc = base.resolve("api-src");
        tb.writeJavaFiles(apiSrc,
                          """
                          package preview.api;
                          @jdk.internal.javac.PreviewFeature(feature=jdk.internal.javac.PreviewFeature.Feature.TEST)
                          public interface Preview {
                              public static final int CONST1 = 0;
                              public static final int CONST2 = 0;
                          }
                          """,
                          """
                          package preview.api;
                          public interface NonPreviewIntf extends Preview {
                              public static final int CONST2 = 0;
                          }
                          """,
                          """
                          package preview.api;
                          public class NonPreview implements Preview {
                              public static final int CONST2 = 0;
                          }
                          """);
        Path apiClasses = base.resolve("api-classes");

        new JavacTask(tb, Task.Mode.CMDLINE)
                .outdir(apiClasses)
                .options("--patch-module", "java.base=" + apiSrc.toString(),
                         "-Werror")
                .files(tb.findJavaFiles(apiSrc))
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        Path testSrc = base.resolve("test-src");
        tb.writeJavaFiles(testSrc,
                          """
                          package test;
                          import preview.api.NonPreview;
                          import preview.api.NonPreviewIntf;
                          import preview.api.Preview;
                          public class Test {
                              public void test() {
                                  int i1 = NonPreview.CONST1;
                                  int i2 = NonPreviewIntf.CONST1;
                                  int i3 = Preview.CONST1;
                                  int i4 = NonPreview.CONST2;
                                  int i5 = NonPreviewIntf.CONST2;
                                  int i6 = Preview.CONST2;
                              }
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
                List.of("Test.java:4:19: compiler.err.is.preview: preview.api.Preview",
                        "Test.java:7:28: compiler.err.is.preview: CONST1",
                        "Test.java:8:32: compiler.err.is.preview: CONST1",
                        "Test.java:9:18: compiler.err.is.preview: preview.api.Preview",
                        "Test.java:12:18: compiler.err.is.preview: preview.api.Preview",
                        "5 errors");

        if (!log.equals(expected))
            throw new Exception("expected output not found" + log);
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
