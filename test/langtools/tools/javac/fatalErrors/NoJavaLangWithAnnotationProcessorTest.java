/*
 * Copyright (c) 2023, Alphabet LLC. All rights reserved.
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
 * @bug 8309499
 * @summary Verify that java.lang unavailable error is not swallowed when
 *  annotation processor is used.
 * @library /tools/lib /tools/javac/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @build NoJavaLangWithAnnotationProcessorTest JavacTestingAbstractProcessor
 * @run main NoJavaLangWithAnnotationProcessorTest
 */

import java.nio.file.*;
import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;

import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class NoJavaLangWithAnnotationProcessorTest extends JavacTestingAbstractProcessor {

    private static final String noJavaLangSrc =
        "public class NoJavaLang {\n" +
        "    private String s;\n" +
        "}";

    private static final String compilerErrorMessage =
        "compiler.err.no.java.lang";

    // No-Op annotation processor
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return false;
    }

    public static void main(String[] args) throws Exception {
        new NoJavaLangWithAnnotationProcessorTest().run();
    }

    final ToolBox tb = new ToolBox();

    void run() throws Exception {
        testCompilesNormallyWithNonEmptyBootClassPath();
        testBootClassPath();
        testModulePath();
    }

    // Normal case with java.lang available
    void testCompilesNormallyWithNonEmptyBootClassPath() {
        new JavacTask(tb)
                .sources(noJavaLangSrc)
                .options("-processor", "NoJavaLangWithAnnotationProcessorTest")
                .run();
    }


    // test with bootclasspath, for as long as its around
    void testBootClassPath() {
        String[] bcpOpts = {"-XDrawDiagnostics", "-Xlint:-options", "-source", "8", "-target", "8",
            "-bootclasspath", ".", "-classpath", ".",
            "-processor", "NoJavaLangWithAnnotationProcessorTest", "-processorpath", System.getProperty("test.class.path") };
        test(bcpOpts, compilerErrorMessage);
    }

    // test with module path
    void testModulePath() throws Exception {
        // need to ensure there is an empty java.base to avoid different error message
        Files.createDirectories(Paths.get("modules/java.base"));
        new JavacTask(tb)
                .sources("module java.base { }",
                         "package java.lang; public class Object {}")
                .outdir("modules/java.base")
                .run();

        Files.delete(Paths.get("modules", "java.base", "java", "lang", "Object.class"));

        String[] mpOpts = {"-XDrawDiagnostics", "--system", "none", "--module-path", "modules",
            "-processor", "NoJavaLangWithAnnotationProcessorTest", "-processorpath", System.getProperty("test.class.path") };
        test(mpOpts, compilerErrorMessage);
    }

    private void test(String[] options, String expect) {
        System.err.println("Testing " + java.util.Arrays.toString(options));

        String out = new JavacTask(tb)
                .options(options)
                .sources(noJavaLangSrc)
                .run(Task.Expect.FAIL, 1)
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!out.contains(expect)) {
            throw new AssertionError("javac generated error output is not correct");
        }
    }

}
