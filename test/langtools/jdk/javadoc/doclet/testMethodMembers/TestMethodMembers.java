/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

/*
 * @test
 * @bug 8304135
 * @library /tools/lib ../../lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.javadoc/jdk.javadoc.internal.tool
 * @build toolbox.ToolBox javadoc.tester.*
 * @run main TestMethodMembers
 */
public class TestMethodMembers extends JavadocTester {

    private final ToolBox tb = new ToolBox();

    // The numbered sections below are from the Java Language Specification,
    // Java SE 20 Edition

    public static void main(String... args) throws Exception {
        new TestMethodMembers().runTests();
    }

    /*
     * Example 8.4.8-1. Inheritance
     */
    @Test
    public void inheritOverrideEquivalent1(Path base) throws Exception {
        var src = base.resolve("src");
        tb.writeJavaFiles(src, """
                interface I1 {
                    int foo();
                }""", """
                interface I2 {
                    int foo();
                }""", """
                abstract class Test implements I1, I2 {}
                """);
        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "--no-platform-links",
                "-package",
                src.resolve("I1.java").toString(),
                src.resolve("I2.java").toString(),
                src.resolve("Test.java").toString());
        checkExit(Exit.OK);
        checkOutput("Test.html", true, """
                <div class="inherited-list">
                <h3 id="methods-inherited-from-class-I1">Methods inherited from interface&nbsp;\
                <a href="I1.html" title="interface in Unnamed Package">I1</a></h3>
                <code><a href="I1.html#foo()">foo</a></code></div>
                <div class="inherited-list">
                <h3 id="methods-inherited-from-class-I2">Methods inherited from interface&nbsp;\
                <a href="I2.html" title="interface in Unnamed Package">I2</a></h3>
                <code><a href="I2.html#foo()">foo</a></code></div>
                </section>""");
    }

    /*
     * Similar to the previous test, but the methods are inherited by an interface.
     * See 9.4.1.3. Inheriting Methods with Override-Equivalent Signatures
     */
    @Test
    public void inheritOverrideEquivalent2(Path base) throws Exception {
        var src = base.resolve("src");
        tb.writeJavaFiles(src, """
                interface I1 {
                    int foo();
                }""", """
                interface I2 {
                    int foo();
                }""", """
                interface I3 extends I1, I2 {}
                """);
        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "--no-platform-links",
                "-package",
                src.resolve("I1.java").toString(),
                src.resolve("I2.java").toString(),
                src.resolve("I3.java").toString());
        checkExit(Exit.OK);
        checkOutput("I3.html", true, """
                <div class="inherited-list">
                <h3 id="methods-inherited-from-class-I1">Methods inherited from interface&nbsp;\
                <a href="I1.html" title="interface in Unnamed Package">I1</a></h3>
                <code><a href="I1.html#foo()">foo</a></code></div>
                <div class="inherited-list">
                <h3 id="methods-inherited-from-class-I2">Methods inherited from interface&nbsp;\
                <a href="I2.html" title="interface in Unnamed Package">I2</a></h3>
                <code><a href="I2.html#foo()">foo</a></code></div>
                </section>""");
    }

    /*
     * A (more interesting) case from javax.lang.model.util.Elements.overrides.
     */
    @Test
    public void overrideByInheriting(Path base) throws Exception {
        var src = base.resolve("src");
        tb.writeJavaFiles(src,
                "class A { public void m() {} }",
                "interface B { void m(); }",
                "class C extends A implements B {}");
        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "--no-platform-links",
                "-package",
                src.resolve("A.java").toString(),
                src.resolve("B.java").toString(),
                src.resolve("C.java").toString());
        checkExit(Exit.OK);
        // C must not inherit B.m: doing so would violate 8.4.8:
        //     No concrete method inherited by C from its direct superclass type
        //     has a signature that is a subsignature of the signature of m as
        //     a member of D.
        checkOutput("C.html", false, """
                <div class="inherited-list">
                <h3 id="methods-inherited-from-class-B">Methods inherited from interface&nbsp;\
                <a href="B.html" title="interface in Unnamed Package">B</a></h3>
                <code><a href="B.html#m()">m</a></code></div>""");
        // TODO: A.m should be mentioned as a non-simple override, I guess
    }

    /*
     * Overriding is complicated by package access sufficiently enough for
     * it NOT to be a transitive binary relation on methods even in a
     * consistently-compiled program.
     *
     * An example which the below two tests are based on is provided by
     * Daniel Smith, one of the JLS authors. In this example, B.m()
     * overrides A.m() and C.m() overrides B.m(); however, C.m()
     * does NOT override A.m().
     */
    @Test
    public void packageAccessOverride1(Path base) throws Exception {
        var src = base.resolve("src");
        tb.writeJavaFiles(src,
                "package p1; public class A { void m() {} }",
                "package p1; public class B extends A { public void m() {} }",
                "package p2; public class C extends p1.B { public void m() {} }");
        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "-package",
                "--no-platform-links",
                "p1",
                "p2");
        checkExit(Exit.OK);
        checkOutput("p1/B.html", true, """
                <section class="detail" id="m()">
                <h3>m</h3>
                <div class="member-signature"><span class="modifiers">public</span>&nbsp;\
                <span class="return-type">void</span>&nbsp;<span class="element-name">m</span>()</div>
                <dl class="notes">
                <dt>Overrides:</dt>
                <dd><code><a href="A.html#m()">m</a></code>&nbsp;in class&nbsp;\
                <code><a href="A.html" title="class in p1">A</a></code></dd>
                </dl>
                </section>""");
        checkOutput("p2/C.html", true, """
                <section class="detail" id="m()">
                <h3>m</h3>
                <div class="member-signature"><span class="modifiers">public</span>&nbsp;\
                <span class="return-type">void</span>&nbsp;<span class="element-name">m</span>()</div>
                <dl class="notes">
                <dt>Overrides:</dt>
                <dd><code><a href="../p1/B.html#m()">m</a></code>&nbsp;in class&nbsp;\
                <code><a href="../p1/B.html" title="class in p1">B</a></code></dd>
                </dl>
                </section>""");
    }

    @Test
    public void packageAccessOverride2(Path base) throws Exception {
        var src = base.resolve("src");
        tb.writeJavaFiles(src,
                "package p1; public class A { void m() {} }",
                "package p1; public class B extends A { }",
                "package p2; public class C extends p1.B { public void m() {} }");
        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "-package",
                "--no-platform-links",
                "p1",
                "p2");
        checkExit(Exit.OK);
        checkOutput("p2/C.html", false, "<dt>Overrides:</dt>");
    }

    /*
     * A curious case from 8.4.8.1 which shows that it is possible for the
     * overridee and the overrider to be declared in the _same class_:
     *
     *     A concrete method in a generic superclass can, under certain
     *     parameterizations, have the same signature as an abstract method
     *     in that class. In this case, the concrete method is inherited and
     *     the abstract method is not (as described above). The inherited
     *     method should then be considered to override its abstract peer
     *     from C.
     */
    @Test
    public void peerOverride(Path base) throws Exception {
        var src = base.resolve("src");
        tb.writeJavaFiles(src, """
                public abstract class A<T> {
                    public abstract void m(String arg);
                    public void m(T arg) { }
                }""", """
                public class B extends A<String> {}
                """);
        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "--no-platform-links",
                src.resolve("A.java").toString(),
                src.resolve("B.java").toString());
        checkExit(Exit.OK);
        checkOutput("B.html", false, "<dt>Overrides:</dt>");
        checkOutput("B.html", true, """
                <div class="inherited-list">
                <h3 id="methods-inherited-from-class-A">Methods inherited from class&nbsp;\
                <a href="A.html" title="class in Unnamed Package">A</a></h3>
                <code><a href="A.html#m(T)">m</a></code></div>""");
    }

    /**
     * Complementary to the above: it shouldn't matter which one of these
     * methods in abstract and which one is concrete.
     */
    @Test
    public void peerOverride2(Path base) throws Exception {
        var src = base.resolve("src");
        tb.writeJavaFiles(src, """
                public abstract class A<T> {
                    public void m(String arg);
                    public abstract void m(T arg) { }
                }""", """
                public class B extends A<String> {}
                """);
        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "--no-platform-links",
                src.resolve("A.java").toString(),
                src.resolve("B.java").toString());
        checkExit(Exit.OK);
        checkOutput("B.html", false, "<dt>Overrides:</dt>");
        checkOutput("B.html", true, """
                <div class="inherited-list">
                <h3 id="methods-inherited-from-class-A">Methods inherited from class&nbsp;\
                <a href="A.html" title="class in Unnamed Package">A</a></h3>
                <code><a href="A.html#m(java.lang.String)">m</a></code></div>""");
    }

    /*
     * Example 8.4.3.1-1. Abstract/Abstract Method Overriding
     */
    @Test
    public void abstractOverridesAbstract(Path base) throws Exception {

        var src = base.resolve("src");
        tb.writeJavaFiles(src, """
                class BufferEmpty extends Exception {
                    BufferEmpty() { super(); }
                    BufferEmpty(String s) { super(s); }
                }""", """
                class BufferError extends Exception {
                    BufferError() { super(); }
                    BufferError(String s) { super(s); }
                }""", """
                interface Buffer {
                    char get() throws BufferEmpty, BufferError;
                }""", """
                abstract class InfiniteBuffer implements Buffer {
                    public abstract char get() throws BufferError;
                }""");
        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "-package",
                "--no-platform-links",
                src.resolve("BufferEmpty.java").toString(),
                src.resolve("BufferError.java").toString(),
                src.resolve("Buffer.java").toString(),
                src.resolve("InfiniteBuffer.java").toString());
        checkExit(Exit.OK);
    }

    /*
     * 8.4.3.1. abstract Methods
     */
    @Test
    public void abstractOverridesConcrete(Path base) throws Exception {
        var src = base.resolve("src");
        tb.writeJavaFiles(src,
                "public class A { public void m() {} }",
                "public abstract class B extends A { public abstract void m(); }");
        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "-package",
                "--no-platform-links",
                src.resolve("A.java").toString(),
                src.resolve("B.java").toString());
        checkExit(Exit.OK);
    }

    /*
     * 9.4.1. Inheritance and Overriding
     */
    @Test
    public void inheritOverridden(Path base) throws Exception {
        var src = base.resolve("src");
        tb.writeJavaFiles(src, """
                interface Top {
                    default String name() { return "unnamed"; }
                }""", """
                interface Left extends Top {
                    default String name() { return getClass().getName(); }
                }""", """
                interface Right extends Top {}
                """, """
                interface Bottom extends Left, Right {}
                """);
        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "-package",
                "--no-platform-links",
                src.resolve("Top.java").toString(),
                src.resolve("Left.java").toString(),
                src.resolve("Right.java").toString(),
                src.resolve("Bottom.java").toString());
        checkExit(Exit.OK);
        checkOutput("Bottom.html", true, """
                <div class="inherited-list">
                <h3 id="methods-inherited-from-class-Left">Methods inherited from interface&nbsp;\
                <a href="Left.html" title="interface in Unnamed Package">Left</a></h3>
                <code><a href="Left.html#name()">name</a></code></div>
                """);
    }

    // TODO: add a test signature-by-signature basis
    // TODO: add a test for an abstract class to inherit from an interface and an abstract superclass
}
