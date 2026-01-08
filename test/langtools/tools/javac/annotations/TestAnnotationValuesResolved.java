/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8322706
 * @summary Verify that annotation values are de-proxies after loading from a classfile.
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.JavacTask toolbox.ToolBox toolbox.Task
 * @run main TestAnnotationValuesResolved
 */

import com.sun.source.tree.ClassTree;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskEvent.Kind;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.nio.file.Files;
import toolbox.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleAnnotationValueVisitorPreview;


public class TestAnnotationValuesResolved extends TestRunner {
    final toolbox.ToolBox tb = new ToolBox();

    public TestAnnotationValuesResolved() {
        super(System.err);
    }

    public static void main(String[] args) throws Exception {
        new TestAnnotationValuesResolved().runTests();
    }

    protected void runTests() throws Exception {
        runTests(m -> new Object[] { Path.of(m.getName()) });
    }

    @Test
    public void test(Path base) throws Exception {
        Path lib = Paths.get("lib");
        Path libSrc = lib.resolve("src");
        Path libClasses = lib.resolve("classes");

        tb.writeJavaFiles(libSrc,
                          """
                          package org.example;

                          public @interface MyFirstAnnotation {
                              MySecondAnnotation secondAnnotation() default @MySecondAnnotation;
                          }
                          """,
                          """
                          package org.example;

                          public @interface MySecondAnnotation {
                              String[] stringArray() default "";
                          }
                          """
        );
        Files.createDirectories(libClasses);
        new toolbox.JavacTask(tb)
                .outdir(libClasses)
                .files(tb.findJavaFiles(libSrc))
                .run();

        Path test = Paths.get("test");
        Path testSrc = test.resolve("src");
        Path testClasses = test.resolve("classes");
        tb.writeJavaFiles(testSrc,
                          """
                          package org.example;

                          @MyFirstAnnotation
                          public class AnnotatedClass {
                          }
                          """);
        Files.createDirectories(testClasses);
        new toolbox.JavacTask(tb)
                .classpath(libClasses)
                .outdir(testClasses)
                .files(tb.findJavaFiles(testSrc))
                .callback(task -> {
                    task.addTaskListener(new TaskListener() {
                        @Override
                        public void finished(TaskEvent e) {
                            if (e.getKind() == Kind.ENTER) {
                                new TreePathScanner<>() {
                                    @Override
                                    public Object visitClass(ClassTree node, Object p) {
                                        Trees trees = Trees.instance(task);
                                        Element el = trees.getElement(getCurrentPath());
                                        verifyAnnotationValuesResolved(task, el);
                                        return super.visitClass(node, p);
                                    }
                                }.scan(e.getCompilationUnit(), null);
                            }
                        }
                    });
                })
                .run()
                .writeAll();
    }

    private void verifyAnnotationValuesResolved(com.sun.source.util.JavacTask task,
                                                Element forElement) {
        Elements elements = task.getElements();

        class SearchAnnotationValues extends SimpleAnnotationValueVisitorPreview {
            @Override
            public Object visitAnnotation(AnnotationMirror a, Object p) {
                for (AnnotationValue av : elements.getElementValuesWithDefaults(a).values()) {
                    av.accept(this, null);
                }
                return super.visitAnnotation(a, p);
            }
        }

        for (AnnotationMirror mirror : forElement.getAnnotationMirrors()) {
            new SearchAnnotationValues().visitAnnotation(mirror, null);
        }
    }
}
