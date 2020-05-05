/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8025524 8031625 8081854 8175200 8186332 8182765
 * @summary Test for constructor name which should be a non-qualified name.
 * @library ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build javadoc.tester.*
 * @run main TestConstructors
 */

import javadoc.tester.JavadocTester;

public class TestConstructors extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestConstructors tester = new TestConstructors();
        tester.runTests();
    }

    @Test
    public void test() {
        javadoc("-d", "out",
                "-sourcepath", testSrc,
                "pkg1");
        checkExit(Exit.OK);

        checkOutput("pkg1/Outer.html", true,
                """
                    <dt>See Also:</dt>
                    <dd><a href="Outer.Inner.html#%3Cinit%3E()"><code>Inner()</code></a>,\s
                    <a href="Outer.Inner.html#%3Cinit%3E(int)"><code>Inner(int)</code></a>,\s
                    <a href="Outer.Inner.NestedInner.html#%3Cinit%3E()"><code>NestedInner()</code></a>,\s
                    <a href="Outer.Inner.NestedInner.html#%3Cinit%3E(int)"><code>NestedInner(int)</code></a>,\s
                    <a href="#%3Cinit%3E()"><code>Outer()</code></a>,\s
                    <a href="#%3Cinit%3E(int)"><code>Outer(int)</code></a></dd>""",
                """
                    Link: <a href="Outer.Inner.html#%3Cinit%3E()"><code>Inner()</code></a>, <a href=\
                    "#%3Cinit%3E(int)"><code>Outer(int)</code></a>, <a href="Outer.Inner.NestedInner\
                    .html#%3Cinit%3E(int)"><code>NestedInner(int)</code></a>""",
                """
                    <a href="#%3Cinit%3E()">Outer</a></span>()""",
                """
                    <section class="detail" id="&lt;init&gt;()">""",
                """
                    <a href="#%3Cinit%3E(int)">Outer</a></span>&#8203;(int&nbsp;i)""",
                """
                    <section class="detail" id="&lt;init&gt;(int)">""");

        checkOutput("pkg1/Outer.Inner.html", true,
                """
                    <a href="#%3Cinit%3E()">Inner</a></span>()""",
                """
                    <section class="detail" id="&lt;init&gt;()">""",
                """
                    <a href="#%3Cinit%3E(int)">Inner</a></span>&#8203;(int&nbsp;i)""",
                """
                    <section class="detail" id="&lt;init&gt;(int)">""");

        checkOutput("pkg1/Outer.Inner.NestedInner.html", true,
                """
                    <a href="#%3Cinit%3E()">NestedInner</a></span>()""",
                """
                    <section class="detail" id="&lt;init&gt;()">""",
                """
                    <a href="#%3Cinit%3E(int)">NestedInner</a></span>&#8203;(int&nbsp;i)""",
                """
                    <section class="detail" id="&lt;init&gt;(int)">""");

        checkOutput("pkg1/Outer.Inner.html", false,
                "Outer.Inner()",
                "Outer.Inner(int)");

        checkOutput("pkg1/Outer.Inner.NestedInner.html", false,
                "Outer.Inner.NestedInner()",
                "Outer.Inner.NestedInner(int)");

        checkOutput("pkg1/Outer.html", false,
                """
                    <a href="Outer.Inner.html#Outer.Inner()"><code>Outer.Inner()</code></a>""",
                """
                    <a href="Outer.Inner.html#Outer.Inner(int)"><code>Outer.Inner(int)</code></a>""",
                """
                    <a href="Outer.Inner.NestedInner.html#Outer.Inner.NestedInner()"><code>Outer.Inner.NestedInner()</code></a>""",
                """
                    <a href="Outer.Inner.NestedInner.html#Outer.Inner.NestedInner(int)"><code>Outer.Inner.NestedInner(int)</code></a>""");
    }
}
