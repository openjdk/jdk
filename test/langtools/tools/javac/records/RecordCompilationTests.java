/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.ElementType;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.testng.annotations.Test;
import tools.javac.combo.CompilationTestCase;

import static java.lang.annotation.ElementType.*;
import static org.testng.Assert.assertEquals;

/**
 * RecordCompilationTests
 *
 * @test
 * @summary Negative compilation tests, and positive compilation (smoke) tests for records
 * @library /lib/combo
 * @modules jdk.compiler/com.sun.tools.javac.util
 * @compile --enable-preview -source ${jdk.version} RecordCompilationTests.java
 * @run testng/othervm --enable-preview RecordCompilationTests
 */
@Test
public class RecordCompilationTests extends CompilationTestCase {

    // @@@ When records become a permanent feature, we don't need these any more
    private static String[] PREVIEW_OPTIONS = {"--enable-preview", "-source",
                                               Integer.toString(Runtime.version().feature())};

    private static final List<String> BAD_COMPONENT_NAMES = List.of(
            "clone", "finalize", "getClass", "hashCode",
            "notify", "notifyAll", "toString", "wait");

    {
        setDefaultFilename("R.java");
        setCompileOptions(PREVIEW_OPTIONS);
    }

    public void testMalformedDeclarations() {
        assertFail("compiler.err.premature.eof", "record R()");
        assertFail("compiler.err.premature.eof", "record R();");
        assertFail("compiler.err.illegal.start.of.type", "record R(,) { }");
        assertFail("compiler.err.illegal.start.of.type", "record R((int x)) { }");
        assertFail("compiler.err.record.header.expected", "record R { }");
        assertFail("compiler.err.expected", "record R(foo) { }");
        assertFail("compiler.err.expected", "record R(int int) { }");
        assertFail("compiler.err.mod.not.allowed.here", "abstract record R(String foo) { }");
        //assertFail("compiler.err.illegal.combination.of.modifiers", "non-sealed record R(String foo) { }");
        assertFail("compiler.err.repeated.modifier", "public public record R(String foo) { }");
        assertFail("compiler.err.repeated.modifier", "private private record R(String foo) { }");
        assertFail("compiler.err.already.defined", "record R(int x, int x) {}");
        for (String s : List.of("var", "record"))
            assertFail("compiler.err.restricted.type.not.allowed.here", "record R(# x) { }", s);
        for (String s : List.of("public", "private", "volatile", "final"))
            assertFail("compiler.err.record.cant.declare.field.modifiers", "record R(# String foo) { }", s);
        assertFail("compiler.err.varargs.must.be.last", "record R(int... x, int... y) {}");
        assertFail("compiler.err.instance.initializer.not.allowed.in.records", "record R(int i) { {} }");
    }

    public void testGoodDeclarations() {
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
    }

    public void testGoodMemberDeclarations() {
        String template = "public record R(int x) {\n"
                + "    public R(int x) { this.x = x; }\n"
                + "    public int x() { return x; }\n"
                + "    public boolean equals(Object o) { return true; }\n"
                + "    public int hashCode() { return 0; }\n"
                + "    public String toString() { return null; }\n"
                + "}";
        assertOK(template);
    }

    public void testBadComponentNames() {
        for (String s : BAD_COMPONENT_NAMES)
            assertFail("compiler.err.illegal.record.component.name", "record R(int #) { } ", s);
    }

    public void testRestrictedIdentifiers() {
        for (String s : List.of("interface record { void m(); }",
                "@interface record { }",
                "class record { }",
                "record record(int x) { }",
                "enum record { A, B }",
                "class R<record> { }")) {
            assertFail("compiler.err.restricted.type.not.allowed", s);
        }
    }

    public void testValidMembers() {
        for (String s : List.of("record X(int j) { }",
                "interface I { }",
                "static { }",
                "enum E { A, B }",
                "class C { }"
        )) {
            assertOK("record R(int i) { # }", s);
        }
    }

    public void testCyclic() {
        // Cyclic records are OK, but cyclic inline records would not be
        assertOK("record R(R r) { }");
    }

    public void testBadExtends() {
        assertFail("compiler.err.expected", "record R(int x) extends Object { }");
        assertFail("compiler.err.expected", "record R(int x) {}\n"
                + "record R2(int x) extends R { }");
        assertFail("compiler.err.cant.inherit.from.final", "record R(int x) {}\n"
                + "class C extends R { }");
    }

    public void testNoExtendRecord() {
        assertFail("compiler.err.invalid.supertype.record",
                   "class R extends Record { public String toString() { return null; } public int hashCode() { return 0; } public boolean equals(Object o) { return false; } } }");
    }

    public void testFieldDeclarations() {
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

    public void testAccessorRedeclaration() {
        assertOK("public record R(int x) {\n" +
                "    public int x() { return x; };" +
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

    public void testConstructorRedeclaration() {
        for (String goodCtor : List.of(
                "public R(int x) { this(x, 0); }",
                "public R(int x, int y) { this.x = x; this.y = y; }",
                "public R { }",
                "public R { this.x = 0; }"))
            assertOK("record R(int x, int y) { # }", goodCtor);

        assertOK("import java.util.*; record R(String x, String y) {  public R { Objects.requireNonNull(x); Objects.requireNonNull(y); } }");

        // Not OK to redeclare canonical without DA
        assertFail("compiler.err.var.might.not.have.been.initialized", "record R(int x, int y) { # }",
                   "public R(int x, int y) { this.x = x; }");

        // Not OK to rearrange or change names
        for (String s : List.of("public R(int y, int x) { this.x = x; this.y = y; }",
                                "public R(int _x, int _y) { this.x = _x; this.y = _y; }"))
            assertFail("compiler.err.invalid.canonical.constructor.in.record", "record R(int x, int y) { # }", s);

        // canonical ctor must be public
        for (String s : List.of("", "protected", "private"))
            assertFail("compiler.err.invalid.canonical.constructor.in.record", "record R(int x, int y) { # }",
                       "# R(int x, int y) { this.x = x; this.y = y; }",
                       s);

        // ctor args must match types
        assertFail("compiler.err.invalid.canonical.constructor.in.record",
                "import java.util.*;\n" +
                        "record R(List<String> list) { # }",
                "R(List list) { this.list = list; }");

        // ctor should not add checked exceptions
        assertFail("compiler.err.invalid.canonical.constructor.in.record",
                   "record R() { # }",
                   "public R() throws Exception { }");

        // not even checked exceptions
        assertFail("compiler.err.invalid.canonical.constructor.in.record",
                "record R() { # }",
                 "public R() throws IllegalArgumentException { }");

        // If types match, names must match
        assertFail("compiler.err.invalid.canonical.constructor.in.record",
                   "record R(int x, int y) { public R(int y, int x) { this.x = this.y = 0; }}");

        // first invocation should be one to the canonical
        assertFail("compiler.err.first.statement.must.be.call.to.another.constructor",
                "record R(int x, int y) { public R(int y, int x, int z) { this.x = this.y = 0; } }");

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

    public void testAnnotationCriteria() {
        String imports = "import java.lang.annotation.*;\n";
        String template = "@Target({ # }) @interface A {}\n";
        EnumMap<ElementType, String> annotations = new EnumMap<>(ElementType.class);
        for (ElementType e : values())
            annotations.put(e, template.replace("#", "ElementType." + e.name()));
        EnumSet<ElementType> goodSet = EnumSet.of(RECORD_COMPONENT, FIELD, METHOD, PARAMETER, TYPE_USE);
        EnumSet<ElementType> badSet = EnumSet.of(CONSTRUCTOR, PACKAGE, TYPE, LOCAL_VARIABLE, ANNOTATION_TYPE, TYPE_PARAMETER, MODULE);

        assertEquals(goodSet.size() + badSet.size(), values().length);
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

    public void testNestedRecords() {
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

    public void testDuplicatedMember() {
        String template
                = "    record R(int i) {\n" +
                  "        public int i() { return i; }\n" +
                  "        public int i() { return i; }\n" +
                  "    }";
        assertFail("compiler.err.already.defined", template);
    }

    public void testLocalRecords() {
        assertOK("class R { \n" +
                "    void m() { \n" +
                "        record RR(int x) { };\n" +
                "    }\n" +
                "}");

        // local records can also be final
        assertOK("class R { \n" +
                "    void m() { \n" +
                "        final record RR(int x) { };\n" +
                "    }\n" +
                "}");

        // Capture locals from local record
        assertOK("class R { \n" +
                "    void m(int y) { \n" +
                "        record RR(int x) { public int x() { return y; }};\n" +
                "    }\n" +
                "}");
        // can be contained inside a lambda
        assertOK("""
                class Outer {
                    Runnable run = () -> {
                        record TestRecord(int i) {}
                    };
                }
                """);

        // Can't self-shadow
        assertFail("compiler.err.already.defined",
                   "class R { \n" +
                   "    void m() { \n" +
                   "        record R(int x) { };\n" +
                   "    }\n" +
                   "}");
    }

    public void testCompactDADU() {
        // trivial cases
        assertOK("record R() { public R {} }");
        assertOK("record R(int x) { public R {} }");

        // throwing an unchecked exception
        assertOK("record R(int x) { public R { if (x < 0) { this.x = x; throw new RuntimeException(); }} }");

        assertOK("record R(int x) { public R { if (x < 0) { this.x = x; throw new RuntimeException(); }} }");

        // x is not DA nor DU in the body of the constructor hence error
        assertFail("compiler.err.var.might.not.have.been.initialized", "record R(int x) { # }",
                "public R { if (x < 0) { this.x = -x; } }");
    }

    public void testReturnInCanonical_Compact() {
        assertFail("compiler.err.invalid.canonical.constructor.in.record", "record R(int x) { # }",
                "public R { return; }");
        assertFail("compiler.err.invalid.canonical.constructor.in.record", "record R(int x) { # }",
                "public R { if (i < 0) { return; }}");
        assertOK("record R(int x) { public R(int x) { this.x = x; return; } }");
        assertOK("record R(int x) { public R { Runnable r = () -> { return; };} }");
    }

    public void testNoNativeMethods() {
        assertFail("compiler.err.mod.not.allowed.here", "record R(int x) { # }",
                "public native R {}");
        assertFail("compiler.err.mod.not.allowed.here", "record R(int x) { # }",
                "public native void m();");
    }

    public void testRecordsInsideInner() {
        assertFail("compiler.err.record.declaration.not.allowed.in.inner.classes",
                "class Outer {\n" +
                "    class Inner {\n" +
                "        record R(int a) {}\n" +
                "    }\n" +
                "}");
        assertFail("compiler.err.record.declaration.not.allowed.in.inner.classes",
                """
                class Outer {
                    public void test() {
                        class Inner extends Outer {
                            record R(int i) {}
                        }
                    }
                }
                """);
        assertFail("compiler.err.record.declaration.not.allowed.in.inner.classes",
                """
                class Outer {
                    Runnable run = new Runnable() {
                        record TestRecord(int i) {}
                        public void run() {}
                    };
                }
                """);
        assertFail("compiler.err.record.declaration.not.allowed.in.inner.classes",
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

    public void testReceiverParameter() {
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
}
