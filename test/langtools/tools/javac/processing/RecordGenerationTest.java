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

/**
 * @test
 * @bug 8332297
 * @summary annotation processor that generates records sometimes fails due to NPE in javac
 * @library /tools/lib /tools/javac/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask toolbox.Task
 * @build RecordGenerationTest JavacTestingAbstractProcessor
 * @run main RecordGenerationTest
 */

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import javax.annotation.processing.FilerException;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedAnnotationTypes;

import javax.lang.model.element.TypeElement;
import javax.tools.StandardLocation;

import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class RecordGenerationTest {
    public static void main(String... args) throws Exception {
        new RecordGenerationTest().run();
    }

    Path[] findJavaFiles(Path... paths) throws Exception {
        return tb.findJavaFiles(paths);
    }

    ToolBox tb = new ToolBox();

    void run() throws Exception {
        Path allInOne = Paths.get("allInOne");
        if (Files.isDirectory(allInOne)) {
            tb.cleanDirectory(allInOne);
        }
        Files.deleteIfExists(allInOne);
        tb.createDirectories(allInOne);

        tb.writeJavaFiles(allInOne,
                """
                import java.io.IOException;
                import java.io.OutputStream;
                import java.io.Writer;
                import java.nio.file.Files;
                import java.nio.file.Path;
                import java.nio.file.Paths;
                import java.util.Set;

                import javax.annotation.processing.AbstractProcessor;
                import javax.annotation.processing.FilerException;
                import javax.annotation.processing.RoundEnvironment;
                import javax.annotation.processing.SupportedOptions;
                import javax.annotation.processing.SupportedAnnotationTypes;

                import javax.lang.model.element.TypeElement;
                import javax.tools.StandardLocation;

                @SupportedAnnotationTypes("*")
                public class AP extends AbstractProcessor {
                    @Override
                    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                        if (roundEnv.processingOver()) {
                            try (Writer w = processingEnv.getFiler().createSourceFile("ConfRecord").openWriter()) {
                                w.append("@RecordBuilder public record ConfRecord(int maxConcurrency) implements Conf {}");
                            } catch (IOException ex) {
                                throw new IllegalStateException(ex);
                            }
                        }
                        return true;
                    }
                }
                """
        );

        new JavacTask(tb).options("-d", allInOne.toString())
                .files(findJavaFiles(allInOne))
                .run()
                .writeAll();

        tb.writeJavaFiles(allInOne,
                """
                interface Conf {
                    int maxConcurrency( );
                }
                """,
                """
                import java.lang.annotation.*;
                public @interface RecordBuilder {
                }
                """
        );

        Path confSource = Paths.get(allInOne.toString(), "Conf.java");
        new JavacTask(tb).options("-processor", "AP",
                "-cp", allInOne.toString(),
                "-d", allInOne.toString())
                .files(confSource)
                .run()
                .writeAll();

        /* the bug reported at JDK-8332297 was reproducible only every other time this is why we reproduce
         * the same compilation command as above basically the second time the compiler is completing the
         * record symbol from the class file produced during the first compilation
         */
        new JavacTask(tb).options("-processor", "AP",
                "-cp", allInOne.toString(),
                "-d", allInOne.toString())
                .files(confSource)
                .run()
                .writeAll();
    }
}
