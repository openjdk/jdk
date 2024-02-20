/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8262891 8268871 8274363 8281100 8294670 8311038 8311815 8325215
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import toolbox.JavacTask;
import toolbox.Task;
import toolbox.TestRunner;
import toolbox.ToolBox;

public class Exhaustiveness extends TestRunner {

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
                           case A a when a.toString().isEmpty() -> 0;
                           case B b -> 1;
                       };
                   }
               }
               """,
               "Test.java:5:16: compiler.err.not.exhaustive",
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
                           case A a when !(!(TEST)) -> 0;
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
                           case A a when false -> 0;
                           case B b -> 1;
                       };
                   }
               }
               """,
               "Test.java:6:27: compiler.err.guard.has.constant.expression.false",
               "Test.java:5:16: compiler.err.not.exhaustive",
               "2 errors");
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
                       }
                   }
               }
               """,
               "Test.java:4:9: compiler.err.not.exhaustive.statement",
               "1 error");
    }

    @Test
    public void testExhaustiveExpression1(Path base) throws Exception {
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
               "1 error");
    }

    @Test
    public void testExhaustiveExpression2(Path base) throws Exception {
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
                           case C c when b -> 0;
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
                           case D d when b -> 0;
                       };
                   }
               }
               """,
               "Test.java:5:16: compiler.err.not.exhaustive",
               "1 error");
    }

    @Test
    public void testIntersection(Path base) throws Exception {
        record TestCase(String snippet, String... expected){}
        TestCase[] testCases = new TestCase[] {
            new TestCase("""
                         return switch (obj) {
                             case A a -> 0;
                             case C c when b -> 0;
                             case C c -> 0;
                             case D d -> 0;
                         };
                         """),
            new TestCase("""
                         return switch (obj) {
                             case A a -> 0;
                             case C c -> 0;
                             case D d when b -> 0;
                         };
                         """,
                         "Test.java:5:16: compiler.err.not.exhaustive",
                         "1 error")
        };
        for (TestCase tc : testCases) {
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
                           ${tc.snippet()}
                       }
                   }
                   """.replace("${tc.snippet()}", tc.snippet()),
                   tc.expected());
        }
    }

    @Test
    public void testRecordPatterns(Path base) throws Exception {
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
                            """,
                            """
                            package lib;
                            public record R(S a, S b) {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private int test(R r) {
                       return switch (r) {
                           case R(A a, A b) -> 0;
                           case R(A a, B b) -> 0;
                           case R(B a, A b) -> 0;
                           case R(B a, B b) -> 0;
                       };
                   }
               }
               """);
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
                            public record B(Object o) implements S {}
                            """,
                            """
                            package lib;
                            public record R(S a, S b) {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private int test(R r) {
                       return switch (r) {
                           case R(A a, A b) -> 0;
                           case R(A a, B b) -> 0;
                           case R(B a, A b) -> 0;
                           case R(B a, B(String s)) -> 0;
                       };
                   }
               }
               """,
               "Test.java:5:16: compiler.err.not.exhaustive",
               "1 error");
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
                            public record B(Object o) implements S {}
                            """,
                            """
                            package lib;
                            public record R(S a, S b) {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private int test(R r) {
                       return switch (r) {
                           case R(A a, A b) -> 0;
                           case R(A a, B b) -> 0;
                           case R(B a, A b) -> 0;
                           case R(B a, B(var o)) -> 0;
                       };
                   }
               }
               """);
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
                            public record B(Object o) implements S {}
                            """,
                            """
                            package lib;
                            public record R(S a, S b) {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private int test(R r) {
                       return switch (r) {
                           case R(A a, A b) -> 0;
                           case R(A a, B b) -> 0;
                           case R(B a, A b) -> 0;
                           case R(B(String s), B b) -> 0;
                       };
                   }
               }
               """,
               "Test.java:5:16: compiler.err.not.exhaustive",
               "1 error");
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
                            public record B(Object o) implements S {}
                            """,
                            """
                            package lib;
                            public record R(S a, S b) {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private int test(R r) {
                       return switch (r) {
                           case R(A a, A b) -> 0;
                           case R(A a, B b) -> 0;
                           case R(B a, A b) -> 0;
                           case R(B(Object o), B b) -> 0;
                       };
                   }
               }
               """);
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
                            public record B(Object o) implements S {}
                            """,
                            """
                            package lib;
                            public record R(S a, S b) {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private int test(R r) {
                       return switch (r) {
                           case R(A a, A b) -> 0;
                           case R(A a, B b) -> 0;
                           case R(B(String s), B b) -> 0;
                           case R(B a, A b) -> 0;
                       };
                   }
               }
               """,
               "Test.java:5:16: compiler.err.not.exhaustive",
               "1 error");
        doTest(base,
               new String[]{"""
                            package lib;
                            public record R(Object o1, Object o2) {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private int test(R r) {
                       return switch (r) {
                           case R(String s, Object o) -> 0;
                       };
                   }
               }
               """,
               "Test.java:5:16: compiler.err.not.exhaustive",
               "1 error");
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
                            public record B(S o) implements S {}
                            """,
                            """
                            package lib;
                            public record R(S a, String s, S b) {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private int test(R r) {
                       return switch (r) {
                           case R(A a, String s, A b) -> 0;
                           case R(A a, String s, B b) -> 0;
                           case R(B a, String s, A b) -> 0;
                           case R(B(A o), String s, B b) -> 0;
                       };
                   }
               }
               """,
               "Test.java:5:16: compiler.err.not.exhaustive",
               "1 error");
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
                            public record B(S o) implements S {}
                            """,
                            """
                            package lib;
                            public record R(S a, String s, S b) {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private int test(R r) {
                       return switch (r) {
                           case R(A a, String s, A b) -> 0;
                           case R(A a, String s, B b) -> 0;
                           case R(B a, String s, A b) -> 0;
                           case R(B(A o), String s, B b) -> 0;
                           case R(B(B o), String s, B b) -> 0;
                       };
                   }
               }
               """);
    }

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
                         "1 error"),
            new TestCase("""
                                     case C3<Integer> c -> {}
                                     case C6<?, Integer> c -> {}
                         """,
                         "Test.java:11:9: compiler.err.not.exhaustive.statement",
                         "1 error"),
            new TestCase("""
                                     case C3<Integer> c -> {}
                                     case C5<Integer, ?> c -> {}
                         """,
                         "Test.java:11:9: compiler.err.not.exhaustive.statement",
                         "1 error"),
            new TestCase("""
                                     case C1 c -> {}
                                     case C3<Integer> c -> {}
                                     case C5<Integer, ?> c -> {}
                                     case C6<?, Integer> c -> {}
                         """,
                         "Test.java:12:18: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: test.Test.I<java.lang.Integer>, test.Test.C1)",
                         "1 error"),
            new TestCase("""
                                     case C2<?> c -> {}
                                     case C3<Integer> c -> {}
                                     case C5<Integer, ?> c -> {}
                                     case C6<?, Integer> c -> {}
                         """,
                         "Test.java:12:18: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: test.Test.I<java.lang.Integer>, test.Test.C2<?>)",
                         "1 error"),
            new TestCase("""
                                     case C4<?, ?> c -> {}
                                     case C3<Integer> c -> {}
                                     case C5<Integer, ?> c -> {}
                                     case C6<?, Integer> c -> {}
                         """,
                         "Test.java:12:18: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: test.Test.I<java.lang.Integer>, test.Test.C4<?,?>)",
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
               "4 errors");
    }

    @Test
    public void testSuperTypesInPattern(Path base) throws Exception {
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
                            """,
                            """
                            package lib;
                            public record R(S a, S b) {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private void testStatement(R obj) {
                       switch (obj) {
                           case R(A a, A b): break;
                           case R(A a, B b): break;
                           case R(B a, A b): break;
                           case R(B a, B b): break;
                       }
                       switch (obj) {
                           case R(S a, A b): break;
                           case R(S a, B b): break;
                       }
                       switch (obj) {
                           case R(Object a, A b): break;
                           case R(Object a, B b): break;
                       }
                   }
               }
               """);
    }

    @Test
    public void testNonPrimitiveBooleanGuard(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   sealed interface A {}
                   final class B1 implements A {}
                   final class B2 implements A {}

                   void test(A arg, Boolean g) {
                       int i = switch (arg) {
                           case B1 b1 when g -> 1;
                           case B2 b2 -> 2;
                       };
                   }
               }
               """,
               "Test.java:8:17: compiler.err.not.exhaustive",
               "1 error");
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   sealed interface A {}
                   final class B1 implements A {}
                   final class B2 implements A {}

                   void test(A arg) {
                       int i = switch (arg) {
                           case B1 b1 when undefined() -> 1;
                           case B2 b2 -> 2;
                       };
                   }
               }
               """,
               "Test.java:9:29: compiler.err.cant.resolve.location.args: kindname.method, undefined, , , (compiler.misc.location: kindname.class, test.Test, null)",
               "Test.java:8:17: compiler.err.not.exhaustive",
               "2 errors");
    }

    @Test //JDK-8294670
    public void testImplicitDefaultCannotCompleteNormally(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   sealed interface A {}
                   final class B implements A {}

                   int test(A arg) {
                       switch (arg) {
                           case B b: return 1;
                       }
                   }
               }
               """);
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   sealed interface A {}
                   final class B implements A {}

                   int test(A arg) {
                       switch (arg) {
                           case B b: return 1;
                           default: return 1;
                       }
                   }
               }
               """);
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   sealed interface A {}
                   final class B implements A {}

                   int test(A arg) {
                       switch (arg) {
                           case B b: break;
                       }
                   }
               }
               """,
               "Test.java:10:5: compiler.err.missing.ret.stmt",
               "1 error");
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   sealed interface A {}
                   final class B implements A {}

                   int test(A arg) {
                       switch (arg) {
                           case B b: return 1;
                           default: break;
                       }
                   }
               }
               """,
               "Test.java:11:5: compiler.err.missing.ret.stmt",
               "1 error");
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   sealed interface A {}
                   final class B implements A {}

                   int test(A arg) {
                       switch (arg) {
                           case B b:
                       }
                   }
               }
               """,
               "Test.java:10:5: compiler.err.missing.ret.stmt",
               "1 error");
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   sealed interface A {}
                   final class B implements A {}

                   int test(A arg) {
                       switch (arg) {
                           case B b: return 1;
                           default:
                       }
                   }
               }
               """,
               "Test.java:11:5: compiler.err.missing.ret.stmt",
               "1 error");
    }

    @Test
    public void testInferenceExhaustive(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   sealed interface Opt<T> {}
                   record Some<T>(T t) implements Opt<T> {}
                   final class None<T> implements Opt<T> {}

                   void test(Opt<String> optValue) {
                       switch (optValue) {
                           case Some<String>(String s) ->
                               System.out.printf("got string: %s%n", s);
                           case None<String> none ->
                               System.out.println("got none");
                       }
                   }
               }
               """);
    }

    @Test
    public void testEnumExhaustiveness(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   sealed interface Opt {}
                   enum A implements Opt { A, B, C; }
                   enum B implements Opt { B, C, D; }

                   void test(Opt optValue) {
                       switch (optValue) {
                           case A.A, A.B -> {}
                           case A.C, B.C -> {}
                           case B.B, B.D -> {}
                       }
                   }
               }
               """);
    }

    public void testNestedApplicable(Path base) throws Exception {
        record TestCase(String cases, String... errors) {}
        TestCase[] subCases = new TestCase[] {
            new TestCase("""
                                     case R(C3<Integer> c) -> {}
                                     case R(C5<Integer, ?> c) -> {}
                                     case R(C6<?, Integer> c) -> {}
                         """), //OK
            new TestCase("""
                                     case R(C5<Integer, ?> c) -> {}
                                     case R(C6<?, Integer> c) -> {}
                         """,
                         "Test.java:12:9: compiler.err.not.exhaustive.statement",
                         "- compiler.note.preview.filename: Test.java, DEFAULT",
                         "- compiler.note.preview.recompile",
                         "1 error"),
            new TestCase("""
                                     case R(C3<Integer> c) -> {}
                                     case R(C6<?, Integer> c) -> {}
                         """,
                         "Test.java:12:9: compiler.err.not.exhaustive.statement",
                         "- compiler.note.preview.filename: Test.java, DEFAULT",
                         "- compiler.note.preview.recompile",
                         "1 error"),
            new TestCase("""
                                     case R(C3<Integer> c) -> {}
                                     case R(C5<Integer, ?> c) -> {}
                         """,
                         "Test.java:12:9: compiler.err.not.exhaustive.statement",
                         "- compiler.note.preview.filename: Test.java, DEFAULT",
                         "- compiler.note.preview.recompile",
                         "1 error"),
            new TestCase("""
                                     case R(C1 c) -> {}
                                     case R(C3<Integer> c) -> {}
                                     case R(C5<Integer, ?> c) -> {}
                                     case R(C6<?, Integer> c) -> {}
                         """,
                         "Test.java:13:20: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: test.Test.I<java.lang.Integer>, test.Test.C1)",
                         "- compiler.note.preview.filename: Test.java, DEFAULT",
                         "- compiler.note.preview.recompile",
                         "1 error"),
            new TestCase("""
                                     case R(C2<?> c) -> {}
                                     case R(C3<Integer> c) -> {}
                                     case R(C5<Integer, ?> c) -> {}
                                     case R(C6<?, Integer> c) -> {}
                         """,
                         "Test.java:13:20: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: test.Test.I<java.lang.Integer>, test.Test.C2<?>)",
                         "- compiler.note.preview.filename: Test.java, DEFAULT",
                         "- compiler.note.preview.recompile",
                         "1 error"),
            new TestCase("""
                                     case R(C4<?, ?> c) -> {}
                                     case R(C3<Integer> c) -> {}
                                     case R(C5<Integer, ?> c) -> {}
                                     case R(C6<?, Integer> c) -> {}
                         """,
                         "Test.java:13:20: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: test.Test.I<java.lang.Integer>, test.Test.C4<?,?>)",
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
                       record R<T>(I<T> i) {}
                       void t(R<Integer> i) {
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
    public void testComplexSubTypes1(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   sealed interface I permits I1, I2, I3 {}
                   sealed interface I1 extends I permits C1 {}
                   sealed interface I2 extends I {}
                   sealed interface I3 extends I {}
                   final class C1 implements I1, I2 {}
                   final class C2 implements I3 {}

                   void test(I i) {
                       switch (i) {
                           case I2 i2 ->
                               System.out.println("I2");
                           case I3 i3 ->
                               System.out.println("I3");
                       }
                   }
               }
               """);
    }

    @Test
    public void testComplexSubTypes2(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   sealed interface I permits I1, I2, I3 {}
                   sealed interface I1 extends I permits C1 {}
                   sealed interface I2 extends I {}
                   sealed interface I3 extends I {}
                   sealed abstract class C1 implements I1 {}
                   final class C2 extends C1 implements I2 {}
                   final class C3 extends C1 implements I3 {}

                   void test(I i) {
                       switch (i) {
                           case I2 i2 ->
                               System.out.println("I2");
                           case I3 i3 ->
                               System.out.println("I3");
                       }
                   }
               }
               """);
    }

    @Test
    public void testComplexSubTypes3(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   sealed interface I {}
                   sealed interface I1 extends I {}
                   final class I2 implements I1 {}

                   void test(I i) {
                       switch (i) {
                           case I1 i1 ->
                               System.out.println("I1");
                           case I2 i2 ->
                               System.out.println("I2");
                       }
                   }
               }
               """,
               "Test.java:11:18: compiler.err.pattern.dominated",
               "1 error");
    }

    @Test
    public void testComplexSubTypes4(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   sealed interface I permits I1, I2, I3 {}
                   sealed interface I1 extends I permits C1 {}
                   sealed class I2 implements I {}
                   sealed interface I3 extends I {}
                   final class C1 extends I2 implements I1 {}
                   final class C2 implements I3 {}

                   void test(I i) {
                       switch (i) {
                           case I2 i2 ->
                               System.out.println("I2");
                           case I3 i3 ->
                               System.out.println("I3");
                       }
                   }
               }
               """,
               "Test.java:11:9: compiler.err.not.exhaustive.statement",
               "1 error");
    }

    @Test
    public void testComplexSubTypes5(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               class Test {
                   sealed interface I permits A, B, C { }
                   interface I3 { }
                   sealed interface I2 extends I3 permits B, C { }
                   final class A implements I {}
                   final class B implements I, I2 {}
                   final class C implements I, I2 {}

                   int m(I i) {
                       return switch (i) {
                           case A a -> 1;
                           case I3 e -> 2;
                       };
                   }
               }
               """);
    }

    @Test
    public void testComplexSubTypes6(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               class Test {
                   sealed interface I0 permits I1, I2 { }
                   sealed interface I00 permits I1, I2 { }

                   sealed interface I1 extends I0, I00 permits B, C { }
                   sealed interface I2 extends I0, I00 permits B, C { }

                   static final class B implements I1, I2 { }
                   static final class C implements I1, I2 { }

                   int test(Object o) {
                       return switch (o) {
                           case B c -> 2;
                           case C d -> 3;
                       };
                   }
               }
               """,
               "Test.java:12:16: compiler.err.not.exhaustive",
               "1 error");
    }

    private static final int NESTING_CONSTANT = 4;

    Set<String> createDeeplyNestedVariants() {
        Set<String> variants = new HashSet<>();
        variants.add("C _");
        variants.add("R(I _, I _, I _)");
        for (int n = 0; n < NESTING_CONSTANT; n++) {
            Set<String> newVariants = new HashSet<>();
            for (String variant : variants) {
                if (variant.contains(", I _")) {
                    newVariants.add(variant.replaceFirst(", I _", ", C _"));
                    newVariants.add(variant.replaceFirst(", I _", ", R(I _, I _, I _)"));
                } else {
                    newVariants.add(variant);
                }
            }
            variants = newVariants;
        }
        for (int n = 0; n < NESTING_CONSTANT; n++) {
            Set<String> newVariants = new HashSet<>();
            for (String variant : variants) {
                if (variant.contains("I _")) {
                    newVariants.add(variant.replaceFirst("I _", "C _"));
                    newVariants.add(variant.replaceFirst("I _", "R(I _, I _, I _)"));
                } else {
                    newVariants.add(variant);
                }
            }
            variants = newVariants;
        }
        OUTER: for (int i = 0; i < NESTING_CONSTANT; i++) {
            Iterator<String> it = variants.iterator();
            while (it.hasNext()) {
                String current = it.next();
                if (current.contains("(I _, I _, I _)")) {
                    it.remove();
                    variants.add(current.replaceFirst("\\(I _, I _, I _\\)", "(C _, I _, C _)"));
                    variants.add(current.replaceFirst("\\(I _, I _, I _\\)", "(C _, I _, R _)"));
                    variants.add(current.replaceFirst("\\(I _, I _, I _\\)", "(R _, I _, C _)"));
                    variants.add(current.replaceFirst("\\(I _, I _, I _\\)", "(R _, I _, R _)"));
                    continue OUTER;
                }
            }
        }

        return variants;
    }

    String testCodeForVariants(Iterable<String> variants) {
        StringBuilder cases = new StringBuilder();

        for (String variant : variants) {
            cases.append("case ");
            String[] parts = variant.split("_");
            for (int i = 0; i < parts.length; i++) {
                cases.append(parts[i]);
                if (i + 1 < parts.length || i == 0) {
                    cases.append("v" + i);
                }
            }
            cases.append(" -> System.err.println();\n");
        }
        String code = """
               package test;
               public class Test {
                   sealed interface I {}
                   final class C implements I {}
                   record R(I c1, I c2, I c3) implements I {}

                   void test(I i) {
                       switch (i) {
                           ${cases}
                       }
                   }
               }
               """.replace("${cases}", cases);

        return code;
    }

    @Test
    public void testDeeplyNestedExhaustive(Path base) throws Exception {
        Set<String> variants = createDeeplyNestedVariants();
        String code = testCodeForVariants(variants);

        System.err.println("analyzing:");
        System.err.println(code);
        doTest(base,
               new String[0],
               code,
               true);
    }

    @Test
    public void testDeeplyNestedNotExhaustive(Path base) throws Exception {
        List<String> variants = createDeeplyNestedVariants().stream().collect(Collectors.toCollection(ArrayList::new));
        variants.remove((int) (Math.random() * variants.size()));
        String code = testCodeForVariants(variants);

        System.err.println("analyzing:");
        System.err.println(code);
        doTest(base,
               new String[0],
               code,
               new String[] {null});
    }

    @Test
    public void testMultipleSealed(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   sealed interface I1 {}
                   sealed interface I2 {}
                   final class A_I1 implements I1 {}
                   final class B_I2 implements I2 {}
                   final class C_I1_I2 implements I1, I2 {}

                   <Z extends I1&I2> void test(Z z) {
                       switch (z) {
                           case C_I1_I2 c ->
                               System.out.println("C_I1_I2");
                       }
                   }
               }
               """);
    }

    @Test
    public void testOverfitting(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   sealed interface I {}
                   final class A implements I {}
                   final class B implements I {}
                   record R(String s, I i) {}

                   void test(R r) {
                       switch (r) {
                           case R(CharSequence s, A a) ->
                               System.out.println("A");
                           case R(Object o, B b) ->
                               System.out.println("B");
                       }
                   }
               }
               """);
    }

    @Test
    public void testDiamondInheritance1(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   sealed interface I {}
                   sealed abstract class A {}
                   final class C extends A implements I {}

                   void test(I i) {
                       switch (i) {
                           case C c ->
                               System.out.println("C");
                       }
                   }
               }
               """);
    }

    @Test
    public void testDiamondInheritance2(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   sealed interface I {}
                   sealed abstract class A {}
                   final class C extends A implements I {}
                   record R(I i) {}

                   void test(R r) {
                       switch (r) {
                           case R(C c) ->
                               System.out.println("C");
                       }
                   }
               }
               """);
    }

    @Test
    public void testDiamondInheritance3(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   sealed interface I {}
                   sealed interface S1 extends I {}
                   sealed interface S2 extends I {}
                   final class C1 implements S1 {}
                   final class C2 implements S2 {}
                   final class C12 implements S1, S2 {}

                   void test(I i) {
                       switch (i) {
                           case C1 c ->
                               System.out.println("C1");
                           case C2 c ->
                               System.out.println("C2");
                           case C12 c ->
                               System.out.println("C12");
                       }
                   }
               }
               """);
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   sealed interface I {}
                   sealed interface S1 extends I {}
                   sealed interface S2 extends I {}
                   final class C1 implements S1 {}
                   final class C2 implements S2 {}
                   final class C12 implements S1, S2 {}

                   void test(I i) {
                       switch (i) {
                           case C12 c ->
                               System.out.println("C12");
                           case C1 c ->
                               System.out.println("C1");
                           case C2 c ->
                               System.out.println("C2");
                       }
                   }
               }
               """);
    }

    @Test
    public void testDiamondInheritance4(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   sealed interface I {}
                   sealed interface S1 extends I {}
                   sealed interface S2 extends I {}
                   final class C1 implements S1 {}
                   final class C2 implements S2 {}
                   final class C12 implements S1, S2 {}
                   record R(I i) {}

                   void test(R r) {
                       switch (r) {
                           case R(C1 c) ->
                               System.out.println("C1");
                           case R(C2 c) ->
                               System.out.println("C2");
                           case R(C12 c) ->
                               System.out.println("C12");
                       }
                   }
               }
               """);
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   sealed interface I {}
                   sealed interface S1 extends I {}
                   sealed interface S2 extends I {}
                   final class C1 implements S1 {}
                   final class C2 implements S2 {}
                   final class C12 implements S1, S2 {}
                   record R(I i) {}

                   void test(R r) {
                       switch (r) {
                           case R(C12 c) ->
                               System.out.println("C12");
                           case R(C1 c) ->
                               System.out.println("C1");
                           case R(C2 c) ->
                               System.out.println("C2");
                       }
                   }
               }
               """);
    }

    @Test
    public void testDiamondInheritance5(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   sealed interface I {}
                   sealed interface S1 extends I {}
                   sealed interface S2 extends I {}
                   sealed interface S3 extends I {}
                   final class C13 implements S1, S3 {}
                   final class C23 implements S2, S3 {}

                   void test(I i) {
                       switch (i) {
                           case C13 c ->
                               System.out.println("C13");
                           case C23 c ->
                               System.out.println("C23");
                       }
                   }
               }
               """);
    }

    @Test
    public void testDiamondInheritance6(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   sealed interface I {}
                   sealed interface S1 extends I {}
                   sealed interface S2 extends I {}
                   final class C1 implements S1 {}
                   sealed abstract class C2 implements S2 {}
                   final class C2Prime extends C2 {}
                   final class C12 implements S1, S2 {}

                   void test(I i) {
                       switch (i) {
                           case C1 c ->
                               System.out.println("C1");
                           case C2Prime c ->
                               System.out.println("C2");
                           case C12 c ->
                               System.out.println("C12");
                       }
                   }
               }
               """);
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   sealed interface I {}
                   sealed interface S1 extends I {}
                   sealed interface S2 extends I {}
                   final class C1 implements S1 {}
                   sealed abstract class C2 implements S2 {}
                   final class C2Prime extends C2 {}
                   final class C12 implements S1, S2 {}

                   void test(I i) {
                       switch (i) {
                           case C2Prime c ->
                               System.out.println("C2");
                           case C1 c ->
                               System.out.println("C1");
                           case C12 c ->
                               System.out.println("C12");
                       }
                   }
               }
               """);
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   sealed interface I {}
                   sealed interface S1 extends I {}
                   sealed interface S2 extends I {}
                   final class C1 implements S1 {}
                   sealed abstract class C2 implements S2 {}
                   final class C2Prime extends C2 {}
                   final class C12 implements S1, S2 {}

                   void test(I i) {
                       switch (i) {
                           case C12 c ->
                               System.out.println("C12");
                           case C1 c ->
                               System.out.println("C1");
                           case C2Prime c ->
                               System.out.println("C2");
                       }
                   }
               }
               """);
    }

    @Test //JDK-8311038
    public void testReducesTooMuch(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   void test(Rec r) {
                       switch (r) {
                           case Rec(String s) ->
                               System.out.println("I2" + s.toString());
                           case Rec(Object o) ->
                               System.out.println("I2");
                       }
                   }
                   record Rec(Object o) {}
               }
               """);
    }

    @Test //JDK-8311815:
    public void testAmbiguousRecordUsage(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                 record Pair(I i1, I i2) {}
                 sealed interface I {}
                 record C() implements I {}
                 record D() implements I {}

                 void exhaustinvenessWithInterface(Pair pairI) {
                   switch (pairI) {
                     case Pair(D fst, C snd) -> {
                     }
                     case Pair(C fst, C snd) -> {
                     }
                     case Pair(C fst, I snd) -> {
                     }
                     case Pair(D fst, D snd) -> {
                     }
                   }
                 }
               }
               """);
    }

    @Test //JDK-8325215:
    public void testTooGenericPatternInRecord(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   sealed interface A permits T, U {}
                   sealed interface B permits V, W {}

                   static final class T implements A { public T() {} }
                   static final class U implements A { public U() {} }

                   static final class V implements B { public V() {} }
                   static final class W implements B { public W() {} }

                   final static record R(A a, B b) { }

                   static int r(R r) {
                      return switch (r) {
                          case R(A a, V b) -> 1; // Any A with specific B
                          case R(T a, B b) -> 2; // Specific A with any B
                          case R(U a, W b) -> 3; // Specific A with specific B
                      };
                   }
               }
               """);
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   sealed interface A permits T, U {}
                   sealed interface B permits V, W {}

                   static final class T implements A { public T() {} }
                   static final class U implements A { public U() {} }

                   static final class V implements B { public V() {} }
                   static final class W implements B { public W() {} }

                   final static record R(B b, A a) { }

                   static int r(R r) {
                      return switch (r) {
                          case R(V b, A a) -> 1; // Any A with specific B
                          case R(B b, T a) -> 2; // Specific A with any B
                          case R(W b, U a) -> 3; // Specific A with specific B
                      };
                   }
               }
               """);
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   sealed interface A permits T, U {}
                   sealed interface B permits V, W {}

                   static final class T implements A { public T() {} }
                   static final class U implements A { public U() {} }

                   static final class V implements B { public V() {} }
                   static final class W implements B { public W() {} }

                   final static record X(B b) { }
                   final static record R(A a, X x) { }

                   static int r(R r) {
                      return switch (r) {
                          case R(A a, X(V b)) -> 1; // Any A with specific B
                          case R(T a, X(B b)) -> 2; // Specific A with any B
                          case R(U a, X(W b)) -> 3; // Specific A with specific B
                      };
                   }
               }
               """);
    }

    private void doTest(Path base, String[] libraryCode, String testCode, String... expectedErrors) throws IOException {
        doTest(base, libraryCode, testCode, false, expectedErrors);
    }

    private void doTest(Path base, String[] libraryCode, String testCode, boolean stopAtFlow, String... expectedErrors) throws IOException {
        Path current = base.resolve(".");
        Path libClasses = current.resolve("libClasses");

        Files.createDirectories(libClasses);

        if (libraryCode.length != 0) {
            Path libSrc = current.resolve("lib-src");

            for (String code : libraryCode) {
                tb.writeJavaFiles(libSrc, code);
            }

            new JavacTask(tb)
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
                    .options("-XDrawDiagnostics",
                             "-Xlint:-preview",
                             "--class-path", libClasses.toString(),
                             "-XDshould-stop.at=FLOW",
                             stopAtFlow ? "-XDshould-stop.ifNoError=FLOW"
                                        : "-XDnoop")
                    .outdir(classes)
                    .files(tb.findJavaFiles(src))
                    .run(expectedErrors.length > 0 ? Task.Expect.FAIL : Task.Expect.SUCCESS)
                    .writeAll()
                    .getOutputLines(Task.OutputKind.DIRECT);
        if (expectedErrors.length > 0 && expectedErrors[0] != null && !List.of(expectedErrors).equals(log)) {
            throw new AssertionError("Incorrect errors, expected: " + List.of(expectedErrors) +
                                      ", actual: " + log);
        }
    }

}
