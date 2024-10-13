/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6934301
 * @library /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build toolbox.ToolBox javadoc.tester.*
 * @run main TestDirectedInheritance
 */

import java.nio.file.Path;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestDirectedInheritance extends JavadocTester {

    public static void main(String... args) throws Exception {
        new TestDirectedInheritance().runTests(m -> new Object[]{Path.of(m.getName())});
    }

    private final ToolBox tb = new ToolBox();

    /*
     * Javadoc won't crash if an unknown tag uses {@inheritDoc}.
     */
    @Test
    public void testUnknownTag(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package x;
                public interface I1 {
                    /** @foo bar */
                    void m();
                }
                """, """
                package x;
                public interface E1 extends I1 {
                    /** @foo {@inheritDoc} */
                    void m();
                }
                """, """
                package x;
                public interface E2 extends I1 {
                    /** @foo {@inheritDoc I1} */
                    void m();
                }
                """);
        // DocLint should neither prevent nor cause a crash. Explicitly check that
        // there's no crash with DocLint on and off, but don't check that the exit
        // code is OK, it likely isn't (after all, there's an unknown tag).
        setAutomaticCheckNoStacktrace(true);
        { // DocLint is explicit
            int i = 0;
            for (var check : new String[]{":all", ":none", ""}) {
                var outputDir = "out-DocLint-" + i++; // use separate output directories
                javadoc("-Xdoclint" + check,
                        "-d", base.resolve(outputDir).toString(),
                        "--source-path", src.toString(),
                        "x");
            }
        }
        // DocLint is default
        javadoc("-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "x");
    }

    /*
     * An interface method inherits documentation from that interface's rightmost
     * superinterface in the `extends` clause.
     */
    @Test
    public void testInterfaceInheritsFromSuperinterface(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package x;
                public interface I1 {
                    /**
                     * I1: main description
                     *
                     * @param <A> I1: first type parameter
                     * @param <B> I1: second type parameter
                     *
                     * @param bObj I1: parameter
                     * @return I1: return
                     *
                     * @throws B I1: first description of an exception
                     * @throws B I1: second description of an exception
                     */
                    <A, B extends RuntimeException> int m(A bObj);
                }
                """, """
                package x;
                public interface I2 {
                    /**
                     * I2: main description
                     *
                     * @param <C> I2: first type parameter
                     * @param <D> I2: second type parameter
                     *
                     * @param cObj I2: parameter
                     * @return I2: return
                     *
                     * @throws D I2: first description of an exception
                     * @throws D I2: second description of an exception
                     */
                    <C, D extends RuntimeException> int m(C cObj);
                }
                """, """
                package x;
                public interface E1 extends I1, I2 {
                    /**
                     * {@inheritDoc I2}
                     *
                     * @param <E> {@inheritDoc I2}
                     * @param <F> {@inheritDoc I2}
                     *
                     * @param eObj {@inheritDoc I2}
                     * @return {@inheritDoc I2}
                     *
                     * @throws F {@inheritDoc I2}
                     */
                    <E, F extends RuntimeException> int m(E eObj);
                }
                """);
        javadoc("-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "x");
        checkExit(Exit.OK);
        new OutputChecker("x/E1.html").check("""
                <div class="block">I2: main description</div>
                """, """
                <dt>Type Parameters:</dt>
                <dd><span id="m(E)-type-param-E"><code>E</code> - I2: first type parameter</span></dd>
                <dd><span id="m(E)-type-param-F"><code>F</code> - I2: second type parameter</span></dd>
                <dt>Parameters:</dt>
                <dd><code>eObj</code> - I2: parameter</dd>
                <dt>Returns:</dt>
                <dd>I2: return</dd>
                <dt>Throws:</dt>
                <dd><code>F</code> - I2: first description of an exception</dd>
                <dd><code>F</code> - I2: second description of an exception</dd>
                </dl>""");
        new OutputChecker(Output.OUT).setExpectFound(false)
                .check("warning: not a direct supertype"); // no unexpected warnings
    }

    /*
     * An interface method both provides and inherits the main description and
     * the exception documentation from all its superinterfaces.
     *
     * Note: the same does not work for @param and @return as these are one-to-one.
     */
    @Test
    public void testInterfaceInheritsFromAllSuperinterfaces(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package x;
                public interface I1 {
                    /**
                     * I1: main description
                     *
                     * @throws B I1: first description of an exception
                     * @throws B I1: second description of an exception
                     */
                    <A, B extends RuntimeException> int m(A bObj);
                }
                """, """
                package x;
                public interface I2 {
                    /**
                     * I2: main description
                     *
                     * @throws D I2: first description of an exception
                     * @throws D I2: second description of an exception
                     */
                    <C, D extends RuntimeException> int m(C cObj);
                }
                """, """
                package x;
                public interface E1 extends I1, I2 {
                    /**
                     * E1: main description
                     * {@inheritDoc I2}
                     * {@inheritDoc I1}
                     *
                     * @throws F E1: description of an exception
                     * @throws F {@inheritDoc I2}
                     * @throws F {@inheritDoc I1}
                     */
                    <E, F extends RuntimeException> int m(E eObj);
                }
                """);
        javadoc("-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "x");
        checkExit(Exit.OK);
        new OutputChecker("x/E1.html").check("""
                <div class="block">E1: main description
                 I2: main description
                 I1: main description</div>""", """
                <dt>Throws:</dt>
                <dd><code>F</code> - E1: description of an exception</dd>
                <dd><code>F</code> - I2: first description of an exception</dd>
                <dd><code>F</code> - I2: second description of an exception</dd>
                <dd><code>F</code> - I1: first description of an exception</dd>
                <dd><code>F</code> - I1: second description of an exception</dd>
                </dl>""");
        new OutputChecker(Output.OUT).setExpectFound(false)
                .check("warning: not a direct supertype"); // no unexpected warnings
    }

    /*
     * C1.m directedly inherits documentation from B1, which inherits A.m
     * along with its documentation.
     *
     * C2.m directedly inherits documentation from B2, whose m overrides A.m
     * and implicitly inherits its documentation.
     */
    @Test
    public void testRecursiveInheritance1(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package x;
                public class A {
                    /** A.m() */
                    public void m() { }
                }
                """, """
                package x;
                public class B1 extends A { }
                """, """
                package x;
                public class C1 extends B1 {
                    /** {@inheritDoc B1} */
                    @Override public void m() { }
                }
                """, """
                package x;
                public class B2 extends A {
                    @Override public void m() { }
                }
                """, """
                package x;
                public class C2 extends B2 {
                    /** {@inheritDoc B2} */
                    @Override public void m() { }
                }
                """);
        javadoc("-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "x");
        checkExit(Exit.OK);
        var m = """
                <section class="detail" id="m()">
                <h3>m</h3>
                <div class="horizontal-scroll">
                <div class="member-signature"><span class="modifiers">\
                public</span>&nbsp;<span class="return-type">void</span>\
                &nbsp;<span class="element-name">m</span>()</div>
                <div class="block">A.m()</div>""";
        new OutputChecker("x/C1.html").check(m);
        new OutputChecker("x/C2.html").check(m);
        new OutputChecker(Output.OUT).setExpectFound(false)
                .check("warning: not a direct supertype"); // no unexpected warnings
    }

    /*
     * C1.m directedly inherits documentation from B1, which in turn inherits
     * it undirectedly from A.
     *
     * C2.m directedly inherits documentation from B2, which in turn inherits
     * in directedly from A.
     */
    @Test
    public void testRecursiveInheritance2(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package x;
                public class A {
                    /** A.m() */
                    public void m() { }
                }
                """, """
                package x;
                public class B1 extends A {
                    /** {@inheritDoc} */
                    @Override public void m() { }
                }
                """, """
                package x;
                public class C1 extends B1 {
                    /** {@inheritDoc B1} */
                    @Override
                    public void m() { }
                }
                """, """
                package x;
                public class B2 extends A {
                    /** {@inheritDoc A} */
                    @Override public void m() { }
                }
                """, """
                package x;
                public class C2 extends B2 {
                    /** {@inheritDoc B2} */
                    @Override public void m() { }
                }
                """);
        javadoc("-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "x");
        checkExit(Exit.OK);
        var m = """
                <section class="detail" id="m()">
                <h3>m</h3>
                <div class="horizontal-scroll">
                <div class="member-signature"><span class="modifiers">\
                public</span>&nbsp;<span class="return-type">void</span>\
                &nbsp;<span class="element-name">m</span>()</div>
                <div class="block">A.m()</div>""";
        new OutputChecker("x/C1.html").check(m);
        new OutputChecker("x/C2.html").check(m);
        new OutputChecker(Output.OUT).setExpectFound(false)
                .check("warning: not a direct supertype"); // no unexpected warnings
    }

    /*
     * Currently, there's no special error for a documentation comment that inherits
     * from itself. Instead, such a comment is seen as a general case of a comment
     * that inherits from a documentation of a method which that comment's method
     * does not override (JLS says that a method does not override itself).
     *
     * TODO: DocLint might not always be able to find another type, but it
     *  should always be capable of detecting the same type; we could
     *  consider implementing this check _also_ in DocLint
     */
    @Test
    public void testSelfInheritance(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package x;
                public class A {
                    /** {@inheritDoc A} */
                    public Integer minus(Integer i) { return -i; }
                }
                """, """
                package x;
                public class B {
                    /** @param i {@inheritDoc B} */
                    public Integer minus(Integer i) { return -i; }
                }
                """, """
                package x;
                public class C {
                    /** @param <T> {@inheritDoc C} */
                    public <T> Integer minus(Integer i) { return -i; }
                }
                """, """
                package x;
                public class D {
                    /** @return {@inheritDoc D} */
                    public Integer minus(Integer i) { return -i; }
                }
                """, """
                package x;
                public class E {
                    /** @throws NullPointerException {@inheritDoc E} */
                    public Integer minus(Integer i) { return -i; }
                }
                """, """
                package x;
                public class F {
                    /** @throws T NullPointerException {@inheritDoc F} */
                    public <T extends RuntimeException> Integer minus(Integer i) { return -i; }
                }
                """);
        javadoc("-Xdoclint:none", // turn off DocLint
                "-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "x");
        checkExit(Exit.ERROR);
        new OutputChecker(Output.OUT).setExpectOrdered(false).check("""
                A.java:3: error: cannot find the overridden method
                    /** {@inheritDoc A} */
                        ^""", """
                B.java:3: error: cannot find the overridden method
                    /** @param i {@inheritDoc B} */
                                 ^""", """
                C.java:3: error: cannot find the overridden method
                    /** @param <T> {@inheritDoc C} */
                                   ^""", """
                D.java:3: error: cannot find the overridden method
                    /** @return {@inheritDoc D} */
                                ^""", """
                E.java:3: error: cannot find the overridden method
                    /** @throws NullPointerException {@inheritDoc E} */
                                                     ^""", """
                F.java:3: error: cannot find the overridden method
                    /** @throws T NullPointerException {@inheritDoc F} */
                                                       ^""");
        new OutputChecker(Output.OUT).setExpectFound(false)
                .check("warning: not a direct supertype"); // no unexpected warnings
    }

    /*
     * While E1.m and I.m have override-equivalent signatures, E1 does not extend I.
     * While E2 extends I, E2.m1 does not override I.m. In either case, there's no
     * (overridden) method to inherit documentation from.
     */
    @Test
    public void testInvalidSupertype1(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package x;
                public interface I {
                    /**
                     * I: main description
                     *
                     * @param <A> I: first type parameter
                     * @param <B> I: second type parameter
                     *
                     * @param bObj I: parameter
                     * @return I: return
                     *
                     * @throws B I: first description of an exception
                     * @throws B I: second description of an exception
                     */
                    <A, B extends RuntimeException> int m(A bObj);
                }
                """, """
                package x;
                public interface E1 {
                    /**
                     * {@inheritDoc I}
                     *
                     * @param <C> {@inheritDoc I}
                     * @param <D> {@inheritDoc I}
                     *
                     * @param cObj {@inheritDoc I}
                     * @return {@inheritDoc I}
                     *
                     * @throws D {@inheritDoc I}
                     */
                    <C, D extends RuntimeException> int m(C cObj);
                }
                """, """
                package x;
                public interface E2 extends I {
                    /**
                     * {@inheritDoc I}
                     *
                     * @param <E> {@inheritDoc I}
                     * @param <F> {@inheritDoc I}
                     *
                     * @param eObj {@inheritDoc I}
                     * @return {@inheritDoc I}
                     *
                     * @throws F {@inheritDoc I}
                     */
                    <E, F extends RuntimeException> int m1(E eObj);
                }
                """);
        javadoc("-Xdoclint:none", // turn off DocLint
                "-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "x");
        checkExit(Exit.ERROR);
        new OutputChecker(Output.OUT).setExpectOrdered(false).check("""
                E1.java:4: error: cannot find the overridden method
                     * {@inheritDoc I}
                       ^""", """
                E1.java:6: error: cannot find the overridden method
                     * @param <C> {@inheritDoc I}
                                  ^""", """
                E1.java:7: error: cannot find the overridden method
                     * @param <D> {@inheritDoc I}
                                  ^""", """
                E1.java:9: error: cannot find the overridden method
                     * @param cObj {@inheritDoc I}
                                   ^""", """
                E1.java:10: error: cannot find the overridden method
                     * @return {@inheritDoc I}
                               ^""", """
                E1.java:12: error: cannot find the overridden method
                     * @throws D {@inheritDoc I}
                                 ^""");
        new OutputChecker(Output.OUT).setExpectOrdered(false).check("""
                E2.java:4: error: cannot find the overridden method
                     * {@inheritDoc I}
                       ^""", """
                E2.java:6: error: cannot find the overridden method
                     * @param <E> {@inheritDoc I}
                                  ^""", """
                E2.java:7: error: cannot find the overridden method
                     * @param <F> {@inheritDoc I}
                                  ^""", """
                E2.java:9: error: cannot find the overridden method
                     * @param eObj {@inheritDoc I}
                                   ^""", """
                E2.java:10: error: cannot find the overridden method
                     * @return {@inheritDoc I}
                               ^""", """
                E2.java:12: error: cannot find the overridden method
                     * @throws F {@inheritDoc I}
                                 ^""");
        new OutputChecker(Output.OUT).setExpectFound(false)
                .check("warning: not a direct supertype"); // no unexpected warnings
    }

    /*
     * Cannot inherit documentation from a subtype.
     */
    @Test
    public void testInvalidSupertype2(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package x;
                public interface E extends I {
                    /**
                     * E: main description
                     *
                     * @param <A> E: first type parameter
                     * @param <B> E: second type parameter
                     *
                     * @param aObj E: parameter
                     * @return E: return
                     *
                     * @throws B E: first description of an exception
                     * @throws B E: second description of an exception
                     */
                    <A, B extends RuntimeException> int m(A aObj);
                }
                """, """
                package x;
                public interface I {
                    /**
                     * {@inheritDoc E}
                     *
                     * @param <C> {@inheritDoc E}
                     * @param <D> {@inheritDoc E}
                     *
                     * @param cObj {@inheritDoc E}
                     * @return {@inheritDoc E}
                     *
                     * @throws D {@inheritDoc E}
                     */
                    <C, D extends RuntimeException> int m(C cObj);
                }
                """);
        javadoc("-Xdoclint:none", // turn off DocLint
                "-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "x");
        checkExit(Exit.ERROR);
        new OutputChecker(Output.OUT).setExpectOrdered(false).check("""
                I.java:4: error: cannot find the overridden method
                     * {@inheritDoc E}
                       ^""", """
                I.java:6: error: cannot find the overridden method
                     * @param <C> {@inheritDoc E}
                                  ^""", """
                I.java:7: error: cannot find the overridden method
                     * @param <D> {@inheritDoc E}
                                  ^""", """
                I.java:9: error: cannot find the overridden method
                     * @param cObj {@inheritDoc E}
                                   ^""", """
                I.java:10: error: cannot find the overridden method
                     * @return {@inheritDoc E}
                               ^""", """
                I.java:12: error: cannot find the overridden method
                     * @throws D {@inheritDoc E}
                                 ^""");
    }

    @Test
    public void testUnknownSupertype(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package x;
                public class A {
                    /** {@inheritDoc MySuperType} */
                    public Integer m(Integer i) { return -i; }
                }
                """, """
                package x;
                public class B {
                    /** @param i {@inheritDoc MySuperType} */
                    public Integer minus(Integer i) { return -i; }
                }
                """, """
                package x;
                public class C {
                    /** @param <T> {@inheritDoc MySuperType} */
                    public <T> Integer minus(Integer i) { return -i; }
                }
                """, """
                package x;
                public class D {
                    /** @return {@inheritDoc MySuperType} */
                    public Integer minus(Integer i) { return -i; }
                }
                """, """
                package x;
                public class E {
                    /** @throws NullPointerException {@inheritDoc MySuperType} */
                    public Integer minus(Integer i) { return -i; }
                }
                """, """
                package x;
                public class F {
                    /** @throws T NullPointerException {@inheritDoc MySuperType} */
                    public <T extends RuntimeException> Integer minus(Integer i) { return -i; }
                }
                """);
        javadoc("-Xdoclint:none", // turn off DocLint
                "-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "x");
        checkExit(Exit.ERROR);
        new OutputChecker(Output.OUT).setExpectOrdered(false).check("""
                A.java:3: error: cannot find the overridden method
                    /** {@inheritDoc MySuperType} */
                        ^""", """
                B.java:3: error: cannot find the overridden method
                    /** @param i {@inheritDoc MySuperType} */
                                 ^""", """
                C.java:3: error: cannot find the overridden method
                    /** @param <T> {@inheritDoc MySuperType} */
                                   ^""", """
                D.java:3: error: cannot find the overridden method
                    /** @return {@inheritDoc MySuperType} */
                                ^""", """
                E.java:3: error: cannot find the overridden method
                    /** @throws NullPointerException {@inheritDoc MySuperType} */
                                                     ^""", """
                F.java:3: error: cannot find the overridden method
                    /** @throws T NullPointerException {@inheritDoc MySuperType} */
                                                       ^""");
        new OutputChecker(Output.OUT).setExpectFound(false)
                .check("warning: not a direct supertype"); // no unexpected warnings
    }
}
