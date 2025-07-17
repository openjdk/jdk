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
                "C.java:4: error: no tag name after '@'",
                "unknown tag");

        checkOutput("p/C.html", true,
                """
                    <div class="block">First sentence.
                    Abc `@' def.
                    More.</div>
                    </div>""");
    }

    @Test
    public void testAnnos(Path base) throws Exception {
        Path src = base.resolve("src");

        // in the following, note that the @ following the backtick
        // is just a literal character and not part of any tag
        tb.writeJavaFiles(src,
                """
                    package p;
                    public class C {
                        /// First sentence.
                        /// 1.  list item
                        ///
                        ///     \\@Anno1 plain
                        ///
                        ///     abc `
                        ///     @Anno2 in span
                        ///     `
                        ///
                        ///     ```
                        ///     @Anno3 fenced
                        ///     ```
                        ///
                        ///         @Anno4 indented
                        ///
                        ///     end of list item
                        ///
                        /// end
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
                "C.java:4: error: no tag name after '@'",
                "unknown tag");

        checkOutput("p/C.html", true,
                """
                    <ol>
                    <li>
                    <p>list item</p>
                    <p>@Anno1 plain</p>
                    <p>abc <code>@Anno2 in span</code></p>
                    <pre><code>@Anno3 fenced
                    </code></pre>
                    <pre><code>@Anno4 indented
                    </code></pre>
                    <p>end of list item</p>
                    </li>
                    </ol>
                    <p>end</p>
                    """);

    }
}
