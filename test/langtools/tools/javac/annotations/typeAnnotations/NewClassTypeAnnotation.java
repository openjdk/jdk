/*
 * Copyright (c) 2025, Google LLC. All rights reserved.
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
 * @bug 8351431
 * @summary Type annotations on new class creation expressions can't be retrieved
 * @library /tools/lib
 * @build toolbox.ToolBox toolbox.JavacTask
 * @modules jdk.compiler/com.sun.tools.javac.api jdk.compiler/com.sun.tools.javac.main
 * @run main NewClassTypeAnnotation
 */
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import toolbox.JavacTask;
import toolbox.Task;
import toolbox.TestRunner;
import toolbox.ToolBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.lang.model.type.TypeMirror;

public class NewClassTypeAnnotation extends TestRunner {

    private ToolBox tb;

    public static void main(String[] args) throws Exception {
        new NewClassTypeAnnotation().runTests();
    }

    NewClassTypeAnnotation() {
        super(System.err);
        tb = new ToolBox();
    }

    public void runTests() throws Exception {
        runTests(m -> new Object[] {Paths.get(m.getName())});
    }

    @Test
    public void testTypeAnnotations(Path base) throws Exception {
        Path current = base.resolve(".");
        Path src = current.resolve("src");
        Path classes = current.resolve("classes");
        tb.writeJavaFiles(
                src,
                """
                package test;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                class Test<T> {

                  @Target(ElementType.TYPE_USE)
                  @Retention(RetentionPolicy.RUNTIME)
                  @interface TypeAnnotation {}

                  public void testMethod() {
                    new Test<@TypeAnnotation String>();
                  }
                }
                """);

        Files.createDirectories(classes);

        AtomicBoolean seenAnnotationMirror = new AtomicBoolean();

        List<String> actual = new ArrayList<>();

        class Scanner extends TreePathScanner<Void, Void> {

            private final Trees trees;

            Scanner(Trees trees) {
                this.trees = trees;
            }

            @Override
            public Void visitNewClass(final NewClassTree node, final Void unused) {
                TypeMirror type = trees.getTypeMirror(getCurrentPath());
                System.err.println(">>> " + type);
                for (Tree t : getCurrentPath()) {
                    System.err.println(t);
                }
                actual.add(String.format("Expression: %s, Type: %s", node, type));
                return null;
            }
        }

        new JavacTask(tb)
                .outdir(classes)
                .callback(
                        task -> {
                            task.addTaskListener(
                                    new TaskListener() {
                                        @Override
                                        public void finished(TaskEvent e) {
                                            if (e.getKind() != TaskEvent.Kind.ANALYZE) {
                                                return;
                                            }
                                            System.err.println(e);
                                            new Scanner(Trees.instance(task))
                                                    .scan(e.getCompilationUnit(), null);
                                        }
                                    });
                        })
                .files(tb.findJavaFiles(src))
                .run(Task.Expect.SUCCESS)
                .writeAll();

        List<String> expected =
                List.of(
                        "Expression: new Test<@TypeAnnotation String>(), Type:"
                                + " test.Test<java.lang.@test.Test.TypeAnnotation String>");
        if (!expected.equals(actual)) {
            throw new AssertionError("expected: " + expected + ", actual: " + actual);
        }
    }
}
