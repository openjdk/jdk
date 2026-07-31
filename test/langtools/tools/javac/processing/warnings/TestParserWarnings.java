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
 * @bug 8381654
 * @summary AP interference with tokenizer warnings
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run junit ${test.main.class}
 */

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class TestParserWarnings {

    static boolean[] apOptions() {
        return new boolean[] {false, true};
    }

    final ToolBox tb = new ToolBox();
    Path base, src, classes;

    @ParameterizedTest @MethodSource("apOptions")
    public void testPreviewWarning(boolean useProcessor) throws Exception {
        tb.writeJavaFiles(src, """
                        public record MyRec() {}
                        """);

        JavacTask task = new JavacTask(tb)
                .options("--enable-preview",
                         "-source", Integer.toString(Runtime.version().feature()),
                         "-XDforcePreview",
                         "-XDrawDiagnostics")
                .files(tb.findJavaFiles(src))
                .outdir(classes);
        if (useProcessor) {
            task.processors(new ProcessorImpl());
        }
        List<String> log = task
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expected = List.of(
                "- compiler.note.preview.filename: MyRec.java, DEFAULT",
                "- compiler.note.preview.recompile"
        );

        tb.checkEqual(expected, log);
    }

    @ParameterizedTest @MethodSource("apOptions")
    public void testTextBlockWarning(boolean useProcessor) throws Exception {
        tb.writeJavaFiles(src, """
                class TextBlockWhitespace {
                    String m() {
                        return ""\"
                \\u0009\\u0009\\u0009\\u0009tab indentation
                \\u0020\\u0020\\u0020\\u0020space indentation and trailing space\\u0020
                \\u0020\\u0020\\u0020\\u0020""\";
                    }
                }
                """);

        JavacTask task = new JavacTask(tb)
                .options("-Xlint:text-blocks",
                         "-XDrawDiagnostics")
                .files(tb.findJavaFiles(src))
                .outdir(classes);
        if (useProcessor) {
            task.processors(new ProcessorImpl());
        }
        List<String> log = task
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expected = List.of(
                "TextBlockWhitespace.java:3:16: compiler.warn.inconsistent.white.space.indentation",
                "TextBlockWhitespace.java:3:16: compiler.warn.trailing.white.space.will.be.removed",
                "2 warnings"
        );

        tb.checkEqual(expected, log);
    }

    @Test
    public void testAPGeneratedSource() throws Exception {
        tb.writeJavaFiles(src, """
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Target;

                @A
                class Test {}

                @Target(ElementType.TYPE)
                @interface A {}
                """);

        List<String> log = new JavacTask(tb)
                .options("-Xlint:text-blocks",
                         "-XDrawDiagnostics")
                .files(tb.findJavaFiles(src))
                .outdir(classes)
                .processors(new ProcessorImpl())
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expected = List.of(
                "Generated.java:3:16: compiler.warn.inconsistent.white.space.indentation",
                "Generated.java:3:16: compiler.warn.trailing.white.space.will.be.removed",
                "2 warnings"
        );

        tb.checkEqual(expected, log);
    }

    @SupportedAnnotationTypes("*")
    private static class ProcessorImpl extends AbstractProcessor {
        private boolean done = false;
        private Filer filer;
        private Messager msgr;

        @Override
        public void init(ProcessingEnvironment env) {
            filer = env.getFiler();
            msgr = env.getMessager();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (!done && !annotations.isEmpty()) {
                try (Writer pw = filer.createSourceFile("Generated").openWriter()) {
                    pw.write("""
                             public class Generated {
                                 String m() {
                                     return ""\"
                             \\u0009\\u0009\\u0009\\u0009tab indentation
                             \\u0020\\u0020\\u0020\\u0020space indentation and trailing space\\u0020
                             \\u0020\\u0020\\u0020\\u0020""\";
                                 }
                             }
                             """);
                    pw.flush();
                    pw.close();
                    done = true;
                } catch (IOException ioe) {
                    msgr.printError(ioe.getMessage());
                    return false;
                }
                return true;
            }
            return false;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latest();
        }
    }

    @BeforeEach
    public void setUp(TestInfo info) throws Exception {
        base = Path.of(".").resolve(info.getTestMethod().get().getName());
        if (Files.exists(base)) {
            tb.cleanDirectory(base);
        }
        src = base.resolve("src");
        classes = base.resolve("classes");
        Files.createDirectories(classes);
    }
}
