/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8262891 8268871 8274363 8281100
 * @summary Check exhaustiveness of switches over sealed types.
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main Exhaustiveness
*/

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import toolbox.TestRunner;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class Exhaustiveness extends TestRunner {

    private static final String JAVA_VERSION = System.getProperty("java.specification.version");

    ToolBox tb;

    public static void main(String... args) throws Exception {
        new Exhaustiveness().runTests();
    }

    Exhaustiveness() {
        super(System.err);
        tb = new ToolBox();
    }

    public void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test
    public void testExhaustiveSealedClasses(Path base) throws Exception {
        doTest(base,
               new String[]{"""
                            package lib;
                            public sealed interface S permits A, B {}
                            """,
                            """
                            package lib;
                            public final class A implements S {}
                            """,
                            """
                            package lib;
                            public final class B implements S {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private int test(S obj) {
                       return switch (obj) {
                           case A a -> 0;
                           case B b -> 1;
                       };
                   }
               }
               """);
    }

    @Test
    public void testNonExhaustiveSealedClasses(Path base) throws Exception {
        doTest(base,
               new String[]{"""
                            package lib;
                            public sealed interface S permits A, B {}
                            """,
                            """
                            package lib;
                            public final class A implements S {}
                            """,
                            """
                            package lib;
                            public final class B implements S {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private int test(S obj) {
                       return switch (obj) {
                           case A a -> 0;
                       };
                   }
               }
               """,
               "Test.java:5:16: compiler.err.not.exhaustive",
               "- compiler.note.preview.filename: Test.java, DEFAULT",
               "- compiler.note.preview.recompile",
               "1 error");
    }

    @Test
    public void testAbstractSealedClasses(Path base) throws Exception {
        doTest(base,
               new String[]{"""
                            package lib;
                            public sealed abstract class S permits A, B {}
                            """,
                            """
                            package lib;
                            public final class A extends S {}
                            """,
                            """
                            package lib;
                            public final class B extends S {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private int test(S obj) {
                       return switch (obj) {
                           case A a -> 0;
                           case B b -> 1;
                       };
                   }
               }
               """);
    }

    @Test
    public void testConcreteSealedClasses(Path base) throws Exception {
        doTest(base,
               new String[]{"""
                            package lib;
                            public sealed class S permits A, B {}
                            """,
                            """
                            package lib;
                            public final class A extends S {}
                            """,
                            """
                            package lib;
                            public final class B extends S {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private int test(S obj) {
                       return switch (obj) {
                           case A a -> 0;
                           case B b -> 1;
                       };
                   }
               }
               """,
               "Test.java:5:16: compiler.err.not.exhaustive",
               "- compiler.note.preview.filename: Test.java, DEFAULT",
               "- compiler.note.preview.recompile",
               "1 error");
    }

    @Test
    public void testGuards1(Path base) throws Exception {
        doTest(base,
               new String[]{"""
                            package lib;
                            public sealed interface S permits A, B {}
                            """,
                            """
                            package lib;
                            public final class A implements S {}
                            """,
                            """
                            package lib;
                            public final class B implements S {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private int test(S obj) {
                       return switch (obj) {
                           case A a && a.toString().isEmpty() -> 0;
                           case B b -> 1;
                       };
                   }
               }
               """,
               "Test.java:5:16: compiler.err.not.exhaustive",
               "- compiler.note.preview.filename: Test.java, DEFAULT",
               "- compiler.note.preview.recompile",
               "1 error");
    }

    @Test
    public void testGuards2(Path base) throws Exception {
        doTest(base,
               new String[]{"""
                            package lib;
                            public sealed interface S permits A, B {}
                            """,
                            """
                            package lib;
                            public final class A implements S {}
                            """,
                            """
                            package lib;
                            public final class B implements S {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private static final boolean TEST = true;
                   private int test(S obj) {
                       return switch (obj) {
                           case A a && !(!(TEST)) -> 0;
                           case B b -> 1;
                       };
                   }
               }
               """);
    }

    @Test
    public void testGuards3(Path base) throws Exception {
        doTest(base,
               new String[]{"""
                            package lib;
                            public sealed interface S permits A, B {}
                            """,
                            """
                            package lib;
                            public final class A implements S {}
                            """,
                            """
                            package lib;
                            public final class B implements S {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private int test(S obj) {
                       return switch (obj) {
                           case A a && false -> 0;
                           case B b -> 1;
                       };
                   }
               }
               """,
               "Test.java:5:16: compiler.err.not.exhaustive",
               "- compiler.note.preview.filename: Test.java, DEFAULT",
               "- compiler.note.preview.recompile",
               "1 error");
    }

    @Test
    public void testCoversType1(Path base) throws Exception {
        doTest(base,
               new String[]{"""
                            package lib;
                            public sealed interface S permits A, B {}
                            """,
                            """
                            package lib;
                            public final class A implements S {}
                            """,
                            """
                            package lib;
                            public final class B implements S {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private int test(S obj) {
                       return switch (obj) {
                           case A a -> 0;
                           case S s -> 1;
                       };
                   }
               }
               """);
    }

    @Test
    public void testCoversType2(Path base) throws Exception {
        doTest(base,
               new String[]{"""
                            package lib;
                            public interface S {}
                            """,
                            """
                            package lib;
                            public final class A implements S {}
                            """,
                            """
                            package lib;
                            public final class B implements S {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private int test(S obj) {
                       return switch (obj) {
                           case A a -> 0;
                           case S s -> 1;
                       };
                   }
               }
               """);
    }

    @Test
    public void testCoversType3(Path base) throws Exception {
        doTest(base,
               new String[]{"""
                            package lib;
                            public interface S<T> {}
                            """,
                            """
                            package lib;
                            public final class A implements S<A> {}
                            """,
                            """
                            package lib;
                            public final class B implements S<B> {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private int test(S obj) {
                       return switch (obj) {
                           case A a -> 0;
                           case S<?> s -> 1;
                       };
                   }
               }
               """);
    }

    @Test
    public void testExhaustiveStatement1(Path base) throws Exception {
        doTest(base,
               new String[]{"""
                            package lib;
                            public interface Lib {}
                            """},
               """
               package test;
               public class Test {
                   private int test(Object obj) {
                       switch (obj) {
                           case Object o: return 0;
                       }
                   }
               }
               """);
    }

    @Test
    public void testExhaustiveStatement2(Path base) throws Exception {
        doTest(base,
               new String[]{"""
                            package lib;
                            public interface Lib {}
                            """},
               """
               package test;
               public class Test {
                   private void test(Object obj) {
                       switch (obj) {
                           case String s: return;
                       };
                   }
               }
               """,
               "Test.java:4:9: compiler.err.not.exhaustive.statement",
               "- compiler.note.preview.filename: Test.java, DEFAULT",
               "- compiler.note.preview.recompile",
               "1 error");
    }

    @Test
    public void testExhaustiveStatement3(Path base) throws Exception {
        doTest(base,
               new String[]{"""
                            package lib;
                            public sealed interface S permits A, B {}
                            """,
                            """
                            package lib;
                            public final class A implements S {}
                            """,
                            """
                            package lib;
                            public final class B implements S {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private int test(S obj) {
                       return switch (obj) {
                           case A a -> 0;
                           case S s -> 1;
                       };
                   }
               }
               """);
    }

    @Test
    public void testExhaustiveStatement4(Path base) throws Exception {
        doTest(base,
               new String[]{"""
                            package lib;
                            public sealed interface S permits A, B {}
                            """,
                            """
                            package lib;
                            public final class A implements S {}
                            """,
                            """
                            package lib;
                            public final class B implements S {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private int test(S obj) {
                       return switch (obj) {
                           case A a -> 0;
                       };
                   }
               }
               """,
               "Test.java:5:16: compiler.err.not.exhaustive",
               "- compiler.note.preview.filename: Test.java, DEFAULT",
               "- compiler.note.preview.recompile",
               "1 error");
    }

    @Test
    public void testExhaustiveStatement5(Path base) throws Exception {
        doTest(base,
               new String[]{"""
                            package lib;
                            public sealed interface S permits A, B {}
                            """,
                            """
                            package lib;
                            public final class A implements S {}
                            """,
                            """
                            package lib;
                            public final class B implements S {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private int test(S obj) {
                       return switch (obj) {
                           case A a -> 0;
                           case B b -> 0;
                       };
                   }
               }
               """);
    }

    @Test
    public void testExhaustiveTransitive(Path base) throws Exception {
        doTest(base,
               new String[]{"""
                            package lib;
                            public sealed interface S permits A, B {}
                            """,
                            """
                            package lib;
                            public final class A implements S {}
                            """,
                            """
                            package lib;
                            public abstract sealed class B implements S permits C, D {}
                            """,
                            """
                            package lib;
                            public final class C extends B {}
                            """,
                            """
                            package lib;
                            public final class D extends B {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private int test(S obj, boolean b) {
                       return switch (obj) {
                           case A a -> 0;
                           case C c && b -> 0;
                           case C c -> 0;
                           case D d -> 0;
                       };
                   }
               }
               """);
    }

    @Test
    public void testNotExhaustiveTransitive(Path base) throws Exception {
        doTest(base,
               new String[]{"""
                            package lib;
                            public sealed interface S permits A, B {}
                            """,
                            """
                            package lib;
                            public final class A implements S {}
                            """,
                            """
                            package lib;
                            public abstract sealed class B implements S permits C, D {}
                            """,
                            """
                            package lib;
                            public final class C extends B {}
                            """,
                            """
                            package lib;
                            public final class D extends B {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private int test(S obj, boolean b) {
                       return switch (obj) {
                           case A a -> 0;
                           case C c -> 0;
                           case D d && b -> 0;
                       };
                   }
               }
               """,
               "Test.java:5:16: compiler.err.not.exhaustive",
               "- compiler.note.preview.filename: Test.java, DEFAULT",
               "- compiler.note.preview.recompile",
               "1 error");
    }

    @Test
    public void testExhaustiveIntersection(Path base) throws Exception {
        doTest(base,
               new String[]{"""
                            package lib;
                            public sealed interface S permits A, B {}
                            """,
                            """
                            package lib;
                            public abstract class Base {}
                            """,
                            """
                            package lib;
                            public interface Marker {}
                            """,
                            """
                            package lib;
                            public final class A extends Base implements S, Marker {}
                            """,
                            """
                            package lib;
                            public abstract sealed class B extends Base implements S permits C, D {}
                            """,
                            """
                            package lib;
                            public final class C extends B implements Marker {}
                            """,
                            """
                            package lib;
                            public final class D extends B implements Marker {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private <T extends Base & S & Marker> int test(T obj, boolean b) {
                       return switch (obj) {
                           case A a -> 0;
                           case C c && b -> 0;
                           case C c -> 0;
                           case D d -> 0;
                       };
                   }
               }
               """);
    }

    @Test
    public void testNotExhaustiveIntersection(Path base) throws Exception {
        doTest(base,
               new String[]{"""
                            package lib;
                            public sealed interface S permits A, B {}
                            """,
                            """
                            package lib;
                            public abstract class Base {}
                            """,
                            """
                            package lib;
                            public interface Marker {}
                            """,
                            """
                            package lib;
                            public final class A extends Base implements S, Marker {}
                            """,
                            """
                            package lib;
                            public abstract sealed class B extends Base implements S permits C, D {}
                            """,
                            """
                            package lib;
                            public final class C extends B implements Marker {}
                            """,
                            """
                            package lib;
                            public final class D extends B implements Marker {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private <T extends Base & S & Marker> int test(T obj, boolean b) {
                       return switch (obj) {
                           case A a -> 0;
                           case C c -> 0;
                           case D d && b -> 0;
                       };
                   }
               }
               """,
               "Test.java:5:16: compiler.err.not.exhaustive",
               "- compiler.note.preview.filename: Test.java, DEFAULT",
               "- compiler.note.preview.recompile",
               "1 error");
    }

    @Test
    public void testTransitiveSealed(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   sealed interface A {}
                   sealed interface B1 extends A {}
                   sealed interface B2 extends A {}
                   sealed interface C extends A {}
                   final class D1 implements B1, C {}
                   final class D2 implements B2, C {}

                   void test(A arg) {
                       int i = switch (arg) {
                           case B1 b1 -> 1;
                           case B2 b2 -> 2;
                       };
                   }
               }
               """);
    }

    @Test
    public void testOnlyApplicable(Path base) throws Exception {
        record TestCase(String cases, String... errors) {}
        TestCase[] subCases = new TestCase[] {
            new TestCase("""
                                     case C3<Integer> c -> {}
                                     case C5<Integer, ?> c -> {}
                                     case C6<?, Integer> c -> {}
                         """), //OK
            new TestCase("""
                                     case C5<Integer, ?> c -> {}
                                     case C6<?, Integer> c -> {}
                         """,
                         "Test.java:11:9: compiler.err.not.exhaustive.statement",
                         "- compiler.note.preview.filename: Test.java, DEFAULT",
                         "- compiler.note.preview.recompile",
                         "1 error"),
            new TestCase("""
                                     case C3<Integer> c -> {}
                                     case C6<?, Integer> c -> {}
                         """,
                         "Test.java:11:9: compiler.err.not.exhaustive.statement",
                         "- compiler.note.preview.filename: Test.java, DEFAULT",
                         "- compiler.note.preview.recompile",
                         "1 error"),
            new TestCase("""
                                     case C3<Integer> c -> {}
                                     case C5<Integer, ?> c -> {}
                         """,
                         "Test.java:11:9: compiler.err.not.exhaustive.statement",
                         "- compiler.note.preview.filename: Test.java, DEFAULT",
                         "- compiler.note.preview.recompile",
                         "1 error"),
            new TestCase("""
                                     case C1 c -> {}
                                     case C3<Integer> c -> {}
                                     case C5<Integer, ?> c -> {}
                                     case C6<?, Integer> c -> {}
                         """,
                         "Test.java:12:18: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: test.Test.I<java.lang.Integer>, test.Test.C1)",
                         "- compiler.note.preview.filename: Test.java, DEFAULT",
                         "- compiler.note.preview.recompile",
                         "1 error"),
            new TestCase("""
                                     case C2<?> c -> {}
                                     case C3<Integer> c -> {}
                                     case C5<Integer, ?> c -> {}
                                     case C6<?, Integer> c -> {}
                         """,
                         "Test.java:12:18: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: test.Test.I<java.lang.Integer>, test.Test.C2<?>)",
                         "- compiler.note.preview.filename: Test.java, DEFAULT",
                         "- compiler.note.preview.recompile",
                         "1 error"),
            new TestCase("""
                                     case C4<?, ?> c -> {}
                                     case C3<Integer> c -> {}
                                     case C5<Integer, ?> c -> {}
                                     case C6<?, Integer> c -> {}
                         """,
                         "Test.java:12:18: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: test.Test.I<java.lang.Integer>, test.Test.C4<?,?>)",
                         "- compiler.note.preview.filename: Test.java, DEFAULT",
                         "- compiler.note.preview.recompile",
                         "1 error"),
        };
        for (TestCase tc : subCases) {
            doTest(base,
                   new String[0],
                   """
                   package test;
                   public class Test {
                       sealed interface I<T> {}
                       final class C1 implements I<String> {}
                       final class C2<T> implements I<String> {}
                       final class C3<T> implements I<T> {}
                       final class C4<T, E> implements I<String> {}
                       final class C5<T, E> implements I<T> {}
                       final class C6<T, E> implements I<E> {}
                       void t(I<Integer> i) {
                           switch (i) {
                   ${cases}
                           }
                       }
                   }
                   """.replace("${cases}", tc.cases),
                   tc.errors);
        }
    }

    @Test
    public void testDefiniteAssignment(Path base) throws Exception {
        doTest(base,
               new String[]{"""
                            package lib;
                            public sealed interface S permits A, B {}
                            """,
                            """
                            package lib;
                            public final class A implements S {}
                            """,
                            """
                            package lib;
                            public final class B implements S {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private void testStatement(S obj) {
                       int data;
                       switch (obj) {
                           case A a -> data = 0;
                           case B b -> data = 0;
                       };
                       System.err.println(data);
                   }
                   private void testExpression(S obj) {
                       int data;
                       int v = switch (obj) {
                           case A a -> data = 0;
                           case B b -> data = 0;
                       };
                       System.err.println(data);
                   }
                   private void testStatementNotExhaustive(S obj) {
                       int data;
                       switch (obj) {
                           case A a -> data = 0;
                       };
                       System.err.println(data);
                   }
                   private void testExpressionNotExhaustive(S obj) {
                       int data;
                       int v = switch (obj) {
                           case A a -> data = 0;
                       };
                       System.err.println(data);
                   }
                   private void testStatementErrorEnum(E e) { //"E" is intentionally unresolvable
                       int data;
                       switch (e) {
                           case A -> data = 0;
                           case B -> data = 0;
                       };
                       System.err.println(data);
                   }
                   private void testExpressionErrorEnum(E e) { //"E" is intentionally unresolvable
                       int data;
                       int v = switch (e) {
                           case A -> data = 0;
                           case B -> data = 0;
                       };
                       System.err.println(data);
                   }
               }
               """,
               "Test.java:34:41: compiler.err.cant.resolve.location: kindname.class, E, , , (compiler.misc.location: kindname.class, test.Test, null)",
               "Test.java:42:42: compiler.err.cant.resolve.location: kindname.class, E, , , (compiler.misc.location: kindname.class, test.Test, null)",
               "Test.java:22:9: compiler.err.not.exhaustive.statement",
               "Test.java:29:17: compiler.err.not.exhaustive",
               "- compiler.note.preview.filename: Test.java, DEFAULT",
               "- compiler.note.preview.recompile",
               "4 errors");
    }

    private void doTest(Path base, String[] libraryCode, String testCode, String... expectedErrors) throws IOException {
        Path current = base.resolve(".");
        Path libClasses = current.resolve("libClasses");

        Files.createDirectories(libClasses);

        if (libraryCode.length != 0) {
            Path libSrc = current.resolve("lib-src");

            for (String code : libraryCode) {
                tb.writeJavaFiles(libSrc, code);
            }

            new JavacTask(tb)
                    .options("--enable-preview",
                             "-source", JAVA_VERSION)
                    .outdir(libClasses)
                    .files(tb.findJavaFiles(libSrc))
                    .run();
        }

        Path src = current.resolve("src");
        tb.writeJavaFiles(src, testCode);

        Path classes = current.resolve("libClasses");

        Files.createDirectories(libClasses);

        var log =
                new JavacTask(tb)
                    .options("--enable-preview",
                             "-source", JAVA_VERSION,
                             "-XDrawDiagnostics",
                             "-Xlint:-preview",
                             "--class-path", libClasses.toString(),
                             "-XDshould-stop.at=FLOW")
                    .outdir(classes)
                    .files(tb.findJavaFiles(src))
                    .run(expectedErrors.length > 0 ? Task.Expect.FAIL : Task.Expect.SUCCESS)
                    .writeAll()
                    .getOutputLines(Task.OutputKind.DIRECT);
        if (expectedErrors.length > 0 && !List.of(expectedErrors).equals(log)) {
            throw new AssertionError("Incorrect errors, expected: " + List.of(expectedErrors) +
                                      ", actual: " + log);
        }
    }

}
