/*
 * Copyright (c) 2023, Alphabet LLC. All rights reserved.
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
 * @bug 8322040
 * @summary Missing array bounds check in ClassReader.parameter
 * @library /tools/lib /tools/javac/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @enablePreview
 * @run main BadMethodParameter
 */

import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.ClassFile;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.attribute.MethodParametersAttribute;

import toolbox.JavacTask;
import toolbox.Task;
import toolbox.TestRunner;
import toolbox.ToolBox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

public class BadMethodParameter extends TestRunner {

    protected ToolBox tb;

    BadMethodParameter() {
        super(System.err);
        tb = new ToolBox();
    }

    public static void main(String... args) throws Exception {
        new BadMethodParameter().runTests();
    }

    protected void runTests() throws Exception {
        runTests(m -> new Object[] {Paths.get(m.getName())});
    }

    @Test
    public void testAnnoOnConstructors(Path base) throws Exception {
        Path src = base.resolve("src");
        Path t = src.resolve("T.java");
        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        tb.writeJavaFiles(
                src,
                """
                class T {
                  public static void f(int x, int y) {
                  }
                }
                """);

        new JavacTask(tb).options("-parameters").files(t).outdir(classes).run();

        transform(classes.resolve("T.class"));

        Path classDir = getClassDir();
        new JavacTask(tb)
                .classpath(classes, classDir)
                .options("--enable-preview",
                         "-source", String.valueOf(Runtime.version().feature()),
                         "-verbose", "-parameters", "-processor", P.class.getName())
                .classes(P.class.getName())
                .outdir(classes)
                .run(Task.Expect.SUCCESS);
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
    public static final class P extends AbstractProcessor {
        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (processingEnv.getElementUtils().getTypeElement("T") == null) {
                throw new AssertionError("could not load T");
            }
            return false;
        }
    }

    private static void transform(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        ClassFile cf = ClassFile.of();
        ClassModel classModel = cf.parse(bytes);
        MethodTransform methodTransform =
                (mb, me) -> {
                    if (me instanceof MethodParametersAttribute mp) {
                        // create a MethodParameters attribute with the wrong number of entries
                        mb.with(
                                MethodParametersAttribute.of(
                                        mp.parameters().subList(0, mp.parameters().size() - 1)));
                    } else {
                        mb.with(me);
                    }
                };

        ClassTransform classTransform = ClassTransform.transformingMethods(methodTransform);
        bytes = cf.transformClass(classModel, classTransform);
        Files.write(path, bytes);
    }
}
