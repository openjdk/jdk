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

/*
 * @test
 * @bug 8272234
 * @summary Verify proper handling of originating elements in javac's Filer.
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
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import javax.annotation.processing.Filer;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.Element;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.util.Elements;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardLocation;
import toolbox.JavacTask;
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
            JavaFileManager fm = new ForwardingJavaFileManager<JavaFileManager>(sjfm) {
                @Override
                public JavaFileObject getJavaFileForOutputForOriginatingFiles(Location location,
                                                                              String className,
                                                                              JavaFileObject.Kind kind,
                                                                              FileObject... originatingFiles) throws IOException {
                    List.of(originatingFiles)
                        .stream()
                        .map(fo -> getInfo(fo))
                        .forEach(testOutput::add);
                    return super.getJavaFileForOutputForOriginatingFiles(location, className, kind, originatingFiles);
                }
                @Override
                public FileObject getFileForOutputForOriginatingFiles(Location location,
                                                                      String packageName,
                                                                      String relativeName,
                                                                      FileObject... originatingFiles) throws IOException {
                    List.of(originatingFiles)
                        .stream()
                        .map(fo -> getInfo(fo))
                        .forEach(testOutput::add);
                    return super.getFileForOutputForOriginatingFiles(location, packageName, relativeName, originatingFiles);
                }
                private String getInfo(FileObject fo) {
                    try {
                        JavaFileObject jfo = (JavaFileObject) fo; //the test only expects JavaFileObjects here:
                        JavaFileManager.Location location = jfo.getKind() == JavaFileObject.Kind.SOURCE
                                ? StandardLocation.SOURCE_PATH
                                : sjfm.getLocationForModule(StandardLocation.SYSTEM_MODULES, "java.base");
                        String binaryName = inferBinaryName(location, jfo);
                        return binaryName + "(" + jfo.getKind() + ")";
                    } catch (IOException ex) {
                        throw new AssertionError(ex);
                    }
                }
            };
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
                                               "-processor", "TestOriginatingElements$P",
                                               "-processorpath", System.getProperty("test.class.path"),
                                               "--module-path", libClasses.toString(),
                                               "--add-modules", "lib",
                                               "-d", classes.toString(),
                                               "-AgeneratedData=" + generatedData);
                ToolProvider.getSystemJavaCompiler()
                            .getTask(null, fm, null, options, null, sjfm.getJavaFileObjects(tb.findJavaFiles(src)))
                            .call();
                List<String> expectedOriginatingFiles = List.of("t.T1(SOURCE)",
                                                                "java.lang.String(CLASS)",
                                                                "p.package-info(SOURCE)",
                                                                "lib1.package-info(CLASS)",
                                                                "module-info(SOURCE)",
                                                                "module-info(CLASS)",
                                                                "t.T2(SOURCE)",
                                                                "java.lang.CharSequence(CLASS)",
                                                                "p.package-info(SOURCE)",
                                                                "lib1.package-info(CLASS)",
                                                                "module-info(SOURCE)",
                                                                "module-info(CLASS)",
                                                                "t.T3(SOURCE)",
                                                                "java.lang.Exception(CLASS)",
                                                                "p.package-info(SOURCE)",
                                                                "lib1.package-info(CLASS)",
                                                                "module-info(SOURCE)",
                                                                "module-info(CLASS)");
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
            if (round++ == 0) {
                Elements elems = processingEnv.getElementUtils();
                ModuleElement mdl = elems.getModuleElement("m");
                ModuleElement java_base = elems.getModuleElement("java.base");
                PackageElement pack = elems.getPackageElement("p");
                PackageElement lib1Pack = elems.getPackageElement("lib1");
                PackageElement lib2Pack = elems.getPackageElement("lib2");
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

    @Test
    public void testVacuousJavaFileManager(Path outerBase) throws Exception {
        List<String> log = new ArrayList<>();
        JavaFileObject expectedOut = new SimpleJavaFileObject(new URI("Out.java"), JavaFileObject.Kind.SOURCE) {};
        JavaFileManager fm = new MinimalJavaFileManager() {
            @Override
            public JavaFileObject getJavaFileForOutput(JavaFileManager.Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
                log.add("getJavaFileForOutput(" + location + ", " + className + ", " + kind + ", " + sibling);
                return expectedOut;
            }
            @Override
            public FileObject getFileForOutput(JavaFileManager.Location location, String packageName, String relativeName, FileObject sibling) throws IOException {
                log.add("getFileForOutput(" + location + ", " + packageName + ", " + relativeName  + ", " + sibling);
                return expectedOut;
            }
        };

        FileObject fo1 = new SimpleJavaFileObject(new URI("Test1.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public String toString() {
                return "Test1 - FO";
            }
        };
        FileObject fo2 = new SimpleJavaFileObject(new URI("Test2.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public String toString() {
                return "Test2 - FO";
            }
        };

        assertEquals(expectedOut,
                     fm.getJavaFileForOutputForOriginatingFiles(StandardLocation.CLASS_OUTPUT, "test.Test", JavaFileObject.Kind.SOURCE, fo1, fo2));
        assertEquals(List.of("getJavaFileForOutput(CLASS_OUTPUT, test.Test, SOURCE, Test1 - FO"), log); log.clear();

        assertEquals(expectedOut,
                     fm.getJavaFileForOutputForOriginatingFiles(StandardLocation.CLASS_OUTPUT, "test.Test", JavaFileObject.Kind.SOURCE));
        assertEquals(List.of("getJavaFileForOutput(CLASS_OUTPUT, test.Test, SOURCE, null"), log); log.clear();

        assertEquals(expectedOut,
                     fm.getFileForOutputForOriginatingFiles(StandardLocation.CLASS_OUTPUT, "test", "Test.java", fo1, fo2));
        assertEquals(List.of("getFileForOutput(CLASS_OUTPUT, test, Test.java, Test1 - FO"), log); log.clear();
        assertEquals(expectedOut,
                     fm.getFileForOutputForOriginatingFiles(StandardLocation.CLASS_OUTPUT, "test", "Test.java"));
        assertEquals(List.of("getFileForOutput(CLASS_OUTPUT, test, Test.java, null"), log); log.clear();
    }

    @Test
    public void testForwardingJavaFileManager(Path outerBase) throws Exception {
        List<String> log = new ArrayList<>();
        JavaFileObject expectedOut = new SimpleJavaFileObject(new URI("Out.java"), JavaFileObject.Kind.SOURCE) {};

        FileObject fo1 = new SimpleJavaFileObject(new URI("Test1.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public String toString() {
                return "Test1 - FO";
            }
        };
        FileObject fo2 = new SimpleJavaFileObject(new URI("Test2.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public String toString() {
                return "Test2 - FO";
            }
        };

        JavaFileManager forwardingWithOverride = new ForwardingJavaFileManager<>(new MinimalJavaFileManager() {
            @Override
            public JavaFileObject getJavaFileForOutput(JavaFileManager.Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
                log.add("getJavaFileForOutput(" + location + ", " + className + ", " + kind + ", " + sibling);
                return expectedOut;
            }
            @Override
            public JavaFileObject getJavaFileForOutputForOriginatingFiles(JavaFileManager.Location location, String className, JavaFileObject.Kind kind, FileObject... originatingFiles) throws IOException {
                throw new AssertionError("Should not be called.");
            }
            @Override
            public FileObject getFileForOutput(JavaFileManager.Location location, String packageName, String relativeName, FileObject sibling) throws IOException {
                log.add("getFileForOutput(" + location + ", " + packageName + ", " + relativeName  + ", " + sibling);
                return expectedOut;
            }
            @Override
            public FileObject getFileForOutputForOriginatingFiles(JavaFileManager.Location location, String packageName, String relativeName, FileObject... originatingFiles) throws IOException {
                throw new AssertionError("Should not be called.");
            }
        }) {
            @Override
            public JavaFileObject getJavaFileForOutput(JavaFileManager.Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
                return super.getJavaFileForOutput(location, className, kind, sibling);
            }
            @Override
            public FileObject getFileForOutput(JavaFileManager.Location location, String packageName, String relativeName, FileObject sibling) throws IOException {
                return super.getFileForOutput(location, packageName, relativeName, sibling);
            }
        };

        assertEquals(expectedOut,
                     forwardingWithOverride.getJavaFileForOutputForOriginatingFiles(StandardLocation.CLASS_OUTPUT, "test.Test", JavaFileObject.Kind.SOURCE, fo1, fo2));
        assertEquals(List.of("getJavaFileForOutput(CLASS_OUTPUT, test.Test, SOURCE, Test1 - FO"), log); log.clear();

        assertEquals(expectedOut,
                     forwardingWithOverride.getJavaFileForOutputForOriginatingFiles(StandardLocation.CLASS_OUTPUT, "test.Test", JavaFileObject.Kind.SOURCE));
        assertEquals(List.of("getJavaFileForOutput(CLASS_OUTPUT, test.Test, SOURCE, null"), log); log.clear();

        assertEquals(expectedOut,
                     forwardingWithOverride.getFileForOutputForOriginatingFiles(StandardLocation.CLASS_OUTPUT, "test", "Test.java", fo1, fo2));
        assertEquals(List.of("getFileForOutput(CLASS_OUTPUT, test, Test.java, Test1 - FO"), log); log.clear();
        assertEquals(expectedOut,
                     forwardingWithOverride.getFileForOutputForOriginatingFiles(StandardLocation.CLASS_OUTPUT, "test", "Test.java"));
        assertEquals(List.of("getFileForOutput(CLASS_OUTPUT, test, Test.java, null"), log); log.clear();

        JavaFileManager forwardingWithOutOverride = new ForwardingJavaFileManager<>(new MinimalJavaFileManager() {
            @Override
            public JavaFileObject getJavaFileForOutput(JavaFileManager.Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
                throw new AssertionError("Should not be called.");
            }
            @Override
            public JavaFileObject getJavaFileForOutputForOriginatingFiles(JavaFileManager.Location location, String className, JavaFileObject.Kind kind, FileObject... originatingFiles) throws IOException {
                log.add("getJavaFileForOutputForOriginatingFiles(" + location + ", " + className + ", " + kind + ", " + List.of(originatingFiles));
                return expectedOut;
            }
            @Override
            public FileObject getFileForOutput(JavaFileManager.Location location, String packageName, String relativeName, FileObject sibling) throws IOException {
                throw new AssertionError("Should not be called.");
            }
            @Override
            public FileObject getFileForOutputForOriginatingFiles(JavaFileManager.Location location, String packageName, String relativeName, FileObject... originatingFiles) throws IOException {
                log.add("getFileForOutputForOriginatingFiles(" + location + ", " + packageName + ", " + relativeName  + ", " + List.of(originatingFiles));
                return expectedOut;
            }
        }) {};

        assertEquals(expectedOut,
                     forwardingWithOutOverride.getJavaFileForOutputForOriginatingFiles(StandardLocation.CLASS_OUTPUT, "test.Test", JavaFileObject.Kind.SOURCE, fo1, fo2));
        assertEquals(List.of("getJavaFileForOutputForOriginatingFiles(CLASS_OUTPUT, test.Test, SOURCE, [Test1 - FO, Test2 - FO]"), log); log.clear();

        assertEquals(expectedOut,
                     forwardingWithOutOverride.getJavaFileForOutputForOriginatingFiles(StandardLocation.CLASS_OUTPUT, "test.Test", JavaFileObject.Kind.SOURCE));
        assertEquals(List.of("getJavaFileForOutputForOriginatingFiles(CLASS_OUTPUT, test.Test, SOURCE, []"), log); log.clear();

        assertEquals(expectedOut,
                     forwardingWithOutOverride.getFileForOutputForOriginatingFiles(StandardLocation.CLASS_OUTPUT, "test", "Test.java", fo1, fo2));
        assertEquals(List.of("getFileForOutputForOriginatingFiles(CLASS_OUTPUT, test, Test.java, [Test1 - FO, Test2 - FO]"), log); log.clear();
        assertEquals(expectedOut,
                     forwardingWithOutOverride.getFileForOutputForOriginatingFiles(StandardLocation.CLASS_OUTPUT, "test", "Test.java"));
        assertEquals(List.of("getFileForOutputForOriginatingFiles(CLASS_OUTPUT, test, Test.java, []"), log); log.clear();
    }

    class MinimalJavaFileManager implements JavaFileManager {
            @Override
            public ClassLoader getClassLoader(JavaFileManager.Location location) {
                throw new UnsupportedOperationException("Not supported.");
            }
            @Override
            public Iterable<JavaFileObject> list(JavaFileManager.Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
                throw new UnsupportedOperationException("Not supported.");
            }
            @Override
            public String inferBinaryName(JavaFileManager.Location location, JavaFileObject file) {
                throw new UnsupportedOperationException("Not supported.");
            }
            @Override
            public boolean isSameFile(FileObject a, FileObject b) {
                throw new UnsupportedOperationException("Not supported.");
            }
            @Override
            public boolean handleOption(String current, Iterator<String> remaining) {
                throw new UnsupportedOperationException("Not supported.");
            }
            @Override
            public boolean hasLocation(JavaFileManager.Location location) {
                throw new UnsupportedOperationException("Not supported.");
            }
            @Override
            public JavaFileObject getJavaFileForInput(JavaFileManager.Location location, String className, JavaFileObject.Kind kind) throws IOException {
                throw new UnsupportedOperationException("Not supported.");
            }
            @Override
            public JavaFileObject getJavaFileForOutput(JavaFileManager.Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
                throw new UnsupportedOperationException("Not supported.");
            }
            @Override
            public FileObject getFileForInput(JavaFileManager.Location location, String packageName, String relativeName) throws IOException {
                throw new UnsupportedOperationException("Not supported.");
            }
            @Override
            public FileObject getFileForOutput(JavaFileManager.Location location, String packageName, String relativeName, FileObject sibling) throws IOException {
                throw new UnsupportedOperationException("Not supported.");
            }
            @Override
            public void flush() throws IOException {
                throw new UnsupportedOperationException("Not supported.");
            }
            @Override
            public void close() throws IOException {
                throw new UnsupportedOperationException("Not supported.");
            }
            @Override
            public int isSupportedOption(String option) {
                throw new UnsupportedOperationException("Not supported.");
            }
        };

    private void assertEquals(Object expected, Object actual) throws AssertionError {
        if (!expected.equals(actual)) {
            throw new AssertionError("Unexpected  output: " + actual + ", expected: " + expected);
        }
    }

}
