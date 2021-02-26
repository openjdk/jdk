/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 9999999
 * @summary XXX
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.TestRunner toolbox.ToolBox TestOriginatingElements
 * @run main TestOriginatingElements
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
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import java.io.IOException;
import java.util.ArrayList;
import javax.lang.model.element.Element;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import toolbox.JavacTask;
import toolbox.TestRunner;
import toolbox.TestRunner.Test;
import toolbox.ToolBox;

public class TestOriginatingElements extends TestRunner {

    public static void main(String... args) throws Exception {
        new TestOriginatingElements().runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    private final ToolBox tb = new ToolBox();
    private final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

    public TestOriginatingElements() {
        super(System.err);
    }

    @Test
    public void testOriginatingElements(Path outerBase) throws Exception {
        Path libSrc = outerBase.resolve("libsrc");
        tb.writeJavaFiles(libSrc,
                          "package lib;\n" +
                          "@lib2.Helper(1)\n" +
                          "public @interface Lib {\n" +
                          "}",
                          "package lib2;\n" +
                          "public @interface Helper {\n" +
                          "    public int value() default 0;\n" +
                          "}");
        Path libClasses = outerBase.resolve("libclasses");
        Files.createDirectories(libClasses);
        new JavacTask(tb)
                .outdir(libClasses.toString())
                .files(tb.findJavaFiles(libSrc))
                .run()
                .writeAll();
        Path src = outerBase.resolve("src");
        tb.writeJavaFiles(src,
                          "package t;\n" +
                          "import lib.Lib;\n" +
                          "public class T {\n" +
                          "}");
        Path classes = outerBase.resolve("classes");
        Files.createDirectories(classes);
        try (StandardJavaFileManager sjfm = compiler.getStandardFileManager(null, null, null)) {
            List<String> actualOriginatingFiles = new ArrayList<>();
            JavaFileManager fm = new ForwardingJavaFileManager<JavaFileManager>(sjfm) {
                @Override
                public JavaFileObject getJavaFileForOutput(JavaFileManager.Location location, String className, JavaFileObject.Kind kind, JavaFileObject... originatingFiles) throws IOException {
                    List.of(originatingFiles)
                        .stream()
                        .map(fo -> fo.getName())
                        .forEach(actualOriginatingFiles::add);
                    return super.getJavaFileForOutput(location, className, kind, originatingFiles);
                }
            };
            try {
                List<String> options = List.of("-processor", "TestOriginatingElements$P",
                                               "-processorpath", System.getProperty("test.classes"),
                                               "-classpath", libClasses.toString() + ":" + classes.toString(),
                                               "-d", classes.toString());
                ToolProvider.getSystemJavaCompiler()
                            .getTask(null, fm, null, options, null, sjfm.getJavaFileObjects(tb.findJavaFiles(src)))
                            .call();
                List<String> expectedOriginatingFiles = List.of("testOriginatingElements/src/t/T.java",
                                                                "/modules/java.base/java/lang/String.class");
                if (!expectedOriginatingFiles.equals(actualOriginatingFiles)) {
                    throw new AssertionError("Unexpected originatingElements: " + actualOriginatingFiles);
                }
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    @SupportedAnnotationTypes("*")
    public static class P extends AbstractProcessor {
        int round;
        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (round++ == 0) {
                try {
                    List<Element> originating = new ArrayList<>();
                    originating.addAll(roundEnv.getRootElements());
                    originating.add(processingEnv.getElementUtils().getTypeElement("java.lang.String"));
                    processingEnv.getFiler().createSourceFile("test.Generated", originating.toArray(s -> new Element[s])).openOutputStream().close();
                } catch (IOException ex) {
                    throw new AssertionError(ex);
                }
            }
            return false;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latest();
        }
    }

}
