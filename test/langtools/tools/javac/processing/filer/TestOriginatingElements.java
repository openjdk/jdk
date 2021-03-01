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
import java.io.OutputStream;
import java.util.ArrayList;
import javax.lang.model.element.Element;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.StandardLocation;
import toolbox.TestRunner;
import toolbox.TestRunner.Test;
import toolbox.ToolBox;
import toolbox.ToolBox.MemoryFileManager;

public class TestOriginatingElements extends TestRunner {

    public static void main(String... args) throws Exception {
        new TestOriginatingElements().runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    private static final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    private final ToolBox tb = new ToolBox();

    public TestOriginatingElements() {
        super(System.err);
    }

    @Test
    public void testOriginatingElements(Path outerBase) throws Exception {
        Path src = outerBase.resolve("src");
        tb.writeJavaFiles(src,
                          "package t;\n" +
                          "public class T1 {\n" +
                          "}",
                          "package t;\n" +
                          "public class T2 {\n" +
                          "}",
                          "package t;\n" +
                          "public class T3 {\n" +
                          "}");
        Path classes = outerBase.resolve("classes");
        Files.createDirectories(classes);
        try (StandardJavaFileManager sjfm = compiler.getStandardFileManager(null, null, null)) {
            List<String> testOutput = new ArrayList<>();
            JavaFileManager fm = new ForwardingJavaFileManager<JavaFileManager>(sjfm) {
                @Override
                public JavaFileObject getJavaFileForOutputForOriginatingFiles(JavaFileManager.Location location, String className, JavaFileObject.Kind kind, JavaFileObject... originatingFiles) throws IOException {
                    List.of(originatingFiles)
                        .stream()
                        .map(fo -> fo.getName())
                        .forEach(testOutput::add);
                    return super.getJavaFileForOutputForOriginatingFiles(location, className, kind, originatingFiles);
                }
                @Override
                public FileObject getFileForOutputForOriginatingFiles(JavaFileManager.Location location, String packageName, String relativeName, JavaFileObject... originatingFiles) throws IOException {
                    List.of(originatingFiles)
                        .stream()
                        .map(fo -> fo.getName())
                        .forEach(testOutput::add);
                    return super.getFileForOutputForOriginatingFiles(location, packageName, relativeName, originatingFiles);
                }
            };
            try {
                List<String> options = List.of("-processor", "TestOriginatingElements$P",
                                               "-processorpath", System.getProperty("test.classes"),
                                               "-d", classes.toString());
                ToolProvider.getSystemJavaCompiler()
                            .getTask(null, fm, null, options, null, sjfm.getJavaFileObjects(tb.findJavaFiles(src)))
                            .call();
                List<String> expectedOriginatingFiles = List.of("testOriginatingElements/src/t/T1.java",
                                                                "/modules/java.base/java/lang/String.class",
                                                                "testOriginatingElements/src/t/T2.java",
                                                                "/modules/java.base/java/lang/CharSequence.class",
                                                                "testOriginatingElements/src/t/T3.java",
                                                                "/modules/java.base/java/lang/Exception.class");
                if (!expectedOriginatingFiles.equals(testOutput)) {
                    throw new AssertionError("Unexpected originatingElements: " + testOutput);
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
                    processingEnv.getFiler().createSourceFile("test.Generated1", originatingElements("t.T1", "java.lang.String")).openOutputStream().close();
                    try (OutputStream out = processingEnv.getFiler().createClassFile("test.Generated2", originatingElements("t.T2", "java.lang.CharSequence")).openOutputStream();
                         StandardJavaFileManager sjfm = compiler.getStandardFileManager(null, null, null);
                         MemoryFileManager fm = new MemoryFileManager(sjfm)) {
                        ToolProvider.getSystemJavaCompiler()
                                    .getTask(null, fm, null, null, null, List.of(new ToolBox.JavaSource("package test; public class Generated2 {}")))
                                    .call();
                        out.write(fm.getFileBytes(StandardLocation.CLASS_OUTPUT, "test.Generated2"));
                    }
                    processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "test", "Generated3.txt", originatingElements("t.T3", "java.lang.Exception")).openOutputStream().close();
                } catch (IOException ex) {
                    throw new AssertionError(ex);
                }
            }
            return false;
        }

        private Element[] originatingElements(String... types) {
            List<Element> originating = new ArrayList<>();

            for (String t : types) {
                originating.add(processingEnv.getElementUtils().getTypeElement(t));
            }

            return originating.toArray(s -> new Element[s]);
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latest();
        }
    }

}
