/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8323057
 * @summary javac should print not-recoverable errors before recoverable errors
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.TestRunner toolbox.ToolBox TestErrorOrder
 * @run main TestErrorOrder
 */

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
import toolbox.Task.Expect;
import toolbox.TestRunner;
import toolbox.TestRunner.Test;
import toolbox.ToolBox;

public class TestErrorOrder extends TestRunner {
    public static void main(String... args) throws Exception {
        new TestErrorOrder().runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    private final ToolBox tb = new ToolBox();

    public TestErrorOrder() {
        super(System.err);
    }

    @Test
    public void testErrorsAfter(Path outerBase) throws Exception {
        Path src = outerBase.resolve("src");
        tb.writeJavaFiles(src,
                          "package t;\n" +
                          "public class T {\n" +
                          "    public void test(Undefined u) { }\n" +
                          "    \n" +
                          "    @SuppressWarnings(\"\")\n" +
                          "    @SuppressWarnings(\"\")\n" +
                          "    public void test() { }\n" +
                          "}");
        Path classes = outerBase.resolve("classes");
        Files.createDirectories(classes);
        List<String> actual = new JavacTask(tb)
                .outdir(classes.toString())
                .options("-XDrawDiagnostics",
                         "-processor", "TestErrorOrder$P",
                         "-processorpath", System.getProperty("test.classes"))
                .files(tb.findJavaFiles(src))
                .run(Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        List<String> expected = List.of(
                "T.java:6:5: compiler.err.duplicate.annotation.missing.container: java.lang.SuppressWarnings",
                "T.java:3:22: compiler.err.cant.resolve.location: kindname.class, Undefined, , , (compiler.misc.location: kindname.class, t.T, null)",
                "2 errors"
        );

        tb.checkEqual(expected, actual);
    }

    @SupportedAnnotationTypes("*")
    public static class P extends AbstractProcessor {
        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            return false;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latest();
        }
    }

}
