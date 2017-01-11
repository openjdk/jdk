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
 * @summary tests for multi-module mode compilation
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.code
 *      jdk.compiler/com.sun.tools.javac.main
 *      jdk.compiler/com.sun.tools.javac.processing
 * @build toolbox.ToolBox toolbox.JavacTask toolbox.ModuleBuilder ModuleTestBase
 * @run main XModuleTest
 */

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import toolbox.JavacTask;
import toolbox.ModuleBuilder;
import toolbox.Task;
import toolbox.Task.Expect;

public class XModuleTest extends ModuleTestBase {

    public static void main(String... args) throws Exception {
        new XModuleTest().runTests();
    }

    @Test
    public void testCorrectXModule(Path base) throws Exception {
        //note: avoiding use of java.base, as that gets special handling on some places:
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, "package javax.lang.model.element; public interface Extra extends Element { }");
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        String log = new JavacTask(tb)
                .options("-Xmodule:java.compiler")
                .outdir(classes)
                .files(findJavaFiles(src))
                .run()
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!log.isEmpty())
            throw new Exception("expected output not found: " + log);
    }

    @Test
    public void testSourcePath(Path base) throws Exception {
        //note: avoiding use of java.base, as that gets special handling on some places:
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, "package javax.lang.model.element; public interface Extra extends Element, Other { }", "package javax.lang.model.element; interface Other { }");
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        String log = new JavacTask(tb)
                .options("-Xmodule:java.compiler", "-sourcepath", src.toString())
                .outdir(classes)
                .files(src.resolve("javax/lang/model/element/Extra.java"))
                .run()
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!log.isEmpty())
            throw new Exception("expected output not found: " + log);
    }

    @Test
    public void testClassPath(Path base) throws Exception {
        Path cpSrc = base.resolve("cpSrc");
        tb.writeJavaFiles(cpSrc, "package p; public interface Other { }");
        Path cpClasses = base.resolve("cpClasses");
        tb.createDirectories(cpClasses);

        String cpLog = new JavacTask(tb)
                .outdir(cpClasses)
                .files(findJavaFiles(cpSrc))
                .run()
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!cpLog.isEmpty())
            throw new Exception("expected output not found: " + cpLog);

        Path src = base.resolve("src");
        //note: avoiding use of java.base, as that gets special handling on some places:
        tb.writeJavaFiles(src, "package javax.lang.model.element; public interface Extra extends Element, p.Other { }");
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        List<String> log = new JavacTask(tb)
                .options("-Xmodule:java.compiler",
                         "--class-path", cpClasses.toString(),
                         "-XDrawDiagnostics")
                .outdir(classes)
                .files(src.resolve("javax/lang/model/element/Extra.java"))
                .run(Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expectedOut = Arrays.asList(
                "Extra.java:1:76: compiler.err.doesnt.exist: p",
                "1 error"
        );

        if (!expectedOut.equals(log))
            throw new Exception("expected output not found: " + log);
    }

    @Test
    public void testNoModuleInfoOnSourcePath(Path base) throws Exception {
        //note: avoiding use of java.base, as that gets special handling on some places:
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                          "module java.compiler {}",
                          "package javax.lang.model.element; public interface Extra { }");
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        List<String> log = new JavacTask(tb)
                .options("-XDrawDiagnostics", "-Xmodule:java.compiler")
                .outdir(classes)
                .files(findJavaFiles(src))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expected = Arrays.asList("Extra.java:1:1: compiler.err.module-info.with.xmodule.sourcepath",
                                              "1 error");

        if (!expected.equals(log))
            throw new Exception("expected output not found: " + log);
    }

    @Test
    public void testNoModuleInfoInClassOutput(Path base) throws Exception {
        //note: avoiding use of java.base, as that gets special handling on some places:
        Path srcMod = base.resolve("src-mod");
        tb.writeJavaFiles(srcMod,
                          "module mod {}");
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        String logMod = new JavacTask(tb)
                .options()
                .outdir(classes)
                .files(findJavaFiles(srcMod))
                .run()
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!logMod.isEmpty())
            throw new Exception("unexpected output found: " + logMod);

        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                          "package javax.lang.model.element; public interface Extra { }");
        tb.createDirectories(classes);

        List<String> log = new JavacTask(tb)
                .options("-XDrawDiagnostics", "-Xmodule:java.compiler")
                .outdir(classes)
                .files(findJavaFiles(src))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expected = Arrays.asList("Extra.java:1:1: compiler.err.module-info.with.xmodule.classpath",
                                              "1 error");

        if (!expected.equals(log))
            throw new Exception("expected output not found: " + log);
    }

    @Test
    public void testModuleSourcePathXModule(Path base) throws Exception {
        //note: avoiding use of java.base, as that gets special handling on some places:
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, "package javax.lang.model.element; public interface Extra extends Element { }");
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        List<String> log = new JavacTask(tb)
                .options("-XDrawDiagnostics", "-Xmodule:java.compiler", "--module-source-path", src.toString())
                .outdir(classes)
                .files(findJavaFiles(src))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expected = Arrays.asList("- compiler.err.xmodule.no.module.sourcepath");

        if (!expected.equals(log))
            throw new Exception("expected output not found: " + log);
    }

    @Test
    public void testXModuleTooMany(Path base) throws Exception {
        //note: avoiding use of java.base, as that gets special handling on some places:
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, "package javax.lang.model.element; public interface Extra extends Element { }");
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        List<String> log = new JavacTask(tb, Task.Mode.CMDLINE)
                .options("-XDrawDiagnostics", "-Xmodule:java.compiler", "-Xmodule:java.compiler")
                .outdir(classes)
                .files(findJavaFiles(src))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expected = Arrays.asList("javac: option -Xmodule: can only be specified once",
                                              "Usage: javac <options> <source files>",
                                              "use --help for a list of possible options");

        if (!expected.equals(log))
            throw new Exception("expected output not found: " + log);
    }

    @Test
    public void testWithModulePath(Path base) throws Exception {
        Path modSrc = base.resolve("modSrc");
        Path modules = base.resolve("modules");
        new ModuleBuilder(tb, "m1")
                .classes("package pkg1; public interface E { }")
                .build(modSrc, modules);

        Path src = base.resolve("src");
        tb.writeJavaFiles(src, "package p; interface A extends pkg1.E { }");

        new JavacTask(tb, Task.Mode.CMDLINE)
                .options("--module-path", modules.toString(),
                        "-Xmodule:m1")
                .files(findJavaFiles(src))
                .run()
                .writeAll();

        //checks module bounds still exist
        new ModuleBuilder(tb, "m2")
                .classes("package pkg2; public interface D { }")
                .build(modSrc, modules);

        Path src2 = base.resolve("src2");
        tb.writeJavaFiles(src2, "package p; interface A extends pkg2.D { }");

        List<String> log = new JavacTask(tb, Task.Mode.CMDLINE)
                .options("-XDrawDiagnostics",
                        "--module-path", modules.toString(),
                        "-Xmodule:m1")
                .files(findJavaFiles(src2))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expected = Arrays.asList("A.java:1:32: compiler.err.package.not.visible: pkg2, (compiler.misc.not.def.access.does.not.read: m1, pkg2, m2)",
                "1 error");

        if (!expected.equals(log))
            throw new Exception("expected output not found: " + log);
    }

    @Test
    public void testWithUpgradeModulePath(Path base) throws Exception {
        Path modSrc = base.resolve("modSrc");
        Path modules = base.resolve("modules");
        new ModuleBuilder(tb, "m1")
                .classes("package pkg1; public interface E { }")
                .build(modSrc, modules);

        Path upgrSrc = base.resolve("upgradeSrc");
        Path upgrade = base.resolve("upgrade");
        new ModuleBuilder(tb, "m1")
                .classes("package pkg1; public interface D { }")
                .build(upgrSrc, upgrade);

        Path src = base.resolve("src");
        tb.writeJavaFiles(src, "package p; interface A extends pkg1.D { }");

        new JavacTask(tb, Task.Mode.CMDLINE)
                .options("--module-path", modules.toString(),
                        "--upgrade-module-path", upgrade.toString(),
                        "-Xmodule:m1")
                .files(findJavaFiles(src))
                .run()
                .writeAll();
    }

    @Test
    public void testUnnamedIsolation(Path base) throws Exception {
        //note: avoiding use of java.base, as that gets special handling on some places:
        Path sourcePath = base.resolve("source-path");
        tb.writeJavaFiles(sourcePath, "package src; public class Src {}");

        Path classPathSrc = base.resolve("class-path-src");
        tb.writeJavaFiles(classPathSrc, "package cp; public class CP { }");
        Path classPath = base.resolve("classPath");
        tb.createDirectories(classPath);

        String cpLog = new JavacTask(tb)
                .outdir(classPath)
                .files(findJavaFiles(classPathSrc))
                .run()
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!cpLog.isEmpty())
            throw new Exception("expected output not found: " + cpLog);

        Path modulePathSrc = base.resolve("module-path-src");
        tb.writeJavaFiles(modulePathSrc,
                          "module m {}",
                          "package m; public class M {}");
        Path modulePath = base.resolve("modulePath");
        tb.createDirectories(modulePath.resolve("m"));

        String modLog = new JavacTask(tb)
                .outdir(modulePath.resolve("m"))
                .files(findJavaFiles(modulePathSrc))
                .run()
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!modLog.isEmpty())
            throw new Exception("expected output not found: " + modLog);

        Path src = base.resolve("src");
        tb.writeJavaFiles(src, "package m; public class Extra { }");
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        String log = new JavacTask(tb)
                .options("-Xmodule:m",
                         "--class-path", classPath.toString(),
                         "--source-path", sourcePath.toString(),
                         "--module-path", modulePath.toString(),
                         "--processor-path", System.getProperty("test.classes"),
                         "-XDaccessInternalAPI=true",
                         "-processor", CheckModuleContentProcessing.class.getName())
                .outdir(classes)
                .files(findJavaFiles(sourcePath))
                .run()
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!log.isEmpty())
            throw new Exception("expected output not found: " + log);
    }

    @SupportedAnnotationTypes("*")
    public static final class CheckModuleContentProcessing extends AbstractProcessor {

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            Symtab syms = Symtab.instance(((JavacProcessingEnvironment) processingEnv).getContext());
            Elements elements = processingEnv.getElementUtils();
            ModuleElement unnamedModule = syms.unnamedModule;
            ModuleElement mModule = elements.getModuleElement("m");

            assertNonNull("mModule found", mModule);
            assertNonNull("src.Src from m", elements.getTypeElement(mModule, "src.Src"));
            assertNull("cp.CP not from m", elements.getTypeElement(mModule, "cp.CP"));
            assertNull("src.Src not from unnamed", elements.getTypeElement(unnamedModule, "src.Src"));
            assertNonNull("cp.CP from unnamed", elements.getTypeElement(unnamedModule, "cp.CP"));

            return false;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latest();
        }

        private static void assertNonNull(String msg, Object val) {
            if (val == null) {
                throw new AssertionError(msg);
            }
        }

        private static void assertNull(String msg, Object val) {
            if (val != null) {
                throw new AssertionError(msg);
            }
        }
    }

}
