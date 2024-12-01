/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8191460
 * @summary crash in Annotate with duplicate declaration and annotation processing enabled
 * @library /tools/lib /tools/javac/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main CrashDuplicateAnnotationDeclarationTest
 */

import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

import toolbox.JavacTask;
import toolbox.Task;
import toolbox.Task.Mode;
import toolbox.Task.OutputKind;
import toolbox.TestRunner;
import toolbox.ToolBox;

public class CrashDuplicateAnnotationDeclarationTest extends TestRunner {
    protected ToolBox tb;

    CrashDuplicateAnnotationDeclarationTest() {
        super(System.err);
        tb = new ToolBox();
    }

    public static void main(String... args) throws Exception {
        new CrashDuplicateAnnotationDeclarationTest().runTests();
    }

    protected void runTests() throws Exception {
        runTests(m -> new Object[]{Paths.get(m.getName())});
    }

    Path[] findJavaFiles(Path... paths) throws IOException {
        return tb.findJavaFiles(paths);
    }

    @Test
    public void testDupAnnoDeclaration(Path base) throws Exception {
        Path src = base.resolve("src");
        Path pkg = src.resolve("pkg");
        Path y = pkg.resolve("Y.java");
        Path t = pkg.resolve("T.java");

        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        tb.writeJavaFiles(src,
                """
                package pkg;
                @SuppressWarnings("deprecation")
                class Y {
                    @interface A {}
                    @interface A {} // error: class A is already defined
                    T t;
                }
                """);

        tb.writeJavaFiles(src,
                """
                package pkg;
                @Deprecated class T {}
                """);

        // we need to compile T first
        new JavacTask(tb)
                .files(t)
                .outdir(classes)
                .run();

        List<String> expected = List.of(
                "Y.java:5:6: compiler.err.already.defined: kindname.class, pkg.Y.A, kindname.class, pkg.Y",
                "1 error");

        Path classDir = getClassDir();
        List<String> found = new JavacTask(tb)
                .classpath(classes, classDir)
                .options("-processor", SimpleProcessor.class.getName(),
                         "-XDrawDiagnostics")
                .files(y, t)
                .outdir(classes)
                .run(Task.Expect.FAIL, 1)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        checkOutputAcceptable(expected, found);
    }

    void checkOutputAcceptable(List<String> expected, List<String> found) {
        if (found.size() != expected.size()) {
            throw new AssertionError("Unexpected output: " + found);
        } else {
            for (int i = 0; i < expected.size(); i++) {
                if (!found.get(i).contains(expected.get(i))) {
                    throw new AssertionError("Unexpected output: " + found);
                }
            }
        }
    }

    public Path getClassDir() {
        String classes = ToolBox.testClasses;
        if (classes == null) {
            return Paths.get("build");
        } else {
            return Paths.get(classes);
        }
    }

    @SupportedAnnotationTypes("*")
    public static final class SimpleProcessor extends AbstractProcessor {
        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            return false;
        }
    }
}
