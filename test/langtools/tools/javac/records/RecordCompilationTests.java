/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * RecordCompilationTests
 *
 * @test
 * @bug 8250629 8252307 8247352 8241151 8246774 8259025 8288130 8282714 8289647 8294020
 * @summary Negative compilation tests, and positive compilation (smoke) tests for records
 * @library /lib/combo /tools/lib /tools/javac/lib
 * @enablePreview
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.code
 *      jdk.compiler/com.sun.tools.javac.util
 *      java.base/jdk.internal.classfile.impl
 * @build JavacTestingAbstractProcessor
 * @run junit/othervm -DuseAP=false RecordCompilationTests
 * @run junit/othervm -DuseAP=true RecordCompilationTests
 */

import java.io.File;

import java.lang.annotation.ElementType;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


import com.sun.tools.javac.util.Assert;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeMirror;

import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.classfile.Opcode;
import java.lang.classfile.constantpool.*;
import java.lang.classfile.instruction.FieldInstruction;

import com.sun.tools.javac.api.ClientCodeWrapper.DiagnosticSourceUnwrapper;
import com.sun.tools.javac.code.Attribute.TypeCompound;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.util.JCDiagnostic;

import tools.javac.combo.CompilationTestCase;
import org.junit.jupiter.api.Test;

import static java.lang.annotation.ElementType.*;

/** Records are the first feature which sports automatic injection of (declarative and type) annotations : from a
 *  given record component to one or more record members, if applicable.
 *  This implies that the record's implementation can be stressed with the presence of annotation processors. Which is
 *  something the implementator could easily skip. For this reason this test is executed twice, once without the
 *  presence of any annotation processor and one with a simple annotation processor (which does not annotation processing
 *  at all) just to force at least a round of annotation processing.
 *
 *  Tests needing special compilation options need to store current options, set its customs options by invoking method
 *  `setCompileOptions` and then reset the previous compilation options for other tests. To see an example of this check
 *  method: testAnnos()
 */

class RecordCompilationTests extends CompilationTestCase {
    private static String[] OPTIONS_WITH_AP = {"-processor", SimplestAP.class.getName()};

    private static final List<String> BAD_COMPONENT_NAMES = List.of(
            "clone", "finalize", "getClass", "hashCode",
            "notify", "notifyAll", "toString", "wait");

    /* simplest annotation processor just to force a round of annotation processing for all tests
     */
    @SupportedAnnotationTypes("*")
    public static class SimplestAP extends AbstractProcessor {
        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            return true;
        }
    }

    boolean useAP;

    public RecordCompilationTests() {
        useAP = System.getProperty("useAP", "false").equals("true");
        setDefaultFilename("R.java");
        if (useAP) {
            setCompileOptions(OPTIONS_WITH_AP);
        }
        System.out.println(useAP ? "running all tests using an annotation processor" : "running all tests without annotation processor");
    }

    @Test
    void testMalformedDeclarations() {
        assertFail("compiler.err.premature.eof", "record R()");
        assertFail("compiler.err.expected", "record R();");
        assertFail("compiler.err.illegal.start.of.type", "record R(,) { }");
        assertFail("compiler.err.illegal.start.of.type", "record R((int x)) { }");
        assertFail("compiler.err.expected", "record R { }");
        assertFail("compiler.err.expected", "record R(foo) { }");
        assertFail("compiler.err.expected", "record R(int int) { }");
        assertFail("compiler.err.mod.not.allowed.here", "abstract record R(String foo) { }");
        //assertFail("compiler.err.illegal.combination.of.modifiers", "non-sealed record R(String foo) { }");
        assertFail("compiler.err.repeated.modifier", "public public record R(String foo) { }");
        assertFail("compiler.err.repeated.modifier", "private private record R(String foo) { }");
        assertFail("compiler.err.already.defined", "record R(int x, int x) {}");
        for (String s : List.of("var", "record"))
            assertFail("compiler.err.restricted.type.not.allowed.here", "record R(# x) { }", s);
        for (String s : List.of("public", "protected", "private", "static", "final", "transient", "volatile",
                "abstract", "synchronized", "native", "strictfp")) // missing: sealed and non-sealed
            assertFail("compiler.err.record.cant.declare.field.modifiers", "record R(# String foo) { }", s);
        assertFail("compiler.err.varargs.must.be.last", "record R(int... x, int... y) {}");
        assertFail("compiler.err.instance.initializer.not.allowed.in.records", "record R(int i) { {} }");
    }

    @Test
    void testGoodDeclarations() {
        assertOK("public record R() { }");
        assertOK("record R() { }");
        assertOK("record R() implements java.io.Serializable, Runnable { public void run() { } }");
        assertOK("record R(int x) { }");
        assertOK("record R(int x, int y) { }");
        assertOK("record R(int... xs) { }");
        assertOK("record R(String... ss) { }");
        assertOK("@Deprecated record R(int x, int y) { }");
        assertOK("record R(@Deprecated int x, int y) { }");
        assertOK("record R<T>(T x, T y) { }");
        assertOK(
                """
                record R<T>(T x) {
                    public T x() {
                        return this.x;
                    }
                }
                """);
        assertOK(
                """
                import java.util.List;
                record R<T>(List<T> x) {
                    public List<T> x() {
                        return this.x;
                    }
                }
                """);
    }

    @Test
    void testGoodMemberDeclarations() {
        String template = "public record R(int x) {\n"
                + "    public R(int x) { this.x = x; }\n"
                + "    public int x() { return x; }\n"
                + "    public boolean equals(Object o) { return true; }\n"
                + "    public int hashCode() { return 0; }\n"
                + "    public String toString() { return null; }\n"
                + "}";
        assertOK(template);
    }

    @Test
    void testBadComponentNames() {
        for (String s : BAD_COMPONENT_NAMES)
            assertFail("compiler.err.illegal.record.component.name", "record R(int #) { } ", s);
    }

    @Test
    void testRestrictedIdentifiers() {
        for (String s : List.of("interface record { void m(); }",
                "@interface record { }",
                "class record { }",
                "record record(int x) { }",
                "enum record { A, B }",
                "class R<record> { }")) {
            assertFail(
                    "compiler.err.restricted.type.not.allowed",
                    diagWrapper -> {
                        JCDiagnostic diagnostic = ((DiagnosticSourceUnwrapper)diagWrapper).d;
                        Object[] args = diagnostic.getArgs();
                        Assert.check(args.length == 2);
                        Assert.check(args[1].toString().equals("JDK14"));
                    },
                    s);
        }
    }

    @Test
    void testValidMembers() {
        for (String s : List.of("record X(int j) { }",
                "interface I { }",
                "static { }",
                "enum E { A, B }",
                "class C { }"
        )) {
            assertOK("record R(int i) { # }", s);
        }
    }

    @Test
    void testCyclic() {
        // Cyclic records are OK, but cyclic inline records would not be
        assertOK("record R(R r) { }");
    }

    @Test
    void testBadExtends() {
        assertFail("compiler.err.expected", "record R(int x) extends Object { }");
        assertFail("compiler.err.expected", "record R(int x) {}\n"
                + "record R2(int x) extends R { }");
        assertFail("compiler.err.cant.inherit.from.final", "record R(int x) {}\n"
                + "class C extends R { }");
    }

    @Test
    void testNoExtendRecord() {
        assertFail("compiler.err.invalid.supertype.record",
                   """
                   class R extends Record {
                       public String toString() { return null; }
                       public int hashCode() { return 0; }
                       public boolean equals(Object o) { return false; }
                   }
                   """
        );
    }

    @Test
    void testFieldDeclarations() {
        // static fields are OK
        assertOK("public record R(int x) {\n" +
                "    static int I = 1;\n" +
                "    static final String S = \"Hello World!\";\n" +
                "    static private Object O = null;\n" +
                "    static protected Object O2 = null;\n" +
                "}");

        // instance fields are not
        assertFail("compiler.err.record.cannot.declare.instance.fields",
                "public record R(int x) {\n" +
                        "    private final int y = 0;" +
                        "}");

        // mutable instance fields definitely not
        assertFail("compiler.err.record.cannot.declare.instance.fields",
                "public record R(int x) {\n" +
                        "    private int y = 0;" +
                        "}");

        // redeclaring components also not
        assertFail("compiler.err.record.cannot.declare.instance.fields",
                "public record R(int x) {\n" +
                        "    private final int x;" +
                        "}");
    }

    @Test
    void testAccessorRedeclaration() {
        assertOK("public record R(int x) {\n" +
                "    public int x() { return x; };" +
                "}");

        assertOK("public record R(int... x) {\n" +
                "    public int[] x() { return x; };" +
                "}");

        assertOK("public record R(int x) {\n" +
                "    public final int x() { return 0; };" +
                "}");

        assertOK("public record R(int x) {\n" +
                "    public final int x() { return 0; };" +
                "}");

        assertFail("compiler.err.invalid.accessor.method.in.record",
                "public record R(int x) {\n" +
                        "    final int x() { return 0; };" +
                        "}");

        assertFail("compiler.err.invalid.accessor.method.in.record",
                "public record R(int x) {\n" +
                        "    int x() { return 0; };" +
                        "}");

        assertFail("compiler.err.invalid.accessor.method.in.record",
                "public record R(int x) {\n" +
                        "    private int x() { return 0; };" +
                        "}");

        assertFail("compiler.err.invalid.accessor.method.in.record",
                   "public record R(int x) {\n" +
                   "    public int x() throws Exception { return 0; };" +
                   "}");

        for (String s : List.of("List", "List<?>", "Object", "ArrayList<String>", "int"))
            assertFail("compiler.err.invalid.accessor.method.in.record",
                    "import java.util.*;\n" +
                            "public record R(List<String> x) {\n" +
                            "    public # x() { return null; };" +
                            "}", s);

        assertFail("compiler.err.invalid.accessor.method.in.record",
                "public record R(int x) {\n" +
                        "    public <T> int x() { return x; };" +
                        "}");

        assertFail("compiler.err.invalid.accessor.method.in.record",
                "public record R(int x) {\n" +
                        "    static private final j = 0;" +
                        "    static public int x() { return j; };" +
                        "}");
    }

    @Test
    void testConstructorRedeclaration() {
        for (String goodCtor : List.of(
                "public R(int x) { this(x, 0); }",
                "public R(int x, int y) { this.x = x; this.y = y; }",
                "public R { }"))
            assertOK("record R(int x, int y) { # }", goodCtor);

        assertOK("import java.util.*; record R(String x, String y) {  public R { Objects.requireNonNull(x); Objects.requireNonNull(y); } }");

        // The lambda expressions in the constructor should be compiled successfully.
        assertOK("""
                import static java.util.Objects.*;
                record R(String v) {
                    R {
                        requireNonNull(v, () -> "v must be provided");
                        requireNonNullElseGet(v, () -> "w");
                    }
                }""");

        // Not OK to redeclare canonical without DA
        assertFail("compiler.err.var.might.not.have.been.initialized", "record R(int x, int y) { # }",
                   "public R(int x, int y) { this.x = x; }");

        // Not OK to rearrange or change names
        for (String s : List.of("public R(int y, int x) { this.x = x; this.y = y; }",
                                "public R(int _x, int _y) { this.x = _x; this.y = _y; }"))
            assertFail("compiler.err.invalid.canonical.constructor.in.record", "record R(int x, int y) { # }", s);

        // ctor args must match types
        assertFail("compiler.err.invalid.canonical.constructor.in.record",
                "import java.util.*;\n" +
                        "record R(List<String> list) { # }",
                "R(List list) { this.list = list; }");

        // canonical ctor should not throw checked exceptions
        assertFail("compiler.err.invalid.canonical.constructor.in.record",
                   "record R() { # }",
                   "public R() throws Exception { }");

        // same for compact
        assertFail("compiler.err.invalid.canonical.constructor.in.record",
                "record R() { # }",
                "public R throws Exception { }");

        // not even unchecked exceptions
        assertFail("compiler.err.invalid.canonical.constructor.in.record",
                "record R() { # }",
                 "public R() throws IllegalArgumentException { }");

        // ditto
        assertFail("compiler.err.invalid.canonical.constructor.in.record",
                "record R() { # }",
                "public R throws IllegalArgumentException { }");

        // If types match, names must match
        assertFail("compiler.err.invalid.canonical.constructor.in.record",
                   "record R(int x, int y) { public R(int y, int x) { this.x = this.y = 0; }}");

        // constructor is not canonical, so it must only invoke another constructor
        assertFail("compiler.err.non.canonical.constructor.invoke.another.constructor",
                "record R(int x, int y) { public R(int y, int x, int z) { this.x = this.y = 0; } }");

        assertFail("compiler.err.non.canonical.constructor.invoke.another.constructor",
                "record R(int x, int y) { public R(int y, int x, int z) { super(); this.x = this.y = 0; } }");

        assertOK("record R(int x, int y) { " +
                 "    public R(int x, int y, int z) { this(x, y); } " +
                 "}");

        assertOK("record R(int x) { " +
                "    public R(int x, int y) { this(x, y, 0); } " +
                "    public R(int x, int y, int z) { this(x); } " +
                "}");

        assertFail("compiler.err.invalid.canonical.constructor.in.record",
                "record R<T>(T a) { # }",
                "public <T> R {}");

        assertFail("compiler.err.invalid.canonical.constructor.in.record",
                "record R(int i) { # }",
                "public <T> R(int i) { this.i = i; }");

        assertFail("compiler.err.invalid.canonical.constructor.in.record",
                "record R<T>(T a) { # }",
                "public <T> R(T a) { this.a = a; }");

        assertFail("compiler.err.invalid.canonical.constructor.in.record",
                "record R(int a) { # }",
                "public R(int a) { super(); this.a = a; }");
    }

    @Test
    void testAnnotationCriteria() {
        String imports = "import java.lang.annotation.*;\n";
        String template = "@Target({ # }) @interface A {}\n";
        EnumMap<ElementType, String> annotations = new EnumMap<>(ElementType.class);
        for (ElementType e : values())
            annotations.put(e, template.replace("#", "ElementType." + e.name()));
        EnumSet<ElementType> goodSet = EnumSet.of(RECORD_COMPONENT, FIELD, METHOD, PARAMETER, TYPE_USE);
        EnumSet<ElementType> badSet = EnumSet.of(CONSTRUCTOR, PACKAGE, TYPE, LOCAL_VARIABLE, ANNOTATION_TYPE, TYPE_PARAMETER, MODULE);

        Assert.check(goodSet.size() + badSet.size() == values().length);
        String A_GOOD = template.replace("#",
                                         goodSet.stream().map(ElementType::name).map(s -> "ElementType." + s).collect(Collectors.joining(",")));
        String A_BAD = template.replace("#",
                                        badSet.stream().map(ElementType::name).map(s -> "ElementType." + s).collect(Collectors.joining(",")));
        String A_ALL = template.replace("#",
                                        Stream.of(ElementType.values()).map(ElementType::name).map(s -> "ElementType." + s).collect(Collectors.joining(",")));
        String A_NONE = "@interface A {}";

        for (ElementType e : goodSet)
            assertOK(imports + annotations.get(e) + "record R(@A int x) { }");
        assertOK(imports + A_GOOD + "record R(@A int x) { }");
        assertOK(imports + A_ALL + "record R(@A int x) { }");
        assertOK(imports + A_NONE);

        for (ElementType e : badSet) {
            assertFail("compiler.err.annotation.type.not.applicable", imports + annotations.get(e) + "record R(@A int x) { }");
        }

        assertFail("compiler.err.annotation.type.not.applicable", imports + A_BAD + "record R(@A int x) { }");

        // TODO: OK to redeclare with or without same annos
    }

    @Test
    void testNestedRecords() {
        String template = "class R { \n" +
                          "    # record RR(int a) { }\n" +
                          "}";

        for (String s : List.of("", "static", "final",
                                "private", "public", "protected",
                                "private static", "public static", "private static final"))
            assertOK(template, s);

        for (String s : List.of("class C { }",
                                "static class C { }",
                                "enum X { A; }",
                                "interface I { }",
                                "record RR(int y) { }"))
            assertOK("record R(int x) { # }", s);
    }

    @Test
    void testDuplicatedMember() {
        String template
                = "    record R(int i) {\n" +
                  "        public int i() { return i; }\n" +
                  "        public int i() { return i; }\n" +
                  "    }";
        assertFail("compiler.err.already.defined", template);
    }

    @Test
    void testStaticLocals() {
        // static locals can't capture local variables, instance fields or type variables
        for (String s : List.of(
                "record RR(int x) { public int x() { return y; }};",
                "record RR(int x) { public int x() { return z; }};",
                "record RR(int x) { public int x() { return instance; }};",
                "record RR(T t) {};",
                "record RR(U u) {};",

                "interface I { default int x() { return y; }};",
                "interface I { default int x() { return z; }};",
                "interface I { default int x() { return instance; }};",
                "interface I { default int x(T t) { return 0; }};",
                "interface I { default int x(U u) { return 0; }};",

                "enum E { A; int x() { return y; }};",
                "enum E { A; int x() { return z; }};",
                "enum E { A; int x() { return instance; }};",
                "enum E { A; int x(T t) { return 0; }};",
                "enum E { A; int x(U u) { return 0; }};"
        )) {
            assertFail("compiler.err.non-static.cant.be.ref",
                """
                class R<T> {
                    int instance = 0;
                    <U> U m(int y) {
                        int z;
                        #S
                        return null;
                    }
                }
                """.replaceFirst("#S", s));
        }

        // a similar example but a bit more complex
        for (String s : List.of(
                "record R() { void test1() { class X { void test2() { System.err.println(localVar); } } } }",
                "record R() { void test1() { class X { void test2() { System.err.println(param); } } } }",
                "record R() {void test1() { class X { void test2() { System.err.println(instanceField); } } } }",
                "record R() { void test1() { class X { T t; } } }",
                "record R() { void test1() { class X { U u; } } }",

                "interface I { default void test1() { class X { void test2() { System.err.println(localVar); } } } }",
                "interface I() { default void test1() { class X { void test2() {System.err.println(param);} } } }",
                "interface I { default void test1() { class X { void test2() { System.err.println(instanceField); } } } }",
                "interface I { default void test1() { class X { T t; } } }",
                "interface I() { default void test1() { class X {U u;} } }",

                "enum E { A; void test1() { class X { void test2() { System.err.println(localVar); } } } }",
                "enum E { A; void test1() { class X { void test2() {System.err.println(param);} } } }",
                "enum E { A; void test1() { class X { void test2() { System.err.println(instanceField); } } } }",
                "enum E { A; void test1() { class X { T t; } } }",
                "enum E { A; void test1() { class X {U u;} } }"
        )) {
            assertFail("compiler.err.non-static.cant.be.ref",
                    """
                    class C<T> {
                        String instanceField = "instance";
                        static <U> U m(String param) {
                            String localVar = "local";
                            #S
                            return null;
                    }
                }
                """.replaceFirst("#S", s));
        }

        // can't self-shadow
        for (String s : List.of("record R() {}", "interface R {}", "enum R { A }")) {
            assertFail("compiler.err.already.defined", "class R { void m() { #S } }".replaceFirst("#S", s));
        }

        // can't be explicitly static
        for (String s : List.of("static record RR() { }", "static interface I {}", "static enum E { A }")) {
            assertFail("compiler.err.illegal.start.of.expr", "class R { void m() { #S } }".replaceFirst("#S", s));
        }

        // but static fields can be accessed
        for (String s : List.of(
                "record RR() { public int x() { return z; } };",
                "interface I { default int x() { return z; } }",
                "enum E { A; int x() { return z; } }"
        )) {
            assertOK("class R { static int z = 0; void m() { #S } }".replaceFirst("#S", s));
        }

        // local records can also be final
        assertOK("class R { void m() { final record RR(int x) { }; } }");
    }

    @Test
    void testStaticDefinitionsInInnerClasses() {
        // static defs in inner classes can't capture instance fields or type variables
        for (String s : List.of(
                """
                record R() {
                    void test() { System.err.println(field); }
                }
                """,
                """
                record R() {
                    void test(T t) {}
                }
                """,
                """
                record R() {
                    void test1() {
                        class X {
                            void test2() { System.err.println(field); }
                        }
                    }
                }
                """,
                """
                record R() {
                    void test1() {
                        class X { void test2(T t) {} }
                    }
                }
                """,

                """
                interface I {
                    default void test() { System.err.println(field); }
                }
                """,
                """
                interface I {
                    default void test(T t) {}
                }
                """,
                """
                interface I {
                    default void test1() {
                        class X {
                            void test2() { System.err.println(field); }
                        }
                    }
                }
                """,
                """
                interface I {
                    default void test1() {
                        class X { void test2(T t) {} }
                    }
                }
                """,

                """
                enum E {
                    A;
                    void test() { System.err.println(field); }
                }
                """,
                """
                enum E {
                    A;
                    void test(T t) {}
                }
                """,
                """
                enum E {
                    A;
                    void test1() {
                        class X {
                            void test2() { System.err.println(field); }
                        }
                    }
                }
                """,
                """
                enum E {
                    A;
                    void test1() {
                        class X { void test2(T t) {} }
                    }
                }
                """,

                """
                static class SC {
                    void test() { System.err.println(field); }
                }
                """,
                """
                static class SC {
                    void test(T t) {}
                }
                """,
                """
                static class SC {
                    void test1() {
                        class X {
                            void test2() { System.err.println(field); }
                        }
                    }
                }
                """,
                """
                static class SC {
                    void test1() {
                        class X { void test2(T t) {} }
                    }
                }
                """
        )) {
            assertFail("compiler.err.non-static.cant.be.ref",
                    """
                    class C<T> {
                        String field = "field";
                        class Inner {
                            #S
                        }
                    }
                    """.replaceFirst("#S", s));
        }

        // another, more complex, example
        // static defs in inner classes can't capture instance locals, fields or type variables
        for (String s : List.of(
                """
                record R() {
                    void test() { System.err.println(field); }
                }
                """,
                """
                record R() {
                    void test1() {
                        class X { void test2() { System.err.println(field); } }
                    }
                }
                """,
                """
                record R() {
                    void test() { System.err.println(param); }
                }
                """,
                """
                record R() {
                    void test1() {
                        class X { void test2() { System.err.println(param); } }
                    }
                }
                """,
                """
                record R() {
                    void test() { System.err.println(local); }
                }
                """,
                """
                record R() {
                    void test1() {
                        class X { void test2() { System.err.println(local); } }
                    }
                }
                """,
                """
                record R() {
                    void test(T t) {}
                }
                """,
                """
                record R() {
                    void test(U u) {}
                }
                """,
                """
                record R() {
                    void test1() {
                        class X { void test2(T t) {} }
                    }
                }
                """,
                """
                record R() {
                    void test1() {
                        class X { void test2(U u) {} }
                    }
                }
                """,

                """
                interface I {
                    default void test() { System.err.println(field); }
                }
                """,
                """
                interface I {
                    default void test1() {
                        class X {
                            void test2() { System.err.println(field); }
                        }
                    }
                }
                """,
                """
                interface I {
                    default void test() { System.err.println(param); }
                }
                """,
                """
                interface I {
                    default void test1() {
                        class X {
                            void test2() { System.err.println(param); }
                        }
                    }
                }
                """,
                """
                interface I {
                    default void test() { System.err.println(local); }
                }
                """,
                """
                interface I {
                    default void test1() {
                        class X {
                            void test2() { System.err.println(local); }
                        }
                    }
                }
                """,
                """
                interface I {
                    default void test(T t) {}
                }
                """,
                """
                interface I {
                    default void test(U u) {}
                }
                """,
                """
                interface I {
                    default void test1() {
                        class X { void test2(T t) {} }
                    }
                }
                """,
                """
                interface I {
                    default void test1() {
                        class X { void test2(U u) {} }
                    }
                }
                """,

                """
                enum E {
                    A;
                    void test() { System.err.println(field); }
                }
                """,
                """
                enum E {
                    A;
                    void test1() {
                        class X {
                            void test2() { System.err.println(field); }
                        }
                    }
                }
                """,
                """
                enum E {
                    A;
                    void test() { System.err.println(param); }
                }
                """,
                """
                enum E {
                    A;
                    void test1() {
                        class X {
                            void test2() { System.err.println(param); }
                        }
                    }
                }
                """,
                """
                enum E {
                    A;
                    void test() { System.err.println(local); }
                }
                """,
                """
                enum E {
                    A;
                    void test1() {
                        class X {
                            void test2() { System.err.println(local); }
                        }
                    }
                }
                """,
                """
                enum E {
                    A;
                    void test(T t) {}
                }
                """,
                """
                enum E {
                    A;
                    void test(U u) {}
                }
                """,
                """
                enum E {
                    A;
                    void test1() {
                        class X { void test2(T t) {} }
                    }
                }
                """,
                """
                enum E {
                    A;
                    void test1() {
                        class X { void test2(U u) {} }
                    }
                }
                """,

                """
                static class SC {
                    void test() { System.err.println(field); }
                }
                """,
                """
                static class SC {
                    void test1() {
                        class X {
                            void test2() { System.err.println(field); }
                        }
                    }
                }
                """,
                """
                static class SC {
                    void test() { System.err.println(param); }
                }
                """,
                """
                static class SC {
                    void test1() {
                        class X {
                            void test2() { System.err.println(param); }
                        }
                    }
                }
                """,
                """
                static class SC {
                    void test() { System.err.println(local); }
                }
                """,
                """
                static class SC {
                    void test1() {
                        class X {
                            void test2() { System.err.println(local); }
                        }
                    }
                }
                """,
                """
                static class SC {
                    void test(T t) {}
                }
                """,
                """
                static class SC {
                    void test(U u) {}
                }
                """,
                """
                static class SC {
                    void test1() {
                        class X { void test2(T t) {} }
                    }
                }
                """,
                """
                static class SC {
                    void test1() {
                        class X { void test2(U u) {} }
                    }
                }
                """
        )) {
            assertFail("compiler.err.non-static.cant.be.ref",
                    """
                    class C<T> {
                        String field = "field";
                        <U> U m(String param) {
                            String local = "local";
                            class Local {
                                class Inner { #S }
                            }
                            return null;
                        }
                    }
                    """.replaceFirst("#S", s));
        }

        // inner classes can contain static methods too
        assertOK(
                """
                class C {
                    class Inner {
                        // static method inside inner class
                        static void m() {}
                    }
                }
                """
        );

        assertOK(
                """
                class C {
                     void m() {
                         new Object() {
                            // static method inside inner class
                            static void m() {}
                         };
                     }
                }
                """
        );

        // but still non-static declarations can't be accessed from a static method inside a local class
        for (String s : List.of(
                "System.out.println(localVar)",
                "System.out.println(param)",
                "System.out.println(field)",
                "T t",
                "U u"
        )) {
            assertFail("compiler.err.non-static.cant.be.ref",
                    """
                    class C<T> {
                        int field = 0;
                        <U> void foo(int param) {
                            int localVar = 1;
                            class Local {
                                static void m() {
                                    #S;
                                }
                            }
                        }
                    }
                    """.replaceFirst("#S", s));
        }
    }

    @Test
    void testReturnInCanonical_Compact() {
        assertFail("compiler.err.invalid.canonical.constructor.in.record", "record R(int x) { # }",
                "public R { return; }");
        assertFail("compiler.err.invalid.canonical.constructor.in.record", "record R(int x) { # }",
                "public R { if (i < 0) { return; }}");
        assertOK("record R(int x) { public R(int x) { this.x = x; return; } }");
        assertOK("record R(int x) { public R { Runnable r = () -> { return; };} }");
    }

    @Test
    void testArgumentsAreNotFinalInCompact() {
        assertOK(
                """
                record R(int x) {
                    public R {
                        x++;
                    }
                }
                """);
    }

    @Test
    void testNoNativeMethods() {
        assertFail("compiler.err.mod.not.allowed.here", "record R(int x) { # }",
                "public native R {}");
        assertFail("compiler.err.mod.not.allowed.here", "record R(int x) { # }",
                "public native void m();");
    }

    @Test
    void testRecordsInsideInner() {
        assertOK(
                """
                class Outer {
                    class Inner {
                        record R(int a) {}
                    }
                }
                """
        );
        assertOK(
                """
                class Outer {
                    public void test() {
                        class Inner extends Outer {
                            record R(int i) {}
                        }
                    }
                }
                """);
        assertOK(
                """
                class Outer {
                    Runnable run = new Runnable() {
                        record TestRecord(int i) {}
                        public void run() {}
                    };
                }
                """);
        assertOK(
                """
                class Outer {
                    void m() {
                        record A() {
                            record B() { }
                        }
                    }
                }
                """);
    }

    @Test
    void testAnnoInsideLocalOrAnonymous() {
        assertFail("compiler.err.annotation.decl.not.allowed.here",
                """
                class Outer {
                    public void test() {
                        class Local {
                            @interface A {}
                        }
                    }
                }
                """);
        assertFail("compiler.err.annotation.decl.not.allowed.here",
                """
                class Outer {
                    public void test() {
                        interface I {
                            @interface A {}
                        }
                    }
                }
                """);
        assertFail("compiler.err.annotation.decl.not.allowed.here",
                """
                class Outer {
                    public void test() {
                        record R() {
                            @interface A {}
                        }
                    }
                }
                """);
        assertFail("compiler.err.annotation.decl.not.allowed.here",
                """
                class Outer {
                    public void test() {
                        enum E {
                            E1;
                            @interface A {}
                        }
                    }
                }
                """);

        assertFail("compiler.err.annotation.decl.not.allowed.here",
                """
                class Outer {
                    public void test() {
                        class Local1 {
                            class Local2 {
                                @interface A {}
                            }
                        }
                    }
                }
                """);
        assertFail("compiler.err.annotation.decl.not.allowed.here",
                """
                class Outer {
                    public void test() {
                        class Local {
                            interface I {
                                @interface A {}
                            }
                        }
                    }
                }
                """);
        assertFail("compiler.err.annotation.decl.not.allowed.here",
                """
                class Outer {
                    public void test() {
                        class Local {
                            record R() {
                                @interface A {}
                            }
                        }
                    }
                }
                """);
        assertFail("compiler.err.annotation.decl.not.allowed.here",
                """
                class Outer {
                    public void test() {
                        class Local {
                            enum E {
                                E1;
                                @interface A {}
                            }
                        }
                    }
                }
                """);

        assertFail("compiler.err.annotation.decl.not.allowed.here",
                """
                class Outer {
                    Runnable run = new Runnable() {
                        @interface A {}
                        public void run() {}
                    };
                }
                """);
    }

    @Test
    void testReceiverParameter() {
        assertFail("compiler.err.receiver.parameter.not.applicable.constructor.toplevel.class",
                """
                record R(int i) {
                    public R(R this, int i) {
                        this.i = i;
                    }
                }
                """);
        assertFail("compiler.err.non-static.cant.be.ref",
                """
                class Outer {
                    record R(int i) {
                        public R(Outer Outer.this, int i) {
                            this.i = i;
                        }
                    }
                }
                """);
        assertOK(
                """
                record R(int i) {
                    void m(R this) {}
                    public int i(R this) { return i; }
                }
                """);
    }

    @Test
    void testOnlyOneFieldRef() throws Exception {
        for (String source : List.of(
                "record R(int recordComponent) {}",
                """
                class Test {
                    class Inner {
                        Inner() {
                            record R(int recordComponent) {}
                        }
                    }
                }
                """,
                """
                class Test {
                    class Inner {
                        void m() {
                            record R(int recordComponent) {}
                        }
                    }
                }
                """,
                """
                class Test {
                    void m() {
                        record R(int recordComponent) {}
                    }
                }
                """
        )) {
            File dir = assertOK(true, source);
            int numberOfFieldRefs = 0;
            for (final File fileEntry : Objects.requireNonNull(dir.listFiles())) {
                if (fileEntry.getName().endsWith("R.class")) {
                    ClassModel classFile = ClassFile.of().parse(fileEntry.toPath());
                    for (PoolEntry pe : classFile.constantPool()) {
                        if (pe instanceof FieldRefEntry fieldRefEntry) {
                            numberOfFieldRefs++;
                            NameAndTypeEntry nameAndType = (NameAndTypeEntry) classFile.constantPool()
                                            .entryByIndex(fieldRefEntry.nameAndType().index());
                            Assert.check(nameAndType.name().equalsString("recordComponent"));
                        }
                    }
                    Assert.check(numberOfFieldRefs == 1);
                }
            }
        }
    }

    //  check that fields are initialized in a canonical constructor in the same declaration order as the corresponding
    //  record component
    @Test
    void testCheckInitializationOrderInCompactConstructor() throws Exception {
        FieldInstruction putField1 = null;
        FieldInstruction putField2 = null;
        File dir = assertOK(true, "record R(int i, String s) { R {} }");
        for (final File fileEntry : Objects.requireNonNull(dir.listFiles())) {
            if (fileEntry.getName().equals("R.class")) {
                ClassModel classFile = ClassFile.of().parse(fileEntry.toPath());
                for (MethodModel method : classFile.methods()) {
                    if (method.methodName().equalsString("<init>")) {
                        CodeAttribute code_attribute = method.findAttribute(Attributes.CODE).orElseThrow();
                        for (CodeElement ce : code_attribute.elementList()) {
                            if (ce instanceof Instruction instruction && instruction.opcode() == Opcode.PUTFIELD) {
                                if (putField1 != null && putField2 != null) {
                                    throw new AssertionError("was expecting only two putfield instructions in this method");
                                }
                                if (putField1 == null) {
                                    putField1 = (FieldInstruction) instruction;
                                } else {
                                    putField2 = (FieldInstruction) instruction;
                                }
                            }
                        }
                        // now we need to check that we are assigning to `i` first and to `s` afterwards
                        assert putField1 != null;
                        FieldRefEntry fieldref_info1 = putField1.field();
                        if (!fieldref_info1.name().equalsString("i")) {
                            throw new AssertionError("was expecting variable name 'i'");
                        }
                        assert putField2 != null;
                        FieldRefEntry fieldref_info2 = putField2.field();
                        if (!fieldref_info2.name().equalsString("s")) {
                            throw new AssertionError("was expecting variable name 's'");
                        }
                    }
                }
            }
        }
    }

    @Test
    void testAcceptRecordId() {
        String[] previousOptions = getCompileOptions();
        try {
            String[] testOptions = {};
            setCompileOptions(testOptions);
            assertFail("compiler.err.illegal.start.of.type",
                    "class R {\n" +
                            "    record RR(int i) {\n" +
                            "        return null;\n" +
                            "    }\n" +
                            "    class record {}\n" +
                            "}");
        } finally {
            setCompileOptions(previousOptions);
        }
    }

    @Test
    void testMultipleAnnosInRecord() throws Exception {
        String[] previousOptions = getCompileOptions();

        try {
            String imports = """
                    import java.lang.annotation.ElementType;
                    import java.lang.annotation.Target;
                    """;

            String annotTemplate =
                    """
                    @Target(ElementType.#TARGET)
                    @interface anno#TARGET { }
                    """;

            String recordTemplate =
                    """
                    record R(#TARGETS String s) {}
                    """;

            String[] generalOptions = {
                    "-processor", Processor.class.getName(),
            };

            List<String> targets = List.of("FIELD", "RECORD_COMPONENT", "PARAMETER", "METHOD");

            var interfaces = targets.stream().map(t -> annotTemplate.replaceAll("#TARGET", t)).collect(Collectors.joining("\n"));
            var recordAnnotations = targets.stream().map(t -> "@anno" + t).collect(Collectors.joining(" "));
            String record = recordTemplate.replaceFirst("#TARGETS", recordAnnotations);
            String code = String.format("%s\n%s\n%s\n",imports,interfaces,record);
            String[] testOptions = generalOptions.clone();
            setCompileOptions(testOptions);

            assertOK(true, code);

        // let's reset the default compiler options for other tests
        } finally {
            setCompileOptions(previousOptions);
        }
    }

    @Test
    void testAnnos() throws Exception {
        String[] previousOptions = getCompileOptions();
        try {
            String srcTemplate =
                    """
                    import java.lang.annotation.*;
                    @Target({#TARGET})
                    @Retention(RetentionPolicy.RUNTIME)
                    @interface Anno { }
                    record R(@Anno String s) {}
                    """;

            // testing several combinations, adding even more combinations won't add too much value
            List<String> annoApplicableTargets = List.of(
                    "ElementType.FIELD",
                    "ElementType.METHOD",
                    "ElementType.PARAMETER",
                    "ElementType.RECORD_COMPONENT",
                    "ElementType.TYPE_USE",
                    "ElementType.TYPE_USE,ElementType.FIELD",
                    "ElementType.TYPE_USE,ElementType.METHOD",
                    "ElementType.TYPE_USE,ElementType.PARAMETER",
                    "ElementType.TYPE_USE,ElementType.RECORD_COMPONENT",
                    "ElementType.TYPE_USE,ElementType.FIELD,ElementType.METHOD",
                    "ElementType.TYPE_USE,ElementType.FIELD,ElementType.PARAMETER",
                    "ElementType.TYPE_USE,ElementType.FIELD,ElementType.RECORD_COMPONENT",
                    "ElementType.FIELD,ElementType.TYPE_USE",
                    "ElementType.FIELD,ElementType.CONSTRUCTOR",
                    "ElementType.FIELD,ElementType.LOCAL_VARIABLE",
                    "ElementType.FIELD,ElementType.ANNOTATION_TYPE",
                    "ElementType.FIELD,ElementType.PACKAGE",
                    "ElementType.FIELD,ElementType.TYPE_PARAMETER",
                    "ElementType.FIELD,ElementType.MODULE",
                    "ElementType.METHOD,ElementType.TYPE_USE",
                    "ElementType.PARAMETER,ElementType.TYPE_USE",
                    "ElementType.RECORD_COMPONENT,ElementType.TYPE_USE",
                    "ElementType.FIELD,ElementType.METHOD,ElementType.TYPE_USE",
                    "ElementType.FIELD,ElementType.PARAMETER,ElementType.TYPE_USE",
                    "ElementType.FIELD,ElementType.RECORD_COMPONENT,ElementType.TYPE_USE"
            );

            String[] generalOptions = {
                    "-processor", Processor.class.getName(),
                    "-Atargets="
            };

            for (String target : annoApplicableTargets) {
                String code = srcTemplate.replaceFirst("#TARGET", target);
                String[] testOptions = generalOptions.clone();
                testOptions[testOptions.length - 1] = testOptions[testOptions.length - 1] + target;
                setCompileOptions(testOptions);

                File dir = assertOK(true, code);

                ClassModel classFile = ClassFile.of().parse(findClassFileOrFail(dir, "R.class").toPath());

                // field first
                Assert.check(classFile.fields().size() == 1);
                FieldModel field = classFile.fields().get(0);
                // if FIELD is one of the targets then there must be a declaration annotation applied to the field, apart from
                // the type annotation
                if (target.contains("ElementType.FIELD")) {
                    checkAnno(findAttributeOrFail(field.attributes(), RuntimeVisibleAnnotationsAttribute.class),
                            "Anno");
                } else {
                    assertAttributeNotPresent(field.attributes(), RuntimeVisibleAnnotationsAttribute.class);
                }

                // lets check now for the type annotation
                if (target.contains("ElementType.TYPE_USE")) {
                    checkTypeAnno(findAttributeOrFail(field.attributes(), RuntimeVisibleTypeAnnotationsAttribute.class),
                            "FIELD", "Anno");
                } else {
                    assertAttributeNotPresent(field.attributes(), RuntimeVisibleTypeAnnotationsAttribute.class);
                }

                // checking for the annotation on the corresponding parameter of the canonical constructor
                MethodModel init = findMethodOrFail(classFile, "<init>");
                // if PARAMETER is one of the targets then there must be a declaration annotation applied to the parameter, apart from
                // the type annotation
                if (target.contains("ElementType.PARAMETER")) {
                    checkParameterAnno(
                            (RuntimeVisibleParameterAnnotationsAttribute) findAttributeOrFail(
                                    init.attributes(),
                                    RuntimeVisibleParameterAnnotationsAttribute.class),
                            "Anno");
                } else {
                    assertAttributeNotPresent(init.attributes(), RuntimeVisibleAnnotationsAttribute.class);
                }
                // let's check now for the type annotation
                if (target.contains("ElementType.TYPE_USE")) {
                    checkTypeAnno(findAttributeOrFail(init.attributes(), RuntimeVisibleTypeAnnotationsAttribute.class),
                            "METHOD_FORMAL_PARAMETER", "Anno");
                } else {
                    assertAttributeNotPresent(init.attributes(), RuntimeVisibleTypeAnnotationsAttribute.class);
                }

                // checking for the annotation in the accessor
                MethodModel accessor = findMethodOrFail(classFile, "s");
                // if METHOD is one of the targets then there must be a declaration annotation applied to the accessor, apart from
                // the type annotation
                if (target.contains("ElementType.METHOD")) {
                    checkAnno(findAttributeOrFail(accessor.attributes(), RuntimeVisibleAnnotationsAttribute.class),
                            "Anno");
                } else {
                    assertAttributeNotPresent(accessor.attributes(), RuntimeVisibleAnnotationsAttribute.class);
                }
                // let's check now for the type annotation
                if (target.contains("ElementType.TYPE_USE")) {
                    checkTypeAnno(findAttributeOrFail(accessor.attributes(), RuntimeVisibleTypeAnnotationsAttribute.class),
                            "METHOD_RETURN", "Anno");
                } else {
                    assertAttributeNotPresent(accessor.attributes(), RuntimeVisibleTypeAnnotationsAttribute.class);
                }

                // checking for the annotation in the Record attribute
                RecordAttribute record = (RecordAttribute) findAttributeOrFail(classFile.attributes(), RecordAttribute.class);
                Assert.check(record.components().size() == 1);
                // if RECORD_COMPONENT is one of the targets then there must be a declaration annotation applied to the
                // field, apart from the type annotation
                if (target.contains("ElementType.RECORD_COMPONENT")) {
                    checkAnno(findAttributeOrFail(record.components().get(0).attributes(), RuntimeVisibleAnnotationsAttribute.class),
                            "Anno");
                } else {
                    assertAttributeNotPresent(record.components().get(0).attributes(), RuntimeVisibleAnnotationsAttribute.class);
                }
                // lets check now for the type annotation
                if (target.contains("ElementType.TYPE_USE")) {
                    checkTypeAnno(findAttributeOrFail(record.components().get(0).attributes(), RuntimeVisibleTypeAnnotationsAttribute.class),
                            "FIELD", "Anno");
                } else {
                    assertAttributeNotPresent(record.components().get(0).attributes(), RuntimeVisibleTypeAnnotationsAttribute.class);
                }
            }

            // let's reset the default compiler options for other tests
        } finally {
            setCompileOptions(previousOptions);
        }
    }

    // JDK-8292159: TYPE_USE annotations on generic type arguments
    //              of record components discarded
    @Test
    void testOnlyTypeAnnotationsOnComponentField() throws Exception {
        String code =
                """
                import java.lang.annotation.*;
                import java.util.List;
                @Target({ElementType.TYPE_USE})
                @Retention(RetentionPolicy.RUNTIME)
                @interface Anno { }
                record R(List<@Anno String> s) {}
                """;

        File dir = assertOK(true, code);

        ClassModel classFile = ClassFile.of().parse(findClassFileOrFail(dir, "R.class").toPath());

        // field first
        Assert.check(classFile.fields().size() == 1);
        FieldModel field = classFile.fields().get(0);
        checkTypeAnno(findAttributeOrFail(field.attributes(), RuntimeVisibleTypeAnnotationsAttribute.class),
                "FIELD",
                "Anno");

        // checking for the annotation on the corresponding parameter of the canonical constructor
        MethodModel init = findMethodOrFail(classFile, "<init>");
        checkTypeAnno(findAttributeOrFail(init.attributes(), RuntimeVisibleTypeAnnotationsAttribute.class),
                "METHOD_FORMAL_PARAMETER", "Anno");

        // checking for the annotation in the accessor
        MethodModel accessor = findMethodOrFail(classFile, "s");
        checkTypeAnno(findAttributeOrFail(accessor.attributes(), RuntimeVisibleTypeAnnotationsAttribute.class),
                "METHOD_RETURN", "Anno");

        // checking for the annotation in the Record attribute
        RecordAttribute record = (RecordAttribute) findAttributeOrFail(classFile.attributes(), RecordAttribute.class);
        Assert.check(record.components().size() == 1);
        checkTypeAnno(findAttributeOrFail(record.components().get(0).attributes(),
                                RuntimeVisibleTypeAnnotationsAttribute.class),
                        "FIELD", "Anno");
    }

    private void checkTypeAnno(Attribute<?> rtAnnos,
                               String positionType,
                               String annoName) {
        // containing only one type annotation
        TypeAnnotation tAnno;
        switch (rtAnnos) {
            case RuntimeVisibleTypeAnnotationsAttribute rtVAnnos -> {
                Assert.check(rtVAnnos.annotations().size() == 1);
                tAnno = rtVAnnos.annotations().get(0);
            }
            case RuntimeInvisibleTypeAnnotationsAttribute rtIAnnos -> {
                Assert.check(rtIAnnos.annotations().size() == 1);
                tAnno = rtIAnnos.annotations().get(0);
            }
            default -> throw new AssertionError();
        }
        assert tAnno != null;
        Assert.check(tAnno.targetInfo().targetType().name().equals(positionType));
        String annotationName = tAnno.classSymbol().displayName();
        Assert.check(annotationName.startsWith(annoName));
    }
    private void checkAnno(Attribute<?> rAnnos,
                           String annoName) {
        // containing only one type annotation
        Annotation anno;
        switch (rAnnos) {
            case RuntimeVisibleAnnotationsAttribute rVAnnos -> {
                Assert.check(rVAnnos.annotations().size() == 1);
                anno = rVAnnos.annotations().get(0);
            }
            case RuntimeInvisibleAnnotationsAttribute rIAnnos -> {
                Assert.check(rIAnnos.annotations().size() == 1);
                anno = rIAnnos.annotations().get(0);
            }
            default -> throw new AssertionError();
        }
        assert anno != null;
        String annotationName = anno.classSymbol().displayName();
        Assert.check(annotationName.startsWith(annoName));
    }

    // special case for parameter annotations
    private void checkParameterAnno(RuntimeVisibleParameterAnnotationsAttribute rAnnos,
                           String annoName) {
        // containing only one type annotation
        Assert.check(rAnnos.parameterAnnotations().size() == 1);
        Assert.check(rAnnos.parameterAnnotations().get(0).size() == 1);
        Annotation anno = rAnnos.parameterAnnotations().get(0).get(0);
        String annotationName = anno.classSymbol().displayName();
        Assert.check(annotationName.startsWith(annoName));
    }

    private File findClassFileOrFail(File dir, String name) {
        for (final File fileEntry : dir.listFiles()) {
            if (fileEntry.getName().equals(name)) {
                return fileEntry;
            }
        }
        throw new AssertionError("file not found");
    }

    private MethodModel findMethodOrFail(ClassModel classFile, String name) {
        for (MethodModel method : classFile.methods()) {
            if (method.methodName().equalsString(name)) {
                return method;
            }
        }
        throw new AssertionError("method not found");
    }

    private Attribute<?> findAttributeOrFail(List<Attribute<?>> attributes, Class<? extends Attribute<?>> attrClass) {
        for (Attribute<?> attribute : attributes) {
            if (attrClass.isAssignableFrom(attribute.getClass())) {
                return attribute;
            }
        }
        throw new AssertionError("attribute not found" + attrClass.toString() + "!!!!" + attributes.getFirst().getClass().toString());
    }

    private void assertAttributeNotPresent(List<Attribute<?>> attributes, Class<? extends Attribute<?>> attrClass) {
        for (Attribute<?> attribute : attributes) {
            if (attribute.getClass() == attrClass) {
                throw new AssertionError("attribute not expected");
            }
        }
    }

    @SupportedAnnotationTypes("*")
    public static final class Processor extends JavacTestingAbstractProcessor {

        String targets;
        int numberOfTypeAnnotations;

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            targets = processingEnv.getOptions().get("targets");
            for (TypeElement te : annotations) {
                if (te.toString().equals("Anno")) {
                    checkElements(te, roundEnv, targets);
                    if (targets.contains("TYPE_USE")) {
                        Element element = processingEnv.getElementUtils().getTypeElement("R");
                        numberOfTypeAnnotations = 0;
                        checkTypeAnnotations(element);
                        Assert.check(numberOfTypeAnnotations == 4);
                    }
                }
            }
            return true;
        }

        void checkElements(TypeElement te, RoundEnvironment renv, String targets) {
            Set<? extends Element> annoElements = renv.getElementsAnnotatedWith(te);
            Set<String> targetSet = new HashSet<>(Arrays.asList(targets.split(",")));
            // we will check for type annotation in another method
            targetSet.remove("ElementType.TYPE_USE");
            for (Element e : annoElements) {
                Symbol s = (Symbol) e;
                switch (s.getKind()) {
                    case FIELD -> {
                        Assert.check(targetSet.contains("ElementType.FIELD"));
                        targetSet.remove("ElementType.FIELD");
                    }
                    case METHOD -> {
                        Assert.check(targetSet.contains("ElementType.METHOD"));
                        targetSet.remove("ElementType.METHOD");
                    }
                    case PARAMETER -> {
                        Assert.check(targetSet.contains("ElementType.PARAMETER"));
                        targetSet.remove("ElementType.PARAMETER");
                    }
                    case RECORD_COMPONENT -> {
                        Assert.check(targetSet.contains("ElementType.RECORD_COMPONENT"));
                        targetSet.remove("ElementType.RECORD_COMPONENT");
                    }
                    default -> throw new AssertionError("unexpected element kind");
                }
            }
        }

        private void checkTypeAnnotations(Element rootElement) {
            new ElementScanner<Void, Void>() {
                @Override public Void visitVariable(VariableElement e, Void p) {
                    Symbol s = (Symbol) e;
                    if (s.getKind() == ElementKind.FIELD ||
                            s.getKind() == ElementKind.PARAMETER &&
                            s.name.toString().equals("s")) {
                        int currentTAs = numberOfTypeAnnotations;
                        verifyTypeAnnotations(e.asType().getAnnotationMirrors());
                        Assert.check(currentTAs + 1 == numberOfTypeAnnotations);
                    }
                    return null;
                }
                @Override
                public Void visitExecutable(ExecutableElement e, Void p) {
                    Symbol s = (Symbol) e;
                    if (s.getKind() == ElementKind.METHOD &&
                                    s.name.toString().equals("s")) {
                        int currentTAs = numberOfTypeAnnotations;
                        verifyTypeAnnotations(e.getReturnType().getAnnotationMirrors());
                        Assert.check(currentTAs + 1 == numberOfTypeAnnotations);
                    }
                    scan(e.getParameters(), p);
                    return null;
                }
                @Override public Void visitRecordComponent(RecordComponentElement e, Void p) {
                    int currentTAs = numberOfTypeAnnotations;
                    verifyTypeAnnotations(e.asType().getAnnotationMirrors());
                    Assert.check(currentTAs + 1 == numberOfTypeAnnotations);
                    return null;
                }
            }.scan(rootElement, null);
        }

        private void verifyTypeAnnotations(Iterable<? extends AnnotationMirror> annotations) {
            for (AnnotationMirror mirror : annotations) {
                Assert.check(mirror.toString().startsWith("@Anno"));
                if (mirror instanceof TypeCompound) {
                    numberOfTypeAnnotations++;
                }
            }
        }
    }

    @Test
    void testMethodsInheritedFromRecordArePublicAndFinal() throws Exception {
        int numberOfFieldRefs = 0;
        File dir = assertOK(true, "record R() {}");
        for (final File fileEntry : Objects.requireNonNull(dir.listFiles())) {
            if (fileEntry.getName().equals("R.class")) {
                ClassModel classFile = ClassFile.of().parse(fileEntry.toPath());
                for (MethodModel method : classFile.methods())
                    switch (method.methodName().stringValue()) {
                        case "toString", "equals", "hashCode" ->
                            Assert.check(((method.flags().flagsMask() & ClassFile.ACC_PUBLIC) != 0) && ((method.flags().flagsMask() & ClassFile.ACC_FINAL) != 0));
                        default -> {}
                    }
            }
        }
    }

    private static final List<String> ACCESSIBILITY = List.of(
            "public", "protected", "", "private");

    @Test
    void testCanonicalAccessibility() throws Exception {
        // accessibility of canonical can't be stronger than that of the record type
        for (String a1 : ACCESSIBILITY) {
            for (String a2 : ACCESSIBILITY) {
                if (protection(a2) > protection(a1)) {
                    assertFail("compiler.err.invalid.canonical.constructor.in.record", "class R {# record RR() { # RR {} } }", a1, a2);
                } else {
                    assertOK("class R {# record RR() { # RR {} } }", a1, a2);
                }
            }
        }

        // now lets check that when compiler the compiler generates the canonical, it has the same accessibility
        // as the record type
        for (String a : ACCESSIBILITY) {
            File dir = assertOK(true, "class R {# record RR() {} }", a);
            for (final File fileEntry : Objects.requireNonNull(dir.listFiles())) {
                if (fileEntry.getName().equals("R$RR.class")) {
                    ClassModel classFile = ClassFile.of().parse(fileEntry.toPath());
                    for (MethodModel method : classFile.methods())
                        if (method.methodName().equalsString("<init>")) {
                            Assert.check(method.flags().flagsMask() == accessFlag(a),
                                    "was expecting access flag " + accessFlag(a) + " but found " + method.flags().flagsMask());
                        }
                }
            }
        }
    }

    private int protection(String access) {
        return switch (access) {
            case "private" -> 3;
            case "protected" -> 1;
            case "public" -> 0;
            case "" -> 2;
            default -> throw new AssertionError();
        };
    }

    private int accessFlag(String access) {
        return switch (access) {
            case "private" -> ClassFile.ACC_PRIVATE;
            case "protected" -> ClassFile.ACC_PROTECTED;
            case "public" -> ClassFile.ACC_PUBLIC;
            case "" -> 0;
            default -> throw new AssertionError();
        };
    }

    @Test
    void testSameArity() {
        for (String source : List.of(
                """
                record R(int... args) {
                    public R(int... args) {
                        this.args = args;
                    }
                }
                """,
                """
                record R(int[] args) {
                    public R(int[] args) {
                        this.args = args;
                    }
                }
                """,
                """
                record R(@A int... ints) {}

                @java.lang.annotation.Target({
                        java.lang.annotation.ElementType.TYPE_USE,
                        java.lang.annotation.ElementType.RECORD_COMPONENT})
                @interface A {}
                """,
                """
                record R(@A int... ints) {
                    R(@A int... ints) {
                        this.ints = ints;
                    }
                }

                @java.lang.annotation.Target({
                        java.lang.annotation.ElementType.TYPE_USE,
                        java.lang.annotation.ElementType.RECORD_COMPONENT})
                @interface A {}
                """
        )) {
            assertOK(source);
        }

        for (String source : List.of(
                """
                record R(int... args) {
                    public R(int[] args) {
                        this.args = args;
                    }
                }
                """,
                """
                record R(int... args) {
                    public R(int[] args) {
                        this.args = args;
                    }
                }
                """,
                """
                record R(String... args) {
                    public R(String[] args) {
                        this.args = args;
                    }
                }
                """,
                """
                record R(String... args) {
                    public R(String[] args) {
                        this.args = args;
                    }
                }
                """
        )) {
            assertFail("compiler.err.invalid.canonical.constructor.in.record", source);
        }
    }

    @Test
    void testSafeVararsAnno() {
        assertFail("compiler.err.annotation.type.not.applicable",
                """
                @SafeVarargs
                record R<T>(T... t) {}
                """,
                """
                @SafeVarargs
                record R<T>(T... t) {
                    R(T... t) {
                        this.t = t;
                    }
                }
                """
        );

        assertOK(
                """
                record R<T>(T... t) {
                    @SafeVarargs
                    R(T... t) {
                        this.t = t;
                    }
                }
                """
        );

        appendCompileOptions("-Xlint:unchecked");
        assertOKWithWarning("compiler.warn.unchecked.varargs.non.reifiable.type",
                """
                record R<T>(T... t) {
                    R(T... t) {
                        this.t = t;
                    }
                }
                """
        );
        removeLastCompileOptions(1);

        assertOK(
                """
                @SuppressWarnings("unchecked")
                record R<T>(T... t) {
                    R(T... t) {
                        this.t = t;
                    }
                }
                """
        );

        assertOK(
                """
                record R<T>(T... t) {
                    @SuppressWarnings("unchecked")
                    R(T... t) {
                        this.t = t;
                    }
                }
                """
        );
    }

    @Test
    void testOverrideAtAccessor() {
        assertOK(
                """
                record R(int i) {
                    @Override
                    public int i() { return i; }
                }
                """,
                """
                record R(int i, int j) {
                    @Override
                    public int i() { return i; }
                    public int j() { return j; }
                }
                """,
                """
                interface I { int i(); }
                record R(int i) implements I {
                    @Override
                    public int i() { return i; }
                }
                """,
                """
                interface I { int i(); }
                record R(int i) implements I {
                    public int i() { return i; }
                }
                """,
                """
                interface I { default int i() { return 0; } }
                record R(int i) implements I {
                    @Override
                    public int i() { return i; }
                }
                """
        );
    }

    @Test
    void testNoAssigmentInsideCompactRecord() {
        assertFail("compiler.err.cant.assign.val.to.var",
                """
                record R(int i) {
                    R {
                        this.i = i;
                    }
                }
                """
        );
        assertFail("compiler.err.cant.assign.val.to.var",
                """
                record R(int i) {
                    R {
                        (this).i = i;
                    }
                }
                """
        );
    }

    @Test
    void testNoNPEStaticAnnotatedFields() {
        assertOK(
                """
                import java.lang.annotation.Native;
                record R() {
                    @Native public static final int i = 0;
                }
                """
        );
        assertOK(
                """
                import java.lang.annotation.Native;
                class Outer {
                    record R() {
                        @Native public static final int i = 0;
                    }
                }
                """
        );
        assertOK(
                """
                import java.lang.annotation.Native;
                class Outer {
                    void m() {
                        record R () {
                            @Native public static final int i = 0;
                        }
                    }
                }
                """
        );
    }

    @Test
    void testDoNotAllowCStyleArraySyntaxForRecComponents() {
        assertFail("compiler.err.record.component.and.old.array.syntax",
                """
                record R(int i[]) {}
                """
        );
        assertFail("compiler.err.record.component.and.old.array.syntax",
                """
                record R(String s[]) {}
                """
        );
        assertFail("compiler.err.record.component.and.old.array.syntax",
                """
                record R<T>(T t[]) {}
                """
        );
    }

    @Test
    void testNoWarningForSerializableRecords() {
        if (!useAP) {
            // don't execute this test when the default annotation processor is on as it will fail due to
            // spurious warnings
            appendCompileOptions("-Werror", "-Xlint:serial");
            assertOK(
                    """
                    import java.io.*;
                    record R() implements java.io.Serializable {}
                    """
            );
            removeLastCompileOptions(2);
        }
    }

    @Test
    void testAnnotationsOnVarargsRecComp() {
        assertOK(
                """
                import java.lang.annotation.*;

                @Target({ElementType.TYPE_USE})
                @interface Simple {}

                record R(@Simple int... val) {
                    static void test() {
                        R rec = new R(10, 20);
                    }
                }
                """
        );
        assertOK(
                """
                import java.lang.annotation.*;

                @Target({ElementType.TYPE_USE})
                @interface SimpleContainer{ Simple[] value(); }

                @Repeatable(SimpleContainer.class)
                @Target({ElementType.TYPE_USE})
                @interface Simple {}

                record R(@Simple int... val) {
                    static void test() {
                        R rec = new R(10, 20);
                    }
                }
                """
        );
    }

    @Test
    void testSaveVarargsAnno() {
        // the compiler would generate an erronous accessor
        assertFail("compiler.err.varargs.invalid.trustme.anno",
                """
                record R(@SafeVarargs String... s) {}
                """
        );
        // but this is OK
        assertOK(
                """
                record R(@SafeVarargs String... s) {
                    public String[] s() { return s; }
                }
                """
        );
    }
}
