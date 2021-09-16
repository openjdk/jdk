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
 *          jdk.compiler/com.sun.tools.javac.processing
 *          jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.TestRunner toolbox.ToolBox TestFilerAsAService
 * @run main TestFilerAsAService
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
import java.util.Base64;
import javax.annotation.processing.Filer;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.Element;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.StandardLocation;

import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.processing.JavacFiler;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Context.Factory;
import java.util.*;

import toolbox.JavacTask;
import toolbox.TestRunner;
import toolbox.TestRunner.Test;
import toolbox.ToolBox;
import toolbox.ToolBox.MemoryFileManager;

public class TestFilerAsAService extends TestRunner {

    public static void main(String... args) throws Exception {
        new TestFilerAsAService().runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    private static final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    private final ToolBox tb = new ToolBox();

    public TestFilerAsAService() {
        super(System.err);
    }

    @Test
    public void testOriginatingElements(Path outerBase) throws Exception {
        Path libSrc = outerBase.resolve("lib-src");
        tb.writeJavaFiles(libSrc,
                          """
                          module lib { exports lib1; exports lib2; }
                          """,
                          """
                          package lib1;
                          public @interface A {
                          }
                          """,
                          """
                          package lib2;
                          public class Lib {
                          }
                          """);
        tb.writeFile(libSrc.resolve("lib1/package-info.java"), "@A package lib1;");
        Path libClasses = outerBase.resolve("lib-classes");
        Path libClassesModule = libClasses.resolve("lib");
        Files.createDirectories(libClassesModule);

        List<String> log = new ArrayList<>();

        new JavacTask(tb)
                .files(tb.findJavaFiles(libSrc))
                .outdir(libClassesModule)
                .run();

        Path src = outerBase.resolve("src");
        tb.writeJavaFiles(src,
                          """
                          module m {}
                          """,
                          """
                          package t;
                          public class T1 {
                          }
                          """,
                          """
                          package t;
                          public class T2 {
                          }
                          """,
                          """
                          package t;
                          public class T3 {
                          }
                          """);
        tb.writeFile(src.resolve("p/package-info.java"), "package p;");
        Path classes = outerBase.resolve("classes");
        Files.createDirectories(classes);
        try (StandardJavaFileManager sjfm = compiler.getStandardFileManager(null, null, null)) {
            List<String> testOutput = new ArrayList<>();
            try {
                String generatedData;
                try (MemoryFileManager mfm = new MemoryFileManager(sjfm)) {
                    compiler.getTask(null, mfm, null, null, null,
                                     List.of(new ToolBox.JavaSource("package test; public class Generated2 {}")))
                            .call();
                    generatedData =
                            Base64.getEncoder().encodeToString(mfm.getFileBytes(StandardLocation.CLASS_OUTPUT, "test.Generated2"));
                }
                List<String> options = List.of("-sourcepath", src.toString(),
                                               "-processor", "TestFilerAsAService$P",
                                               "-processorpath", System.getProperty("test.classes"),
                                               "--module-path", libClasses.toString(),
                                               "--add-modules", "lib",
                                               "-d", classes.toString(),
                                               "-AgeneratedData=" + generatedData);
                JavacTaskImpl task = (JavacTaskImpl) ToolProvider.getSystemJavaCompiler()
                        .getTask(null, null, null, options, null, sjfm.getJavaFileObjects(tb.findJavaFiles(src)));

                TestJavacFiler.preRegister(task.getContext(), testOutput);
                task.call();

                List<String> expectedOriginatingFiles = List.of("t.T1", "java.lang.String", "p", "lib1", "lib2", "m", "java.base",
                                                                "t.T2", "java.lang.CharSequence", "p", "lib1", "lib2", "m", "java.base",
                                                                "t.T3", "java.lang.Exception", "p", "lib1", "lib2", "m", "java.base");
                assertEquals(expectedOriginatingFiles, testOutput);
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    @SupportedAnnotationTypes("*")
    @SupportedOptions("generatedData")
    public static class P extends AbstractProcessor {
        int round;
        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            System.err.println("nazdar");
            if (round++ == 0) {
                ModuleElement mdl = processingEnv.getElementUtils().getModuleElement("m");
                ModuleElement java_base = processingEnv.getElementUtils().getModuleElement("java.base");
                PackageElement pack = processingEnv.getElementUtils().getPackageElement("p");
                PackageElement lib1Pack = processingEnv.getElementUtils().getPackageElement("lib1");
                PackageElement lib2Pack = processingEnv.getElementUtils().getPackageElement("lib2");
                Filer filer = processingEnv.getFiler();
                try {
                    filer.createSourceFile("test.Generated1",
                                           element("t.T1"),
                                           element("java.lang.String"),
                                           pack,
                                           lib1Pack,
                                           lib2Pack,
                                           mdl,
                                           java_base).openOutputStream().close();
                    try (OutputStream out = filer.createClassFile("test.Generated2",
                                                                  element("t.T2"),
                                                                  element("java.lang.CharSequence"),
                                                                  pack,
                                                                  lib1Pack,
                                                                  lib2Pack,
                                                                  mdl,
                                                                  java_base).openOutputStream()) {
                        out.write(Base64.getDecoder().decode(processingEnv.getOptions().get("generatedData")));
                    }
                    filer.createResource(StandardLocation.CLASS_OUTPUT,
                                         "test",
                                         "Generated3.txt",
                                         element("t.T3"),
                                         element("java.lang.Exception"),
                                         pack,
                                         lib1Pack,
                                         lib2Pack,
                                         mdl,
                                         java_base).openOutputStream().close();
                } catch (IOException ex) {
                    throw new AssertionError(ex);
                }
            }
            return false;
        }

        private Element element(String type) {
            return processingEnv.getElementUtils().getTypeElement(type);
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latest();
        }
    }

    public static class TestJavacFiler extends JavacFiler {


        protected static void preRegister(Context context, List<String> log) {
            context.put(filerKey, (Factory<JavacFiler>) c -> new TestJavacFiler(c, log));
        }

        private final List<String> log;

        public TestJavacFiler(Context c, List<String> log) {
            super(c);
            this.log = log;
        }

        @Override
        public JavaFileObject createSourceFile(CharSequence nameAndModule, Element... originatingElements) throws IOException {
            logOriginatingElements(originatingElements);
            return super.createSourceFile(nameAndModule, originatingElements);
        }

        @Override
        public JavaFileObject createClassFile(CharSequence nameAndModule, Element... originatingElements) throws IOException {
            logOriginatingElements(originatingElements);
            return super.createClassFile(nameAndModule, originatingElements);
        }

        @Override
        public FileObject createResource(JavaFileManager.Location location, CharSequence moduleAndPkg, CharSequence relativeName, Element... originatingElements) throws IOException {
            logOriginatingElements(originatingElements);
            return super.createResource(location, moduleAndPkg, relativeName, originatingElements);
        }

        private void logOriginatingElements(Element[] originatingElements) {
            Arrays.stream(originatingElements)
                    .map(e -> e.toString())
                    .forEach(log::add);
        }
        
    }
    private void assertEquals(Object expected, Object actual) throws AssertionError {
        if (!expected.equals(actual)) {
            throw new AssertionError("Unexpected  output: " + actual + ", expected: " + expected);
        }
    }

}
