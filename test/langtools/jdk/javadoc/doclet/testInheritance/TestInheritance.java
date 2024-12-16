/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8302324
 * @summary  Inheritance tree does not show correct type parameters/arguments
 * @library  /tools/lib ../../lib
 * @modules  jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @run main TestInheritance
 */

import java.nio.file.Path;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestInheritance extends JavadocTester {

    public static void main(String... args) throws Exception {
        var test = new TestInheritance();
        test.runTests();
    }

    @Test
    public void testInheritanceGeneric(Path base) throws Exception {
        Path src = base.resolve("src");
        new ToolBox().writeJavaFiles(src, """
                    package pkg;
                    /**
                     * Base class
                     * @param <M> param M
                     * @param <N> param N
                     */
                    public class A<M, N> { private A() { } }
                    """,
                """
                    package pkg;
                    /**
                     * First subclass
                     * @param <O> param O
                     * @param <P> param P
                     */
                    public class B<O, P> extends A<O, P> { private B() { } }
                    """,
                """
                    package pkg;
                    /**
                     * Second subclass
                     * @param <Q> param Q
                     */
                    public class C<Q> extends B<String, Q> { private C() { } }
                    """,
                """
                    package pkg;
                    /**
                     * Second subclass
                     * @param <R> param R
                     * @param <S> param S
                     */
                    public class D<R, S> extends B<S, B> { private D() { } }
                    """);
        javadoc("-d", base.resolve("docs").toString(),
                "-sourcepath", src.toString(),
                "--no-platform-links",
                "pkg");
        checkExit(Exit.OK);
        checkOrder("pkg/A.html", """
                     <div class="inheritance" title="Inheritance Tree">java.lang.Object
                     <div class="inheritance">pkg.A&lt;M,<wbr>N&gt;</div>""");
        checkOrder("pkg/B.html", """
                     <div class="inheritance" title="Inheritance Tree">java.lang.Object
                     <div class="inheritance"><a href="A.html" title="class in pkg">pkg.A</a>&lt;O,<wbr>P&gt;
                     <div class="inheritance">pkg.B&lt;O,<wbr>P&gt;</div>""");
        checkOrder("pkg/C.html", """
                     <div class="inheritance" title="Inheritance Tree">java.lang.Object
                     <div class="inheritance"><a href="A.html" title="class in pkg">pkg.A</a>&lt;java.lang.String, Q&gt;
                     <div class="inheritance"><a href="B.html" title="class in pkg">pkg.B</a>&lt;java.lang.String, Q&gt;
                     <div class="inheritance">pkg.C&lt;Q&gt;</div>""");
        checkOrder("pkg/D.html", """
                     <div class="inheritance" title="Inheritance Tree">java.lang.Object
                     <div class="inheritance"><a href="A.html" title="class in pkg">pkg.A</a>&lt;S,<wbr><a href="B.html" title="class in pkg">B</a>&gt;
                     <div class="inheritance"><a href="B.html" title="class in pkg">pkg.B</a>&lt;S,<wbr><a href="B.html" title="class in pkg">B</a>&gt;
                     <div class="inheritance">pkg.D&lt;R,<wbr>S&gt;</div>""");
    }
}
