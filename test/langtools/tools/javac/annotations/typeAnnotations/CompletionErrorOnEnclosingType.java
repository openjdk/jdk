/*
 * Copyright (c) 2024, Alphabet LLC. All rights reserved.
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
 * @bug 8337998 8370800
 * @summary CompletionFailure in getEnclosingType attaching type annotations
 * @library /tools/javac/lib /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 */

import toolbox.*;
import toolbox.Task.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;

public class CompletionErrorOnEnclosingType {
    ToolBox tb = new ToolBox();

    public static void main(String... args) throws Exception {
        CompletionErrorOnEnclosingType t = new CompletionErrorOnEnclosingType();
        t.testMissingEnclosingType();
        t.testAnnotationProcessing();
    }

    Path src;
    Path out;

    void setup() throws Exception {
        String annoSrc =
                """
                import static java.lang.annotation.ElementType.TYPE_USE;
                import java.lang.annotation.Target;
                @Target(TYPE_USE)
                @interface Anno {}

                class A<E> {}

                class B {
                  private @Anno A<String> a;
                  private @Anno A<String> f() { return null; };
                }
                """;
        String cSrc =
                """
                class C {
                  B b;
                }
                """;

        Path base = Paths.get(".");
        src = base.resolve("src");
        tb.createDirectories(src);
        tb.writeJavaFiles(src, annoSrc, cSrc);
        out = base.resolve("out");
        tb.createDirectories(out);
        new JavacTask(tb).outdir(out).files(tb.findJavaFiles(src)).run();
    }

    void testMissingEnclosingType() throws Exception {
        setup();

        // Missing references through fields and methods are not reported as errors,
        // because those symbols aren't completed here.
        tb.deleteFiles(out.resolve("A.class"));
        new JavacTask(tb)
                .outdir(out)
                .classpath(out)
                .options("-XDrawDiagnostics")
                .files(src.resolve("C.java"))
                .run(Expect.SUCCESS)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
    }

    @SupportedAnnotationTypes("*")
    public static class Processor extends AbstractProcessor {
        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        boolean first = true;

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (!first) {
                return false;
            }
            Element te = processingEnv.getElementUtils().getTypeElement("B");
            for (var f : ElementFilter.fieldsIn(te.getEnclosedElements())) {
                TypeMirror t = f.asType();
                processingEnv
                        .getMessager()
                        .printMessage(
                                Diagnostic.Kind.NOTE,
                                "%s (%s) has annotations [%s]"
                                        .formatted(f, t, t.getAnnotationMirrors()));
            }
            for (var m : ElementFilter.methodsIn(te.getEnclosedElements())) {
                TypeMirror t = m.getReturnType();
                processingEnv
                        .getMessager()
                        .printMessage(
                                Diagnostic.Kind.NOTE,
                                "%s (%s) has annotations [%s]"
                                        .formatted(m, t, t.getAnnotationMirrors()));
            }
            first = false;
            return false;
        }
    }

    void testAnnotationProcessing() throws Exception {
        setup();

        List<String> log =
                new JavacTask(tb)
                        .outdir(out)
                        .classpath(out)
                        .options("-XDrawDiagnostics")
                        .files(src.resolve("C.java"))
                        .processors(new Processor())
                        .run(Expect.SUCCESS)
                        .writeAll()
                        .getOutputLines(Task.OutputKind.DIRECT);

        var expectedOutput =
                List.of(
                        "- compiler.note.proc.messager: a (@Anno A<java.lang.String>) has"
                            + " annotations [@Anno]",
                        "- compiler.note.proc.messager: f() (@Anno A<java.lang.String>) has"
                            + " annotations [@Anno]");
        if (!expectedOutput.equals(log)) {
            throw new Exception("expected output not found: " + log);
        }

        // now if we remove A.class there will be an error and the annotations won't be available
        // but javac should not crash
        tb.deleteFiles(out.resolve("A.class"));

        log =
                new JavacTask(tb)
                        .outdir(out)
                        .classpath(out)
                        .options("-XDrawDiagnostics")
                        .files(src.resolve("C.java"))
                        .processors(new Processor())
                        .run(Expect.FAIL)
                        .writeAll()
                        .getOutputLines(Task.OutputKind.DIRECT);

        expectedOutput =
                List.of(
                        "B.class:-:-: compiler.err.cant.attach.type.annotations: @Anno, B, f,"
                            + " (compiler.misc.class.file.not.found: A)",
                        "- compiler.note.proc.messager: a (A<java.lang.String>) has annotations []",
                        "- compiler.note.proc.messager: f() (A<java.lang.String>) has annotations"
                            + " []",
                        "1 error");
        if (!expectedOutput.equals(log)) {
            throw new Exception("expected output not found: " + log);
        }
    }
}
