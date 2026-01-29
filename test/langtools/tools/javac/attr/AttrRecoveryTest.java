/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8273039 8344706
 * @summary Verify error recovery in Attr
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main AttrRecoveryTest
*/

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ErroneousTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

import toolbox.TestRunner;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class AttrRecoveryTest extends TestRunner {

    ToolBox tb;

    public static void main(String... args) throws Exception {
        new AttrRecoveryTest().runTests();
    }

    AttrRecoveryTest() {
        super(System.err);
        tb = new ToolBox();
    }

    public void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test
    public void testModifiers(Path base) throws Exception {
        record TestCase(String name, String source, String expectedAnnotation, String... errors) {}
        TestCase[] tests = new TestCase[] {
            new TestCase("a",
                         """
                         public class Test {
                             Object i () { return int strictfp @Deprecated = 0; }
                         }
                         """,
                         "java.lang.Deprecated",
                         "Test.java:2:30: compiler.err.dot.class.expected",
                         "Test.java:2:51: compiler.err.class.method.or.field.expected",
                         "Test.java:2:26: compiler.err.unexpected.type: kindname.value, kindname.class",
                         "3 errors"),
            new TestCase("b",
                         """
                         public class Test {
                             Object i () { return int strictfp = 0; }
                         }
                         """,
                         null,
                         "Test.java:2:30: compiler.err.dot.class.expected",
                         "Test.java:2:39: compiler.err.class.method.or.field.expected",
                         "Test.java:2:26: compiler.err.unexpected.type: kindname.value, kindname.class",
                         "3 errors")
        };
        for (TestCase test : tests) {
            Path current = base.resolve("" + test.name);
            Path src = current.resolve("src");
            Path classes = current.resolve("classes");
            tb.writeJavaFiles(src,
                              test.source);

            Files.createDirectories(classes);

            var log =
                    new JavacTask(tb)
                        .options("-XDrawDiagnostics",
                                 "-XDshould-stop.at=FLOW",
                                 "-Xlint:-preview")
                        .outdir(classes)
                        .files(tb.findJavaFiles(src))
                        .callback(t -> {
                            t.addTaskListener(new TaskListener() {
                                CompilationUnitTree parsed;
                                @Override
                                public void finished(TaskEvent e) {
                                    switch (e.getKind()) {
                                        case PARSE -> parsed = e.getCompilationUnit();
                                        case ANALYZE ->
                                            checkAnnotationsValid(t, parsed, test.expectedAnnotation);
                                    }
                                }
                            });
                        })
                        .run(Task.Expect.FAIL, 1)
                        .writeAll()
                        .getOutputLines(Task.OutputKind.DIRECT);
            if (!List.of(test.errors).equals(log)) {
                throw new AssertionError("Incorrect errors, expected: " + List.of(test.errors) +
                                          ", actual: " + log);
            }
        }
    }

    private void checkAnnotationsValid(com.sun.source.util.JavacTask task,
                                       CompilationUnitTree cut,
                                       String expected) {
        boolean[] foundAnnotation = new boolean[1];
        Trees trees = Trees.instance(task);

        new TreePathScanner<Void, Void>() {
            @Override
            public Void visitAnnotation(AnnotationTree node, Void p) {
                TreePath typePath = new TreePath(getCurrentPath(), node.getAnnotationType());
                Element el = trees.getElement(typePath);
                if (el == null || !el.equals(task.getElements().getTypeElement(expected))) {
                    throw new AssertionError();
                }
                foundAnnotation[0] = true;
                return super.visitAnnotation(node, p);
            }

            @Override
            public Void visitErroneous(ErroneousTree node, Void p) {
                return scan(node.getErrorTrees(), p);
            }
        }.scan(cut, null);
        if (foundAnnotation[0] ^ (expected != null)) {
            throw new AssertionError();
        }
    }

    @Test
    public void testVarAssignment2Self(Path base) throws Exception {
        Path current = base;
        Path src = current.resolve("src");
        Path classes = current.resolve("classes");
        tb.writeJavaFiles(src,
                          """
                          public class Test {
                              void t() {
                                  var v = v;
                              }
                          }
                          """);

        Files.createDirectories(classes);

        AtomicInteger seenVariables = new AtomicInteger();
        TreePathScanner<Void, Trees> checkTypes = new TreePathScanner<>() {
            @Override
            public Void visitVariable(VariableTree node, Trees trees) {
                if (node.getName().contentEquals("v")) {
                    TypeMirror type = trees.getTypeMirror(getCurrentPath());
                    if (type == null) {
                        throw new AssertionError("Unexpected null type!");
                    }
                    seenVariables.incrementAndGet();
                }
                return super.visitVariable(node, trees);
            }
        };

        new JavacTask(tb)
            .options("-XDrawDiagnostics")
            .outdir(classes)
            .files(tb.findJavaFiles(src))
            .callback(t -> {
                t.addTaskListener(new TaskListener() {
                    CompilationUnitTree parsed;
                    @Override
                    public void finished(TaskEvent e) {
                        switch (e.getKind()) {
                            case PARSE -> parsed = e.getCompilationUnit();
                            case COMPILATION ->
                                checkTypes.scan(parsed, Trees.instance(t));
                        }
                    }
                });
            })
            .run(Task.Expect.FAIL)
            .writeAll()
            .getOutputLines(Task.OutputKind.DIRECT);

        if (seenVariables.get() != 1) {
            throw new AssertionError("Didn't see enough variables: " + seenVariables);
        }
    }
}
