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

/**
 * @test
 * @bug 8367530
 * @summary Check enhanced exhaustiveness errors
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main ExhaustivenessConvenientErrors
*/

import com.sun.tools.javac.api.ClientCodeWrapper.DiagnosticSourceUnwrapper;
import com.sun.tools.javac.util.JCDiagnostic;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import toolbox.JavacTask;
import toolbox.Task;
import toolbox.TestRunner;
import toolbox.ToolBox;

public class ExhaustivenessConvenientErrors extends TestRunner {

    ToolBox tb;

    public static void main(String... args) throws Exception {
        new ExhaustivenessConvenientErrors().runTests();
    }

    ExhaustivenessConvenientErrors() {
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
                       };
                   }
               }
               """,
               "lib.B _");
    }

    @Test
    public void testExhaustiveSealedClassesTransitive(Path base) throws Exception {
        doTest(base,
               new String[]{"""
                            package lib;
                            public sealed interface S1 permits S2, A {}
                            """,
                            """
                            package lib;
                            public sealed interface S2 extends S1 permits S3, B {}
                            """,
                            """
                            package lib;
                            public sealed interface S3 extends S2 permits C, D {}
                            """,
                            """
                            package lib;
                            public final class A implements S1 {}
                            """,
                            """
                            package lib;
                            public final class B implements S2 {}
                            """,
                            """
                            package lib;
                            public final class C implements S3 {}
                            """,
                            """
                            package lib;
                            public final class D implements S3 {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private int test(S1 obj) {
                       return switch (obj) {
                           case A a -> 0;
                           case B a -> 0;
                           case D a -> 0;
                       };
                   }
               }
               """,
               "lib.C _");
    }

    @Test
    public void testTrivialRecord(Path base) throws Exception {
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
                            public record R(S s) {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private int test(R r) {
                       return switch (r) {
                           case R(A a) -> 0;
                       };
                   }
               }
               """,
               "lib.R(lib.B _)");
    }

    @Test
    public void testNonNestedRecord(Path base) throws Exception {
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
                            public record R(S s1, S s2) {}
                            """},
               """
               package test;
               import lib.*;
               public class Test {
                   private int test(R r) {
                       return switch (r) {
                           case R(A a, B b) -> 0;
                           case R(B b, A a) -> 0;
                       };
                   }
               }
               """,
               "lib.R(lib.A _,lib.A _)",
               "lib.R(lib.B _,lib.B _)");
    }

    @Test
    public void testComplex1(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               package test;
               import lib.*;
               public class Test {
                   private int test(Root r) {
                       return switch (r) {
                           case Root(R1 _, _, _) -> 0;
                       };
                   }
                   sealed interface Base {}
                   record R1() implements Base {}
                   record R2() implements Base {}
                   record R3(Base b1, Base b2) implements Base {}
                   record Root(Base b1, Base b2, Base b3) {}
               }
               """,
               "test.Test.Root(test.Test.R2 _,test.Test.Base _,test.Test.Base _)",
               "test.Test.Root(test.Test.R3 _,test.Test.Base _,test.Test.Base _)");
    }

    @Test
    public void testComplex2(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               package test;
               import lib.*;
               public class Test {
                   private int test(Root r) {
                       return switch (r) {
                           case Root(R1 _, _, _) -> 0;
                           case Root(R2 _, R1 _, _) -> 0;
                           case Root(R2 _, R2 _, R1 _) -> 0;
                           case Root(R2 _, R2(R1 _, R1 _), R2(R1 _, R1 _)) -> 0;
                           case Root(R2 _, R2(R1 _, R1 _), R2(R1 _, R2 _)) -> 0;
                           case Root(R2 _, R2(R1 _, R1 _), R2(R2 _, R1 _)) -> 0;
                           case Root(R2 _, R2(R1 _, R1 _), R2(R2 _, R2 _)) -> 0;
                           case Root(R2 _, R2(R1 _, R2 _), R2(R1 _, R1 _)) -> 0;
                           case Root(R2 _, R2(R1 _, R2 _), R2(R1 _, R2 _)) -> 0;
                           case Root(R2 _, R2(R1 _, R2 _), R2(R2 _, R1 _)) -> 0;
                           case Root(R2 _, R2(R1 _, R2 _), R2(R2 _, R2 _)) -> 0;
                           case Root(R2 _, R2(R2 _, R1 _), R2(R1 _, R1 _)) -> 0;
                           case Root(R2 _, R2(R2 _, R1 _), R2(R1 _, R2 _)) -> 0;
                           case Root(R2 _, R2(R2 _, R1 _), R2(R2 _, R1 _)) -> 0;
                           case Root(R2 _, R2(R2 _, R1 _), R2(R2 _, R2 _)) -> 0;
                           case Root(R2 _, R2(R2 _, R2 _), R2(R1 _, R1 _)) -> 0;
                           case Root(R2 _, R2(R2 _, R2 _), R2(R1 _, R2 _)) -> 0;
                           case Root(R2 _, R2(R2 _, R2 _), R2(R2 _, R1 _)) -> 0;
//                           case Root(R2 _, R2(R2 _, R2 _), R2(R2 _, R2 _)) -> 0;
                       };
                   }
                   sealed interface Base {}
                   record R1() implements Base {}
                   record R2(Base b1, Base b2) implements Base {}
                   record Root(Base b1, Base b2, Base b3) {}
               }
               """,
               "test.Test.Root(test.Test.R2 _,test.Test.R2(test.Test.R2 _,test.Test.R2 _),test.Test.R2(test.Test.R2 _,test.Test.R2 _))");
    }

    @Test
    public void testComplex3(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   private int test(Triple p) {
                       return switch (p) {
                           case Triple(B _, _, _) -> 0;
                           case Triple(_, A _, _) -> 0;
                           case Triple(_, _, A _) -> 0;
                           case Triple(A p, C(Nested _, NestedBaseA _), _) -> 0;
                           case Triple(A p, C(Nested _, NestedBaseB _), C(Nested _, NestedBaseA _)) -> 0;
                           case Triple(A p, C(Nested _, NestedBaseB _), C(Nested _, NestedBaseB _)) -> 0;
                           case Triple(A p, C(Nested _, NestedBaseB _), C(Nested _, NestedBaseC _)) -> 0;
                           case Triple(A p, C(Nested _, NestedBaseC _), C(Nested _, NestedBaseA _)) -> 0;
                           case Triple(A p, C(Nested _, NestedBaseC _), C(Nested _, NestedBaseB _)) -> 0;
//                           case Path(A p, C(Nested _, NestedBaseC _), C(Nested _, NestedBaseC _)) -> 0;
                       };
                   }
                   record Triple(Base c1, Base c2, Base c3) {}
                   sealed interface Base permits A, B {}
                   record A(boolean key) implements Base {
                   }
                   sealed interface B extends Base {}
                   record C(Nested n, NestedBase b) implements B {}
                   record Nested() {}
                   sealed interface NestedBase {}
                   record NestedBaseA() implements NestedBase {}
                   record NestedBaseB() implements NestedBase {}
                   record NestedBaseC() implements NestedBase {}
               }
               """,
               "test.Test.Triple(test.Test.A _,test.Test.C(test.Test.Nested _,test.Test.NestedBaseC _),test.Test.C(test.Test.Nested _,test.Test.NestedBaseC _))");
    }

    @Test
    public void testComplex4(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               package test;
               import lib.*;
               public class Test {
                   private int test(Root r) {
                       return switch (r) {
                           case Root(R1 _, _, _) -> 0;
                           case Root(R2 _, R1 _, _) -> 0;
                           case Root(R2 _, R2 _, R1 _) -> 0;
                           case Root(R2 _, R2(R1 _, R1 _), R2(R1 _, R1 _)) -> 0;
                           case Root(R2 _, R2(R1 _, R1 _), R2(R1 _, R2 _)) -> 0;
                           case Root(R2 _, R2(R1 _, R1 _), R2(R2 _, R1 _)) -> 0;
                           case Root(R2 _, R2(R1 _, R1 _), R2(R2 _, R2 _)) -> 0;
                           case Root(R2 _, R2(R1 _, R2 _), R2(R1 _, R1 _)) -> 0;
                           case Root(R2 _, R2(R1 _, R2 _), R2(R1 _, R2 _)) -> 0;
//                           case Root(R2 _, R2(R1 _, R2 _), R2(R2 _, R1 _)) -> 0;
                           case Root(R2 _, R2(R1 _, R2 _), R2(R2 _, R2 _)) -> 0;
                           case Root(R2 _, R2(R2 _, R1 _), R2(R1 _, R1 _)) -> 0;
                           case Root(R2 _, R2(R2 _, R1 _), R2(R1 _, R2 _)) -> 0;
                           case Root(R2 _, R2(R2 _, R1 _), R2(R2 _, R1 _)) -> 0;
                           case Root(R2 _, R2(R2 _, R1 _), R2(R2 _, R2 _)) -> 0;
                           case Root(R2 _, R2(R2 _, R2 _), R2(R1 _, R1 _)) -> 0;
                           case Root(R2 _, R2(R2 _, R2 _), R2(R1 _, R2 _)) -> 0;
                           case Root(R2 _, R2(R2 _, R2 _), R2(R2 _, R1 _)) -> 0;
//                           case Root(R2 _, R2(R2 _, R2 _), R2(R2 _, R2 _)) -> 0;
                       };
                   }
                   sealed interface Base {}
                   record R1() implements Base {}
                   record R2(Base b1, Base b2) implements Base {}
                   record Root(Base b1, Base b2, Base b3) {}
               }
               """,
               "test.Test.Root(test.Test.R2 _,test.Test.R2(test.Test.Base _,test.Test.R2 _),test.Test.R2(test.Test.R2 _,test.Test.Base _))");
               //ideally,the result would be as follow,but it is difficult to split Base on two distinct places:
//               "test.Test.Root(test.Test.R2 _,test.Test.R2(test.Test.R1 _,test.Test.R2 _),test.Test.R2(test.Test.R2 _,test.Test.R1 _))",
//               "test.Test.Root(test.Test.R2 _,test.Test.R2(test.Test.R2 _,test.Test.R2 _),test.Test.R2(test.Test.R2 _,test.Test.R2 _))");
    }

    @Test
    public void testComplex5(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   private int test(Triple p) {
                       return switch (p) {
                           case Triple(B _, _, _) -> 0;
                           case Triple(_, A _, _) -> 0;
                           case Triple(_, _, A _) -> 0;
//                           case Triple(A _, C(Nested _, NestedBaseA _), _) -> 0;
                           case Triple(A _, C(Nested _, NestedBaseB _), C(Nested _, NestedBaseA _)) -> 0;
                           case Triple(A _, C(Nested _, NestedBaseB _), C(Nested _, NestedBaseB _)) -> 0;
                           case Triple(A _, C(Nested _, NestedBaseB _), C(Nested _, NestedBaseC _)) -> 0;
                           case Triple(A _, C(Nested _, NestedBaseC _), C(Nested _, NestedBaseA _)) -> 0;
                           case Triple(A _, C(Nested _, NestedBaseC _), C(Nested _, NestedBaseB _)) -> 0;
//                           case Path(A _, C(Nested _, NestedBaseC _), C(Nested _, NestedBaseC _)) -> 0;
                       };
                   }
                   record Triple(Base c1, Base c2, Base c3) {}
                   sealed interface Base permits A, B {}
                   record A(boolean key) implements Base {
                   }
                   sealed interface B extends Base {}
                   record C(Nested n, NestedBase b) implements B {}
                   record Nested() {}
                   sealed interface NestedBase {}
                   record NestedBaseA() implements NestedBase {}
                   record NestedBaseB() implements NestedBase {}
                   record NestedBaseC() implements NestedBase {}
               }
               """,
               "test.Test.Triple(test.Test.A _,test.Test.C(test.Test.Nested _,test.Test.NestedBaseA _),test.Test.C _)",
               //the following could be:
               //test.Test.Triple(test.Test.A _,test.Test.C(test.Test.Nested _,test.Test.NestedBaseC _),test.Test.C(test.Test.Nested _,test.Test.NestedBaseC _))
               "test.Test.Triple(test.Test.A _,test.Test.C(test.Test.Nested _,test.Test.NestedBaseC _),test.Test.C _)");
    }

    @Test
    public void testNoInfiniteRecursion(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   private int test(R r) {
                       return switch (r) {
                           case R(_, _, R(_, _, _, _), String s) -> 0;
                           case R(_, _, R(_, _, _, String str), _) -> 0;
                       };
                   }
               }
               public record R(R r1, R r2, R r3, Object o) {}
               """,
               "test.R(test.R _,test.R _,test.R(test.R _,test.R _,test.R _,java.lang.Object _),java.lang.Object _)");
    }

    @Test
    public void testEnum(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   private int test(I i) {
                       return switch (i) {
                           case E.A -> 0;
                           case C _ -> 1;
                       };
                   }
                   sealed interface I {}
                   enum E implements I {A, B}
                   final class C implements I {}
               }
               public record R(R r1, R r2, R r3, Object o) {}
               """,
               "test.Test.E.B");
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   private int test(I i) {
                       return switch (i) {
                           case C _ -> 1;
                       };
                   }
                   sealed interface I {}
                   enum E implements I {A, B}
                   final class C implements I {}
               }
               public record R(R r1, R r2, R r3, Object o) {}
               """,
               "test.Test.E _");
    }

    @Test
    public void testInstantiateComponentTypes(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               package test;
               public class Test {
                   private int test(Pair<Base<Base>> p) {
                       return switch (p) {
                           case Pair(A(A(_)) -> 0;
                           case Pair(A(B(_)) -> 0;
                           case Pair(B(A(_)) -> 0;
                       };
                   }
                   record Pair<T>(T c) {}
                   sealed interface Base<T> permits A, B {}
                   record A<T>(T c) implements Base<T> {}
                   record B<T>(T c) implements Base<T> {}
               }
               """,
               "test.Test.Pair(test.Test.B(test.Test.B _))");
    }

    @Test
    public void testNeedToExpandIfRecordExists(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               package test;
                class Test {
                   sealed interface A { }
                   record B() implements A { }
                   record C(A a) implements A { }

                   void test(A a) {
                       switch (a) {
                           case C(B _) -> throw null;
                       }
                   }
               }               """,
               "test.Test.B _",
               "test.Test.C(test.Test.C _)");
    }

    @Test
    public void testComplex6(Path base) throws Exception {
        doTest(base,
               new String[0],
               """
               public class Test {
                   sealed interface Base {}
                   record NoOp() implements Base {}
                   record Const() implements Base {}
                   record Pair(Base n1,
                               Base b2) implements Base {}

                   int t(Base b) {
                       return switch (b) {
                           case NoOp _ -> 0;
                           case Const _ -> 0;
                           case Pair(NoOp _, _) -> 0;
                           case Pair(Const _, _) -> 0;
                           case Pair(Pair _, NoOp _) -> 0;
                           case Pair(Pair _, Const _) -> 0;
                           case Pair(Pair _, Pair(NoOp _, _)) -> 0;
                           case Pair(Pair _, Pair(Const _, _)) -> 0;
                           case Pair(Pair _, Pair(Pair(NoOp _, _), _)) -> 0;
                           case Pair(Pair _, Pair(Pair(Const _, _), _)) -> 0;
                           case Pair(Pair(NoOp _, _), Pair(Pair(Pair _, _), _)) -> 0;
                           case Pair(Pair(Const _, _), Pair(Pair(Pair _, _), _)) -> 0;
//                           case Pair(Pair(Pair _, _), Pair(Pair(Pair _, _), _)) -> 0;
                       };
                   }
               }
               """,
               "Test.Pair(Test.Pair(Test.Pair _,Test.Base _),Test.Pair(Test.Pair(Test.Pair _,Test.Base _),Test.Base _))");
    }

    private void doTest(Path base, String[] libraryCode, String testCode, String... expectedMissingPatterns) throws IOException {
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
        Set<String> missingPatterns = new HashSet<>();

        new JavacTask(tb)
            .options("-XDrawDiagnostics",
                     "--class-path", libClasses.toString(),
                     "-XDshould-stop.at=FLOW",
                     "-XDshould-stop.ifNoError=FLOW",
                     "-XDexhaustivityMaxBaseChecks=" + Long.MAX_VALUE) //never give up
            .outdir(classes)
            .files(tb.findJavaFiles(src))
            .diagnosticListener(d -> {
                if ("compiler.err.not.exhaustive.details".equals(d.getCode()) ||
                    "compiler.err.not.exhaustive.statement.details".equals(d.getCode())) {
                    if (d instanceof DiagnosticSourceUnwrapper uw) {
                        d = uw.d;
                    }
                    if (d instanceof JCDiagnostic.MultilineDiagnostic diag) {
                        diag.getSubdiagnostics()
                                .stream()
                                .map(fragment -> fragment.toString())
                                .forEach(missingPatterns::add);
                    }
                }
            })
            .run(Task.Expect.FAIL)
            .writeAll();

        Set<String> expectedPatterns = new HashSet<>(List.of(expectedMissingPatterns));

        if (!expectedPatterns.equals(missingPatterns)) {
            throw new AssertionError("Incorrect errors, expected: " + expectedPatterns +
                                      ", actual: " + missingPatterns);
        }
    }

}
