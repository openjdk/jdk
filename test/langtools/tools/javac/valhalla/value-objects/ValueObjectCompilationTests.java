/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * ValueObjectCompilationTests
 *
 * @test
 * @bug 8287136 8292630 8279368 8287136 8287770 8279840 8279672 8292753 8287763 8279901 8287767 8293183 8293120
 *      8329345 8341061 8340984 8334484
 * @summary Negative compilation tests, and positive compilation (smoke) tests for Value Objects
 * @enablePreview
 * @library /lib/combo /tools/lib
 * @modules
 *     jdk.compiler/com.sun.tools.javac.util
 *     jdk.compiler/com.sun.tools.javac.api
 *     jdk.compiler/com.sun.tools.javac.main
 *     jdk.compiler/com.sun.tools.javac.code
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run junit ValueObjectCompilationTests
 */

import java.io.File;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Instruction;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.constant.ConstantDescs;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.sun.tools.javac.util.Assert;

import com.sun.tools.javac.code.Flags;

import org.junit.jupiter.api.Test;
import tools.javac.combo.CompilationTestCase;
import toolbox.ToolBox;

class ValueObjectCompilationTests extends CompilationTestCase {

    private static String[] PREVIEW_OPTIONS = {
            "--enable-preview",
            "-source", Integer.toString(Runtime.version().feature())
    };

    public ValueObjectCompilationTests() {
        setDefaultFilename("ValueObjectsTest.java");
        setCompileOptions(PREVIEW_OPTIONS);
    }

    @Test
    void testValueModifierConstraints() {
        assertFail("compiler.err.illegal.combination.of.modifiers",
                """
                value @interface IA {}
                """);
        assertFail("compiler.err.illegal.combination.of.modifiers",
                """
                value interface I {}
                """);
        assertFail("compiler.err.mod.not.allowed.here",
                """
                class Test {
                    value int x;
                }
                """);
        assertFail("compiler.err.mod.not.allowed.here",
                """
                class Test {
                    value int foo();
                }
                """);
        assertFail("compiler.err.mod.not.allowed.here",
                """
                value enum Enum {}
                """);
    }

    record TestData(String message, String snippet, String[] compilerOptions, boolean testLocalToo) {
        TestData(String snippet) {
            this("", snippet, null, true);
        }

        TestData(String snippet, boolean testLocalToo) {
            this("", snippet, null, testLocalToo);
        }

        TestData(String message, String snippet) {
            this(message, snippet, null, true);
        }

        TestData(String snippet, String[] compilerOptions) {
            this("", snippet, compilerOptions, true);
        }

        TestData(String message, String snippet, String[] compilerOptions) {
            this(message, snippet, compilerOptions, true);
        }

        TestData(String message, String snippet, boolean testLocalToo) {
            this(message, snippet, null, testLocalToo);
        }
    }

    private void testHelper(List<TestData> testDataList) {
        String ttt =
                """
                    class TTT {
                        void m() {
                            #LOCAL
                        }
                    }
                """;
        for (TestData td : testDataList) {
            String localSnippet = ttt.replace("#LOCAL", td.snippet);
            String[] previousOptions = getCompileOptions();
            try {
                if (td.compilerOptions != null) {
                    setCompileOptions(td.compilerOptions);
                }
                if (td.message == "") {
                    assertOK(td.snippet);
                    if (td.testLocalToo) {
                        assertOK(localSnippet);
                    }
                } else if (td.message.startsWith("compiler.err")) {
                    assertFail(td.message, td.snippet);
                    if (td.testLocalToo) {
                        assertFail(td.message, localSnippet);
                    }
                } else {
                    assertOKWithWarning(td.message, td.snippet);
                    if (td.testLocalToo) {
                        assertOKWithWarning(td.message, localSnippet);
                    }
                }
            } finally {
                setCompileOptions(previousOptions);
            }
        }
    }

    private static final List<TestData> superClassConstraints = List.of(
            new TestData(
                    "compiler.err.super.class.method.cannot.be.synchronized",
                    """
                    abstract class I {
                        synchronized void foo() {}
                    }
                    value class V extends I {}
                    """
            ),
            new TestData(
                    "compiler.err.concrete.supertype.for.value.class",
                    """
                    class ConcreteSuperType {
                        static abstract value class V extends ConcreteSuperType {}  // Error: concrete super.
                    }
                    """
            ),
            new TestData(
                    """
                    value record Point(int x, int y) {}
                    """
            ),
            new TestData(
                    """
                    value class One extends Number {
                        public int intValue() { return 0; }
                        public long longValue() { return 0; }
                        public float floatValue() { return 0; }
                        public double doubleValue() { return 0; }
                    }
                    """
            ),
            new TestData(
                    """
                    value class V extends Object {}
                    """
            ),
            new TestData(
                    "compiler.err.value.type.has.identity.super.type",
                    """
                    abstract class A {}
                    value class V extends A {}
                    """
            )
    );

    @Test
    void testSuperClassConstraints() {
        testHelper(superClassConstraints);
    }

    @Test
    void testRepeatedModifiers() {
        assertFail("compiler.err.repeated.modifier", "value value class ValueTest {}");
    }

    @Test
    void testParserTest() {
        assertOK(
                """
                value class Substring implements CharSequence {
                    private String str;
                    private int start;
                    private int end;

                    public Substring(String str, int start, int end) {
                        checkBounds(start, end, str.length());
                        this.str = str;
                        this.start = start;
                        this.end = end;
                    }

                    public int length() {
                        return end - start;
                    }

                    public char charAt(int i) {
                        checkBounds(0, i, length());
                        return str.charAt(start + i);
                    }

                    public Substring subSequence(int s, int e) {
                        checkBounds(s, e, length());
                        return new Substring(str, start + s, start + e);
                    }

                    public String toString() {
                        return str.substring(start, end);
                    }

                    private static void checkBounds(int start, int end, int length) {
                        if (start < 0 || end < start || length < end)
                            throw new IndexOutOfBoundsException();
                    }
                }
                """
        );
    }

    private static final List<TestData> semanticsViolations = List.of(
            new TestData(
                    "compiler.err.cant.inherit.from.final",
                    """
                    value class Base {}
                    class Subclass extends Base {}
                    """
            ),
            new TestData(
                    "compiler.err.cant.assign.val.to.var",
                    """
                    value class Point {
                        int x = 10;
                        int y;
                        Point (int x, int y) {
                            this.x = x; // Error, final field 'x' is already assigned to.
                            this.y = y; // OK.
                        }
                    }
                    """
            ),
            new TestData(
                    "compiler.err.cant.assign.val.to.var",
                    """
                    value class Point {
                        int x;
                        int y;
                        Point (int x, int y) {
                            this.x = x;
                            this.y = y;
                        }
                        void foo(Point p) {
                            this.y = p.y; // Error, y is final and can't be written outside of ctor.
                        }
                    }
                    """
            ),
            new TestData(
                    "compiler.err.cant.assign.val.to.var",
                    """
                    abstract value class Point {
                        int x;
                        int y;
                        Point (int x, int y) {
                            this.x = x;
                            this.y = y;
                        }
                        void foo(Point p) {
                            this.y = p.y; // Error, y is final and can't be written outside of ctor.
                        }
                    }
                    """
            ),
            new TestData(
                    "compiler.err.strict.field.not.have.been.initialized.before.super",
                    """
                    value class Point {
                        int x;
                        int y;
                        Point (int x, int y) {
                            this.x = x;
                            // y hasn't been initialized
                        }
                    }
                    """
            ),
            new TestData(
                    "compiler.err.mod.not.allowed.here",
                    """
                    abstract value class V {
                        synchronized void foo() {
                         // Error, abstract value class may not declare a synchronized instance method.
                        }
                    }
                    """
            ),
            new TestData(
                    """
                    abstract value class V {
                        static synchronized void foo() {} // OK static
                    }
                    """
            ),
            new TestData(
                    "compiler.err.mod.not.allowed.here",
                    """
                    value class V {
                        synchronized void foo() {}
                    }
                    """
            ),
            new TestData(
                    """
                    value class V {
                        synchronized static void soo() {} // OK static
                    }
                    """
            ),
            new TestData(
                    "compiler.err.type.found.req",
                    """
                    value class V {
                        { synchronized(this) {} }
                    }
                    """
            ),
            new TestData(
                    "compiler.err.mod.not.allowed.here",
                    """
                    value record R() {
                        synchronized void foo() { } // Error;
                        synchronized static void soo() {} // OK.
                    }
                    """
            ),
            new TestData(
                    "compiler.err.cant.ref.before.ctor.called",
                    """
                    value class V {
                        int x;
                        V() {
                            foo(this); // Error.
                            x = 10;
                        }
                        void foo(V v) {}
                    }
                    """
            ),
            new TestData(
                    "compiler.err.cant.ref.before.ctor.called",
                    """
                    value class V {
                        int x;
                        V() {
                            x = 10;
                            foo(this); // error
                        }
                        void foo(V v) {}
                    }
                    """
            ),
            new TestData(
                    "compiler.err.type.found.req",
                    """
                    interface I {}
                    interface VI extends I {}
                    class C {}
                    value class VC<T extends VC> {
                        void m(T t) {
                            synchronized(t) {} // error
                        }
                    }
                    """
            ),
            new TestData(
                    "compiler.err.type.found.req",
                    """
                    interface I {}
                    interface VI extends I {}
                    class C {}
                    value class VC<T extends VC> {
                        void foo(Object o) {
                            synchronized ((VC & I)o) {} // error
                        }
                    }
                    """
            ),
            new TestData(
                    // OK if the value class is abstract
                    """
                    interface I {}
                    abstract value class VI implements I {}
                    class C {}
                    value class VC<T extends VC> {
                        void bar(Object o) {
                            synchronized ((VI & I)o) {} // error
                        }
                    }
                    """
            ),
            new TestData(
                    "compiler.err.type.found.req", // --enable-preview -source"
                    """
                    class V {
                        final Integer val = Integer.valueOf(42);
                        void test() {
                            synchronized (val) { // error
                            }
                        }
                    }
                    """
            ),
            new TestData(
                    "compiler.err.type.found.req", // --enable-preview -source"
                    """
                    import java.time.*;
                    class V {
                        final Duration val = Duration.ZERO;
                        void test() {
                            synchronized (val) { // warn
                            }
                        }
                    }
                    """,
                    false // cant do local as there is an import statement
            ),
            new TestData(
                    "compiler.warn.attempt.to.synchronize.on.instance.of.value.based.class", // empty options
                    """
                    class V {
                        final Integer val = Integer.valueOf(42);
                        void test() {
                            synchronized (val) { // warn
                            }
                        }
                    }
                    """,
                    new String[] {}
            ),
            new TestData(
                    "compiler.warn.attempt.to.synchronize.on.instance.of.value.based.class", // --source
                    """
                    class V {
                        final Integer val = Integer.valueOf(42);
                        void test() {
                            synchronized (val) { // warn
                            }
                        }
                    }
                    """,
                    new String[] {"--source", Integer.toString(Runtime.version().feature())}
            ),
            new TestData(
                    "compiler.warn.attempt.to.synchronize.on.instance.of.value.based.class", // --source
                    """
                    class V {
                        final Integer val = Integer.valueOf(42);
                        void test() {
                            synchronized (val) { // warn
                            }
                        }
                    }
                    """,
                    new String[] {"--source", Integer.toString(Runtime.version().feature())}
            ),
            new TestData(
                    "compiler.err.illegal.combination.of.modifiers", // --enable-preview -source"
                    """
                    value class V {
                        volatile int f = 1;
                    }
                    """
            )
    );

    @Test
    void testSemanticsViolations() {
        testHelper(semanticsViolations);
    }

    private static final List<TestData> sealedClassesData = List.of(
            new TestData(
                    """
                    abstract sealed value class SC {}
                    value class VC extends SC {}
                    """,
                    false // local sealed classes are not allowed
            ),
            new TestData(
                    """
                    abstract sealed interface SI {}
                    value class VC implements SI {}
                    """,
                    false // local sealed classes are not allowed
            ),
            new TestData(
                    """
                    abstract sealed class SC {}
                    final class IC extends SC {}
                    non-sealed class IC2 extends SC {}
                    final class IC3 extends IC2 {}
                    """,
                    false
            ),
            new TestData(
                    """
                    abstract sealed interface SI {}
                    final class IC implements SI {}
                    non-sealed class IC2 implements SI {}
                    final class IC3 extends IC2 {}
                    """,
                    false // local sealed classes are not allowed
            ),
            new TestData(
                    "compiler.err.non.abstract.value.class.cant.be.sealed.or.non.sealed",
                    """
                    abstract sealed value class SC {}
                    non-sealed value class VC extends SC {}
                    """,
                    false
            ),
            new TestData(
                    "compiler.err.non.abstract.value.class.cant.be.sealed.or.non.sealed",
                    """
                    sealed value class SI {}
                    """,
                    false
            ),
            new TestData(
                    """
                    sealed abstract value class SI {}
                    value class V extends SI {}
                    """,
                    false
            ),
            new TestData(
                    """
                    sealed abstract value class SI permits V {}
                    value class V extends SI {}
                    """,
                    false
            ),
            new TestData(
                    """
                    sealed interface I {}
                    non-sealed abstract value class V implements I {}
                    """,
                    false
            ),
            new TestData(
                    """
                    sealed interface I permits V {}
                    non-sealed abstract value class V implements I {}
                    """,
                    false
            )
    );

    @Test
    void testInteractionWithSealedClasses() {
        testHelper(sealedClassesData);
    }

    @Test
    void testCheckClassFileFlags() throws Exception {
        for (String source : List.of(
                """
                interface I {}
                class Test {
                    I i = new I() {};
                }
                """,
                """
                class C {}
                class Test {
                    C c = new C() {};
                }
                """,
                """
                class Test {
                    Object o = new Object() {};
                }
                """,
                """
                class Test {
                    abstract class Inner {}
                }
                """
        )) {
            File dir = assertOK(true, source);
            for (final File fileEntry : dir.listFiles()) {
                if (fileEntry.getName().contains("$")) {
                    var classFile = ClassFile.of().parse(fileEntry.toPath());
                    Assert.check(classFile.flags().has(AccessFlag.IDENTITY));
                }
            }
        }

        for (String source : List.of(
                """
                class C {}
                """,
                """
                abstract class A {
                    int i;
                }
                """,
                """
                abstract class A {
                    synchronized void m() {}
                }
                """,
                """
                class C {
                    synchronized void m() {}
                }
                """,
                """
                abstract class A {
                    int i;
                    { i = 0; }
                }
                """,
                """
                abstract class A {
                    A(int i) {}
                }
                """,
                """
                    enum E {}
                """,
                """
                    record R() {}
                """
        )) {
            File dir = assertOK(true, source);
            for (final File fileEntry : dir.listFiles()) {
                var classFile = ClassFile.of().parse(fileEntry.toPath());
                Assert.check(classFile.flags().has(AccessFlag.IDENTITY));
            }
        }

        {
            String source =
                    """
                    abstract value class A {}
                    value class Sub extends A {} //implicitly final
                    """;
            File dir = assertOK(true, source);
            for (final File fileEntry : dir.listFiles()) {
                var classFile = ClassFile.of().parse(fileEntry.toPath());
                switch (classFile.thisClass().asInternalName()) {
                    case "Sub":
                        Assert.check((classFile.flags().flagsMask() & (Flags.FINAL)) != 0);
                        break;
                    case "A":
                        Assert.check((classFile.flags().flagsMask() & (Flags.ABSTRACT)) != 0);
                        break;
                    default:
                        throw new AssertionError("you shoulnd't be here");
                }
            }
        }

        for (String source : List.of(
                """
                value class V {
                    int i = 0;
                    static int j;
                }
                """,
                """
                abstract value class A {
                    static int j;
                }

                value class V extends A {
                    int i = 0;
                }
                """
        )) {
            File dir = assertOK(true, source);
            for (final File fileEntry : dir.listFiles()) {
                var classFile = ClassFile.of().parse(fileEntry.toPath());
                for (var field : classFile.fields()) {
                    if (!field.flags().has(AccessFlag.STATIC)) {
                        Set<AccessFlag> fieldFlags = field.flags().flags();
                        Assert.check(fieldFlags.size() == 2 && fieldFlags.contains(AccessFlag.FINAL) && fieldFlags.contains(AccessFlag.STRICT_INIT));
                    }
                }
            }
        }
    }

    @Test
    void testConstruction() throws Exception {
        record Data(String src, boolean isRecord) {
            Data(String src) {
                this(src, false);
            }
        }
        for (Data data : List.of(
                new Data(
                    """
                    value class Test {
                        int i = 100;
                    }
                    """),
                new Data(
                    """
                    value class Test {
                        int i;
                        Test() {
                            i = 100;
                        }
                    }
                    """),
                new Data(
                    """
                    value class Test {
                        int i;
                        Test() {
                            i = 100;
                            super();
                        }
                    }
                    """),
                new Data(
                    """
                    value class Test {
                        int i;
                        Test() {
                            this.i = 100;
                            super();
                        }
                    }
                    """),
                new Data(
                    """
                    value record Test(int i) {}
                    """, true)
        )) {
            if (!data.isRecord()) {
                checkMnemonicsFor(data.src, "aload_0,bipush,putfield,aload_0,invokespecial,return");
            } else {
                checkMnemonicsFor(data.src, "aload_0,iload_1,putfield,aload_0,invokespecial,return");
            }
        }

        String source =
                """
                value class Test {
                    int i = 100;
                    int j = 0;
                    {
                        System.out.println(j);
                    }
                }
                """;
        checkMnemonicsFor(
                """
                value class Test {
                    int i = 100;
                    int j = 0;
                    {
                        System.out.println(j);
                    }
                }
                """,
                "aload_0,bipush,putfield,aload_0,iconst_0,putfield,aload_0,invokespecial,getstatic,iconst_0,invokevirtual,return"
        );

        assertFail("compiler.err.cant.ref.before.ctor.called",
                """
                value class Test {
                    Test() {
                        m();
                    }
                    void m() {}
                }
                """
        );
        assertFail("compiler.err.strict.field.not.have.been.initialized.before.super",
                """
                value class Test {
                    int i;
                    Test() {
                        super();
                        this.i = i;
                    }
                }
                """
        );
        assertOK(
                """
                class UnrelatedThisLeak {
                    value class V {
                        int f;
                        V() {
                            UnrelatedThisLeak x = UnrelatedThisLeak.this;
                            f = 10;
                            x = UnrelatedThisLeak.this;
                        }
                    }
                }
                """
        );
        assertFail("compiler.err.cant.ref.before.ctor.called",
                """
                value class Test {
                    Test t = null;
                    Runnable r = () -> { System.err.println(t); }; // cant reference `t` from a lambda expression in the prologue
                }
                """
        );
        assertFail("compiler.err.strict.field.not.have.been.initialized.before.super",
                """
                value class Test {
                    int f;
                    {
                        f = 1;
                    }
                }
                """
        );
        assertOK(
                """
                value class V {
                    int x;
                    int y = x + 1; // allowed
                    V() {
                        x = 12;
                        // super();
                    }
                }
                """
        );
        assertFail("compiler.err.cant.ref.before.ctor.called",
                """
                value class V2 {
                    int x;
                    V2() { this(x = 3); } // error
                    V2(int i) { x = 4; }
                }
                """
        );
        assertOK(
                """
                abstract value class AV1 {
                    AV1(int i) {}
                }
                value class V3 extends AV1 {
                    int x;
                    V3() {
                        super(x = 3); // ok
                    }
                }
                """
        );
        assertOK(
                """
                value class V4 {
                    int x;
                    int y = x + 1;
                    V4() {
                        x = 12;
                    }
                    V4(int i) {
                        x = i;
                    }
                }
                """
        );
        assertOK(
                """
                value class V {
                    final int x = "abc".length();
                    { System.out.println(x); }
                }
                """
        );
        assertFail("compiler.err.illegal.forward.ref",
                """
                value class V {
                    { System.out.println(x); }
                    final int x = "abc".length();
                }
                """
        );
        assertOK(
                """
                value class V {
                    int x = "abc".length();
                    int y = x;
                }
                """
        );
        assertOK(
                """
                value class V {
                    int x = "abc".length();
                    { int y = x; }
                }
                """
        );
        assertOK(
                """
                value class V {
                    String s1;
                    { System.out.println(s1); }
                    String s2 = (s1 = "abc");
                }
                """
        );

        source =
            """
            value class V {
                int i = 1;
                int y;
                V() {
                    y = 2;
                }
            }
            """;
        {
            File dir = assertOK(true, source);
            File fileEntry = dir.listFiles()[0];
            var expectedCodeSequence = "putfield i,putfield y";
            var classFile = ClassFile.of().parse(fileEntry.toPath());
            for (var method : classFile.methods()) {
                if (method.methodName().equalsString("<init>")) {
                    var code = method.findAttribute(Attributes.code()).orElseThrow();
                    List<String> mnemonics = new ArrayList<>();
                    for (var coe : code) {
                        if (coe instanceof FieldInstruction inst && inst.opcode() == Opcode.PUTFIELD) {
                            mnemonics.add(inst.opcode().name().toLowerCase(Locale.ROOT) + " " + inst.name());
                        }
                    }
                    var foundCodeSequence = String.join(",", mnemonics);
                    Assert.check(expectedCodeSequence.equals(foundCodeSequence), "found " + foundCodeSequence);
                }
            }
        }

        // check that javac doesn't generate duplicate initializer code
        checkMnemonicsFor(
                """
                value class Test {
                    static class Foo {
                        int x;
                        int getX() { return x; }
                    }
                    Foo data = new Foo();
                    Test() { // we will check that: `data = new Foo();` is generated only once
                        data.getX();
                        super();
                    }
                }
                """,
                "new,dup,invokespecial,astore_1,aload_1,invokevirtual,pop,aload_0,aload_1,putfield,aload_0,invokespecial,return"
        );

        assertFail("compiler.err.invalid.canonical.constructor.in.record",
                """
                record R(int x) {
                    public R {
                        super();
                    }
                }
                """
        );

        assertFail("compiler.err.invalid.canonical.constructor.in.record",
                """
                record R(int x) {
                    public R {
                        this();
                    }
                    public R() {
                        this(1);
                    }
                }
                """
        );

        assertOK(
                """
                record R(int x) {
                    public R(int x) {
                        this.x = x;
                        super();
                    }
                }
                """
        );
    }

    void checkMnemonicsFor(String source, String expectedMnemonics) throws Exception {
        File dir = assertOK(true, source);
        for (final File fileEntry : dir.listFiles()) {
            var classFile = ClassFile.of().parse(fileEntry.toPath());
            if (classFile.thisClass().name().equalsString("Test")) {
                for (var method : classFile.methods()) {
                    if (method.methodName().equalsString("<init>")) {
                        var code = method.findAttribute(Attributes.code()).orElseThrow();
                        List<String> mnemonics = new ArrayList<>();
                        for (var coe : code) {
                            if (coe instanceof Instruction inst) {
                                mnemonics.add(inst.opcode().name().toLowerCase(Locale.ROOT));
                            }
                        }
                        var foundCodeSequence = String.join(",", mnemonics);
                        Assert.check(expectedMnemonics.equals(foundCodeSequence), "found " + foundCodeSequence);
                    }
                }
            }
        }
    }

    @Test
    void testThisCallingConstructor() throws Exception {
        // make sure that this() calling constructors doesn't initialize final fields
        String source =
                """
                value class Test {
                    int i;
                    Test() {
                        this(0);
                    }

                    Test(int i) {
                        this.i = i;
                    }
                }
                """;
        File dir = assertOK(true, source);
        File fileEntry = dir.listFiles()[0];
        String expectedCodeSequenceThisCallingConst = "aload_0,iconst_0,invokespecial,return";
        String expectedCodeSequenceNonThisCallingConst = "aload_0,iload_1,putfield,aload_0,invokespecial,return";
        var classFile = ClassFile.of().parse(fileEntry.toPath());
        for (var method : classFile.methods()) {
            if (method.methodName().equalsString("<init>")) {
                var code = method.findAttribute(Attributes.code()).orElseThrow();
                List<String> mnemonics = new ArrayList<>();
                for (var coe : code) {
                    if (coe instanceof Instruction inst) {
                        mnemonics.add(inst.opcode().name().toLowerCase(Locale.ROOT));
                    }
                }
                var foundCodeSequence = String.join(",", mnemonics);
                var expected = method.methodTypeSymbol().parameterCount() == 0 ?
                        expectedCodeSequenceThisCallingConst : expectedCodeSequenceNonThisCallingConst;
                Assert.check(expected.equals(foundCodeSequence), "found " + foundCodeSequence);
            }
        }
    }

    @Test
    void testSelectors() throws Exception {
        assertOK(
                """
                value class V {
                    void selector() {
                        Class<?> c = int.class;
                    }
                }
                """
        );
        assertFail("compiler.err.expected",
                """
                value class V {
                    void selector() {
                        int i = int.some_selector;
                    }
                }
                """
        );
    }

    @Test
    void testNullAssigment() throws Exception {
        assertOK(
                """
                value final class V {
                    final int x = 10;

                    value final class X {
                        final V v;
                        final V v2;

                        X() {
                            this.v = null;
                            this.v2 = null;
                        }

                        X(V v) {
                            this.v = v;
                            this.v2 = v;
                        }

                        V foo(X x) {
                            x = new X(null);  // OK
                            return x.v;
                        }
                    }
                    V bar(X x) {
                        x = new X(null); // OK
                        return x.v;
                    }

                    class Y {
                        V v;
                        V [] va = { null }; // OK: array initialization
                        V [] va2 = new V[] { null }; // OK: array initialization
                        void ooo(X x) {
                            x = new X(null); // OK
                            v = null; // legal assignment.
                            va[0] = null; // legal.
                            va = new V[] { null }; // legal
                        }
                    }
                }
                """
        );
    }

    @Test
    void testSerializationWarnings() throws Exception {
        String[] previousOptions = getCompileOptions();
        try {
            setCompileOptions(new String[] {"-Xlint:serial", "--enable-preview", "--source",
                    Integer.toString(Runtime.version().feature())});
            assertOK(
                    """
                    import java.io.*;
                    abstract value class AVC implements Serializable {}
                    """);
            assertOKWithWarning("compiler.warn.serializable.value.class.without.write.replace.1",
                    """
                    import java.io.*;
                    value class VC implements Serializable {
                        private static final long serialVersionUID = 0;
                    }
                    """);
            assertOK(
                    """
                    import java.io.*;
                    class C implements Serializable {
                        private static final long serialVersionUID = 0;
                    }
                    """);
            assertOK(
                    """
                    import java.io.*;
                    abstract value class Super implements Serializable {
                        private static final long serialVersionUID = 0;
                        protected Object writeReplace() throws ObjectStreamException {
                            return null;
                        }
                    }
                    value class ValueSerializable extends Super {
                        private static final long serialVersionUID = 1;
                    }
                    """);
            assertOK(
                    """
                    import java.io.*;
                    abstract value class Super implements Serializable {
                        private static final long serialVersionUID = 0;
                        Object writeReplace() throws ObjectStreamException {
                            return null;
                        }
                    }
                    value class ValueSerializable extends Super {
                        private static final long serialVersionUID = 1;
                    }
                    """);
            assertOK(
                    """
                    import java.io.*;
                    abstract value class Super implements Serializable {
                        private static final long serialVersionUID = 0;
                        public Object writeReplace() throws ObjectStreamException {
                            return null;
                        }
                    }
                    value class ValueSerializable extends Super {
                        private static final long serialVersionUID = 1;
                    }
                    """);
            assertOKWithWarning("compiler.warn.serializable.value.class.without.write.replace.1",
                    """
                    import java.io.*;
                    abstract value class Super implements Serializable {
                        private static final long serialVersionUID = 0;
                        private Object writeReplace() throws ObjectStreamException {
                            return null;
                        }
                    }
                    value class ValueSerializable extends Super {
                        private static final long serialVersionUID = 1;
                    }
                    """);
            assertOKWithWarning("compiler.warn.serializable.value.class.without.write.replace.2",
                    """
                    import java.io.*;
                    abstract value class Super implements Serializable {
                        private static final long serialVersionUID = 0;
                        private Object writeReplace() throws ObjectStreamException {
                            return null;
                        }
                    }
                    class Serializable1 extends Super {
                        private static final long serialVersionUID = 1;
                    }
                    class Serializable2 extends Serializable1 {
                        private static final long serialVersionUID = 1;
                    }
                    """);
            assertOK(
                    """
                    import java.io.*;
                    abstract value class Super implements Serializable {
                        private static final long serialVersionUID = 0;
                        Object writeReplace() throws ObjectStreamException {
                            return null;
                        }
                    }
                    class ValueSerializable extends Super {
                        private static final long serialVersionUID = 1;
                    }
                    """);
            assertOK(
                    """
                    import java.io.*;
                    abstract value class Super implements Serializable {
                        private static final long serialVersionUID = 0;
                        public Object writeReplace() throws ObjectStreamException {
                            return null;
                        }
                    }
                    class ValueSerializable extends Super {
                        private static final long serialVersionUID = 1;
                    }
                    """);
            assertOK(
                    """
                    import java.io.*;
                    abstract value class Super implements Serializable {
                        private static final long serialVersionUID = 0;
                        protected Object writeReplace() throws ObjectStreamException {
                            return null;
                        }
                    }
                    class ValueSerializable extends Super {
                        private static final long serialVersionUID = 1;
                    }
                    """);
            assertOK(
                    """
                    import java.io.*;
                    value record ValueRecord() implements Serializable {
                        private static final long serialVersionUID = 1;
                    }
                    """);
            assertOK(
                    // Number is a special case, no warning for identity classes extending it
                    """
                    class NumberSubClass extends Number {
                        private static final long serialVersionUID = 0L;
                        @Override
                        public double doubleValue() { return 0; }
                        @Override
                        public int intValue() { return 0; }
                        @Override
                        public long longValue() { return 0; }
                        @Override
                        public float floatValue() { return 0; }
                    }
                    """
            );
        } finally {
            setCompileOptions(previousOptions);
        }
    }

    @Test
    void testAssertUnsetFieldsSMEntry() throws Exception {
        String[] previousOptions = getCompileOptions();
        try {
            String[] testOptions = {
                    "--enable-preview",
                    "-source", Integer.toString(Runtime.version().feature()),
                    "-XDnoLocalProxyVars",
                    "-XDdebug.stackmap",
            };
            setCompileOptions(testOptions);

            record Data(String src, int[] expectedFrameTypes, String[][] expectedUnsetFields) {}
            for (Data data : List.of(
                    new Data(
                            """
                            value class Test {
                                final int x;
                                final int y;
                                Test(boolean a, boolean b) {
                                    if (a) { // early_larval {x, y}
                                        x = 1;
                                        if (b) { // early_larval {y}
                                            y = 1;
                                        } else { // early_larval {y}
                                            y = 2;
                                        }
                                    } else { // early_larval {x, y}
                                        x = y = 3;
                                    }
                                    super();
                                }
                            }
                            """,
                            // three unset_fields entries, entry type 246, are expected in the stackmap table
                            new int[] {246, 246, 246},
                            // expected fields for each of them:
                            new String[][] { new String[] { "y:I" }, new String[] { "x:I", "y:I" }, new String[] {} }
                    ),
                    new Data(
                            """
                            value class Test {
                                final int x;
                                final int y;
                                Test(int n) {
                                    switch(n) {
                                        case 2:
                                            x = y = 2;
                                            break;
                                        default:
                                            x = y = 100;
                                            break;
                                    }
                                    super();
                                }
                            }
                            """,
                            // here we expect only one
                            new int[] {20, 12, 246},
                            // stating that no field is unset
                            new String[][] { new String[] {} }
                    ),
                    new Data(
                            """
                            value class Test {
                                final int x;
                                final int y;
                                Test(int n) {
                                    if (n % 3 == 0) {
                                        x = n / 3;
                                    } else { // no unset change
                                        x = n + 2;
                                    } // early_larval {y}
                                    y = n >>> 3;
                                    super();
                                    if ((char) n != n) {
                                        n -= 5;
                                    } // no uninitializedThis - automatically cleared unsets
                                    Math.abs(n);
                                }
                            }
                            """,
                            // here we expect only one, none for the post-larval frame
                            new int[] {16, 246, 255},
                            // stating that y is unset when if-else finishes
                            new String[][] { new String[] {"y:I"} }
                    )
            )) {
                File dir = assertOK(true, data.src());
                for (final File fileEntry : dir.listFiles()) {
                    var classFile = ClassFile.of().parse(fileEntry.toPath());
                    for (var method : classFile.methods()) {
                        if (method.methodName().equalsString(ConstantDescs.INIT_NAME)) {
                            var code = method.findAttribute(Attributes.code()).orElseThrow();
                            var stackMapTable = code.findAttribute(Attributes.stackMapTable()).orElseThrow();
                            Assert.check(data.expectedFrameTypes().length == stackMapTable.entries().size(), "unexpected stackmap length");
                            int entryIndex = 0;
                            int expectedUnsetFieldsIndex = 0;
                            for (var entry : stackMapTable.entries()) {
                                Assert.check(data.expectedFrameTypes()[entryIndex++] == entry.frameType(), "expected " + data.expectedFrameTypes()[entryIndex - 1] + " found " + entry.frameType());
                                if (entry.frameType() == 246) {
                                    Assert.check(data.expectedUnsetFields()[expectedUnsetFieldsIndex].length == entry.unsetFields().size());
                                    int index = 0;
                                    for (var nat : entry.unsetFields()) {
                                        String unsetStr = nat.name() + ":" + nat.type();
                                        Assert.check(data.expectedUnsetFields()[expectedUnsetFieldsIndex][index++].equals(unsetStr));
                                    }
                                    expectedUnsetFieldsIndex++;
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            setCompileOptions(previousOptions);
        }
    }

    @Test
    void testLocalProxyVars() throws Exception {
        checkMnemonicsFor(
                    """
                    value class Test {
                        int i;
                        int j;
                        Test() {// javac should generate a proxy local var for `i`
                            i = 1;
                            j = i; // as here `i` is being read during the early construction phase, use the local var instead
                            super();
                        }
                    }
                    """,
                    "iconst_1,istore_1,aload_0,iload_1,putfield,aload_0,iload_1,putfield,aload_0,invokespecial,return");
        checkMnemonicsFor(
                    """
                    value class Test {
                        static String s0;
                        String s;
                        String ss;
                        Test(boolean b) {
                            s0 = null;
                            s = s0; // no local proxy variable for `s0` as it is static
                            ss = s; // but there should be a local proxy for `s`
                            super();
                        }
                    }
                    """,
                    "aconst_null,putstatic,getstatic,astore_2,aload_0,aload_2,putfield,aload_0,aload_2," +
                    "putfield,aload_0,invokespecial,return");
    }
}
