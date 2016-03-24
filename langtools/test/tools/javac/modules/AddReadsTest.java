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
 * @summary Test the -XaddReads option
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.javap
 * @build ToolBox ModuleTestBase
 * @run main AddReadsTest
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.ModuleElement.RequiresDirective;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;

public class AddReadsTest extends ModuleTestBase {

    public static void main(String... args) throws Exception {
        new AddReadsTest().runTests();
    }

    @Test
    void testAddReads(Path base) throws Exception {
        Path src = base.resolve("src");
        Path src_m1 = src.resolve("m1");
        tb.writeJavaFiles(src_m1,
                          "module m1 { exports api; }",
                          "package api; public class Api { }");
        Path src_m2 = src.resolve("m2");
        tb.writeJavaFiles(src_m2,
                          "module m2 { }",
                          "package test; public class Test extends api.Api { }");
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        String log = tb.new JavacTask()
                .options("-XDrawDiagnostics",
                         "-modulesourcepath", src.toString())
                .outdir(classes)
                .files(findJavaFiles(src))
                .run(ToolBox.Expect.FAIL)
                .writeAll()
                .getOutput(ToolBox.OutputKind.DIRECT);

        if (!log.contains("Test.java:1:44: compiler.err.not.def.access.package.cant.access: api.Api, api"))
            throw new Exception("expected output not found");

        //test add dependencies:
        tb.new JavacTask()
                .options("-XaddReads:m2=m1",
                         "-modulesourcepath", src.toString(),
                         "-processor", VerifyRequires.class.getName())
                .outdir(classes)
                .files(findJavaFiles(src))
                .run()
                .writeAll();

        String decompiled = tb.new JavapTask()
                .options("-verbose", classes.resolve("m2").resolve("module-info.class").toString())
                .run()
                .getOutput(ToolBox.OutputKind.DIRECT);

        if (decompiled.contains("m1")) {
            throw new Exception("Incorrectly refers to m1 module.");
        }

        //cyclic dependencies OK when created through addReads:
        tb.new JavacTask()
                .options("-XaddReads:m2=m1,m1=m2",
                         "-modulesourcepath", src.toString())
                .outdir(classes)
                .files(findJavaFiles(src))
                .run()
                .writeAll();

        tb.writeJavaFiles(src_m2,
                          "module m2 { requires m1; }");

        tb.new JavacTask()
                .options("-XaddReads:m1=m2",
                         "-modulesourcepath", src.toString())
                .outdir(classes)
                .files(findJavaFiles(src))
                .run()
                .writeAll();
    }

    @SupportedAnnotationTypes("*")
    public static final class VerifyRequires extends AbstractProcessor {

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            ModuleElement m2Module = processingEnv.getElementUtils().getModuleElement("m2");
            if (m2Module == null) {
                throw new AssertionError("Cannot find the m2 module!");
            }
            boolean foundM1 = false;
            for (RequiresDirective rd : ElementFilter.requiresIn(m2Module.getDirectives())) {
                foundM1 |= rd.getDependency().getSimpleName().contentEquals("m1");
            }
            if (!foundM1) {
                throw new AssertionError("Cannot find the dependency on m1 module!");
            }
            return false;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latest();
        }

    }

    @Test
    void testAddReadsUnnamedModule(Path base) throws Exception {
        Path jar = prepareTestJar(base);

        Path moduleSrc = base.resolve("module-src");
        Path m1 = moduleSrc.resolve("m1");

        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        tb.writeJavaFiles(m1,
                          "module m1 { }",
                          "package impl; public class Impl { api.Api api; }");

        tb.new JavacTask()
          .options("-classpath", jar.toString(),
                   "-XaddReads:m1=ALL-UNNAMED",
                   "-XDrawDiagnostics")
          .outdir(classes)
          .files(findJavaFiles(moduleSrc))
          .run()
          .writeAll();
    }

    @Test
    void testAddReadsUnnamedModulePackageConflict(Path base) throws Exception {
        Path jar = prepareTestJar(base);

        Path moduleSrc = base.resolve("module-src");
        Path m1 = moduleSrc.resolve("m1");

        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        tb.writeJavaFiles(m1,
                          "module m1 { }",
                          "package api; public class Api { public static void test() { } }",
                          "package impl; public class Impl { { api.Api.test(); } }");

        tb.new JavacTask()
          .options("-classpath", jar.toString(),
                   "-modulesourcepath", moduleSrc.toString(),
                   "-XaddReads:m1=ALL-UNNAMED",
                   "-XDrawDiagnostics")
          .outdir(classes)
          .files(m1.resolve("impl").resolve("Impl.java"))
          .run()
          .writeAll();
    }

    @Test
    void testAddReadsUnnamedToJavaBase(Path base) throws Exception {
        Path jar = prepareTestJar(base);
        Path src = base.resolve("src");
        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        tb.writeJavaFiles(src,
                          "package impl; public class Impl { api.Api a; }");

        tb.new JavacTask()
          .options("-classpath", jar.toString(),
                   "-XaddReads:java.base=ALL-UNNAMED",
                   "-Xmodule:java.base")
          .outdir(classes)
          .files(src.resolve("impl").resolve("Impl.java"))
          .run()
          .writeAll();
    }

    @Test
    void testAddReadsToJavaBase(Path base) throws Exception {
        Path src = base.resolve("src");
        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        tb.writeJavaFiles(src,
                          "package impl; public class Impl { javax.swing.JButton b; }");

        tb.new JavacTask()
          .options("-XaddReads:java.base=java.desktop",
                   "-Xmodule:java.base")
          .outdir(classes)
          .files(findJavaFiles(src))
          .run()
          .writeAll();
    }

    private Path prepareTestJar(Path base) throws Exception {
        Path legacySrc = base.resolve("legacy-src");
        tb.writeJavaFiles(legacySrc,
                          "package api; public abstract class Api {}");
        Path legacyClasses = base.resolve("legacy-classes");
        Files.createDirectories(legacyClasses);

        String log = tb.new JavacTask()
                .options()
                .outdir(legacyClasses)
                .files(findJavaFiles(legacySrc))
                .run()
                .writeAll()
                .getOutput(ToolBox.OutputKind.DIRECT);

        if (!log.isEmpty()) {
            throw new Exception("unexpected output: " + log);
        }

        Path lib = base.resolve("lib");

        Files.createDirectories(lib);

        Path jar = lib.resolve("test-api-1.0.jar");

        tb.new JarTask(jar)
          .baseDir(legacyClasses)
          .files("api/Api.class")
          .run();

        return jar;
    }

    @Test
    void testX(Path base) throws Exception {
        Path src = base.resolve("src");
        Path src_m1 = src.resolve("m1");
        tb.writeJavaFiles(src_m1,
                          "module m1 { provides java.lang.Runnable with impl.Impl; }",
                          "package impl; public class Impl implements Runnable { public void run() { } }");
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        tb.new JavacTask()
                .options("-modulesourcepath", src.toString())
                .outdir(classes)
                .files(findJavaFiles(src))
                .run()
                .writeAll();

        Path unnamedSrc = base.resolve("unnamed-src");
        Path unnamedClasses = base.resolve("unnamed-classes");

        Files.createDirectories(unnamedClasses);

        tb.writeJavaFiles(unnamedSrc,
                          "package impl; public class Impl { }");

        tb.new JavacTask()
          .options("-XaddReads:m1=ALL-UNNAMED",
                   "-Xmodule:m1",
                   "-modulepath", classes.toString())
          .outdir(unnamedClasses)
          .files(findJavaFiles(unnamedSrc))
          .run()
          .writeAll();
    }
}
