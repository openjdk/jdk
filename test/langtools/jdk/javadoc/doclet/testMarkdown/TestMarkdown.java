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
 * @bug      8298405
 * @summary  Markdown support in the standard doclet
 * @library  /tools/lib ../../lib
 * @modules  jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @run main TestMarkdown
 */

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

import java.nio.file.Path;

public class TestMarkdown extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestMarkdown tester = new TestMarkdown();
        tester.runTests();
    }

    ToolBox tb = new ToolBox();

    @Test
    public void testMinimal(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    /**md
                     * Hello, _Markdown_ world!
                     */
                    public class C { }
                    """);
        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--source-path", src.toString(),
                "p");

        checkOutput("p/C.html", true,
                """
                    <div class="block"><p>Hello, <em>Markdown</em> world!</p>
                    </div>
                    """);
    }

    @Test
    public void testFirstSentence(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    public class C {
                        /**md
                         * This is the _first_ sentence.
                         * This is the _second_ sentence.
                         */
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
                    <div class="block"><p>This is the <em>first</em> sentence.</p>
                    </div>""",
                """
                    <section class="method-details" id="method-detail">""",
                """
                    <div class="block"><p>This is the <em>first</em> sentence.
                    This is the <em>second</em> sentence.</p>
                    </div>""");
    }

    @Test
    public void testMarkdownList(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    /**md
                     * Before list.
                     *
                     * * item 1
                     * * item 2
                     * * item 3
                     *
                     * After list.
                     */
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
                    /**md
                     Before list.

                     - item 1
                     - item 2
                     - item 3

                     After list.
                     */
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
                    /**md
                     * Regular, `Monospace`, _italic_, and **bold** font.
                     */
                    public class C { }
                    """);
        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--source-path", src.toString(),
                "p");

        checkOutput("p/C.html", true,
                """
                    <p>Regular, <code>Monospace</code>, <em>italic</em>, and <strong>bold</strong> font.</p>""");
    }

    @Test
    public void testInherit_md_md(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    public class Base {
                        /**md
                         * Markdown comment.
                         * @throws Exception Base _Markdown_
                         */
                         public void m() throws Exception { }
                    }""",
                """
                    package p;
                    public class Derived extends Base {
                        /**md
                         * Markdown comment.
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
                    <dd><code>java.lang.Exception</code> - <p>Base <em>Markdown</em></p>
                    </dd>
                    """);
    }

    @Test
    public void testInherit_md_plain(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    public class Base {
                        /**md
                         * Markdown comment.
                         * @throws Exception Base _Markdown_
                         */
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
                    <dd><code>java.lang.Exception</code> - <p>Base <em>Markdown</em></p>
                    </dd>
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
                        /**md
                         * Markdown comment.
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
                        /**md
                         * Method m1.
                         * This is different from {@link #m2()}.
                         */
                        public void m1() { }
                        /**md
                         * Method m2.
                         * This is different from {@link #m1()}.
                         */
                        public void m2() { }
                    }
                    """);
        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--source-path", src.toString(),
                "p");

        checkOutput("p/C.html", true,
                """
                    <p>Method m1.
                    This is different from <a href="#m2()"><code>m2()</code></a>.</p>
                    """);

    }

    @Test
    public void testLinkWithDescription(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    public class C {
                        /**md
                         * Method m1.
                         * This is different from {@linkplain #m2() _Markdown_ m2}.
                         */
                        public void m1() { }
                        /**md
                         * Method m2.
                         * This is different from {@linkplain #m1() _Markdown_ m1}.
                         */
                        public void m2() { }
                    }
                    """);
        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--source-path", src.toString(),
                "p");

        checkOutput("p/C.html", true,
                """
                    <p>Method m1.
                    This is different from <a href="#m2()"><p><em>Markdown</em> m2</p>""");

    }

    @Test
    public void testSee(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    /**md
                     * First sentence.
                     * @see "A reference"
                     * @see <a href="http://www.example.com">Example</a>
                     * @see D a _Markdown_ description
                     */
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
                    <li><p><a href="http://www.example.com">Example</a></p>
                    </li>
                    <li><a href="D.html" title="class in p"><code><p>a <em>Markdown</em> description</p>
                    </code></a></li>
                    </ul>
                    </dd>""");

    }

    @Test
    public void testFFFC(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    /**md
                     * First sentence. 1{@code 1}1 \ufffc 2{@code 2}2
                     */
                    public class C { }
                    """);
        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--source-path", src.toString(),
                "p");

        checkOutput("p/C.html", true,
                """
                        <div class="block"><p>First sentence. 1<code>1</code>1 \ufffc 2<code>2</code>2</p>
                        </div>
                        """);
    }
}
