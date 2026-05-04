/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8301580 8322159 8333107 8332230 8338678 8351260 8366196 8372336 8373094
 * @summary Verify error recovery w.r.t. Attr
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main AttrRecovery
 */

import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

import toolbox.JavacTask;
import toolbox.Task.Expect;
import toolbox.Task.OutputKind;
import toolbox.TestRunner;
import toolbox.ToolBox;

public class AttrRecovery extends TestRunner {

    ToolBox tb;

    public AttrRecovery() {
        super(System.err);
        tb = new ToolBox();
    }

    public static void main(String[] args) throws Exception {
        AttrRecovery t = new AttrRecovery();
        t.runTests();
    }

    @Test
    public void testFlowExits() throws Exception {
        String code = """
                      class C {
                          void build
                          {
                              return ;
                          }
                      }
                      """;
        Path curPath = Path.of(".");
        List<String> actual = new JavacTask(tb)
                .options("-XDrawDiagnostics", "-XDdev", "-XDshould-stop.at=FLOW")
                .sources(code)
                .outdir(curPath)
                .run(Expect.FAIL)
                .getOutputLines(OutputKind.DIRECT);

        List<String> expected = List.of(
                "C.java:3:5: compiler.err.expected: '('",
                "C.java:4:9: compiler.err.ret.outside.meth",
                "2 errors"
        );

        if (!Objects.equals(actual, expected)) {
            error("Expected: " + expected + ", but got: " + actual);
        }
    }

    @Test
    public void testX() throws Exception {
        String code = """
                      public class C {
                          public C() {
                              Undefined.method();
                              undefined1();
                              Runnable r = this::undefined2;
                              overridable(this); //to verify ThisEscapeAnalyzer has been run
                          }
                          public void overridable(C c) {}
                      }
                      """;
        Path curPath = Path.of(".");
        List<String> actual = new JavacTask(tb)
                .options("-XDrawDiagnostics", "-XDdev",
                         "-XDshould-stop.at=WARN", "-Xlint:this-escape")
                .sources(code)
                .outdir(curPath)
                .run(Expect.FAIL)
                .writeAll()
                .getOutputLines(OutputKind.DIRECT);

        List<String> expected = List.of(
                "C.java:3:9: compiler.err.cant.resolve.location: kindname.variable, Undefined, , , (compiler.misc.location: kindname.class, C, null)",
                "C.java:4:9: compiler.err.cant.resolve.location.args: kindname.method, undefined1, , , (compiler.misc.location: kindname.class, C, null)",
                "C.java:5:22: compiler.err.invalid.mref: kindname.method, (compiler.misc.cant.resolve.location.args: kindname.method, undefined2, , , (compiler.misc.location: kindname.class, C, null))",
                "C.java:6:20: compiler.warn.possible.this.escape",
                "3 errors",
                "1 warning"
        );

        if (!Objects.equals(actual, expected)) {
            error("Expected: " + expected + ", but got: " + actual);
        }
    }

    @Test //JDK-8332230
    public void testAnnotationsInErroneousTree1() throws Exception {
        String code = """
                      package p;
                      public class C {
                          static int v;
                          public void t() {
                              //not a statement expression,
                              //will be wrapped in an erroneous tree:
                              p.@Ann C.v;
                          }
                          @interface Ann {}
                      }
                      """;
        Path curPath = Path.of(".");
        List<String> actual = new JavacTask(tb)
                .options("-XDrawDiagnostics", "-XDdev", "-XDshould-stop.at=FLOW")
                .sources(code)
                .outdir(curPath)
                .run(Expect.FAIL)
                .writeAll()
                .getOutputLines(OutputKind.DIRECT);

        List<String> expected = List.of(
                "C.java:7:17: compiler.err.not.stmt",
                "1 error"
        );

        if (!Objects.equals(actual, expected)) {
            error("Expected: " + expected + ", but got: " + actual);
        }
    }

    @Test //JDK-8333107
    public void testNestedLambda() throws Exception {
        String code = """
                      public class Dummy {
                          private void main() {
                              Stream l = null;
                              l.map(a -> {
                                  l.map(b -> {
                                      return null;
                                  });
                                  l.map(new FI() {
                                      public String convert(String s) {
                                          return null;
                                      }
                                  });
                                  class Local {}
                              });
                          }
                          public interface Stream {
                              public void map(FI fi);
                          }
                          public interface FI {
                              public String convert(String s);
                          }
                      }
                      """;
        Path curPath = Path.of(".");
        List<String> actual = new JavacTask(tb)
                .options("-XDrawDiagnostics", "-XDdev",
                         "-XDshould-stop.at=FLOW")
                .sources(code)
                .outdir(curPath)
                .run(Expect.FAIL)
                .writeAll()
                .getOutputLines(OutputKind.DIRECT);

        List<String> expected = List.of(
                "Dummy.java:4:10: compiler.err.cant.apply.symbol: kindname.method, map, Dummy.FI, @15, kindname.interface, Dummy.Stream, (compiler.misc.no.conforming.assignment.exists: (compiler.misc.incompatible.ret.type.in.lambda: (compiler.misc.missing.ret.val: java.lang.String)))",
                "1 error"
        );

        if (!Objects.equals(actual, expected)) {
            error("Expected: " + expected + ", but got: " + actual);
        }
    }

    @Test
    public void testErroneousTarget() throws Exception {
        String code = """
                      public class C {
                          public Undefined g(Undefined u) {
                              return switch (0) {
                                  default -> u;
                              };
                          }
                      }
                      """;
        Path curPath = Path.of(".");
        List<String> actual = new JavacTask(tb)
                .options("-XDrawDiagnostics")
                .sources(code)
                .outdir(curPath)
                .run(Expect.FAIL, 1)
                .writeAll()
                .getOutputLines(OutputKind.DIRECT);

        List<String> expected = List.of(
                "C.java:2:24: compiler.err.cant.resolve.location: kindname.class, Undefined, , , (compiler.misc.location: kindname.class, C, null)",
                "C.java:2:12: compiler.err.cant.resolve.location: kindname.class, Undefined, , , (compiler.misc.location: kindname.class, C, null)",
                "2 errors"
        );

        if (!Objects.equals(actual, expected)) {
            error("Expected: " + expected + ", but got: " + actual);
        }
    }

    @Test
    public void testParameterizedErroneousType() throws Exception {
        String code = """
                      public class C {
                          Undefined1<Undefined2, Undefined3> variable1;
                      }
                      """;
        Path curPath = Path.of(".");
        List<String> actual = new JavacTask(tb)
                .options("-XDrawDiagnostics")
                .sources(code)
                .outdir(curPath)
                .callback(task -> {
                    task.addTaskListener(new TaskListener() {
                        @Override
                        public void finished(TaskEvent e) {
                            Trees trees = Trees.instance(task);

                            if (e.getKind() == TaskEvent.Kind.ANALYZE) {
                                new TreePathScanner<Void, Void>() {
                                    @Override
                                    public Void visitVariable(VariableTree tree, Void p) {
                                        VariableElement var = (VariableElement) trees.getElement(getCurrentPath());

                                        trees.printMessage(Diagnostic.Kind.NOTE, type2String(var.asType()), tree, e.getCompilationUnit());

                                        return super.visitVariable(tree, p);
                                    }
                                }.scan(e.getCompilationUnit(), null);
                            }
                        }
                        Map<Element, Integer> identityRename = new IdentityHashMap<>();
                        String type2String(TypeMirror type) {
                            StringBuilder result = new StringBuilder();

                            result.append(type.getKind());
                            result.append(":");
                            result.append(type.toString());

                            if (type.getKind() == TypeKind.DECLARED ||
                                type.getKind() == TypeKind.ERROR) {
                                DeclaredType dt = (DeclaredType) type;
                                Element el = task.getTypes().asElement(dt);
                                result.append(":");
                                result.append(el.toString());
                                if (!dt.getTypeArguments().isEmpty()) {
                                    result.append(dt.getTypeArguments()
                                                    .stream()
                                                    .map(tm -> type2String(tm))
                                                    .collect(Collectors.joining(", ", "<", ">")));
                                }
                            } else {
                                throw new AssertionError(type.getKind().name());
                            }

                            return result.toString();
                        }
                    });
                })
                .run(Expect.FAIL)
                .writeAll()
                .getOutputLines(OutputKind.DIRECT);

        List<String> expected = List.of(
                "C.java:2:5: compiler.err.cant.resolve.location: kindname.class, Undefined1, , , (compiler.misc.location: kindname.class, C, null)",
                "C.java:2:16: compiler.err.cant.resolve.location: kindname.class, Undefined2, , , (compiler.misc.location: kindname.class, C, null)",
                "C.java:2:28: compiler.err.cant.resolve.location: kindname.class, Undefined3, , , (compiler.misc.location: kindname.class, C, null)",
                "C.java:2:40: compiler.note.proc.messager: ERROR:Undefined1<Undefined2,Undefined3>:Undefined1<ERROR:Undefined2:Undefined2, ERROR:Undefined3:Undefined3>",
                "3 errors"
        );

        if (!Objects.equals(actual, expected)) {
            error("Expected: " + expected + ", but got: " + actual);
        }
    }

    @Test //JDK-8351260
    public void testVeryBrokenAnnotation() throws Exception {
        String code = """
                      class ListUtilsTest {
                          void test(List<@AlphaChars <@StringLength(int value = 5)String> s){
                          }
                      }
                      """;
        Path curPath = Path.of(".");
        //should not fail with an exception:
        new JavacTask(tb)
            .options("-XDrawDiagnostics",
                     "-XDshould-stop.at=FLOW")
            .sources(code)
            .outdir(curPath)
            .run(Expect.FAIL)
            .writeAll();
    }


    @Test //JDK-8366196
    public void testInferenceFailure() throws Exception {
        String code = """
                      import module java.base;
                      public class Test {
                          public void test(Consumer<String> c) {
                            List.of("")
                                .stream()
                                .filter(
                                    buildPredicate(
                                        String.class,
                                        //missing supplier
                                        c,
                                        buildSupplier(
                                            // Missing: Class,
                                            Integer.class,
                                            String.class,
                                            i -> { int check; return i; })));
                          }

                          private static <T> Predicate<T> buildPredicate(
                              Class<T> tClass,
                              Supplier<T> bSupplier,
                              Consumer<T> cConsumer,
                              Supplier<T> dSupplier) {
                            return null;
                          }

                          private static <T, A, B> Supplier<String> buildSupplier(
                              Class<T> tClass, Class<A> aClass, Class<B> bClass,
                              Function<A, B> function) {
                            return null;
                          }
                      }""";
        Path curPath = Path.of(".");
        List<String> actual = new JavacTask(tb)
                .options("-XDrawDiagnostics", "-XDshould-stop.at=FLOW")
                .sources(code)
                .outdir(curPath)
                .run(Expect.FAIL)
                .writeAll()
                .getOutputLines(OutputKind.DIRECT);

        List<String> expected = List.of(
                "Test.java:7:29: compiler.err.prob.found.req: (compiler.misc.infer.no.conforming.assignment.exists: T, (compiler.misc.inconvertible.types: java.util.function.Consumer<java.lang.String>, java.util.function.Supplier<T>))",
                "1 error"
        );

        if (!Objects.equals(actual, expected)) {
            error("Expected: " + expected + ", but got: " + actual);
        }
    }

    @Test //JDK-8372336
    public void testCompletionFailureNoBreakInvocation() throws Exception {
        Path curPath = Path.of(".");
        Path lib = curPath.resolve("lib");
        Path classes = lib.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
            .outdir(classes)
            .sources("""
                     package test;
                     public class Intermediate extends Base {}
                     """,
                     """
                     package test;
                     public class Base {
                         public int get() {
                             return -1;
                         }
                     }
                     """)
            .run()
            .writeAll();

        Files.delete(classes.resolve("test").resolve("Base.class"));

        record TestCase(String code, String... expectedErrors) {}
        TestCase[] testCases = new TestCase[] {
            new TestCase("""
                         package test;
                         public class Test {
                             private void test(Intermediate i) {
                                 int j = i != null ? i.get() : -1;
                             }
                         }
                         """,
                         "Test.java:4:30: compiler.err.cant.access: test.Base, (compiler.misc.class.file.not.found: test.Base)",
                         "1 error"),
            new TestCase("""
                         package test;
                         public class Test {
                             private void test(Intermediate i) {
                                 i.get();
                             }
                         }
                         """,
                         "Test.java:4:10: compiler.err.cant.access: test.Base, (compiler.misc.class.file.not.found: test.Base)",
                         "1 error")
        };

        for (TestCase tc : testCases) {
            List<String> actual = new JavacTask(tb)
                    .options("-XDrawDiagnostics", "-XDdev")
                    .classpath(classes)
                    .sources(tc.code())
                    .outdir(curPath)
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
                                    public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
                                        if (!node.toString().contains("super")) {
                                            verifyElement();
                                        }
                                        return super.visitMethodInvocation(node, p);
                                    }
                                    @Override
                                    public Void visitMemberReference(MemberReferenceTree node, Void p) {
                                        verifyElement();
                                        return super.visitMemberReference(node, p);
                                    }
                                    private void verifyElement() {
                                        Element el = trees.getElement(getCurrentPath());
                                        if (!el.getSimpleName().contentEquals("get")) {
                                            error("Expected good Element, but got: " + el);
                                        }
                                    }
                                }.scan(e.getCompilationUnit(), null);
                            }
                        });
                    })
                    .run(Expect.FAIL)
                    .writeAll()
                    .getOutputLines(OutputKind.DIRECT);

            List<String> expected = List.of(tc.expectedErrors);

            if (!Objects.equals(actual, expected)) {
                error("Expected: " + expected + ", but got: " + actual);
            }
        }
    }

    @Test //JDK-8373094
    public void testSensibleAttribution() throws Exception {
        Path curPath = Path.of(".");
        Path lib = curPath.resolve("lib");
        Path classes = lib.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
            .outdir(classes)
            .sources("""
                     package test;
                     public class Intermediate<T> extends Base<T> {}
                     """,
                     """
                     package test;
                     public class Base<T> {
                         public void t(Missing<T> m) {}
                     }
                     """,
                     """
                     package test;
                     public class Missing<T> {
                     }
                     """)
            .run()
            .writeAll();

        Files.delete(classes.resolve("test").resolve("Missing.class"));

        record TestCase(String code, List<String> options, String... expectedErrors) {}
        TestCase[] testCases = new TestCase[] {
            new TestCase("""
                         package test;
                         public class Test extends Intermediate<String> {
                             private void test() {
                                 int i = 0;
                                 System.err.println(i);
                                 while (true) {
                                     break;
                                 }
                             }
                         }
                         """,
                         List.of(),
                         "Test.java:2:8: compiler.err.cant.access: test.Missing, (compiler.misc.class.file.not.found: test.Missing)",
                         "1 error"),
            new TestCase("""
                         package test;
                         public class Test extends Intermediate<String> {
                             private void test() {
                                 int i = 0;
                                 System.err.println(i);
                                 while (true) {
                                    break;
                                 }
                             }
                         }
                         """,
                         List.of("-XDshould-stop.at=FLOW"),
                         "Test.java:2:8: compiler.err.cant.access: test.Missing, (compiler.misc.class.file.not.found: test.Missing)",
                         "1 error"),
        };

        for (TestCase tc : testCases) {
            List<String> attributes = new ArrayList<>();
            List<String> actual = new JavacTask(tb)
                    .options(Stream.concat(List.of("-XDrawDiagnostics", "-XDdev").stream(),
                                           tc.options.stream()).toList())
                    .classpath(classes)
                    .sources(tc.code())
                    .outdir(curPath)
                    .callback(task -> {
                        task.addTaskListener(new TaskListener() {
                            @Override
                            public void finished(TaskEvent e) {
                                if (e.getKind() != TaskEvent.Kind.ANALYZE) {
                                    return ;
                                }
                                Trees trees = Trees.instance(task);
                                new TreePathScanner<Void, Void>() {
                                    boolean check;

                                    @Override
                                    public Void visitMethod(MethodTree node, Void p) {
                                        if (node.getName().contentEquals("test")) {
                                            check = true;
                                            try {
                                                return super.visitMethod(node, p);
                                            } finally {
                                                check = false;
                                            }
                                        }

                                        return super.visitMethod(node, p);
                                    }

                                    @Override
                                    public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
                                        if (!node.toString().contains("super")) {
                                            verifyElement();
                                        }
                                        return super.visitMethodInvocation(node, p);
                                    }

                                    @Override
                                    public Void visitIdentifier(IdentifierTree node, Void p) {
                                        verifyElement();
                                        return super.visitIdentifier(node, p);
                                    }

                                    @Override
                                    public Void visitMemberSelect(MemberSelectTree node, Void p) {
                                        verifyElement();
                                        return super.visitMemberSelect(node, p);
                                    }

                                    private void verifyElement() {
                                        if (!check) {
                                            return ;
                                        }

                                        Element el = trees.getElement(getCurrentPath());
                                        if (el == null) {
                                            error("Unattributed tree: " + getCurrentPath().getLeaf());
                                        } else {
                                            attributes.add(el.toString());
                                        }
                                    }
                                }.scan(e.getCompilationUnit(), null);
                            }
                        });
                    })
                    .run(Expect.FAIL)
                    .writeAll()
                    .getOutputLines(OutputKind.DIRECT);

            List<String> expectedErrors = List.of(tc.expectedErrors);

            if (!Objects.equals(actual, expectedErrors)) {
                error("Expected: " + expectedErrors + ", but got: " + actual);
            }

            List<String> expectedAttributes =
                    List.of("println(int)", "println(int)", "err", "java.lang.System", "i");

            if (!Objects.equals(attributes, expectedAttributes)) {
                error("Expected: " + expectedAttributes + ", but got: " + attributes);
            }
        }
    }

}
