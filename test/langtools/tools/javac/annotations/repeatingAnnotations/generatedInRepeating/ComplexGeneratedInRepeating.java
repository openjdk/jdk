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
 * @bug 8378524
 * @summary Check that repeating annotations whose attributes are not-yet-generated classes and their members work.
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run junit ComplexGeneratedInRepeating
 */

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.TypeElement;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

public class ComplexGeneratedInRepeating {

    Path base;
    ToolBox tb = new ToolBox();

    @Test
    void testMember() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
                .options("-d", classes.toString())
                .sources("""
                         package test;

                         import java.lang.annotation.Repeatable;

                         @Rep(Constants.C)
                         @Rep(Constants.C)
                         public class Test {}

                         @Repeatable(Reps.class)
                         @interface Rep {
                            int value();
                         }
                         @interface Reps {
                            Rep[] value();
                         }
                         """)
                .processors(new ProcessorImpl())
                .run()
                .writeAll();
    }

    @Test
    void testUnresolvableMember() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        List<String> out = new JavacTask(tb)
                .options("-d", classes.toString(), "-XDrawDiagnostics", "-nowarn")
                .sources("""
                         package test;

                         import java.lang.annotation.Repeatable;

                         @Rep(Constants.C)
                         @Rep(Constants.Unknown)
                         public class Test {}

                         @Repeatable(Reps.class)
                         @interface Rep {
                            int value();
                         }
                         @interface Reps {
                            Rep[] value();
                         }
                         """)
                .processors(new ProcessorImpl())
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        tb.checkEqual(out, List.of(
                "Test.java:6:15: compiler.err.cant.resolve.location: kindname.variable, Unknown, , , (compiler.misc.location: kindname.class, test.Constants, null)",
                "1 error"));
    }

    @Test
    void testIncompatibleMember() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        List<String> out = new JavacTask(tb)
                .options("-d", classes.toString(), "-XDrawDiagnostics", "-nowarn")
                .sources("""
                         package test;

                         import java.lang.annotation.Repeatable;

                         @Rep(Constants.C)
                         @Rep(Constants.S)
                         public class Test {}

                         @Repeatable(Reps.class)
                         @interface Rep {
                            int value();
                         }
                         @interface Reps {
                            Rep[] value();
                         }
                         """)
                .processors(new ProcessorImpl())
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        tb.checkEqual(out, List.of(
                "Test.java:6:15: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: java.lang.String, int)",
                "1 error"));
    }

    @Test
    void testAnnotation() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
                .options("-d", classes.toString())
                .sources("""
                         package test;

                         import java.lang.annotation.Repeatable;

                         @Rep(@Ann(Constants.C))
                         @Rep(@Ann(Constants.C))
                         public class Test {}

                         @Repeatable(Reps.class)
                         @interface Rep {
                            Ann value();
                         }
                         @interface Reps {
                            Rep[] value();
                         }
                         """)
                .processors(new ProcessorImpl())
                .run()
                .writeAll();
    }

    @SupportedAnnotationTypes("*")
    private static class ProcessorImpl extends AbstractProcessor {

        int round = 0;

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (round++ == 0) {
                try (Writer w = processingEnv.getFiler().createSourceFile("test.Constants").openWriter()) {
                    w.append("""
                             package test;
                             public class Constants {
                                 public static final int C = 0;
                                 public static final String S = "";
                             }
                             """);
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
                try (Writer w = processingEnv.getFiler().createSourceFile("test.Ann").openWriter()) {
                    w.append("""
                             package test;
                             public @interface Ann {
                                int value();
                             }
                             """);
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            }
            return false;
        }
    }

    @BeforeEach
    public void setUp(TestInfo info) {
        base = Paths.get(".")
                    .resolve(info.getTestMethod()
                                 .orElseThrow()
                                 .getName());
    }
}
