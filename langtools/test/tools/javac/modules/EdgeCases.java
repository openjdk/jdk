/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8154283 8167320
 * @summary tests for multi-module mode compilation
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.code
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JarTask toolbox.JavacTask ModuleTestBase
 * @run main EdgeCases
 */

import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.sun.source.tree.CompilationUnitTree;
//import com.sun.source.util.JavacTask; // conflicts with toolbox.JavacTask
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.code.Symbol.ModuleSymbol;
import com.sun.tools.javac.code.Symtab;

import toolbox.JarTask;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.Task.Expect;
import toolbox.Task.OutputKind;

public class EdgeCases extends ModuleTestBase {

    public static void main(String... args) throws Exception {
        new EdgeCases().runTests();
    }

    @Test
    public void testAddExportUndefinedModule(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, "package test; import undefPackage.Any; public class Test {}");
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        List<String> log = new JavacTask(tb)
                .options("--add-exports", "undefModule/undefPackage=ALL-UNNAMED",
                         "-XDrawDiagnostics")
                .outdir(classes)
                .files(findJavaFiles(src))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expected = Arrays.asList("- compiler.warn.module.for.option.not.found: --add-exports, undefModule",
                                              "Test.java:1:34: compiler.err.doesnt.exist: undefPackage",
                                              "1 error", "1 warning");

        if (!expected.equals(log))
            throw new Exception("expected output not found: " + log);
    }

    @Test
    public void testModuleSymbolOutterMostClass(Path base) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
            Path moduleSrc = base.resolve("module-src");
            Path m1 = moduleSrc.resolve("m1x");

            tb.writeJavaFiles(m1, "module m1x { }");

            Iterable<? extends JavaFileObject> files = fm.getJavaFileObjects(findJavaFiles(moduleSrc));
            com.sun.source.util.JavacTask task =
                (com.sun.source.util.JavacTask) compiler.getTask(null, fm, null, null, null, files);

            task.analyze();

            ModuleSymbol msym = (ModuleSymbol) task.getElements().getModuleElement("m1x");

            msym.outermostClass();
        }
    }

    @Test
    public void testParseEnterAnalyze(Path base) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
            Path moduleSrc = base.resolve("module-src");
            Path m1 = moduleSrc.resolve("m1x");

            tb.writeJavaFiles(m1, "module m1x { }",
                                  "package p;",
                                  "package p; class T { }");

            Path classes = base.resolve("classes");
            Iterable<? extends JavaFileObject> files = fm.getJavaFileObjects(findJavaFiles(moduleSrc));
            List<String> options = Arrays.asList("-d", classes.toString(), "-Xpkginfo:always");
            JavacTaskImpl task = (JavacTaskImpl) compiler.getTask(null, fm, null, options, null, files);

            Iterable<? extends CompilationUnitTree> parsed = task.parse();
            Iterable<? extends Element> entered = task.enter(parsed);
            Iterable<? extends Element> analyzed = task.analyze(entered);
            Iterable<? extends JavaFileObject> generatedFiles = task.generate(analyzed);

            Set<String> generated = new HashSet<>();

            for (JavaFileObject jfo : generatedFiles) {
                generated.add(jfo.getName());
            }

            Set<String> expected = new HashSet<>(
                    Arrays.asList(Paths.get("testParseEnterAnalyze", "classes", "p", "package-info.class").toString(),
                                  Paths.get("testParseEnterAnalyze", "classes", "module-info.class").toString(),
                                  Paths.get("testParseEnterAnalyze", "classes", "p", "T.class").toString())
            );

            if (!Objects.equals(expected, generated))
                throw new AssertionError("Incorrect generated files: " + generated);
        }
    }

    @Test
    public void testModuleImplicitModuleBoundaries(Path base) throws Exception {
        Path src = base.resolve("src");
        Path src_m1 = src.resolve("m1x");
        tb.writeJavaFiles(src_m1,
                          "module m1x { exports api1; }",
                          "package api1; public class Api1 { public void call() { } }");
        Path src_m2 = src.resolve("m2x");
        tb.writeJavaFiles(src_m2,
                          "module m2x { requires m1x; exports api2; }",
                          "package api2; public class Api2 { public static api1.Api1 get() { return null; } }");
        Path src_m3 = src.resolve("m3x");
        tb.writeJavaFiles(src_m3,
                          "module m3x { requires m2x; }",
                          "package test; public class Test { { api2.Api2.get().call(); api2.Api2.get().toString(); } }");
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        String log = new JavacTask(tb)
                .options("-XDrawDiagnostics",
                         "--module-source-path", src.toString())
                .outdir(classes)
                .files(findJavaFiles(src))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!log.contains("Test.java:1:52: compiler.err.not.def.access.class.intf.cant.access.reason: call(), api1.Api1, api1, (compiler.misc.not.def.access.does.not.read: m3x, api1, m1x)") ||
            !log.contains("Test.java:1:76: compiler.err.not.def.access.class.intf.cant.access: toString(), java.lang.Object"))
            throw new Exception("expected output not found");
    }

    @Test
    public void testAssignClassToAutomaticModule(Path base) throws Exception {
        //check that if a ClassSymbol belongs to an automatic module, it is properly assigned and not
        //duplicated when being accessed through a classfile.
        Path automaticSrc = base.resolve("automaticSrc");
        tb.writeJavaFiles(automaticSrc, "package api1; public class Api1 {}");
        Path automaticClasses = base.resolve("automaticClasses");
        tb.createDirectories(automaticClasses);

        String automaticLog = new JavacTask(tb)
                                .outdir(automaticClasses)
                                .files(findJavaFiles(automaticSrc))
                                .run()
                                .writeAll()
                                .getOutput(Task.OutputKind.DIRECT);

        if (!automaticLog.isEmpty())
            throw new Exception("expected output not found: " + automaticLog);

        Path modulePath = base.resolve("module-path");

        Files.createDirectories(modulePath);

        Path automaticJar = modulePath.resolve("a-1.0.jar");

        new JarTask(tb, automaticJar)
          .baseDir(automaticClasses)
          .files("api1/Api1.class")
          .run();

        Path src = base.resolve("src");
        Path src_m2 = src.resolve("m2x");
        tb.writeJavaFiles(src_m2,
                          "module m2x { requires a; exports api2; }",
                          "package api2; public class Api2 { public static api1.Api1 get() { return null; } }");
        Path src_m3 = src.resolve("m3x");
        tb.writeJavaFiles(src_m3,
                          "module m3x { requires a; requires m2x; }",
                          "package test; public class Test { { api2.Api2.get(); api1.Api1 a1; } }");
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        new JavacTask(tb)
                .options("--module-path", modulePath.toString(),
                         "--module-source-path", src.toString())
                .outdir(classes)
                .files(findJavaFiles(src_m2))
                .run()
                .writeAll();

        new JavacTask(tb)
                .options("--module-path", modulePath.toString(),
                         "--module-source-path", src.toString())
                .outdir(classes)
                .files(findJavaFiles(src_m3))
                .run()
                .writeAll();
    }

    @Test
    public void testEmptyImplicitModuleInfo(Path base) throws Exception {
        Path src = base.resolve("src");
        Path src_m1 = src.resolve("m1x");
        Files.createDirectories(src_m1);
        try (Writer w = Files.newBufferedWriter(src_m1.resolve("module-info.java"))) {}
        tb.writeJavaFiles(src_m1,
                          "package test; public class Test {}");
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        new JavacTask(tb)
                .options("--source-path", src_m1.toString(),
                         "-XDrawDiagnostics")
                .outdir(classes)
                .files(findJavaFiles(src_m1.resolve("test")))
                .run(Task.Expect.FAIL)
                .writeAll();

        tb.writeJavaFiles(src_m1,
                          "module m1x {}");

        new JavacTask(tb)
                .options("--source-path", src_m1.toString())
                .outdir(classes)
                .files(findJavaFiles(src_m1.resolve("test")))
                .run()
                .writeAll();

    }

    @Test
    public void testClassPackageClash(Path base) throws Exception {
        Path src = base.resolve("src");
        Path src_m1 = src.resolve("m1x");
        tb.writeJavaFiles(src_m1,
                          "module m1x { exports test.m1x; }",
                          "package test.m1x;\n" +
                          "public class Test {}\n");
        Path src_m2 = src.resolve("m2x");
        tb.writeJavaFiles(src_m2,
                          "module m2x { requires m1x; }",
                          "package test;\n" +
                          "public class m1x {}\n");
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        List<String> log = new JavacTask(tb)
                .options("--module-source-path", src.toString(),
                         "-XDrawDiagnostics")
                .outdir(classes)
                .files(findJavaFiles(src))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expected = Arrays.asList(
            "m1x.java:2:8: compiler.err.clash.with.pkg.of.same.name: kindname.class, test.m1x",
            "1 error"
        );

        if (!expected.equals(log)) {
            throw new IllegalStateException(log.toString());
        }
    }

    @Test
    public void testImplicitJavaBase(Path base) throws Exception {
        Path src = base.resolve("src");
        Path src_java_base = src.resolve("java.base");
        Files.createDirectories(src_java_base);
        tb.writeJavaFiles(src_java_base, "module java.base { exports java.lang; }");
        tb.writeJavaFiles(src_java_base,
                          "package java.lang; public class Object {}");
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        //module-info from source:
        new JavacTask(tb)
            .options("-sourcepath", src_java_base.toString())
            .outdir(classes)
            .files(findJavaFiles(src_java_base.resolve("java").resolve("lang").resolve("Object.java")))
            .run()
            .writeAll();

        //module-info from class:
        if (!Files.exists(classes.resolve("module-info.class"))) {
            throw new AssertionError("module-info.class not created!");
        }

        new JavacTask(tb)
            .outdir(classes)
            .files(findJavaFiles(src_java_base.resolve("java").resolve("lang").resolve("Object.java")))
            .run()
            .writeAll();

        //broken module-info.class:
        Files.newOutputStream(classes.resolve("module-info.class")).close();

        List<String> log = new JavacTask(tb)
            .options("-XDrawDiagnostics")
            .outdir(classes)
            .files(findJavaFiles(src_java_base.resolve("java").resolve("lang").resolve("Object.java")))
            .run(Expect.FAIL)
            .writeAll()
            .getOutputLines(OutputKind.DIRECT);

        List<String> expected = Arrays.asList(
                "- compiler.err.cant.access: <error>.module-info, (compiler.misc.bad.class.file.header: module-info.class, (compiler.misc.illegal.start.of.class.file))",
                "1 error");

        if (!expected.equals(log)) {
            throw new AssertionError("Unexpected output: " + log);
        }

        //broken module-info.java:
        Files.delete(classes.resolve("module-info.class"));

        try (Writer out = Files.newBufferedWriter(src_java_base.resolve("module-info.java"))) {
            out.write("class Broken {}");
        }

        log = new JavacTask(tb)
            .options("-sourcepath", src_java_base.toString(),
                                "-XDrawDiagnostics")
            .outdir(classes)
            .files(findJavaFiles(src_java_base.resolve("java").resolve("lang").resolve("Object.java")))
            .run(Expect.FAIL)
            .writeAll()
            .getOutputLines(OutputKind.DIRECT);

        expected = Arrays.asList("X");

        if (expected.equals(log)) {
            throw new AssertionError("Unexpected output: " + log);
        }
    }

    @Test
    public void testModuleInfoNameMismatchSource(Path base) throws Exception {
        Path src = base.resolve("src");
        Path m1 = src.resolve("m1x");
        Files.createDirectories(m1);
        tb.writeJavaFiles(m1, "module other { }",
                              "package test; public class Test {}");
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        List<String> log = new JavacTask(tb)
            .options("--module-source-path", src.toString(),
                     "-XDrawDiagnostics")
            .outdir(classes)
            .files(findJavaFiles(m1.resolve("test").resolve("Test.java")))
            .run(Expect.FAIL)
            .writeAll()
            .getOutputLines(OutputKind.DIRECT);

        List<String> expected = Arrays.asList(
                "module-info.java:1:1: compiler.err.module.name.mismatch: other, m1x",
                "- compiler.err.cant.access: m1x.module-info, (compiler.misc.cant.resolve.modules)",
                "2 errors");

        if (!expected.equals(log)) {
            throw new AssertionError("Unexpected output: " + log);
        }
    }

    @Test
    public void testModuleInfoNameMismatchClass(Path base) throws Exception {
        Path src = base.resolve("src");
        Files.createDirectories(src);
        tb.writeJavaFiles(src, "module other { }",
                               "package test; public class Test {}");
        Path classes = base.resolve("classes");
        Path m1Classes = classes.resolve("m1x");
        tb.createDirectories(m1Classes);

        new JavacTask(tb)
            .outdir(m1Classes)
            .files(findJavaFiles(src))
            .run()
            .writeAll()
            .getOutputLines(OutputKind.DIRECT);

        Path src2 = base.resolve("src2");
        Files.createDirectories(src2);
        tb.writeJavaFiles(src2, "module use { requires m1x; }");

        Path classes2 = base.resolve("classes2");
        tb.createDirectories(classes2);

        List<String> log = new JavacTask(tb)
            .options("--module-path", classes.toString(),
                     "-XDrawDiagnostics")
            .outdir(classes2)
            .files(findJavaFiles(src2))
            .run(Expect.FAIL)
            .writeAll()
            .getOutputLines(OutputKind.DIRECT);

        List<String> expected = Arrays.asList(
                "- compiler.err.cant.access: m1x.module-info, (compiler.misc.bad.class.file.header: module-info.class, (compiler.misc.module.name.mismatch: other, m1x))",
                "1 error");

        if (!expected.equals(log)) {
            throw new AssertionError("Unexpected output: " + log);
        }
    }

    @Test
    public void testGetDirectivesComplete(Path base) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        JavacTaskImpl task = (JavacTaskImpl) compiler.getTask(null, null, null, null, null, null);
        Symtab syms = Symtab.instance(task.getContext());

        syms.java_base.getDirectives();
    }

    @Test
    public void testPackageInModuleInfo(Path base) throws Exception {
        Path src = base.resolve("src");
        Files.createDirectories(src);
        tb.writeJavaFiles(src, "package p; module foo { }");
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        List<String> log = new JavacTask(tb)
            .options("-XDrawDiagnostics", "-XDshould-stop.ifError=FLOW")
            .outdir(classes)
            .files(findJavaFiles(src))
            .run(Expect.FAIL)
            .writeAll()
            .getOutputLines(OutputKind.DIRECT);

        List<String> expected = Arrays.asList(
                "module-info.java:1:1: compiler.err.no.pkg.in.module-info.java",
                "1 error");

        if (!expected.equals(log)) {
            throw new AssertionError("Unexpected output: " + log);
        }
    }
}
