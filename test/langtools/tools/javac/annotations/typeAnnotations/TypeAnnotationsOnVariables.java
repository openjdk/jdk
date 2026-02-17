/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8371155
 * @summary Verify type annotations on local-like variables are propagated to
 *          their types at an appropriate time.
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main TypeAnnotationsOnVariables
 */

import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.UnionType;
import toolbox.JavacTask;
import toolbox.ToolBox;

public class TypeAnnotationsOnVariables {

    public static void main(String... args) throws Exception {
        new TypeAnnotationsOnVariables().run();
    }

    ToolBox tb = new ToolBox();

    void run() throws Exception {
        typeAnnotationInConstantExpressionFieldInit(Paths.get("."));
    }

    void typeAnnotationInConstantExpressionFieldInit(Path base) throws Exception {
        Path src = base.resolve("src");
        Path classes = base.resolve("classes");
        tb.writeJavaFiles(src,
                          """
                          import java.lang.annotation.ElementType;
                          import java.lang.annotation.Target;
                          import java.util.function.Supplier;

                          class Test {
                              @Target(ElementType.TYPE_USE)
                              @interface TypeAnno { }

                              @TypeAnno Supplier<String> r_f_i = () -> "r_f_i";
                              static @TypeAnno Supplier<String> r_f_s = () -> "r_f_s";

                              {
                                  @TypeAnno Supplier<String> r_init_i = () -> "r_init_i";
                              }

                              static {
                                  @TypeAnno Supplier<String> r_init_s = () -> "r_init_s";
                              }

                              void m() {
                                  @TypeAnno Supplier<String> r_m_i = () -> "r_m_i";
                              }

                              static void g() {
                                  @TypeAnno Supplier<String> r_g_s = () -> "r_g_s";
                              }

                              void h() {
                                  t_cr(() -> "t_cr");
                              }

                              void i() {
                                  t_no_cr((@TypeAnno Supplier<String>)() -> "t_no_cr");
                              }

                              void j() {
                                  t_no_cr((java.io.Serializable & @TypeAnno Supplier<String>)() -> "t_no_cr");
                              }

                              void k() throws Throwable {
                                  try (@TypeAnno AutoCloseable ac = () -> {}) {}
                              }

                              void l() {
                                  try {
                                  } catch (@TypeAnno Exception e1) {}
                              }

                              void n() {
                                  try {
                                  } catch (@TypeAnno final Exception e2) {}
                              }

                              void o() {
                                  try {
                                  } catch (@TypeAnno IllegalStateException | @TypeAnno NullPointerException | IllegalArgumentException e3) {}
                              }

                              void t_cr(@TypeAnno Supplier<String> r_p) { }
                              void t_no_cr(@TypeAnno Supplier<String> r_p) { }
                          }
                          """);
        Files.createDirectories(classes);
        List<String> actual = new ArrayList<>();
        new JavacTask(tb)
                .options("-d", classes.toString())
                .files(tb.findJavaFiles(src))
                .callback(task -> {
                    task.addTaskListener(new TaskListener() {
                        @Override
                        public void finished(TaskEvent e) {
                            if (e.getKind() != TaskEvent.Kind.ANALYZE) {
                                return ;
                            }
                            Trees trees = Trees.instance(task);
                            new TreePathScanner<Void, Void>() {
                                @Override
                                public Void visitVariable(VariableTree node, Void p) {
                                    actual.add(node.getName() + ": " + typeToString(trees.getTypeMirror(getCurrentPath())));
                                    return super.visitVariable(node, p);
                                }
                                @Override
                                public Void visitLambdaExpression(LambdaExpressionTree node, Void p) {
                                    actual.add(node.toString()+ ": " + typeToString(trees.getTypeMirror(getCurrentPath())));
                                    return super.visitLambdaExpression(node, p);
                                }
                            }.scan(e.getCompilationUnit(), null);
                        }
                    });
                })
                .run()
                .writeAll();

        List<String> expected = List.of(
            "r_f_i: java.util.function.@Test.TypeAnno Supplier<java.lang.String>",
            "()->\"r_f_i\": java.util.function.@Test.TypeAnno Supplier<java.lang.String>",
            "r_f_s: java.util.function.@Test.TypeAnno Supplier<java.lang.String>",
            "()->\"r_f_s\": java.util.function.@Test.TypeAnno Supplier<java.lang.String>",
            "r_init_i: java.util.function.@Test.TypeAnno Supplier<java.lang.String>",
            "()->\"r_init_i\": java.util.function.@Test.TypeAnno Supplier<java.lang.String>",
            "r_init_s: java.util.function.@Test.TypeAnno Supplier<java.lang.String>",
            "()->\"r_init_s\": java.util.function.@Test.TypeAnno Supplier<java.lang.String>",
            "r_m_i: java.util.function.@Test.TypeAnno Supplier<java.lang.String>",
            "()->\"r_m_i\": java.util.function.@Test.TypeAnno Supplier<java.lang.String>",
            "r_g_s: java.util.function.@Test.TypeAnno Supplier<java.lang.String>",
            "()->\"r_g_s\": java.util.function.@Test.TypeAnno Supplier<java.lang.String>",
            "()->\"t_cr\": java.util.function.@Test.TypeAnno Supplier<java.lang.String>",
            "()->\"t_no_cr\": java.util.function.@Test.TypeAnno Supplier<java.lang.String>",
            "()->\"t_no_cr\": java.lang.Object&java.io.Serializable&java.util.function.@Test.TypeAnno Supplier<java.lang.String>",
            "ac: java.lang.@Test.TypeAnno AutoCloseable",
            "()->{\n}: java.lang.@Test.TypeAnno AutoCloseable",
            "e1: java.lang.@Test.TypeAnno Exception",
            "e2: java.lang.@Test.TypeAnno Exception",
            "e3: java.lang.@Test.TypeAnno IllegalStateException | java.lang.@Test.TypeAnno NullPointerException | java.lang.IllegalArgumentException",
            "r_p: java.util.function.@Test.TypeAnno Supplier<java.lang.String>",
            "r_p: java.util.function.@Test.TypeAnno Supplier<java.lang.String>"
        );

        actual.forEach(System.out::println);
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected: " + expected + ", but got: " + actual);
        }
    }

    static String typeToString(TypeMirror type) {
        if (type != null && type.getKind() == TypeKind.UNION) {
            return ((UnionType) type).getAlternatives().stream().map(t -> typeToString(t)).collect(Collectors.joining(" | "));
        } else {
            return String.valueOf(type);
        }
    }
}
