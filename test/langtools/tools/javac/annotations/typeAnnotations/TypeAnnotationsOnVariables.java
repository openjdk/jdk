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
 * @bug 8371155 8379550
 * @summary Verify type annotations on local-like variables are propagated to
 *          their types at an appropriate time.
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 *      jdk.jdeps/com.sun.tools.javap
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run junit TypeAnnotationsOnVariables
 */

import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.RuntimeInvisibleTypeAnnotationsAttribute;
import java.lang.classfile.constantpool.ConstantPool;
import java.lang.classfile.constantpool.Utf8Entry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.UnionType;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import toolbox.JavacTask;
import toolbox.JavapTask;
import toolbox.Task;
import toolbox.ToolBox;

public class TypeAnnotationsOnVariables {

    private static final Pattern CP_REFERENCE = Pattern.compile("#([1-9][0-9]*)");
    final ToolBox tb = new ToolBox();
    Path base;

    @Test
    void typeAnnotationInConstantExpressionFieldInit() throws Exception {
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
                                    actual.add(treeToString(node)+ ": " + typeToString(trees.getTypeMirror(getCurrentPath())));
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
            "()->{ }: java.lang.@Test.TypeAnno AutoCloseable",
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

    static String treeToString(Tree tree) {
        return String.valueOf(tree).replaceAll("\\R", " ");
    }

    @Test
    void properPathForLocalVarsInLambdas() throws Exception {
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

                              void o() {
                                  Runnable r = () -> {
                                      @TypeAnno long test1 = 0;
                                      while (true) {
                                          @TypeAnno long test2 = 0;
                                          System.err.println(test2);
                                          try (@TypeAnno AutoCloseable ac = null) {
                                              System.err.println(ac);
                                          } catch (@TypeAnno Exception e1) {
                                              System.err.println(e1);
                                          }
                                          try {
                                              "".length();
                                          } catch (@TypeAnno final Exception e2) {
                                              System.err.println(e2);
                                          }
                                          try {
                                              "".length();
                                          } catch (@TypeAnno IllegalStateException | @TypeAnno NullPointerException | IllegalArgumentException e3) {
                                              System.err.println(e3);
                                          }
                                          Runnable r2 = () -> {
                                              @TypeAnno long test3 = 0;
                                              while (true) {
                                                  @TypeAnno long test4 = 0;
                                                  System.err.println(test4);
                                              }
                                          };
                                          Object o = null;
                                          if (o instanceof @TypeAnno String s) {
                                              System.err.println(s);
                                          }
                                      }
                                  };
                              }
                              void lambdaInClass() {
                                  class C {
                                      Runnable r = () -> {
                                          @TypeAnno long test1 = 0;
                                          System.err.println(test1);
                                      };
                                  }
                              }
                              void classInLambda() {
                                  Runnable r = () -> {
                                      class C {
                                          void method() {
                                              @TypeAnno long test1 = 0;
                                              System.err.println(test1);
                                          }
                                      }
                                  };
                              }
                          }
                          """);
        Files.createDirectories(classes);
        new JavacTask(tb)
                .options("-d", classes.toString())
                .files(tb.findJavaFiles(src))
                .run()
                .writeAll();

        Path testClass = classes.resolve("Test.class");
        TestClassDesc testClassDesc = TestClassDesc.create(testClass);
        MethodModel oMethod = singletonValue(testClassDesc.name2Method().get("o"));
        var oTypeAnnos = getAnnotations(oMethod);
        assertFalse(oTypeAnnos.isPresent(), () -> oTypeAnnos.toString());

        checkTypeAnnotations(testClassDesc,
                             "lambda$o$0",
                             "        0: LTest$TypeAnno;(): LOCAL_VARIABLE, {start_pc=2, length=151, index=0}",
                             "          Test$TypeAnno",
                             "        1: LTest$TypeAnno;(): LOCAL_VARIABLE, {start_pc=4, length=146, index=2}",
                             "          Test$TypeAnno",
                             "        2: LTest$TypeAnno;(): RESOURCE_VARIABLE, {start_pc=14, length=52, index=4}",
                             "          Test$TypeAnno",
                             "        3: LTest$TypeAnno;(): EXCEPTION_PARAMETER, exception_index=2",
                             "          Test$TypeAnno",
                             "        4: LTest$TypeAnno;(): EXCEPTION_PARAMETER, exception_index=3",
                             "          Test$TypeAnno",
                             "        5: LTest$TypeAnno;(): EXCEPTION_PARAMETER, exception_index=4",
                             "          Test$TypeAnno",
                             "        6: LTest$TypeAnno;(): EXCEPTION_PARAMETER, exception_index=5",
                             "          Test$TypeAnno",
                             "        7: LTest$TypeAnno;(): LOCAL_VARIABLE, {start_pc=142, length=8, index=6}",
                             "          Test$TypeAnno");

        checkTypeAnnotations(testClassDesc,
                             "lambda$o$1",
                             "        0: LTest$TypeAnno;(): LOCAL_VARIABLE, {start_pc=2, length=12, index=0}",
                             "          Test$TypeAnno",
                             "        1: LTest$TypeAnno;(): LOCAL_VARIABLE, {start_pc=4, length=7, index=2}",
                             "          Test$TypeAnno");

        checkTypeAnnotations(testClassDesc,
                             "lambda$classInLambda$0");

        checkTypeAnnotations(TestClassDesc.create(classes.resolve("Test$1C.class")),
                             "lambda$new$0",
                             "        0: LTest$TypeAnno;(): LOCAL_VARIABLE, {start_pc=2, length=8, index=0}",
                             "          Test$TypeAnno");
    }

    private void checkTypeAnnotations(TestClassDesc testClassDesc,
                                      String lambdaMethodName,
                                      String... expectedEntries) throws IOException {
        MethodModel lambdaMethod = singletonValue(testClassDesc.name2Method().get(lambdaMethodName));
        var lambdaTypeAnnos = getAnnotations(lambdaMethod);
        if (expectedEntries.length == 0) {
            assertFalse(lambdaTypeAnnos.isPresent(), () -> lambdaTypeAnnos.toString());
        } else {
            assertTrue(lambdaTypeAnnos.isPresent(), () -> lambdaTypeAnnos.toString());
            assertEquals(expectedEntries.length / 2,
                         lambdaTypeAnnos.orElseThrow().annotations().size(),
                         () -> lambdaTypeAnnos.orElseThrow().annotations().toString());

            checkJavapOutput(testClassDesc,
                             List.of(expectedEntries));
        }
    }

    private <T> T singletonValue(List<T> values) {
        assertEquals(1, values.size());
        return values.get(0);
    }

    private Optional<RuntimeInvisibleTypeAnnotationsAttribute> getAnnotations(MethodModel m) {
        return m.findAttribute(Attributes.code())
                .orElseThrow()
                .findAttribute(Attributes.runtimeInvisibleTypeAnnotations());
    }

    void checkJavapOutput(TestClassDesc testClassDesc, List<String> expectedOutput) throws IOException {
        String javapOut = new JavapTask(tb)
                .options("-v", "-p")
                .classes(testClassDesc.pathToClass().toString())
                .run()
                .getOutput(Task.OutputKind.DIRECT);

        StringBuilder expandedJavapOutBuilder = new StringBuilder();
        Matcher m = CP_REFERENCE.matcher(javapOut);

        while (m.find()) {
            String cpIndexText = m.group(1);
            int cpIndex = Integer.parseInt(cpIndexText);
            m.appendReplacement(expandedJavapOutBuilder, Matcher.quoteReplacement(testClassDesc.cpIndex2Name().getOrDefault(cpIndex, cpIndexText)));
        }

        m.appendTail(expandedJavapOutBuilder);

        String expandedJavapOut = expandedJavapOutBuilder.toString();

        for (String expected : expectedOutput) {
            if (!expandedJavapOut.contains(expected)) {
                System.err.println(expandedJavapOut);
                throw new AssertionError("unexpected output");
            }
        }
    }

    record TestClassDesc(Path pathToClass,
                     Map<String, List<MethodModel>> name2Method,
                     Map<Integer, String> cpIndex2Name) {
        public static TestClassDesc create(Path pathToClass) throws IOException{
            ClassModel model = ClassFile.of().parse(pathToClass);
            Map<String, List<MethodModel>> name2Method =
                    model.methods()
                         .stream()
                         .collect(Collectors.groupingBy(m -> m.methodName().stringValue()));
            ConstantPool cp = model.constantPool();
            int cpSize = cp.size();
            Map<Integer, String> cpIndex2Name = new HashMap<>();

            for (int i = 1; i < cpSize; i++) {
                if (cp.entryByIndex(i) instanceof Utf8Entry string) {
                    cpIndex2Name.put(i, string.stringValue());
                }
            }

            return new TestClassDesc(pathToClass, name2Method, cpIndex2Name);
        }
    }

    @BeforeEach
    void setUp(TestInfo thisTest) {
        base = Path.of(thisTest.getTestMethod().orElseThrow().getName());
    }
}
