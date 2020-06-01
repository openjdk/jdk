/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * SealedCompilationTests
 *
 * @test
 * @summary Negative compilation tests, and positive compilation (smoke) tests for sealed classes
 * @library /lib/combo /tools/lib
 * @modules
 *     jdk.compiler/com.sun.tools.javac.util
 *     jdk.compiler/com.sun.tools.javac.api
 *     jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @compile --enable-preview -source ${jdk.version} SealedCompilationTests.java
 * @run testng/othervm --enable-preview SealedCompilationTests
 */

import java.lang.constant.ClassDesc;

import java.io.File;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.sun.tools.javac.util.Assert;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import tools.javac.combo.CompilationTestCase;

import toolbox.ToolBox;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.Task.OutputKind;

@Test
public class SealedCompilationTests extends CompilationTestCase {

    ToolBox tb = new ToolBox();

    // When sealed classes become a permanent feature, we don't need these any more
    private static String[] PREVIEW_OPTIONS = {"--enable-preview", "-source",
                                               Integer.toString(Runtime.version().feature())};

    {
        setDefaultFilename("SealedTest.java");
        setCompileOptions(PREVIEW_OPTIONS);
    }

    private static final String NO_SHELL = """
                 #
                 """;
    private static final String NEST_SHELL = """
                 class SealedTest {
                     #
                 }
                 """;
    private static final String AUX_SHELL = """
                 class SealedTest {
                 }
                 #
                 """;
    private static final List<String> SHELLS = List.of(NO_SHELL, NEST_SHELL, AUX_SHELL);

    public void testSimpleExtension() {
        String CC1 =
            """
            sealed class Sup # { }
            # class Sub extends Sup { }
            """;

        String CC2 =
                """
                sealed class Sup<T> # { }
                # class Sub<T> extends Sup<T> { }
                """;
        String CC3 =
                """
                sealed class Sup<T> # { }
                    # class Sub extends Sup<String> { }
                """;
        String AC1 =
            """
            sealed abstract class Sup # { }
            # class Sub extends Sup { }
            """;
        String AC2 =
                """
                sealed abstract class Sup<T> # { }
                    # class Sub<T> extends Sup<T> { }
                """;
        String AC3 =
                """
                sealed abstract class Sup<T> # { }
                    # class Sub extends Sup<String> { }
                """;
        String I1 =
            """
            sealed interface Sup # { }
            # class Sub implements Sup { }
            """;
        String I11 =
                """
                sealed interface Sup<T> # { }
                # class Sub<T> implements Sup<T> { }
                """;
        String I12 =
                """
                sealed interface Sup<T> # { }
                # class Sub<T> implements Sup<String> { }
                """;
        String I2 =
            """
            sealed interface Sup # { }
            # class Sub1 implements Sup { }
            # class Sub2 implements Sup { }
            """;

        // Assert that all combinations work:
        // { class, abs class, interface } x { implicit permits, explicit permits }
        //                                 x { final, non-sealed subtype }
        for (String shell : SHELLS)
            for (String b : List.of(CC1, CC2, CC3, AC1, AC2, AC3, I1, I11, I12))
                for (String p : List.of("", "permits Sub"))
                    for (String m : List.of("final", "non-sealed", "non\u002Dsealed"))
                        assertOK(shell, b, p, m);


        // Same for type with two subtypes
        for (String shell : SHELLS)
            for (String p : List.of("", "permits Sub1, Sub2"))
                for (String m : List.of("final", "non-sealed", "non\u002Dsealed"))
                    assertOK(shell, expandMarkers(I2, p, m, m));

        // Expect failure if there is no explicit final / sealed / non-sealed
        for (String shell : SHELLS)
            for (String b : List.of(CC1, CC2, CC3, AC1, AC2, AC3, I1, I11, I12))
                for (String p : List.of("", "permits Sub"))
                    for (String m : List.of(""))
                        assertFail("compiler.err.non.sealed.sealed.or.final.expected", shell, expandMarkers(b, p, m));
    }

    public void testSealedAndRecords() {
        String P =
            """
            sealed interface Sup # { }
            record A(int a) implements Sup { }
            record B(int b) implements Sup { }
            record C(int c) implements Sup { }
            """;

        for (String shell : SHELLS)
            for (String b : List.of(P))
                for (String p : List.of("", "permits A, B, C"))
                    assertOK(shell, b, p);
    }

    // Test that a type that explicitly permits one type, can't be extended by another
    public void testBadExtension() {
        String CC2 =
                """
                sealed class Sup permits Sub1 { }
                final class Sub1 extends Sup { }
                final class Sub2 extends Sup { }
                """;
        String AC2 =
                """
                sealed abstract class Sup permits Sub1 { }
                final class Sub1 extends Sup { }
                final class Sub2 extends Sup { }
                """;
        String I2c =
                """
                sealed interface Sup permits Sub1 { }
                final class Sub1 implements Sup { }
                final class Sub2 implements Sup { }
                """;
        String I2i =
                """
                sealed interface Sup permits Sub1 { }
                non-sealed interface Sub1 extends Sup { }
                non-sealed interface Sub2 extends Sup { }
                """;

        for (String shell : SHELLS)
            for (String b : List.of(CC2, AC2, I2c, I2i))
                assertFail("compiler.err.cant.inherit.from.sealed", shell, b);
    }

    public void testRestrictedKeyword() {
        for (String s : List.of(
                "class SealedTest { String sealed; }",
                "class SealedTest { int sealed = 0; int non = 0; int ns = non-sealed; }",
                "class SealedTest { void test(String sealed) { } }",
                "class SealedTest { void sealed(String sealed) { } }",
                "class SealedTest { void test() { String sealed = null; } }"
        )) {
            assertOK(s);
        }

        for (String s : List.of(
                "class sealed {}",
                "interface sealed {}",
                "@interface sealed {}"
        )) {
            assertFail("compiler.err.restricted.type.not.allowed", s);
        }

        for (String s : List.of(
                "class Foo { sealed m() {} }",
                "class Foo { sealed i; }",
                "class Foo { void m(sealed i) {} }"
                )) {
            assertFail("compiler.err.restricted.type.not.allowed.here", s);
        }

        String[] testOptions = {/* no options */};
        setCompileOptions(testOptions);
        // now testing with preview disabled
        for (String s : List.of(
                "sealed class S {}",
                "class Outer { sealed class S {} }",
                "class Outer { void m() { sealed class S {} } }",
                "non-sealed class S {}",
                "class Outer { non-sealed class S {} }",
                "class Outer { void m() { non-sealed class S {} } }"
        )) {
            assertFail("compiler.err.preview.feature.disabled.plural", s);
        }
        setCompileOptions(PREVIEW_OPTIONS);
    }

    public void testRejectPermitsInNonSealedClass() {
        assertFail("compiler.err.invalid.permits.clause",
                "class SealedTest {\n" +
                "    class NotSealed permits Sub {}\n" +
                "    class Sub extends NotSealed {}\n" +
                "}");
        assertFail("compiler.err.invalid.permits.clause",
                "class SealedTest {\n" +
                "    interface NotSealed permits Sub {}\n" +
                "    class Sub implements NotSealed {}\n" +
                "}");
    }

    public void testTypeInPermitsIsSameClassOrSuper() {
        assertFail("compiler.err.invalid.permits.clause",
                """
                sealed class Sealed permits Sealed {}
                """
                );
        assertFail("compiler.err.invalid.permits.clause",
                """
                interface I {}
                sealed class Sealed implements I permits I {}
                """
                );
        assertFail("compiler.err.invalid.permits.clause",
                """
                interface I {}
                interface I2 extends I {}
                sealed class Sealed implements I2 permits I {}
                """
                );
    }

    /* It is a compile-time error if a class declaration has more than one of the class modifiers
     * sealed, non-sealed and final
     */
    public void testBadModifiers() {
        assertFail("compiler.err.non.sealed.with.no.sealed.supertype",
                "class SealedTest { non-sealed class NoSealedSuper {} }");
        assertFail("compiler.err.mod.not.allowed.here",
                   "class SealedTest { sealed public void m() {} }");
        for (String s : List.of(
                "class SealedTest { sealed non-sealed class Super {} }",
                "class SealedTest { final non-sealed class Super {} }",
                "class SealedTest { final sealed class Super {} }",
                "class SealedTest { final sealed non-sealed class Super {} }",
                "class SealedTest {\n" +
                "    sealed class Super {}\n" +
                "    sealed non-sealed class Sub extends Super {}\n" +
                "}"))
            assertFail("compiler.err.illegal.combination.of.modifiers", s);
    }

    public void testAnonymous_FunctionalExpr_and_Sealed() {
        for (String s : List.of(
                """
                sealed interface I extends Runnable {
                    public static I i = () -> {};
                }

                final class Sub implements I {}
                """,
                """
                sealed interface I extends Runnable {}

                final class Sub implements I {
                    I a = Sub::action;
                    static void action() {}
                }
                """
                ))
            assertFail("compiler.err.prob.found.req", s);

        for (String s : List.of(
                """
                @FunctionalInterface
                sealed interface Action {
                    void doAction();
                }

                final class C implements Action {
                    public void doAction() {}
                }
                """
                ))
            assertFail("compiler.err.bad.functional.intf.anno.1", s);

        for (String s : List.of(
                """
                sealed interface I extends Runnable {
                    public static I i = new I() { public void run() { } };
                }
                final class C implements I {
                    @Override public void run() {}
                }
                """
                ))
            assertFail("compiler.err.local.classes.cant.extend.sealed", s);

        for (String s : List.of(
                """
                sealed interface I extends Runnable {
                    public static void foo() { new I() { public void run() { } }; }
                }
                final class C implements I {
                    @Override public void run() {}
                }
                """
                ))
        assertFail("compiler.err.local.classes.cant.extend.sealed", s);
    }

    public void testNoLocalSealedClasses() {
        for (String s : List.of(
                """
                sealed class C {
                    void m() {
                        sealed class D { }
                    }
                }
                """,
                """
                sealed class C {
                    void m() {
                        non-sealed class D { }
                    }
                }
                """))
            assertFail("compiler.err.sealed.or.non.sealed.local.classes.not.allowed", s);
    }

    public void testLocalCantExtendSealed() {
        for (String s : List.of(
                """
                sealed class C {
                    void m() {
                        final class D extends C { }
                    }
                }
                """))
            assertFail("compiler.err.local.classes.cant.extend.sealed", s);
    }

    public void testSealedInterfaceAndAbstracClasses() {
        for (String s : List.of(
                """
                sealed interface I {}
                """,
                """
                sealed abstract class AC {}
                """,
                """
                sealed class C {}
                """))
            assertFail("compiler.err.sealed.class.must.have.subclasses", s);

        for (String s : List.of(
                """
                sealed interface I {}

                non-sealed interface I2 extends I {}
                """,
                """
                sealed interface I {}

                sealed interface I2 extends I {}

                non-sealed interface I3 extends I2 {}
                """,
                """
                sealed interface I permits I2 {}

                non-sealed interface I2 extends I {}
                """,
                """
                sealed interface I permits I2 {}

                sealed interface I2 extends I permits I3 {}

                non-sealed interface I3 extends I2 {}
                """
                ))
            assertOK(s);
    }

    public void testEnumsCantBeSealedOrNonSealed() {
        for (String s : List.of(
                """
                sealed interface I {}

                sealed enum E implements I {E1}
                """,
                """
                sealed interface I {}

                non-sealed enum E implements I {E1}
                """))
            assertFail("compiler.err.mod.not.allowed.here", s);
    }

    public void testEnumsCanImplementSealedInterfaces() {
        for (String s : List.of(
                """
                sealed interface I {}

                enum E implements I {E1}
                """))
            assertOK(s);
    }

    public void testClassesCanExtendNonSealed() {
        for (String s : List.of(
                """
                sealed class C {}

                non-sealed class Sub extends C {}

                class Sub2 extends Sub {}
                """)) {
            assertOK(s);
        }
    }

    public void testEmptyPermits() {
        for (String s : List.of(
            """
            sealed class C permits {}
            non-sealed class Sub extends C {}
            """)) {
            assertFail("compiler.err.expected", s);
        }
    }

    public void testTypeVarInPermits() {
        for (String s : List.of(
            """
            class Outer<T> {
                sealed class C permits T  {}
            }
            """)) {
            assertFail("compiler.err.invalid.permits.clause", s);
        }
    }

    public void testRepeatedTypeInPermits() {
        for (String s : List.of(
            """
            sealed class C permits Sub, Sub {}

            final class Sub extends C {}
            """)) {
            assertFail("compiler.err.invalid.permits.clause", s);
        }
    }

    public void testSubtypeDoesntExtendSealed() {
        for (String s : List.of(
            """
            sealed class C permits Sub {}

            final class Sub {}
            """,
            """
            sealed interface I permits Sub {}

            final class Sub {}
            """,
            """
            sealed class C permits Sub1, Sub2 {}

            sealed class Sub1 extends C permits Sub2 {}

            final class Sub2 extends Sub1 {}
            """
            )) {
            assertFail("compiler.err.invalid.permits.clause", s);
        }
    }

    public void testAPIForPrimitiveAndArrayClasses() {
        for (Class<?> c : new Class[]{byte.class, byte[].class, short.class, short[].class, int.class, int[].class, long.class, long[].class,
            float.class, float[].class, double.class, double[].class, char.class, char[].class, boolean.class, boolean[].class, void.class,
            String[].class}) {
            Assert.check(!c.isSealed());
            Assert.check(c.permittedSubclasses().length == 0);
        }
    }

    public void testPrinting() throws Exception {
        Path base = Paths.get("testPrinting");
        Path src = base.resolve("src");
        Path test = src.resolve("Test");

        tb.writeJavaFiles(test,
            """
            sealed class SealedClassNoPermits {}

            final class FinalSubClass extends SealedClassNoPermits {}

            non-sealed class NonSealedSubClass extends SealedClassNoPermits {}

            sealed interface SealedInterfaceNoPermits {}

            non-sealed interface NonSealedInterface extends SealedInterfaceNoPermits {}

            final class FinalSubClass2 implements SealedInterfaceNoPermits {}


            sealed class SealedClassWithPermits permits SealedClassWithPermits, NonSealedSubClass2 {}

            final class FinalSubClass3 extends SealedClassWithPermits {}

            non-sealed class NonSealedSubClass2 extends SealedClassWithPermits {}

            sealed interface SealedInterfaceWithPermits permits NonSealedInterface2, FinalSubClass4 {}

            non-sealed interface NonSealedInterface2 extends SealedInterfaceWithPermits {}

            final class FinalSubClass4 implements SealedInterfaceWithPermits {}


            enum SealedEnum {
                E {}
            }

            enum Enum {
                E
            }
            """
        );

        Path out = base.resolve("out");

        Files.createDirectories(out);

        List<String> output = new JavacTask(tb)
            .outdir(out)
            .options("--enable-preview", "-source", Integer.toString(Runtime.version().feature()), "-Xprint")
            .files(findJavaFiles(test))
            .run()
            .writeAll()
            .getOutputLines(OutputKind.STDOUT);

        List<String> expected = List.of(
            "sealed class SealedClassNoPermits permits FinalSubClass, NonSealedSubClass {",
            "  SealedClassNoPermits();",
            "}",
            "final class FinalSubClass extends SealedClassNoPermits {",
            "  FinalSubClass();",
            "}",
            "non-sealed class NonSealedSubClass extends SealedClassNoPermits {",
            "  NonSealedSubClass();",
            "}",
            "sealed interface SealedInterfaceNoPermits permits NonSealedInterface, FinalSubClass2 {",
            "}",
            "non-sealed interface NonSealedInterface extends SealedInterfaceNoPermits {",
            "}",
            "final class FinalSubClass2 implements SealedInterfaceNoPermits {",
            "  FinalSubClass2();",
            "}",
            "sealed class SealedClassWithPermits permits SealedClassWithPermits, NonSealedSubClass2 {",
            "  SealedClassWithPermits();",
            "}",
            "final class FinalSubClass3 extends SealedClassWithPermits {",
            "  FinalSubClass3();",
            "}",
            "non-sealed class NonSealedSubClass2 extends SealedClassWithPermits {",
            "  NonSealedSubClass2();",
            "}",
            "sealed interface SealedInterfaceWithPermits permits NonSealedInterface2, FinalSubClass4 {",
            "}",
            "non-sealed interface NonSealedInterface2 extends SealedInterfaceWithPermits {",
            "}",
            "final class FinalSubClass4 implements SealedInterfaceWithPermits {",
            "  FinalSubClass4();",
            "}",
            "enum SealedEnum {",
            "  E;",
            "  public static SealedEnum[] values();",
            "  public static SealedEnum valueOf(java.lang.String name);",
            "  private SealedEnum();",
            "}",
            "enum Enum {",
            "  E;",
            "  public static Enum[] values();",
            "  public static Enum valueOf(java.lang.String name);",
            "  private Enum();",
            "}"
        );
        // remove empty strings
        String newLine = System.getProperty("line.separator");
        output = output.stream().filter(s -> !s.isEmpty()).map(s -> s.replaceAll(newLine, "\n").replaceAll("\n", "")).collect(Collectors.toList());
        if (!output.containsAll(expected)) {
            for (int i = 0; i < output.size(); i++) {
                if (!output.get(i).equals(expected.get(i))) {
                    System.out.println("failing at index " + i);
                    System.out.println("expected:" + expected.get(i));
                    System.out.println("found:" + output.get(i));
                }
            }
            throw new AssertionError("Expected output not found. Expected: " + expected);
        }
    }

    public void testIllFormedNonSealed() {
        for (String s : List.of(
            """
            sealed class C permits Sub {}
            non -sealed class Sub extends C {}
            """,
            """
            sealed class C permits Sub {}
            non sealed class Sub extends C {}
            """,
            """
            sealed class C permits Sub {}
            non - sealed class Sub extends C {}
            """,
            """
            sealed class C permits Sub {}
            non/**/sealed class Sub extends C {}
            """
            )) {
            assertFail("compiler.err.expected4", s);
        }
    }

    public void testParameterizedPermitted() {
        for (String s : List.of(
            """
            sealed class C<T> permits Sub<T> {}
            final class Sub<T> extends C<T> {}
            """,
            """
            sealed class C permits Sub<String> {}
            final class Sub<T> extends C {}
            """
            )) {
            assertFail("compiler.err.expected", s);
        }
    }

    private Path[] findJavaFiles(Path... paths) throws IOException {
        return tb.findJavaFiles(paths);
    }

    public void testSealedNonSealedWithOtherModifiers() {
        String template1 =
            """
            @interface A {}

            class Outer {
                sealed class Sup { }
                # # class Sub extends Sup {}
                final class Sub2 extends Sub {}
            }
            """;

        String template2 =
            """
            @interface A {}

            class Outer {
                sealed interface Sup { }
                # # interface Sub extends Sup {}
                final class Sub2 implements Sub {}
            }
            """;

        List<String> templateList = List.of(template1, template2);
        List<String> otherModifiers = List.of(
                "@A", "public", "protected", "private", "abstract", "static", "strictfp", "final", "sealed", "non-sealed"
        );

        for (String template : templateList) {
            for (String sealed_non_sealed : List.of("sealed", "non-sealed")) {
                for (String modifier : otherModifiers) {
                    if (sealed_non_sealed.equals(modifier)) {
                        assertFail("compiler.err.repeated.modifier", template, sealed_non_sealed, modifier);
                    } else if (modifier.equals("final") || modifier.equals("sealed") || modifier.equals("non-sealed")) {
                        assertFail("compiler.err.illegal.combination.of.modifiers", template, sealed_non_sealed, modifier);
                    } else {
                        assertOK(template, sealed_non_sealed, modifier);
                    }
                }
            }
        }
    }
}
