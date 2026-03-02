/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8378740
 * @summary Verify warnings are properly suppress in the combination of
 *          annotation processing and implicit compilation
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run junit APImplicitClassesWarnings
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class APImplicitClassesWarnings {

    ToolBox tb = new ToolBox();
    Path base;

    @Test
    public void testCorrectSource() throws Exception {
        Path src = base.resolve("src");
        Path classes = base.resolve("classes");
        tb.writeJavaFiles(src,
                          """
                          package test;

                          @Deprecated(forRemoval=true)
                          public class Depr {
                          }
                          """,
                          """
                          package test;
                          public class Use {
                              Implicit implicit;
                              Depr depr;
                          }
                          """,
                          """
                          package test;
                          public interface Implicit {}
                          """);
        Files.createDirectories(classes);

        List<String> log = new JavacTask(tb)
            .options("-d", classes.toString(),
                     "-XDrawDiagnostics",
                     "-implicit:class",
                     "-sourcepath", src.toString())
            .files(src.resolve("test").resolve("Depr.java"),
                    src.resolve("test").resolve("Use.java"))
            .processors(new ProcessorImpl())
            .run()
            .writeAll()
            .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expected = List.of(
            "Use.java:4:5: compiler.warn.has.been.deprecated.for.removal: test.Depr, test",
            "Use.java:4:5: compiler.warn.has.been.deprecated.for.removal: test.Depr, test",
            "Use.java:4:5: compiler.warn.has.been.deprecated.for.removal: test.Depr, test",
            "3 warnings"
        );

        tb.checkEqual(expected, log);
    }

    @Test
    public void testCorrectSuppress() throws Exception {
        Path src = base.resolve("src");
        Path classes = base.resolve("classes");
        tb.writeJavaFiles(src,
                          """
                          package test;

                          @Deprecated(forRemoval=true)
                          public class Depr {
                          }
                          """,
                          """
                          package test;
                          public class Use {
                              Implicit implicit;
                              @SuppressWarnings("removal")
                              Depr depr;
                          }
                          """,
                          """
                          package test;
                          public interface Implicit {}
                          """);
        Files.createDirectories(classes);

        new JavacTask(tb)
                .options("-d", classes.toString(),
                         "-Werror",
                         "-implicit:class",
                         "-sourcepath", src.toString())
                .files(src.resolve("test").resolve("Depr.java"),
                       src.resolve("test").resolve("Use.java"))
                .processors(new ProcessorImpl())
                .run()
                .writeAll();
    }

    @SupportedAnnotationTypes("*")
    private static class ProcessorImpl extends AbstractProcessor {
        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            return false;
        }
        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latest();
        }
    }

    @BeforeEach
    public void setUp(TestInfo info) {
        base = Paths.get(".")
                    .resolve(info.getTestMethod().orElseThrow().getName());
    }
}
