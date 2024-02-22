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
 * @build    toolbox.ToolBox javadoc.tester.* DivTaglet SpanTaglet
 * @run main TestMarkdown
 */

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

import java.nio.file.Path;
import java.util.List;

public class TestMarkdown extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestMarkdown();
        tester.runTests();
    }

    ToolBox tb = new ToolBox();

    @Test
    public void testMinimal(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    /// Hello, _Markdown_ world!
                    public class C { }
                    """);
        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--source-path", src.toString(),
                "p");

        checkOutput("p/C.html", true,
                """
                    <div class="block">Hello, <em>Markdown</em> world!</div>
                    """);
    }

    @Test
    public void testFirstSentence(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    public class C {
                        /// This is the _first_ sentence.
                        /// This is the _second_ sentence.
                         public void m() { }
                    }
                    """);
        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--source-path", src.toString(),
                "p");

        checkOrder("p/C.html",
                """
                    <section class="method-summary" id="method-summary">""",
                """
                    <div class="block">This is the <em>first</em> sentence.</div>""",
                """
                    <section class="method-details" id="method-detail">""",
                """
                    <div class="block">This is the <em>first</em> sentence.
                    This is the <em>second</em> sentence.</div>""");
    }

    @Test
    public void testMarkdownList(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    /// Before list.
                    ///
                    /// * item 1
                    /// * item 2
                    /// * item 3
                    ///
                    /// After list.
                    public class C { }
                    """);
        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--source-path", src.toString(),
                "p");

        checkOrder("p/C.html",
                """
                    <p>Before list.</p>
                    <ul>
                    <li>item 1</li>
                    <li>item 2</li>
                    <li>item 3</li>
                    </ul>
                    <p>After list.</p>
                    """);
    }

    @Test
    public void testMarkdownList2(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    /// Before list.
                    ///
                    /// - item 1
                    /// - item 2
                    /// - item 3
                    ///
                    /// After list.
                    public class C { }
                    """);
        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--source-path", src.toString(),
                "p");

        checkOrder("p/C.html",
                """
                    <p>Before list.</p>
                    <ul>
                    <li>item 1</li>
                    <li>item 2</li>
                    <li>item 3</li>
                    </ul>
                    <p>After list.</p>
                    """);
    }

    @Test
    public void testFont(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    /// Regular, `Monospace`, _italic_, and **bold** font.
                    public class C { }
                    """);
        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--source-path", src.toString(),
                "p");

        checkOutput("p/C.html", true,
                """
                    Regular, <code>Monospace</code>, <em>italic</em>, and <strong>bold</strong> font.""");
    }

    @Test
    public void testInherit_md_md(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    public class Base {
                        /// Markdown comment.
                        /// @throws Exception Base _Markdown_
                        public void m() throws Exception { }
                    }""",
                """
                    package p;
                    public class Derived extends Base {
                        /// Markdown comment.
                        /// @throws {@inheritDoc}
                        public void m() throws Exception { }
                    }
                    """);
        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--no-platform-links",
                "--source-path", src.toString(),
                "p");

        checkOutput("p/Derived.html", true,
                """
                    <dt>Throws:</dt>
                    <dd><code>java.lang.Exception</code> - Base <em>Markdown</em></dd>
                    """);
    }

    @Test
    public void testInherit_md_plain(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    public class Base {
                        /// Markdown comment.
                        /// @throws Exception Base _Markdown_
                        public void m() throws Exception { }
                    }""",
                """
                    package p;
                    public class Derived extends Base {
                        /**
                         * Plain comment.
                         * @throws {@inheritDoc}
                         */
                         public void m() throws Exception { }
                    }
                    """);
        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--no-platform-links",
                "--source-path", src.toString(),
                "p");

        checkOutput("p/Derived.html", true,
                """
                    <dt>Throws:</dt>
                    <dd><code>java.lang.Exception</code> - Base <em>Markdown</em></dd>
                    """);
    }

    @Test
    public void testInherit_plain_md(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    public class Base {
                        /**
                         * Plain comment.
                         * @throws Exception Base _Not Markdown_
                         */
                         public void m() throws Exception { }
                    }""",
                """
                    package p;
                    public class Derived extends Base {
                        /// Markdown comment.
                        /// @throws {@inheritDoc}
                        public void m() throws Exception { }
                    }
                    """);
        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--no-platform-links",
                "--source-path", src.toString(),
                "p");

        checkOutput("p/Derived.html", true,
                """
                    <dt>Throws:</dt>
                    <dd><code>java.lang.Exception</code> - Base _Not Markdown_</dd>
                    """);
    }

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

    @Test
    public void testIndentedInlineReturn(Path base) throws Exception {
        //this is a Markdown-specific test, because leading whitespace is ignored in HTML comments
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    /// Class description.
                    public class C {
                        ///    {@return an int}
                        /// More description.
                        public int m() { return 0; }
                    }
                    """);
        javadoc("-d", base.resolve("api").toString(),
                "--source-path", src.toString(),
                "p");

        checkOutput("p/C.html", true,
                """
                    <section class="detail" id="m()">
                    <h3>m</h3>
                    <div class="horizontal-scroll">
                    <div class="member-signature"><span class="modifiers">public</span>&nbsp;<span class="return-type">int</span>&nbsp;<span class="element-name">m</span>()</div>
                    <div class="block">Returns an int.
                    More description.</div>
                    <dl class="notes">
                    <dt>Returns:</dt>
                    <dd>an int</dd>
                    </dl>
                    </div>
                    </section>""");
    }

    @Test
    public void testFFFC(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    /// First sentence. 1{@code 1}1 \ufffc 2{@code 2}2
                    public class C { }
                    """);
        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--source-path", src.toString(),
                "p");

        checkOutput("p/C.html", true,
                """
                    <div class="block">First sentence. 1<code>1</code>1 \ufffc 2<code>2</code>2</div>
                    """);
    }

    @Test
    public void testDocFile(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                package p;
                /// First sentence.
                public class C { }
                """);
        tb.writeFile(src.resolve("p").resolve("doc-files").resolve("markdown.md"),
                """
                # This is a _Markdown_ heading

                Lorem ipsum""");

        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--source-path", src.toString(),
                "p");

        checkOutput("p/doc-files/markdown.html", true,
                """
                    <title>This is a Markdown heading</title>
                    """,
                """
                    <main role="main"><h1 id="this-is-a-markdown-heading-heading1">This is a <em>Markdown</em> heading</h1>
                    <p>Lorem ipsum</p>
                    </main>
                    """);
    }

    @Test
    public void testOverview(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                package p;
                /// First sentence.
                public class C { }
                """);
        var overviewFile = src.resolve("overview.md");
        tb.writeFile(overviewFile,
                """
                This is a _Markdown_ overview.
                Lorem ipsum""");

        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "-overview", overviewFile.toString(),
                "--source-path", src.toString(),
                "p");

        checkOutput("index.html", true,
                """
                    <div class="block">This is a <em>Markdown</em> overview.
                    Lorem ipsum</div>""");
    }

    // The following test checks whether the output of system taglets
    // is or is not wrapped in <p> tags, depending on the context within
    // the doc comment and the output generated by the taglet.
    //
    // {@code ...} is used as an example of a tag that generates phrasing content.
    // {@snippet ...} is used as an example of a tag that generates block content.
    //
    // See also: testUserTaglets
    @Test
    public void testSystemTaglets(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    public class C {
                        /// First sentence.
                        ///
                        /// {@code this is code_standalone}
                        ///
                        /// Lorem ipsum.
                        public void code_standalone() { }

                        /// First sentence.
                        ///
                        /// Before.
                        /// {@code this is code_wrapped}
                        /// After.
                        ///
                        /// Lorem ipsum.
                        public void code_wrapped() { }

                        /// First sentence.
                        ///
                        /// {@snippet :
                        ///    this is snippet_standalone
                        /// }
                        ///
                        /// Lorem ipsum.
                        public void snippet_standalone() { }

                        /// First sentence.
                        ///
                        /// Before.
                        /// {@snippet :
                        ///    this is a snippet_wrapped
                        /// }
                        /// After.
                        ///
                        /// Lorem ipsum.
                        public void snippet_wrapped() { }
                    }""");

        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--source-path", src.toString(),
                "p");

        checkOutput("p/C.html", true,
                """
                    <div class="block"><p>First sentence.</p>
                    <p><code>this is code_standalone</code></p>
                    <p>Lorem ipsum.</p>
                    </div>""",
                """
                    <div class="block"><p>First sentence.</p>
                    <p>Before.
                    <code>this is code_wrapped</code>
                    After.</p>
                    <p>Lorem ipsum.</p>
                    </div>""",
                """
                    <div class="block"><p>First sentence.</p>
                    <div class="snippet-container"><button class="copy snippet-copy" aria-label="Copy snippet" \
                    onclick="copySnippet(this)"><span data-copied="Copied!">Copy</span>\
                    <img src="../resource-files/copy.svg" alt="Copy snippet"></button>
                    <pre class="snippet"><code>   this is snippet_standalone
                    </code></pre>
                    </div>

                    <p>Lorem ipsum.</p>
                    </div>""",
                """
                    <div class="block"><p>First sentence.</p>
                    <p>Before.</p>
                    <div class="snippet-container"><button class="copy snippet-copy" aria-label="Copy snippet" onclick="copySnippet(this)"><span data-copied="Copied!">Copy</span><img src="../resource-files/copy.svg" alt="Copy snippet"></button>
                    <pre class="snippet"><code>   this is a snippet_wrapped
                    </code></pre>
                    </div>

                    <p>After.</p>
                    <p>Lorem ipsum.</p>
                    </div>""");
    }

    // The following test checks whether the output of user-defined taglets
    // is or is not wrapped in <p> tags, depending on the context within
    // the doc comment and the output generated by the taglet.
    //
    // {@span ...} is used as an example of a taglet that generates phrasing content.
    // {@div ...} is used as an example of a taglet that generates block content.
    //
    // See also: testSystemTaglets
    @Test
    public void testUserTaglets(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    public class C {
                        /// First sentence.
                        ///
                        /// {@span this is phrasing_standalone}
                        ///
                        /// Lorem ipsum.
                        public void phrasing_standalone() { }

                        /// First sentence.
                        ///
                        /// Before.
                        /// {@span this is phrasing_wrapped}
                        /// After.
                        ///
                        /// Lorem ipsum.
                        public void phrasing_wrapped() { }

                        /// First sentence.
                        ///
                        /// {@div this is block_standalone}
                        ///
                        /// Lorem ipsum.
                        public void block_standalone() { }

                        /// First sentence.
                        ///
                        /// Before.
                        /// {@div this is block_wrapped}
                        /// After.
                        ///
                        /// Lorem ipsum.
                        public void block_wrapped() { }
                    }""");

        String testClasses = System.getProperty("test.classes");

        javadoc("-d", base.resolve("api").toString(),
                "-tagletpath", testClasses,
                "-taglet", "DivTaglet",
                "-taglet", "SpanTaglet",
                "-Xdoclint:none",
                "--source-path", src.toString(),
                "p");

        checkOutput("p/C.html", true, """
                    <div class="block"><p>First sentence.</p>
                    <p><span>this is phrasing_standalone</span></p>
                    <p>Lorem ipsum.</p>
                    </div>""",
                """
                    <div class="block"><p>First sentence.</p>
                    <p>Before.
                    <span>this is phrasing_wrapped</span>
                    After.</p>
                    <p>Lorem ipsum.</p>
                    </div>""",
                """
                    <div class="block"><p>First sentence.</p>
                    <div>this is block_standalone</div>
                    <p>Lorem ipsum.</p>
                    </div>""",
                """
                    <div class="block"><p>First sentence.</p>
                    <p>Before.</p>
                    <div>this is block_wrapped</div>
                    <p>After.</p>
                    <p>Lorem ipsum.</p>
                    </div>""");
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

    // Test the ability to put links in the first sentence of a description.
    // Note that we user-defined reference links cannot be used in the first
    // sentence, and so in that case we verify the behavior is "as expected".
    @Test
    public void testFirstSentenceLinks(Path base) throws Exception {
        Path src = base.resolve("src");

        // Apart from the (control) case, the other cases exercise
        // various kinds of links in the first sentence of a description.
        // Note the last case is an explicit test of a link that is
        // _not_ currently supported, since the link reference definition
        // is not part of the first sentence.
        tb.writeJavaFiles(src,
                """
                    package p;
                    import q.MyObject;
                    public class C {
                        /// First sentence.
                        /// Control: [MyObject]
                        public void m1() { }

                        /// Simple autoref in first sentence [MyObject].
                        /// More.
                        public void m2() { }

                        /// Qualified autoref in first sentence [q.MyObject].
                        /// More.
                        public void m3() { }

                        /// Standard link with periods [example.com](http://example.com).
                        /// More.
                        public void m4() { }

                        /// Manual ref link [foo].
                        /// More.
                        ///
                        /// [foo]: http:example.com
                        public void m5() { }
                    }""",
                // use a simple class in a different package, to avoid platform links to system classes
                """
                    package q;
                    public class MyObject { }""");

        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--source-path", src.toString(),
                "p", "q");
        checkExit(Exit.OK);

        // use checkOrder and the delimiter comments to ensure that
        // we check the strings in the method summary table, and not
        // subsequently in the method details section.
        checkOrder("p/C.html",
                "<!-- ========== METHOD SUMMARY =========== -->",
                """
                    <div class="block">Simple autoref in first sentence <a href="../q/MyObject.html" \
                    title="class in q"><code>MyObject</code></a>.</div>""",
                """
                    <div class="block">Qualified autoref in first sentence <a href="../q/MyObject.html" \
                    title="class in q"><code>MyObject</code></a>.</div>""",
                """
                    <div class="block">Standard link with periods \
                    <a href="http://example.com">example.com</a>.</div>""",
                // The following is a test of the regrettably expected behavior,
                // because the link reference definition is not carried into
                // the first sentence.
                """
                    <div class="block">Manual ref link [foo].</div>""",
                "<!-- ============ METHOD DETAIL ========== -->"
                );
    }

    // Test that periods within certain constructs do not prematurely terminate
    // the first sentence.
    @Test
    public void testFirstSentencePeriods(Path base) throws Exception {
        testFirstSentencePeriods(base.resolve("no-bi"), false);
        testFirstSentencePeriods(base.resolve("bi"), true);
    }

    void testFirstSentencePeriods(Path base, boolean useBreakIterator) throws Exception {
        Path src = base.resolve("src");

        tb.writeJavaFiles(src,
                """
                        package p;
                        public class C {
                            /// Code span `1.0` end.
                            /// More.
                            public void m1() { }
                            /// Complex code span ``` `1.0` ``` end.
                            /// More.
                            public void m2() { }
                            /// Period space `1.  2.  3.` end.
                            /// More.
                            public void m3() { }
                            /// Link [example.com](http://example.com) end.
                            /// More.
                            public void m4() { }
                        }
                        """);

        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                (useBreakIterator ? "-breakiterator" : "-XDdummy"),
                "--source-path", src.toString(),
                "p");
        checkExit(Exit.OK);

        // use checkOrder and the delimiter comments to ensure that
        // we check the strings in the method summary table, and not
        // subsequently in the method details section.
        checkOrder("p/C.html",
                "<!-- ========== METHOD SUMMARY =========== -->",
                """
                    <div class="block">Code span <code>1.0</code> end.</div>""",
                """
                    <div class="block">Complex code span <code>`1.0`</code> end.</div>""",
                """
                    <div class="block">Period space <code>1.  2.  3.</code> end.</div>""",
                """
                    <div class="block">Link <a href="http://example.com">example.com</a> end.</div>""",
                "<!-- ============ METHOD DETAIL ========== -->"
        );
    }

    @Test
    public void testDeprecated(Path base) throws Exception {
        Path src = base.resolve("src");

        tb.writeJavaFiles(src,
                """
                    package p;
                    public class Control {
                        /**
                         * First sentence. Second sentence.
                         */
                         @Deprecated
                         public void anno_noTag() { }
                        /**
                         * First sentence. Second sentence.
                         * @deprecated deprecated-text
                         */
                         public void noAnno_tag() { }
                        /**
                         * First sentence. Second sentence.
                         * @deprecated deprecated-text
                         */
                        @Deprecated
                        public void anno_tag() { }
                    }""",
                """
                    package p;
                    public class MarkdownComments {
                        /// First sentence. Second sentence.
                        @Deprecated
                        public void anno_noTag() { }
                        /// First sentence. Second sentence.
                        /// @deprecated deprecated-text.
                        public void noAnno_tag() { }
                        /// First sentence. Second sentence.
                        /// @deprecated deprecated-text
                        @Deprecated
                        public void anno_tag() { }
                    }""");

        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--no-platform-links",
                "--source-path", src.toString(),
                "p");
        checkExit(Exit.OK);

        // Note: javadoc does not generate warnings about any mismatch
        // between @Deprecated annotations and @deprecated tags:
        // the mismatch is detected and reported by javac Attr phase,
        // when enabled by -Xlint:dep-ann.

        // the output for these two files should be the same, except where it is not
        for (var f : List.of("p/Control.html", "p/MarkdownComments.html")) {
            // in the following checks we check from the signature,
            // beginning at the name, through to the end of the main description.
            checkOutput(f, true,
                    """
                        <span class="element-name">anno_noTag</span>()</div>
                        <div class="deprecation-block"><span class="deprecated-label">Deprecated.</span></div>
                        <div class="block">First sentence. Second sentence.</div>""",

                    switch (f) {
                        // @deprecated but no annotation in a traditional comment implies deprecation
                        case "p/Control.html" -> """
                            <span class="element-name">noAnno_tag</span>()</div>
                            <div class="deprecation-block"><span class="deprecated-label">Deprecated.</span>
                            <div class="deprecation-comment">deprecated-text</div>
                            </div>
                            <div class="block">First sentence. Second sentence.</div>""";

                        // @deprecated but no annotation in a Markdown comment does not imply deprecation
                        case "p/MarkdownComments.html" -> """
                            <span class="element-name">noAnno_tag</span>()</div>
                            <div class="block">First sentence. Second sentence.</div>""";

                        default -> throw new Error();
                    },

                    """
                        <span class="element-name">anno_tag</span>()</div>
                        <div class="deprecation-block"><span class="deprecated-label">Deprecated.</span>
                        <div class="deprecation-comment">deprecated-text</div>
                        </div>
                        <div class="block">First sentence. Second sentence.</div>""");
        }
    }

    @Test
    public void testEscape(Path base) throws Exception {
        Path src = base.resolve("src");

        tb.writeJavaFiles(src,
                // In the following, note the need to double the escape character,
                // so that the comment contains a single escape to precede the backtick.
                // Also, note that because the first backtick is escaped, the comment
                // is as-if there are two unmatched backticks, with an inline tag
                // between them, and not a code span enclosing literal text.
                """
                    package p;
                    public class C {
                        /// Abc \\` def {@code xyz} ghi ` jkl.
                        /// More.
                        public void m() { }
                    }
                    """);

        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--no-platform-links",
                "--source-path", src.toString(),
                "p");
        checkExit(Exit.OK);

        checkOutput("p/C.html", true,
                """
                    <span class="element-name">m</span>()</div>
                    <div class="block">Abc ` def <code>xyz</code> ghi ` jkl.
                    More.</div>""");
    }

    @Test
    public void testBacktickAt(Path base) throws Exception {
        Path src = base.resolve("src");

        // in the following, note that the @ following the backtick
        // is just a literal character and not part of any tag
        tb.writeJavaFiles(src,
                """
                    package p;
                    public class C {
                        /// First sentence.
                        /// Abc `@' def.
                        /// More.
                        public void m() { }
                    }
                    """);

        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:syntax", // enable check for "no tag after '@'
                "--no-platform-links",
                "--source-path", src.toString(),
                "p");
        checkExit(Exit.OK);

        checkOutput(Output.OUT, false,
                "C.java:4: error: no tag name after '@'");

        checkOutput("p/C.html", true,
                """
                    <div class="block">First sentence.
                    Abc `@' def.
                    More.</div>
                    </div>""");
    }
}
