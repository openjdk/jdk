/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8291869
 * @library /tools/lib ../../lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.javadoc/jdk.javadoc.internal.tool
 * @build toolbox.ToolBox javadoc.tester.*
 * @run main TestExceptionTypeMatching
 */

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

import java.nio.file.Path;
import java.nio.file.Paths;

/*
 * The goal of the tests in this suite is two-fold:
 *
 * 1. Provoke javadoc into treating like-named but different elements as
 *    the same element
 * 2. Provoke javadoc into treating differently named but semantically
 *    same elements as different elements
 */
public class TestExceptionTypeMatching extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestExceptionTypeMatching();
        tester.runTests(m -> new Object[]{Paths.get(m.getName())});
    }

    private final ToolBox tb = new ToolBox();

    /*
     * In Child, MyException is c.MyException, whereas in Parent, MyException
     * is p.MyException. Those are different exceptions which happen to
     * share the simple name.
     */
    @Test
    public void testDifferentPackages(Path base) throws Exception {
        var src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package c;

                import p.Parent;

                public class Child extends Parent {

                    /** @throws MyException {@inheritDoc} */
                    @Override
                    public void m() { }
                }
                """, """
                package c;

                public class MyException extends RuntimeException { }

                """, """
                package p;

                public class Parent {

                    /** @throws MyException sometimes */
                    public void m() { }
                }
                """, """
                package p;

                public class MyException extends RuntimeException { }
                """);
        javadoc("-d", base.resolve("out").toString(), "-sourcepath", src.toString(), "c", "p");
        checkExit(Exit.OK);
        checkOutput(Output.OUT, true, """
                Child.java:7: warning: overridden methods do not document exception type c.MyException \
                (module <unnamed module> package c class MyException)
                    /** @throws MyException {@inheritDoc} */
                        ^
                """);
    }

    /*
     * Type parameters declared by methods where one of the methods overrides
     * the other, are matched by position, not by name. In this example, <P>
     * and <R> are semantically the same.
     */
    @Test
    public void testDifferentTypeVariables1(Path base) throws Exception {
        var src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package x;

                public class Parent {

                    /** @throws P sometimes */
                    public <P extends RuntimeException> void m() { }
                }
                """, """
                package x;

                public class Child extends Parent {

                    /** @throws R {@inheritDoc} */
                    @Override
                    public <R extends RuntimeException> void m() { }
                }
                """);
        javadoc("-d", base.resolve("out").toString(), "-sourcepath", src.toString(), "x");
        checkExit(Exit.OK);
        checkOutput("x/Child.html", true, """
                <dl class="notes">
                <dt>Overrides:</dt>
                <dd><code><a href="Parent.html#m()">m</a></code>&nbsp;in class&nbsp;<code>\
                <a href="Parent.html" title="class in x">Parent</a></code></dd>
                <dt>Throws:</dt>
                <dd><code>R</code> - sometimes</dd>
                </dl>
                """);
    }

    /*
     * Type parameters declared by methods where one of the methods overrides
     * the other, are matched by position, not by name.
     *
     * Here the match is criss-cross:
     *
     *   - Child.m's <K> corresponds to Parent.m's <V>
     *   - Child.m's <V> corresponds to Parent.m's <K>
     */
    @Test
    public void testDifferentTypeVariables2(Path base) throws Exception {
        var src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package x;

                public class Parent {

                    /**
                     * @throws K some of the times
                     * @throws V other times
                     */
                    public <K extends RuntimeException, V extends RuntimeException> void m() { }
                }
                """, """
                package x;

                public class Child extends Parent {

                    /**
                     * @throws K {@inheritDoc}
                     * @throws V {@inheritDoc}
                     */
                    @Override
                    public <V extends RuntimeException, K extends RuntimeException> void m() { }
                }
                """);
        javadoc("-d", base.resolve("out").toString(), "-sourcepath", src.toString(), "x");
        checkExit(Exit.OK);
        checkOutput("x/Child.html", true, """
                <dl class="notes">
                <dt>Overrides:</dt>
                <dd><code><a href="Parent.html#m()">m</a></code>&nbsp;in class&nbsp;<code>\
                <a href="Parent.html" title="class in x">Parent</a></code></dd>
                <dt>Throws:</dt>
                <dd><code>K</code> - other times</dd>
                <dd><code>V</code> - some of the times</dd>
                </dl>
                """);
    }

    /*
     * X is unknown to Child.m as it isn't defined by Child.m and
     * type parameters declared by methods are not inherited.
     */
    @Test
    public void testUndefinedTypeParameter(Path base) throws Exception {
        var src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package x;

                public class Parent {

                    /** @throws T sometimes */
                    public <T extends RuntimeException> void m() { }
                }
                """, """
                package x;

                public class Child extends Parent {

                    /** @throws T {@inheritDoc} */
                    @Override
                    public void m() { }
                }
                """);
        // turn off DocLint so that it does not interfere with diagnostics
        // by raising an error for the condition we are testing:
        //
        // Child.java:5: error: reference not found
        //    /** @throws T {@inheritDoc} */
        //        ^
        javadoc("-Xdoclint:none", "-d", base.resolve("out").toString(), "-sourcepath", src.toString(), "x");
        checkExit(Exit.OK);
        checkOutput(Output.OUT, true, """
                Child.java:5: warning: cannot find exception type by name
                    /** @throws T {@inheritDoc} */
                                ^
                """);
    }

    // A related (but separate from this test suite) test. This test is
    // introduced here because it tests for the error condition that is
    // detected by JDK-8291869, which is tested by tests in this test
    // suite.
    // TODO: consider moving this test to a more suitable test suite.
    @Test
    public void testWrongType(Path base) throws Exception {
        var src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package x;

                public class MyClass {

                    /** @throws OtherClass description */
                    public void m() { }
                }
                """, """
                package x;

                public class OtherClass { }
                """);
        // turn off DocLint so that it does not interfere with diagnostics
        // by raising an error for the condition we are testing
        javadoc("-Xdoclint:none", "-d", base.resolve("out").toString(), "-sourcepath", src.toString(), "x");
        checkExit(Exit.OK);
        checkOutput(Output.OUT, true, """
                MyClass.java:5: warning: not an exception type: \
                x.OtherClass (module <unnamed module> package x class OtherClass)
                    /** @throws OtherClass description */
                                ^
                """);
        checkOutput("x/MyClass.html", false, """
                <dl class="notes">
                <dt>Throws:</dt>
                <dd><code><a href="OtherClass.html" title="class in x">OtherClass</a></code> - description</dd>
                </dl>
                """);
    }

    // A related (but separate from this test suite) test. This test is
    // introduced here because it tests for the error condition that is
    // detected by JDK-8291869, which is tested by tests in this test
    // suite.
    // TODO: consider moving this test to a more suitable test suite.
    @Test
    public void testExceptionTypeNotFound(Path base) throws Exception {
        var src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package x;

                public class MyClass {

                    /** @throws un1queEn0ughS0asT0N0tBeF0und description */
                    public void m() { }
                }
                """);
        // turn off DocLint so that it does not interfere with diagnostics
        // by raising an error for the condition we are testing
        javadoc("-Xdoclint:none", "-d", base.resolve("out").toString(), "-sourcepath", src.toString(), "x");
        checkExit(Exit.OK);
        checkOutput(Output.OUT, true, """
                MyClass.java:5: warning: cannot find exception type by name
                    /** @throws un1queEn0ughS0asT0N0tBeF0und description */
                                ^
                """);
    }

    /*
     * In Child, R is a class residing in an unnamed package, whereas
     * in Parent, R is a type variable.
     */
    @Test
    public void testTypeAndTypeParameter(Path base) throws Exception {
        var src = base.resolve("src");
        tb.writeJavaFiles(src, """
                public class Parent {

                    /** @throws R sometimes */
                    public <R extends RuntimeException> void m() { }
                }
                """, """
                public class Child extends Parent {

                    /** @throws R {@inheritDoc} */
                    @Override public void m() { }
                }
                """, """
                public class R extends RuntimeException { }
                """);
        javadoc("-d", base.resolve("out").toString(), src.resolve("Parent.java").toString(),
                src.resolve("Child.java").toString(), src.resolve("R.java").toString());
        checkExit(Exit.OK);
        checkOutput(Output.OUT, true, """
                Child.java:3: warning: overridden methods do not document exception type R \
                (module <unnamed module> package <unnamed package> class R)
                    /** @throws R {@inheritDoc} */
                        ^
                """);
        checkOutput("Child.html", false, """
                <dl class="notes">
                <dt>Overrides:</dt>
                <dd><code><a href="Parent.html#m()">m</a></code>&nbsp;in class&nbsp;<code>\
                <a href="Parent.html" title="class in Unnamed Package">Parent</a></code></dd>
                <dt>Throws:</dt>
                <dd><code><a href="R.html" title="class in Unnamed Package">R</a></code> - sometimes</dd>
                </dl>""");
        checkOutput("Child.html", false, """
                <dl class="notes">
                <dt>Overrides:</dt>
                <dd><code><a href="Parent.html#m()">m</a></code>&nbsp;in class&nbsp;<code>\
                <a href="Parent.html" title="class in Unnamed Package">Parent</a></code></dd>
                <dt>Throws:</dt>
                <dd><code>R</code> - sometimes</dd>
                </dl>""");
    }

    /*
     * There are two different exceptions that share the same simple name:
     *
     *   1. P.MyException (a nested static class in an unnamed package)
     *   2. P.MyException (a public class in the P package)
     *
     * Although unconventional, it is not prohibited for a package name to
     * start with an upper case letter. This test disregards that
     * convention for the setup to work: the package and the
     * class should have the same FQN to be confusing.
     *
     * A permissible but equally unconventional alternative would be to
     * keep the package lower-case but give the class a lower-case name p.
     *
     * This setup works likely because of JLS 6.3. Scope of a Declaration:
     *
     *     The scope of a top level class or interface (7.6) is all class
     *     and interface declarations in the package in which the top
     *     level class or interface is declared.
     */
    @Test
    public void testOuterClassAndPackage(Path base) throws Exception {
        var src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package P;

                public class MyException extends RuntimeException { }
                """, """
                package pkg;

                public class Parent {

                    /** @throws P.MyException sometimes */
                    public void m() { }
                }
                """, """
                public class Child extends pkg.Parent {

                    /** @throws P.MyException {@inheritDoc} */
                    @Override
                    public void m() { }
                }
                """, """
                public class P {
                    public static class MyException extends RuntimeException { }
                }
                """);
        setAutomaticCheckLinks(false); // otherwise the link checker reports that P.MyException is defined twice
                                       // (tracked by 8297085)
        javadoc("-d",
                base.resolve("out").toString(),
                src.resolve("P").resolve("MyException.java").toString(),
                src.resolve("pkg").resolve("Parent.java").toString(),
                src.resolve("Child.java").toString(),
                src.resolve("P.java").toString());
        checkExit(Exit.OK);
        checkOutput(Output.OUT, true, """
                Child.java:3: warning: overridden methods do not document exception type P.MyException \
                (module <unnamed module> package <unnamed package> class P class MyException)
                    /** @throws P.MyException {@inheritDoc} */
                        ^
                """);
        checkOutput("Child.html", false, """
                <dl class="notes">
                <dt>Overrides:</dt>
                <dd><code><a href="pkg/Parent.html#m()">m</a></code>&nbsp;in class&nbsp;<code>\
                <a href="pkg/Parent.html" title="class in pkg">Parent</a></code></dd>
                <dt>Throws:</dt>
                <dd><code><a href="P.MyException.html" title="class in Unnamed Package">P.MyException</a></code> - sometimes</dd>
                </dl>""");
        checkOutput("Child.html", false, "P/MyException.html");
    }

    /*
     * It's unclear how to match type parameters that aren't declared by
     * a method. For example, consider that for B to be a subtype of A,
     * it is not necessary for A and B to have the same number or
     * types of type parameters.
     *
     * For that reason, exception documentation inheritance involving
     * such parameters is currently unsupported. This test simply
     * checks that we produce helpful warnings.
     */
    @Test
    public void testGenericTypes(Path base) throws Exception {
        var src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package x;

                public class Parent<T extends RuntimeException> {

                    /** @throws T description */
                    public void m() { }
                }
                """, """
                package x;

                public class Child1<T extends RuntimeException> extends Parent<T> {

                    /** @throws T {@inheritDoc} */
                    @Override public void m() { }
                }
                """, """
                package x;

                public class Child2<T extends IllegalArgumentException> extends Parent<T> {

                    /** @throws T {@inheritDoc} */
                    @Override public void m() { }
                }
                """, """
                package x;

                public class Child3 extends Parent<NullPointerException> {

                    /** @throws NullPointerException {@inheritDoc} */
                    @Override public void m() { }
                }
                """);
        javadoc("-d", base.resolve("out").toString(), "-sourcepath", src.toString(), "x");
        checkExit(Exit.OK);
        checkOutput(Output.OUT, true, """
                Child1.java:5: warning: @inheritDoc is not supported for exception-type type parameters \
                that are not declared by a method; document such exception types directly
                    /** @throws T {@inheritDoc} */
                                ^
                """);
        checkOutput(Output.OUT, true, """
                Child2.java:5: warning: @inheritDoc is not supported for exception-type type parameters \
                that are not declared by a method; document such exception types directly
                    /** @throws T {@inheritDoc} */
                                ^
                """);
        checkOutput(Output.OUT, true, """
                Child3.java:5: warning: overridden methods do not document exception type java.lang.NullPointerException \
                (module java.base package java.lang class NullPointerException)
                    /** @throws NullPointerException {@inheritDoc} */
                        ^
                    """);
    }
}
