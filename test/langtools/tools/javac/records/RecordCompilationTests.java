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
 * RecordCompilationTests
 *
 * @test
 * @summary Negative compilation tests, and positive compilation (smoke) tests for records
 * @library /lib/combo /tools/lib /tools/javac/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.code
 *      jdk.compiler/com.sun.tools.javac.util
 *      jdk.jdeps/com.sun.tools.classfile
 * @build JavacTestingAbstractProcessor
 * @compile --enable-preview -source ${jdk.version} RecordCompilationTests.java
 * @run testng/othervm --enable-preview RecordCompilationTests
 */

import java.io.File;

import java.lang.annotation.ElementType;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;


import com.sun.tools.javac.util.Assert;

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

import com.sun.tools.classfile.Annotation;
import com.sun.tools.classfile.Attribute;
import com.sun.tools.classfile.Attributes;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPool.CPInfo;
import com.sun.tools.classfile.Field;
import com.sun.tools.classfile.Method;
import com.sun.tools.classfile.Record_attribute;
import com.sun.tools.classfile.Record_attribute.ComponentInfo;
import com.sun.tools.classfile.RuntimeAnnotations_attribute;
import com.sun.tools.classfile.RuntimeTypeAnnotations_attribute;
import com.sun.tools.classfile.RuntimeVisibleAnnotations_attribute;
import com.sun.tools.classfile.RuntimeVisibleParameterAnnotations_attribute;
import com.sun.tools.classfile.RuntimeVisibleTypeAnnotations_attribute;
import com.sun.tools.classfile.TypeAnnotation;

import com.sun.tools.javac.api.ClientCodeWrapper.DiagnosticSourceUnwrapper;
import com.sun.tools.javac.code.Attribute.TypeCompound;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.util.JCDiagnostic;

import org.testng.annotations.Test;
import tools.javac.combo.CompilationTestCase;

import static java.lang.annotation.ElementType.*;
import static org.testng.Assert.assertEquals;

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

        // Cant capture locals
        assertFail("compiler.err.non-static.cant.be.ref",
                "class R { \n" +
                        "    void m(int y) { \n" +
                        "        record RR(int x) { public int x() { return y; }};\n" +
                        "    }\n" +
                        "}");

        assertFail("compiler.err.non-static.cant.be.ref",
                "class R { \n" +
                        "    void m() {\n" +
                        "        int y;\n" +
                        "        record RR(int x) { public int x() { return y; }};\n" +
                        "    }\n" +
                        "}");

        // instance fields
        assertFail("compiler.err.non-static.cant.be.ref",
                "class R { \n" +
                        "    int z = 0;\n" +
                        "    void m() { \n" +
                        "        record RR(int x) { public int x() { return z; }};\n" +
                        "    }\n" +
                        "}");

        // or type variables
        assertFail("compiler.err.non-static.cant.be.ref",
                "class R<T> { \n" +
                        "    void m() { \n" +
                        "        record RR(T t) {};\n" +
                        "    }\n" +
                        "}");

        // but static fields are OK
        assertOK("class R { \n" +
                "    static int z = 0;\n" +
                "    void m() { \n" +
                "        record RR(int x) { public int x() { return z; }};\n" +
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

        // if static fields are not DA then error
        assertFail("compiler.err.var.might.not.have.been.initialized",
                "record R() { # }", "static final String x;");

        // ditto
        assertFail("compiler.err.var.might.not.have.been.initialized",
                "record R() { # }", "static final String x; public R {}");

        // ditto
        assertFail("compiler.err.var.might.not.have.been.initialized",
                "record R(int i) { # }", "static final String x; public R {}");
    }

    public void testReturnInCanonical_Compact() {
        assertFail("compiler.err.invalid.canonical.constructor.in.record", "record R(int x) { # }",
                "public R { return; }");
        assertFail("compiler.err.invalid.canonical.constructor.in.record", "record R(int x) { # }",
                "public R { if (i < 0) { return; }}");
        assertOK("record R(int x) { public R(int x) { this.x = x; return; } }");
        assertOK("record R(int x) { public R { Runnable r = () -> { return; };} }");
    }

    public void testArgumentsAreNotFinalInCompact() {
        assertOK(
                """
                record R(int x) {
                    public R {
                        x++;
                    }
                }
                """);
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

    public void testOnlyOneFieldRef() throws Exception {
        int numberOfFieldRefs = 0;
        File dir = assertOK(true, "record R(int recordComponent) {}");
        for (final File fileEntry : dir.listFiles()) {
            if (fileEntry.getName().equals("R.class")) {
                ClassFile classFile = ClassFile.read(fileEntry);
                for (CPInfo cpInfo : classFile.constant_pool.entries()) {
                    if (cpInfo instanceof ConstantPool.CONSTANT_Fieldref_info) {
                        numberOfFieldRefs++;
                        ConstantPool.CONSTANT_NameAndType_info nameAndType =
                                (ConstantPool.CONSTANT_NameAndType_info)classFile.constant_pool
                                        .get(((ConstantPool.CONSTANT_Fieldref_info)cpInfo).name_and_type_index);
                        Assert.check(nameAndType.getName().equals("recordComponent"));
                    }
                }
            }
        }
        Assert.check(numberOfFieldRefs == 1);
    }

    public void testAcceptRecordId() {
        String[] testOptions = {/* no options */};
        setCompileOptions(testOptions);
        assertOKWithWarning("compiler.warn.restricted.type.not.allowed.preview",
                "class R {\n" +
                "    record RR(int i) {\n" +
                "        return null;\n" +
                "    }\n" +
                "    class record {}\n" +
                "}");
        setCompileOptions(PREVIEW_OPTIONS);
    }

    public void testAnnos() throws Exception {
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
                "ElementType.METHOD,ElementType.TYPE_USE",
                "ElementType.PARAMETER,ElementType.TYPE_USE",
                "ElementType.RECORD_COMPONENT,ElementType.TYPE_USE",
                "ElementType.FIELD,ElementType.METHOD,ElementType.TYPE_USE",
                "ElementType.FIELD,ElementType.PARAMETER,ElementType.TYPE_USE",
                "ElementType.FIELD,ElementType.RECORD_COMPONENT,ElementType.TYPE_USE"
        );

        String[] generalOptions = {
                "--enable-preview",
                "-source", Integer.toString(Runtime.version().feature()),
                "-processor", Processor.class.getName(),
                "-Atargets="
        };

        for (String target : annoApplicableTargets) {
            String code = srcTemplate.replaceFirst("#TARGET", target);
            String[] testOptions = generalOptions.clone();
            testOptions[testOptions.length - 1] = testOptions[testOptions.length - 1] + target;
            setCompileOptions(testOptions);

            File dir = assertOK(true, code);

            ClassFile classFile = ClassFile.read(findClassFileOrFail(dir, "R.class"));

            // field first
            Assert.check(classFile.fields.length == 1);
            Field field = classFile.fields[0];
            /* if FIELD is one of the targets then there must be a declaration annotation applied to the field, apart from
             * the type annotation
             */
            if (target.contains("FIELD")) {
                checkAnno(classFile,
                        (RuntimeAnnotations_attribute)findAttributeOrFail(
                                field.attributes,
                                RuntimeVisibleAnnotations_attribute.class),
                        "Anno");
            } else {
                assertAttributeNotPresent(field.attributes, RuntimeVisibleAnnotations_attribute.class);
            }

            // lets check now for the type annotation
            if (target.contains("TYPE_USE")) {
                checkTypeAnno(
                        classFile,
                        (RuntimeVisibleTypeAnnotations_attribute)findAttributeOrFail(field.attributes, RuntimeVisibleTypeAnnotations_attribute.class),
                        "FIELD",
                        "Anno");
            } else {
                assertAttributeNotPresent(field.attributes, RuntimeVisibleTypeAnnotations_attribute.class);
            }

            // checking for the annotation on the corresponding parameter of the canonical constructor
            Method init = findMethodOrFail(classFile, "<init>");
            /* if PARAMETER is one of the targets then there must be a declaration annotation applied to the parameter, apart from
             * the type annotation
             */
            if (target.contains("PARAMETER")) {
                checkParameterAnno(classFile,
                        (RuntimeVisibleParameterAnnotations_attribute)findAttributeOrFail(
                                init.attributes,
                                RuntimeVisibleParameterAnnotations_attribute.class),
                        "Anno");
            } else {
                assertAttributeNotPresent(init.attributes, RuntimeVisibleAnnotations_attribute.class);
            }
            // let's check now for the type annotation
            if (target.contains("TYPE_USE")) {
                checkTypeAnno(
                        classFile,
                        (RuntimeVisibleTypeAnnotations_attribute) findAttributeOrFail(init.attributes, RuntimeVisibleTypeAnnotations_attribute.class),
                        "METHOD_FORMAL_PARAMETER", "Anno");
            } else {
                assertAttributeNotPresent(init.attributes, RuntimeVisibleTypeAnnotations_attribute.class);
            }

            // checking for the annotation in the accessor
            Method accessor = findMethodOrFail(classFile, "s");
            /* if METHOD is one of the targets then there must be a declaration annotation applied to the accessor, apart from
             * the type annotation
             */
            if (target.contains("METHOD")) {
                checkAnno(classFile,
                        (RuntimeAnnotations_attribute)findAttributeOrFail(
                                accessor.attributes,
                                RuntimeVisibleAnnotations_attribute.class),
                        "Anno");
            } else {
                assertAttributeNotPresent(accessor.attributes, RuntimeVisibleAnnotations_attribute.class);
            }
            // let's check now for the type annotation
            if (target.contains("TYPE_USE")) {
                checkTypeAnno(
                        classFile,
                        (RuntimeVisibleTypeAnnotations_attribute)findAttributeOrFail(accessor.attributes, RuntimeVisibleTypeAnnotations_attribute.class),
                        "METHOD_RETURN", "Anno");
            } else {
                assertAttributeNotPresent(accessor.attributes, RuntimeVisibleTypeAnnotations_attribute.class);
            }

            // checking for the annotation in the Record attribute
            Record_attribute record = (Record_attribute)findAttributeOrFail(classFile.attributes, Record_attribute.class);
            Assert.check(record.component_count == 1);
            /* if RECORD_COMPONENT is one of the targets then there must be a declaration annotation applied to the
             * field, apart from the type annotation
             */
            if (target.contains("RECORD_COMPONENT")) {
                checkAnno(classFile,
                        (RuntimeAnnotations_attribute)findAttributeOrFail(
                                record.component_info_arr[0].attributes,
                                RuntimeVisibleAnnotations_attribute.class),
                        "Anno");
            } else {
                assertAttributeNotPresent(record.component_info_arr[0].attributes, RuntimeVisibleAnnotations_attribute.class);
            }
            // lets check now for the type annotation
            if (target.contains("TYPE_USE")) {
                checkTypeAnno(
                        classFile,
                        (RuntimeVisibleTypeAnnotations_attribute)findAttributeOrFail(
                                record.component_info_arr[0].attributes,
                                RuntimeVisibleTypeAnnotations_attribute.class),
                        "FIELD", "Anno");
            } else {
                assertAttributeNotPresent(record.component_info_arr[0].attributes, RuntimeVisibleTypeAnnotations_attribute.class);
            }
        }

        // let's reset the default compiler options for other tests
        setCompileOptions(PREVIEW_OPTIONS);
    }

    private void checkTypeAnno(ClassFile classFile,
                               RuntimeTypeAnnotations_attribute rtAnnos,
                               String positionType,
                               String annoName) throws Exception {
        // containing only one type annotation
        Assert.check(rtAnnos.annotations.length == 1);
        TypeAnnotation tAnno = (TypeAnnotation)rtAnnos.annotations[0];
        Assert.check(tAnno.position.type.toString().equals(positionType));
        String annotationName = classFile.constant_pool.getUTF8Value(tAnno.annotation.type_index).toString().substring(1);
        Assert.check(annotationName.startsWith(annoName));
    }

    private void checkAnno(ClassFile classFile,
                           RuntimeAnnotations_attribute rAnnos,
                           String annoName) throws Exception {
        // containing only one type annotation
        Assert.check(rAnnos.annotations.length == 1);
        Annotation anno = (Annotation)rAnnos.annotations[0];
        String annotationName = classFile.constant_pool.getUTF8Value(anno.type_index).toString().substring(1);
        Assert.check(annotationName.startsWith(annoName));
    }

    // special case for parameter annotations
    private void checkParameterAnno(ClassFile classFile,
                           RuntimeVisibleParameterAnnotations_attribute rAnnos,
                           String annoName) throws Exception {
        // containing only one type annotation
        Assert.check(rAnnos.parameter_annotations.length == 1);
        Assert.check(rAnnos.parameter_annotations[0].length == 1);
        Annotation anno = (Annotation)rAnnos.parameter_annotations[0][0];
        String annotationName = classFile.constant_pool.getUTF8Value(anno.type_index).toString().substring(1);
        Assert.check(annotationName.startsWith(annoName));
    }

    private File findClassFileOrFail(File dir, String name) {
        for (final File fileEntry : dir.listFiles()) {
            if (fileEntry.getName().equals("R.class")) {
                return fileEntry;
            }
        }
        throw new AssertionError("file not found");
    }

    private Method findMethodOrFail(ClassFile classFile, String name) throws Exception {
        for (Method method : classFile.methods) {
            if (method.getName(classFile.constant_pool).equals(name)) {
                return method;
            }
        }
        throw new AssertionError("method not found");
    }

    private Attribute findAttributeOrFail(Attributes attributes, Class<? extends Attribute> attrClass) {
        for (Attribute attribute : attributes) {
            if (attribute.getClass() == attrClass) {
                return attribute;
            }
        }
        throw new AssertionError("attribute not found");
    }

    private void assertAttributeNotPresent(Attributes attributes, Class<? extends Attribute> attrClass) {
        for (Attribute attribute : attributes) {
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
                    case FIELD:
                        Assert.check(targetSet.contains("ElementType.FIELD"));
                        targetSet.remove("ElementType.FIELD");
                        break;
                    case METHOD:
                        Assert.check(targetSet.contains("ElementType.METHOD"));
                        targetSet.remove("ElementType.METHOD");
                        break;
                    case PARAMETER:
                        Assert.check(targetSet.contains("ElementType.PARAMETER"));
                        targetSet.remove("ElementType.PARAMETER");
                        break;
                    case RECORD_COMPONENT:
                        Assert.check(targetSet.contains("ElementType.RECORD_COMPONENT"));
                        targetSet.remove("ElementType.RECORD_COMPONENT");
                        break;
                    default:
                        throw new AssertionError("unexpected element kind");
                }
            }
            Assert.check(targetSet.isEmpty(), targetSet.toString());
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
}
