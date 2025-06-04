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
 * @bug 8305971
 * @summary NPE in JavacProcessingEnvironment for missing enum constructor body
 * @library /tools/lib /tools/javac/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main CrashEmptyEnumConstructorTest
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

public class CrashEmptyEnumConstructorTest extends TestRunner {
    protected ToolBox tb;

    CrashEmptyEnumConstructorTest() {
        super(System.err);
        tb = new ToolBox();
    }

    public static void main(String... args) throws Exception {
        new CrashEmptyEnumConstructorTest().runTests();
    }

    protected void runTests() throws Exception {
        runTests(m -> new Object[]{Paths.get(m.getName())});
    }

    Path[] findJavaFiles(Path... paths) throws IOException {
        return tb.findJavaFiles(paths);
    }

    @Test
    public void testEmptyEnumConstructor(Path base) throws Exception {
        Path src = base.resolve("src");
        Path r = src.resolve("E");

        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        tb.writeJavaFiles(r,
                """
                enum E {
                    ONE("");
                    E(String one);
                }
                """);

        List<String> expected = List.of(
            "E.java:3: error: missing method body, or declare abstract",
            "    E(String one);",
            "    ^",
            "1 error");

        List<String> log = new JavacTask(tb)
                .options("-processor", SimpleProcessor.class.getName())
                .files(findJavaFiles(src))
                .outdir(classes)
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        if (log.size() != expected.size()) {
            throw new AssertionError("Unexpected output: " + log);
        } else {
            for (int i = 0; i < expected.size(); i++) {
                if (!log.get(i).contains(expected.get(i))) {
                    throw new AssertionError("Unexpected output: " + log);
                }
            }
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
