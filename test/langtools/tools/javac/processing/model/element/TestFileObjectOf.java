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
 * @bug 8224922
 * @summary Verify the behavior of the Elements.getFileObjectOf
 * @library /tools/lib
 * @enablePreview
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          java.base/jdk.internal.classfile.impl
 * @build toolbox.ToolBox toolbox.JavacTask toolbox.TestRunner
 * @build TestFileObjectOf
 * @run main TestFileObjectOf
 */

import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.Elements;

import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import javax.tools.JavaFileObject;
import toolbox.JarTask;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.TestRunner;
import toolbox.ToolBox;

public class TestFileObjectOf extends TestRunner {

    private final ToolBox tb;

    TestFileObjectOf() {
        super(System.err);
        tb = new ToolBox();
    }

    public static void main(String... args) throws Exception {
        new TestFileObjectOf().runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test
    public void testSourceFiles(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                          """
                          module m {}
                          """,
                          """
                          package test;
                          """,
                          """
                          package test;
                          public class TestClass {
                              int fieldTestClass;
                              TestClass() { }
                              void methodTestClass(int parameterTestClass) {
                                  int localTestClass;
                              }
                              public static class InnerClass {
                                  int fieldInnerClass;
                                  InnerClass() {}
                                  void methodInnerClass(int parameterInnerClass) {
                                      int localInnerClass;
                                  }
                              }
                          }
                          """,
                          """
                          package test;
                          public enum TestEnum {
                              CONSTANT;
                          }
                          """,
                          """
                          package test2;
                          public class TestClass2 {}
                          """);
        Path classes = base.resolve("classes").resolve("m");
        tb.createDirectories(classes);

        //from source, implicit:
        {
            String moduleInfoSource = src.resolve("module-info.java").toUri().toString();
            String packageInfoSource = src.resolve("test").resolve("package-info.java").toUri().toString();
            String testClassSource = src.resolve("test").resolve("TestClass.java").toUri().toString();
            String testEnumSource = src.resolve("test").resolve("TestEnum.java").toUri().toString();
            String testClass2Source = src.resolve("test2").resolve("TestClass2.java").toUri().toString();

            List<String> log;

            log = new JavacTask(tb)
                .options("-Xpkginfo:always",
                         "-processorpath", System.getProperty("test.classes"),
                         "-processor", PrintFiles.class.getName(),
                         "-sourcepath", src.toString())
                .outdir(classes)
                .classes("java.lang.Object")
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.STDOUT);

            List<String> expected = List.of(
                    "m: " + moduleInfoSource,
                    "test: " + packageInfoSource,
                    "test2: " + "<null>",
                    "TestClass: " + testClassSource,
                    "TestEnum: " + testEnumSource,
                    "TestClass2: " + testClass2Source,
                    "<init>: " + testClassSource,
                    "InnerClass: " + testClassSource,
                    "fieldTestClass: " + testClassSource,
                    "methodTestClass: " + testClassSource,
                    "parameterTestClass: " + testClassSource,
                    "localTestClass: " + testClassSource,
                    "<init>: " + testEnumSource,
                    "CONSTANT: " + testEnumSource,
                    "valueOf: " + testEnumSource,
                    "values: " + testEnumSource,
                    "<init>: " + testClass2Source,
                    "<init>: " + testClassSource,
                    "fieldInnerClass: " + testClassSource,
                    "methodInnerClass: " + testClassSource,
                    "parameterInnerClass: " + testClassSource,
                    "localInnerClass: " + testClassSource
            );

            if (!expected.equals(log))
                throw new AssertionError("expected output not found: " + log);
        }

        tb.cleanDirectory(classes);

        //from source, explicit:
        {
            String moduleInfoSource = src.resolve("module-info.java").toUri().toString();
            String packageInfoSource = src.resolve("test").resolve("package-info.java").toUri().toString();
            String testClassSource = src.resolve("test").resolve("TestClass.java").toUri().toString();
            String testEnumSource = src.resolve("test").resolve("TestEnum.java").toUri().toString();
            String testClass2Source = src.resolve("test2").resolve("TestClass2.java").toUri().toString();

            List<String> log;

            log = new JavacTask(tb)
                .options("-Xpkginfo:always",
                         "-processorpath", System.getProperty("test.classes"),
                         "-processor", PrintFiles.class.getName())
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.STDOUT);

            List<String> expected = List.of(
                    "m: " + moduleInfoSource,
                    "test: " + packageInfoSource,
                    "test2: " + "<null>",
                    "TestClass: " + testClassSource,
                    "TestEnum: " + testEnumSource,
                    "TestClass2: " + testClass2Source,
                    "<init>: " + testClassSource,
                    "InnerClass: " + testClassSource,
                    "fieldTestClass: " + testClassSource,
                    "methodTestClass: " + testClassSource,
                    "parameterTestClass: " + testClassSource,
                    "localTestClass: " + testClassSource,
                    "<init>: " + testEnumSource,
                    "CONSTANT: " + testEnumSource,
                    "valueOf: " + testEnumSource,
                    "values: " + testEnumSource,
                    "<init>: " + testClass2Source,
                    "<init>: " + testClassSource,
                    "fieldInnerClass: " + testClassSource,
                    "methodInnerClass: " + testClassSource,
                    "parameterInnerClass: " + testClassSource,
                    "localInnerClass: " + testClassSource
            );

            if (!expected.equals(log))
                throw new AssertionError("expected output not found: " + log);
        }

        //from class:
        {
            String moduleInfoSource = classes.resolve("module-info.class").toUri().toString();
            String packageInfoSource = classes.resolve("test").resolve("package-info.class").toUri().toString();
            String testClassSource = classes.resolve("test").resolve("TestClass.class").toUri().toString();
            String testInnerClassSource = classes.resolve("test").resolve("TestClass$InnerClass.class").toUri().toString();
            String testEnumSource = classes.resolve("test").resolve("TestEnum.class").toUri().toString();
            String testClass2Source = classes.resolve("test2").resolve("TestClass2.class").toUri().toString();

            List<String> log;

            log = new JavacTask(tb)
                .options("-processorpath", System.getProperty("test.classes"),
                         "-processor", PrintFiles.class.getName(),
                         "--module-path", classes.toString(),
                         "--add-modules", "m")
                .outdir(classes)
                .classes("java.lang.Object")
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.STDOUT);

            List<String> expected = List.of(
                    "m: " + moduleInfoSource,
                    "test: " + packageInfoSource,
                    "test2: " + "<null>",
                    "TestClass: " + testClassSource,
                    "TestEnum: " + testEnumSource,
                    "TestClass2: " + testClass2Source,
                    "<init>: " + testClassSource,
                    "InnerClass: " + testInnerClassSource,
                    "fieldTestClass: " + testClassSource,
                    "methodTestClass: " + testClassSource,
                    "<clinit>: " + testEnumSource,
                    "<init>: " + testEnumSource,
                    "CONSTANT: " + testEnumSource,
                    "valueOf: " + testEnumSource,
                    "values: " + testEnumSource,
                    "<init>: " + testClass2Source,
                    "<init>: " + testInnerClassSource,
                    "fieldInnerClass: " + testInnerClassSource,
                    "methodInnerClass: " + testInnerClassSource
            );

            if (!expected.equals(log))
                throw new AssertionError("expected output not found: " + log);
        }
    }

    @SupportedAnnotationTypes("*")
    @SupportedOptions("fromClass")
    public static final class PrintFiles extends AbstractProcessor {

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (!roundEnv.processingOver())
                return false;

            Elements elements = processingEnv.getElementUtils();
            Trees trees = Trees.instance(processingEnv);
            Queue<Element> q = new ArrayDeque<>();
            q.add(elements.getModuleElement("m"));

            while (!q.isEmpty()) {
                Element currentElement = q.remove();

                handleDeclaration(currentElement);

                switch (currentElement.getKind()) {
                    case METHOD -> {
                        ExecutableElement method = (ExecutableElement) currentElement;
                        TreePath tp = trees.getPath(method);
                        if (tp != null) {
                            new TreePathScanner<>() {
                                @Override
                                public Object visitVariable(VariableTree node, Object p) {
                                    Element el = trees.getElement(getCurrentPath());
                                    handleDeclaration(el);
                                    return super.visitVariable(node, p);
                                }
                            }.scan(tp, null);
                        }
                    }
                    case MODULE -> {
                        q.add(elements.getPackageElement("test"));
                        q.add(elements.getPackageElement("test2"));
                    }
                    default ->
                        currentElement.getEnclosedElements()
                                      .stream()
                                      .sorted((e1, e2) -> e1.getSimpleName().toString().compareTo(e2.getSimpleName().toString()))
                                      .forEach(q::add);
                }
            }

            return false;
        }

        void handleDeclaration(Element el) {
            Elements elements = processingEnv.getElementUtils();
            JavaFileObject fileObjects = elements.getFileObjectOf(el);
            System.out.println(el.getSimpleName() + ": " + (fileObjects != null ? fileObjects.toUri().toString() : "<null>"));
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

    }

    @Test
    public void testUnnamed(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                          """
                          public class TestClass {
                          }
                          """);
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        //from source, implicit:
        {
            String testClassSource = src.resolve("TestClass.java").toUri().toString();

            List<String> log;

            log = new JavacTask(tb)
                .options("-Xpkginfo:always",
                         "-classpath", "",
                         "-processorpath", System.getProperty("test.classes"),
                         "-processor", UnnamedPrintFiles.class.getName(),
                         "-sourcepath", src.toString())
                .outdir(classes)
                .classes("java.lang.Object")
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.STDOUT);

            List<String> expected = List.of(
                    ": " + "<null>",
                    ": " + "<null>",
                    "TestClass: " + testClassSource,
                    "<init>: " + testClassSource
            );

            if (!expected.equals(log))
                throw new AssertionError("expected output not found: " + log);
        }

        tb.cleanDirectory(classes);

        //from source, explicit:
        {
            String testClassSource = src.resolve("TestClass.java").toUri().toString();

            List<String> log;

            log = new JavacTask(tb)
                .options("-Xpkginfo:always",
                         "-classpath", "",
                         "-processorpath", System.getProperty("test.classes"),
                         "-processor", UnnamedPrintFiles.class.getName())
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.STDOUT);

            List<String> expected = List.of(
                    ": " + "<null>",
                    ": " + "<null>",
                    "TestClass: " + testClassSource,
                    "<init>: " + testClassSource
            );

            if (!expected.equals(log))
                throw new AssertionError("expected output not found: " + log);
        }

        //from class:
        {
            String testClassSource = classes.resolve("TestClass.class").toUri().toString();

            List<String> log;

            log = new JavacTask(tb)
                .options("-processorpath", System.getProperty("test.classes"),
                         "-processor", UnnamedPrintFiles.class.getName(),
                         "-classpath", classes.toString())
                .outdir(classes)
                .classes("java.lang.Object")
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.STDOUT);

            List<String> expected = List.of(
                    ": " + "<null>",
                    ": " + "<null>",
                    "TestClass: " + testClassSource,
                    "<init>: " + testClassSource
            );

            if (!expected.equals(log))
                throw new AssertionError("expected output not found: " + log);
        }
    }

    @SupportedAnnotationTypes("*")
    @SupportedOptions("fromClass")
    public static final class UnnamedPrintFiles extends AbstractProcessor {

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (!roundEnv.processingOver())
                return false;

            Elements elements = processingEnv.getElementUtils();
            Trees trees = Trees.instance(processingEnv);
            Queue<Element> q = new ArrayDeque<>();
            q.add(elements.getModuleElement(""));

            while (!q.isEmpty()) {
                Element currentElement = q.remove();

                handleDeclaration(currentElement);

                switch (currentElement.getKind()) {
                    case METHOD -> {
                        ExecutableElement method = (ExecutableElement) currentElement;
                        TreePath tp = trees.getPath(method);
                        if (tp != null) {
                            new TreePathScanner<>() {
                                @Override
                                public Object visitVariable(VariableTree node, Object p) {
                                    Element el = trees.getElement(getCurrentPath());
                                    handleDeclaration(el);
                                    return super.visitVariable(node, p);
                                }
                            }.scan(tp, null);
                        }
                    }
                    case MODULE -> {
                        q.add(elements.getPackageElement(""));
                    }
                    default ->
                        currentElement.getEnclosedElements()
                                      .stream()
                                      .sorted((e1, e2) -> e1.getSimpleName().toString().compareTo(e2.getSimpleName().toString()))
                                      .forEach(q::add);
                }
            }

            return false;
        }

        void handleDeclaration(Element el) {
            Elements elements = processingEnv.getElementUtils();
            JavaFileObject fileObjects = elements.getFileObjectOf(el);
            System.out.println(el.getSimpleName() + ": " + (fileObjects != null ? fileObjects.toUri().toString() : "<null>"));
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

    }

    @Test
    public void testAutomaticModule(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                          """
                          package test;
                          public class TestClass {
                          }
                          """);
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        Path module = base.resolve("m.jar");

        new JavacTask(tb)
            .options("-classpath", "")
            .outdir(classes)
            .files(tb.findJavaFiles(src))
            .run()
            .writeAll();
        new JarTask(tb, module)
            .baseDir(classes)
            .files(".")
            .run();

        String testClassSource = "jar:" + module.toUri().toString() + "!/test/TestClass.class";

        List<String> log;

        log = new JavacTask(tb)
            .options("-processorpath", System.getProperty("test.classes"),
                     "-processor", AutomaticModulePrintFiles.class.getName(),
                     "--module-path", module.toString(),
                     "--add-modules", "m")
            .outdir(classes)
            .classes("java.lang.Object")
            .run()
            .writeAll()
            .getOutputLines(Task.OutputKind.STDOUT);

        List<String> expected = List.of(
                "m: " + "<null>",
                "test: " + "<null>",
                "TestClass: " + testClassSource,
                "<init>: " + testClassSource
        );

        if (!expected.equals(log))
            throw new AssertionError("expected output not found: " + log);
    }

    @SupportedAnnotationTypes("*")
    @SupportedOptions("fromClass")
    public static final class AutomaticModulePrintFiles extends AbstractProcessor {

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (!roundEnv.processingOver())
                return false;

            Elements elements = processingEnv.getElementUtils();
            Trees trees = Trees.instance(processingEnv);
            Queue<Element> q = new ArrayDeque<>();
            q.add(elements.getModuleElement("m"));

            while (!q.isEmpty()) {
                Element currentElement = q.remove();

                handleDeclaration(currentElement);

                switch (currentElement.getKind()) {
                    case METHOD -> {
                        ExecutableElement method = (ExecutableElement) currentElement;
                        TreePath tp = trees.getPath(method);
                        if (tp != null) {
                            new TreePathScanner<>() {
                                @Override
                                public Object visitVariable(VariableTree node, Object p) {
                                    Element el = trees.getElement(getCurrentPath());
                                    handleDeclaration(el);
                                    return super.visitVariable(node, p);
                                }
                            }.scan(tp, null);
                        }
                    }
                    case MODULE -> {
                        q.add(elements.getPackageElement("test"));
                    }
                    default ->
                        currentElement.getEnclosedElements()
                                      .stream()
                                      .sorted((e1, e2) -> e1.getSimpleName().toString().compareTo(e2.getSimpleName().toString()))
                                      .forEach(q::add);
                }
            }

            return false;
        }

        void handleDeclaration(Element el) {
            Elements elements = processingEnv.getElementUtils();
            JavaFileObject fileObjects = elements.getFileObjectOf(el);
            System.out.println(el.getSimpleName() + ": " + (fileObjects != null ? fileObjects.toUri().toString() : "<null>"));
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

    }

}
