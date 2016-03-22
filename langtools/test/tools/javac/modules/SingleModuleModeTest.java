/*
 * Copyright (c) 2015-2016, Oracle and/or its affiliates. All rights reserved.
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
 * @summary tests for single module mode compilation
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 *      jdk.jdeps/com.sun.tools.javap
 * @build ToolBox ModuleTestBase
 * @run main SingleModuleModeTest
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

public class SingleModuleModeTest extends ModuleTestBase{

    public static void main(String... args) throws Exception {
        new SingleModuleModeTest().run();
    }

    void run() throws Exception {
        tb = new ToolBox();

        runTests();
    }

    @Test
    void testTooManyModules(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src.resolve("m1"), "module m1 { }");
        tb.writeJavaFiles(src.resolve("m2"), "module m2 { }");

        String log = tb.new JavacTask()
                .options("-XDrawDiagnostics")
                .files(findJavaFiles(src))
                .run(ToolBox.Expect.FAIL)
                .writeAll()
                .getOutput(ToolBox.OutputKind.DIRECT);

        if (!log.contains("module-info.java:1:1: compiler.err.too.many.modules"))
            throw new Exception("expected output not found");
    }

    @Test
    void testImplicitModuleSource(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "module m { }",
                "class C { }");

        tb.new JavacTask()
                .classpath(src)
                .files(src.resolve("C.java"))
                .run()
                .writeAll();
    }

    @Test
    void testImplicitModuleClass(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "module m { }",
                "class C { }");
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);

        tb.new JavacTask()
                .outdir(classes)
                .files(src.resolve("module-info.java"))
                .run()
                .writeAll();

        tb.new JavacTask()
                .classpath(classes)
                .files(src.resolve("C.java"))
                .run()
                .writeAll();
    }

    @Test
    void testImplicitModuleClassAP(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "module m { uses java.lang.Runnable; }",
                "class C { }");
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);

        tb.new JavacTask()
                .outdir(classes)
                .files(src.resolve("module-info.java"))
                .run()
                .writeAll();

        tb.new JavacTask()
                .options("-processor", VerifyUsesProvides.class.getName(),
                         "-processorpath", System.getProperty("test.classes"))
                .outdir(classes)
                .classpath(classes)
                .files(src.resolve("C.java"))
                .run()
                .writeAll();
    }

    @Test
    void testImplicitModuleSourceAP(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "module m { uses java.lang.Runnable; }",
                "class C { }");
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);

        tb.new JavacTask()
                .options("-processor", VerifyUsesProvides.class.getName(),
                         "-processorpath", System.getProperty("test.classes"))
                .outdir(classes)
                .sourcepath(src)
                .classpath(classes)
                .files(src.resolve("C.java"))
                .run()
                .writeAll();
    }

    @SupportedAnnotationTypes("*")
    public static final class VerifyUsesProvides extends AbstractProcessor {

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (processingEnv.getElementUtils().getModuleElement("m") == null) {
                throw new AssertionError();
            }

            return false;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latest();
        }

    }
}
