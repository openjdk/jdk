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
 * @run main TestMarkdownFirstSentence
 */

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

import java.nio.file.Path;
import java.util.List;

public class TestMarkdownFirstSentence extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestMarkdownFirstSentence();
        tester.runTests();
    }

    ToolBox tb = new ToolBox();

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

    // Test the ability to put links in the first sentence of a description.
    // Note that user-defined reference links cannot be used in the first
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
    public void testExtraPara(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    /// This is the class description.
                    ///
                    /// # Heading
                    /// Lorem ipsum
                    public class C { }
                    """);
        javadoc("-d", base.resolve("api").toString(),
                "--no-platform-links",
                "--source-path", src.toString(),
                "p");

        checkOutput("p/package-summary.html", true,
                """
                    <div class="col-first even-row-color class-summary class-summary-tab2"><a href="C.html" title="clas\
                    s in p">C</a></div>
                    <div class="col-last even-row-color class-summary class-summary-tab2">
                    <div class="block">This is the class description.</div>
                    </div>""");

        checkOutput("p/C.html", true,
                """
                        <span class="element-name type-name-label">C</span>
                        <span class="extends-implements">extends java.lang.Object</span></div>
                        <div class="block"><p>This is the class description.</p>
                        <h2 id="heading-heading">Heading</h2>
                        <p>Lorem ipsum</p>
                        </div>""");

    }
}
