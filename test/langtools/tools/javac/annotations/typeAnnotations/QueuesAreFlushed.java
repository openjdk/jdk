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
 * @bug 8332230
 * @summary Verify that the last type in the last method has correct type annotations
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main QueuesAreFlushed
*/

import com.sun.source.tree.AnnotatedTypeTree;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskEvent.Kind;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeMirror;

import toolbox.TestRunner;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class QueuesAreFlushed extends TestRunner {

    private ToolBox tb;

    public static void main(String... args) throws Exception {
        new QueuesAreFlushed().runTests();
    }

    QueuesAreFlushed() {
        super(System.err);
        tb = new ToolBox();
    }

    public void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test
    public void testTypeAnnotations(Path base) throws Exception {
        Path current = base.resolve(".");
        Path src = current.resolve("src");
        Path classes = current.resolve("classes");
        tb.writeJavaFiles(src,
                          """
                          package test;
                          import java.lang.annotation.ElementType;
                          import java.lang.annotation.Target;

                          public class Test {
                              @Target(ElementType.TYPE_USE)
                              @interface Ann {}

                              public static void main(Object o) {
                                  boolean b;
                                  b = o instanceof @Ann String;
                              }

                          }
                          """);

        Files.createDirectories(classes);

        AtomicBoolean seenAnnotationMirror = new AtomicBoolean();

        new JavacTask(tb)
            .outdir(classes)
            .callback(task -> {
                task.addTaskListener(new TaskListener() {
                    @Override
                    public void finished(TaskEvent e) {
                        if (e.getKind() != Kind.ANALYZE) {
                            return ;
                        }
                        new TreePathScanner<Void, Void>() {
                            @Override
                            public Void visitAnnotatedType(AnnotatedTypeTree node, Void p) {
                                TypeMirror type = Trees.instance(task).getTypeMirror(getCurrentPath());
                                List<? extends AnnotationMirror> ams = type.getAnnotationMirrors();
                                if (ams.size() != 1) {
                                    throw new AssertionError("Expected a single annotation, but got: " + ams);
                                }
                                String expectedMirror = "@test.Test.Ann";
                                String actualMirror = ams.get(0).toString();
                                if (!Objects.equals(expectedMirror, actualMirror)) {
                                    throw new AssertionError("Expected: " + expectedMirror +
                                                             ", but got: " + actualMirror);
                                }

                                seenAnnotationMirror.set(true);

                                return super.visitAnnotatedType(node, p);
                            }
                        }.scan(e.getCompilationUnit(), null);
                    }
                });
            })
            .files(tb.findJavaFiles(src))
            .run(Task.Expect.SUCCESS)
            .writeAll();

        if (!seenAnnotationMirror.get()) {
            throw new AssertionError("Didn't see the AnnotatedTypeTree!");
        }
    }

}
