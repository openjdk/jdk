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
 * @bug      8298405
 * @summary  Markdown support in the standard doclet
 * @library  /tools/lib ../../lib
 * @modules  jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @run main TestMarkdownLinks
 */

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

import java.nio.file.Path;
import java.util.List;

public class TestMarkdownLinks extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestMarkdownLinks();
        tester.runTests();
    }

    ToolBox tb = new ToolBox();

    @Test
    public void testSimpleLink(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    public class C {
                        /// Method m1.
                        /// This is different from {@link #m2()}.
                        public void m1() { }
                        /// Method m2.
                        /// This is different from {@link #m1()}.
                        public void m2() { }
                    }
                    """);
        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--source-path", src.toString(),
                "p");

        checkOutput("p/C.html", true,
                """
                    Method m1.
                    This is different from <a href="#m2()"><code>m2()</code></a>.""");

    }

    @Test
    public void testSimpleRefLink(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    public class C {
                        /// Method m1.
                        /// This is different from [#m2()].
                        public void m1() { }
                        /// Method m2.
                        /// This is different from [#m1()].
                        public void m2() { }
                    }
                    """);
        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--source-path", src.toString(),
                "p");

        checkOutput("p/C.html", true,
                """
                    Method m1.
                    This is different from <a href="#m2()"><code>m2()</code></a>.""");

    }

    @Test
    public void testLinkWithDescription(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    public class C {
                        /// Method m1.
                        /// This is different from {@linkplain #m2() _Markdown_ m2}.
                        public void m1() { }
                        /// Method m2.
                        /// This is different from {@linkplain #m1() _Markdown_ m1}.
                        public void m2() { }
                    }
                    """);
        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--source-path", src.toString(),
                "p");

        checkOutput("p/C.html", true,
                """
                    Method m1.
                    This is different from <a href="#m2()"><em>Markdown</em> m2""");

    }

    @Test
    public void testRefLinkWithDescription(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    public class C {
                        /// Method m1.
                        /// This is different from [_Markdown_ m2][#m2()].
                        public void m1() { }
                        /// Method m2.
                        /// This is different from [_Markdown_ m1][#m1()]}.
                        public void m2() { }
                    }
                    """);
        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--source-path", src.toString(),
                "p");

        checkOutput("p/C.html", true,
                """
                    Method m1.
                    This is different from <a href="#m2()"><em>Markdown</em> m2""");

    }

    @Test
    public void testLinkToHeadingAnchor(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    public class C {
                        /// Method m1.
                        /// # Lorem Ipsum.
                        ///
                        /// Lorem ipsum [reference in same comment][##lorem-ipsum--heading].
                        public void m1() { }
                        /// Method m2.
                        /// Lorem ipsum [reference in another comment][##lorem-ipsum--heading].
                        public void m2() { }
                    }
                    """,
                """
                    package p;
                    /// Lorem ipsum [reference in another class][C##lorem-ipsum--heading].
                    public class D {
                    }""");

        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--source-path", src.toString(),
                "p");

    }

    @Test
    public void testLinkToUserAnchor(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    public class C {
                        /// Method m1.
                        /// <span id="lorem-ipsum">Lorem Ipsum</span>.
                        ///
                        /// Lorem ipsum [reference in same comment][##lorem-ipsum].
                        public void m1() { }
                        /// Method m2.
                        /// Lorem ipsum [reference in another comment][##lorem-ipsum].
                        public void m2() { }
                    }
                    """,
                """
                    package p;
                    /// Lorem ipsum [reference in another class][C##lorem-ipsum].
                    public class D {
                    }""");

        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--source-path", src.toString(),
                "p");

    }

    @Test
    public void testLinkElementKinds(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                        package p;
                        /// First sentence.
                        ///
                        /// * module [java.base/]
                        /// * package [java.util]
                        /// * class [String] or interface [Runnable]
                        /// * a field [String#CASE_INSENSITIVE_ORDER]
                        /// * a constructor [String#String()]
                        /// * a method [String#chars()]
                        public class C { }""");
        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--source-path", src.toString(),
                "p");

        // in the following carefully avoid checking the URL host, which is of less importance and may vary over time;
        // the interesting part is the tail of the path after the host
        new OutputChecker("p/C.html")
                .setExpectOrdered(true)
                .check("module <a href=\"https://",
                        "/api/java.base/module-summary.html\" class=\"external-link\"><code>java.base</code></a>",

                        "package <a href=\"https://",
                        "/api/java.base/java/util/package-summary.html\" class=\"external-link\"><code>java.util</code></a>",

                        "class <a href=\"https://",
                        "/api/java.base/java/lang/String.html\" title=\"class or interface in java.lang\" class=\"external-link\"><code>String</code></a>",

                        "interface <a href=\"https://",
                        "/api/java.base/java/lang/Runnable.html\" title=\"class or interface in java.lang\" class=\"external-link\"><code>Runnable</code></a>",

                        "a field <a href=\"https://",
                        "/api/java.base/java/lang/String.html#CASE_INSENSITIVE_ORDER\" title=\"class or interface in java.lang\" class=\"external-link\"><code>String.CASE_INSENSITIVE_ORDER</code></a>",

                        "a constructor <a href=\"https://",
                        "/api/java.base/java/lang/String.html#%3Cinit%3E()\" title=\"class or interface in java.lang\" class=\"external-link\"><code>String()</code></a></li>",

                        "a method <a href=\"https://",
                        "/api/java.base/java/lang/String.html#chars()\" title=\"class or interface in java.lang\" class=\"external-link\"><code>String.chars()</code></a>");
    }

    /// Test the ability to include array elements in method signatures for
    /// automatic links to program elements.
    @Test
    public void testReferenceWithArrays(Path base) throws Exception {
        Path src = base.resolve("src");

        // in the following,
        //
        // * Link 0 is a simple control for a shortcut reference link (without any arrays)
        // * Link 1a and Link 2a are negative controls, in that they are _not_ valid links
        //   because of the unescaped [] pair
        // * Link 1b and 2b are the positive tests, showing that the square brackets
        //   need to be escaped in the source code, and that they are not escaped in
        //   the generated HTML

        tb.writeJavaFiles(src,
                """
                    package p;
                    /// First sentence.
                    /// * Link 0 to [util.Arrays]
                    /// * Link 1a to [Arrays-equals][util.Arrays#equals(Object[],Object[])]
                    /// * Link 1b to [Arrays-equals][util.Arrays#equals(Object\\[\\],Object\\[\\])]
                    /// * Link 2a to [util.Arrays#equals(Object[],Object[])]
                    /// * Link 2b to [util.Arrays#equals(Object\\[\\],Object\\[\\])]
                    public class C { }""",
                // simulate java.util.Arrays.equals, to avoid links to platform references
                """
                    package util;
                    public class Arrays {
                        public boolean equals(Object[] a, Object[] a2);
                    }""");

        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--source-path", src.toString(),
                "p", "util");

        checkExit(Exit.OK);
        checkOutput("p/C.html", true,
                """
                    Link 0 to <a href="../util/Arrays.html" title="class in util"><code>Arrays</code></a>""",
                """
                    Link 1a to [Arrays-equals][util.Arrays#equals(Object[],Object[])]""",
                """
                    Link 1b to <a href="../util/Arrays.html#equals(java.lang.Object%5B%5D,\
                    java.lang.Object%5B%5D)">Arrays-equals</a>""",
                """
                    Link 2a to [util.Arrays#equals(Object[],Object[])]""",
                """
                    Link 2b to <a href="../util/Arrays.html#equals(java.lang.Object%5B%5D,\
                    java.lang.Object%5B%5D)"><code>Arrays.equals(Object[],Object[])</code></a>"""
        );
    }

    @Test
    public void testSee(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    /// First sentence.
                    /// @see "A reference"
                    /// @see <a href="http://www.example.com">Example</a>
                    /// @see D a _Markdown_ description
                    public class C { }
                    """,
                """
                    package p;
                    public class D { }""");
        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--source-path", src.toString(),
                "p");

        checkOutput("p/C.html", true,
                """
                    <dt>See Also:</dt>
                    <dd>
                    <ul class="tag-list">
                    <li>"A reference"</li>
                    <li><a href="http://www.example.com">Example</a></li>
                    <li><a href="D.html" title="class in p">a <em>Markdown</em> description</a></li>
                    </ul>
                    </dd>""");

    }
}