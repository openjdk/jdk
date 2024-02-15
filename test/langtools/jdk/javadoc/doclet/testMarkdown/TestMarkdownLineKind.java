/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @run main TestMarkdownLineKind
 */

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
 * These are "somewhat silly" tests of extreme corner cases, in which
 * an unmatched backtick is followed by an inline tag, followed by more
 * content which also has an unmatched backtick. The test is that
 * the unmatched backticks are recognized as such, and not as a matched
 * pair enclosing literal text.  Given how extreme the corner case is,
 * the tests are basic and minimal and not intended to be exhaustive.
 *
 * The use of the inline tag is to detect whether the parser sees the
 * backticks as a pair enclosing literal text, or as unmatched backticks
 * leaving the characters, including the inline tag, to be interpreted
 * appropriately.
 *
 * Note that the corner case only applies when there is the potential for
 * an inline tag. If there is no inline tag, the Markdown parser will
 * "do the right thing" with the backticks in the separate blocks.
 *
 * Also note that an author can force the use of an unmatched backtick
 * by escaping it with a backslash, instead of relying on Markdown to
 * recognize that a backtick is not matched within the current block.
 */
public class TestMarkdownLineKind extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestMarkdownLineKind();
        tester.runTests();
    }

    ToolBox tb = new ToolBox();

    enum TestCase {
        BLANK("""

                    abc ` def""",
                """
                        <p>abc ` def</p>"""),

        ATX_HEADER("""
                    # ATX Heading ` more
                    """,
                """
                    <h4 id="atx-heading-more-heading">ATX Heading ` more</h4>"""),

        // this is an almost redundant test case, since we need a blank line before the
        // setext heading, so that the heading does not get merged with the preceding text
        SETEXT_UNDERLINE("""

                    Setext Heading
                    =============
                    abc ` def""",
                """
                    <h4 id="setext-heading-heading">Setext Heading</h4>
                    <p>abc ` def</p>"""),

        THEMATIC_BREAK_DASH("""
                    - - - - - - - - - -
                    abc ` def
                    """,
                """
                    <hr />
                    <p>abc ` def</p>"""),

        THEMATIC_BREAK_ASTERISK("""
                    * * * * *
                    abc ` def""",
                """
                    <hr />
                    <p>abc ` def</p>"""),

        THEMATIC_BREAK_UNDERLINE("""
                    ____________
                    abc ` def""",
                """
                    <hr />
                    <p>abc ` def</p>"""),

        CODE_FENCE("""
                    ```
                    code block
                    ```""",
                """
                    <pre><code>code block
                    </code></pre>"""),

        BULLETED_LIST_ITEM_SPACE("""
                    * list ` item""",
                """
                    <ul>
                    <li>list ` item</li>
                    </ul>"""),

        BULLETED_LIST_ITEM_TAB("""
                    *\tlist ` item""",
                """
                    <ul>
                    <li>list ` item</li>
                    </ul>"""),

        ORDERED_LIST_ITEM_SPACE("""
                    1. list ` item""",
                """
                    <ol>
                    <li>list ` item</li>
                    </ol>"""),

        ORDERED_LIST_ITEM_TAB("""
                    1.\tlist ` item""",
                """
                    <ol>
                    <li>list ` item</li>
                    </ol>"""),

        BLOCK_QUOTE("""
                    > Block ` quote""",
                """
                    <blockquote>
                    <p>Block ` quote</p>
                    </blockquote>"""),

        // this is an almost redundant test case, since we need a blank line before the
        // code block, so that the content does not get merged with the preceding text
        INDENTED_CODE_BLOCK("""

                        indented code
                    abc `def""",
                """
                        <pre><code>indented code
                        </code></pre>
                        <p>abc `def</p>""");

        final String srcFragment;
        final String expect;

        TestCase(String srcFragment, String expect) {
            this.srcFragment = srcFragment;
            this.expect = expect;
        }

        String method() {
            return """
                    /// First sentence.
                    /// Lorem ipsum ` {@code CODE}
                    #FRAG#
                    public void #NAME#() { }
                    """
                    .replace("#FRAG#", srcFragment.lines()
                            .map(l -> "/// " + l + "\n")
                            .collect(Collectors.joining()))
                    .replace("#NAME#", name().toLowerCase(Locale.ROOT));

        }
    }

    @Test
    public void testLineKinds(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, Stream.of(TestCase.values())
                .map(TestCase::method)
                .collect(Collectors.joining("", """
                    package p;
                    public class C {
                    """, """
                    }""")));

        javadoc("-d", base.resolve("api").toString(),
                "--no-platform-links",
                "-Xdoclint:none",
                "--source-path", src.toString(),
                "p");
        checkExit(Exit.OK);

        for (var tc : TestCase.values()) {
            checkOutput("p/C.html", true,
                    """
                        <span class="element-name">#NAME#</span>()</div>
                        <div class="block"><p>First sentence.
                        Lorem ipsum ` <code>CODE</code></p>
                        #FRAG#
                        </div>"""
                            .replace("#NAME#", tc.name().toLowerCase(Locale.ROOT))
                            .replace("#FRAG#", tc.expect));
        }

    }
}