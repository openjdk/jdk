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
 * @run main TestMarkdownCodeSpans
 */

import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Collectors;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

/**
 * Tests Markdown code spans.
 *
 * Code spans are enclosed within a matching sequence of one or more backtick ({@code `})
 * characters and provide a way to specify "literal text" within a block,
 * such as a paragraph, list item, or block quote.  Code spans may span multiple lines
 * but not cross block boundaries: if a sequence of backtick characters is not matched within
 * a block, the sequence is treated as literal text, even if there is a matching sequence
 * in a subsequent block.
 *
 * In the various test cases, javadoc tags are used as a way to determine
 * if the parser is correctly interpreting the text in its surrounding context.
 * Within a code span, character sequences resembling a tag are treated as
 * literal text, and appear "as is" in the generated output. Outside a
 * code span, character sequences resembling a tag are treated as tags and
 * are translated as appropriate in the generated output.
 *
 * A primary consideration in the test cases is the use and handling of lines
 * that may or may not be part of the same block.  As such, the source for
 * each test case can be considered to be in two parts, each containing a backtick,
 * and which may or may not represent a code span.  Note that only some kinds
 * of lines (for paragraphs, list items and block quotes) may contain code spans.
 * All kinds of lines either continue a block or terminate it.
 */
public class TestMarkdownCodeSpans extends JavadocTester {
    public static void main(String... args) throws Exception {
        var tester = new TestMarkdownCodeSpans();
        tester.runTests();
    }

    ToolBox tb = new ToolBox();

    @Test
    public void testCodeSpans(Path base) throws Exception {
        // Test cases provide a fragment of content for a documentation comment
        // and a corresponding fragment of content to be found in the generated output.
        // The name of each member is used to generate the name of a declaration
        // with which the documentation comment is associated.
        //
        // In the source fragments:
        // - when "{@code TEXT}" appears, it is expected it will appear "as is" in the output
        //   enclosed within an HTML <code> element
        // - when "{@code TAG}" appears, it is expected it will be treated as a tag
        //   and will appear in the output as "<code>TAG</code>"
        // Thus, the character sequence "{@code TAG}" should never appear in any output.
        //
        // Note: for test cases involving ATX and setext headings, care must be taken to
        // ensure that the content is such that the auto-generated ids are unique,
        // so that we do not depend on the doclet to disambiguate the ids, based on the
        // order in which the headings are generated.
        enum TestCase {
            SIMPLE_PARA(
                    """

                        abc `p {@code TEXT} q` def

                        end""",
                    """
                        <p>abc <code>p {@code TEXT} q</code> def</p>
                        <p>end</p>"""),

            // a multi-line code span
            PARA_PARA(
                    """

                        abc `p {@code TEXT}
                        q` def

                        end""",
                    """
                        <p>abc <code>p {@code TEXT} q</code> def</p>
                        <p>end</p>"""),

            // a paragraph with a heavily indented continuation line,
            // including a multi-line code span
            PARA_INDENT(
                    """

                        abc `p {@code TEXT}
                                      q` def

                        end""",
                    """
                        <p>abc <code>p {@code TEXT} q</code> def</p>
                        <p>end</p>"""),

            // blank line after a paragraph; no code spans, the tag is processed
            PARA_BLANK(
                    """

                        abc `p {@code TAG}

                        q` def

                        end""",
                    """
                        <p>abc `p <code>TAG</code></p>
                        <p>q` def</p>
                        <p>end</p>"""),

            // thematic break after a paragraph; no code spans, the tag is processed
            PARA_THEMATIC_BREAK_DASH(
                    """

                        abc `p {@code TAG}
                        - - - - - - - - - - - -
                        q` def

                        end""",
                    """
                        <p>abc `p <code>TAG</code></p>
                        <hr />
                        <p>q` def</p>
                        <p>end</p>"""),

            // thematic break after a paragraph; no code spans, the tag is processed
            PARA_THEMATIC_BREAK_ASTERISK(
                    """

                        abc `p {@code TAG}
                        * * * * * * * * * *
                        q` def

                        end""",
                    """
                        <p>abc `p <code>TAG</code></p>
                        <hr />
                        <p>q` def</p>
                        <p>end</p>"""),

            // ATX heading after a paragraph; no code spans, the tag is processed
            PARA_ATX(
                    """

                        abc `p1 {@code TAG}
                        # q1` def

                        end""",
                    """
                        <p>abc `p1 <code>TAG</code></p>
                        <h4 id="q1-def-heading">q1` def</h4>
                        <p>end</p>"""),

            // setext heading; no code spans, the tag is processed
            PARA_SETEXT(
                    """

                        abc `p {@code TAG}
                        ===================
                        q` def

                        end""",
                    """
                        <h4 id="abc-p--heading">abc `p <code>TAG</code></h4>
                        <p>q` def</p>
                        <p>end</p>"""),

            SIMPLE_LIST(
                    """

                        * abc `p {@code TEXT} q` def

                        end""",
                    """
                        <ul>
                        <li>abc <code>p {@code TEXT} q</code> def</li>
                        </ul>
                        <p>end</p>"""),

            // two list items; no code spans, the tag is processed
            LIST_LIST(
                    """

                        * abc `p {@code TAG}
                        * q` def

                        end""",
                    """
                        <ul>
                        <li>abc `p <code>TAG</code></li>
                        <li>q` def</li>
                        </ul>
                        <p>end</p>"""),

            // a multi-line code span in a multi-line list item
            LIST_PARA(
                    """

                        * abc `p {@code TEXT}
                        q` def

                        end""",
                    """
                <ul>
                <li>abc <code>p {@code TEXT} q</code> def</li>
                </ul>
                <p>end</p>"""),

            // a list item with a heavily indented continuation line,
            // including a multi-line code span
            LIST_INDENT(
                    """

                        * abc `p {@code TEXT}
                                      q` def

                        end""",
                    """
                <ul>
                <li>abc <code>p {@code TEXT} q</code> def</li>
                </ul>
                <p>end</p>"""),

            SIMPLE_BLOCKQUOTE(
                    """

                        > abc `p {@code TEXT} q` def

                        end""",
                    """
                        <blockquote>
                        <p>abc <code>p {@code TEXT} q</code> def</p>
                        </blockquote>
                        <p>end</p>"""),

            // a multi-line code span in a multi-line block quote
            BLOCKQUOTE_BLOCKQUOTE(
                    """

                        > abc `p {@code TEXT}
                        > q` def

                        end""",
                    """
                        <blockquote>
                        <p>abc <code>p {@code TEXT} q</code> def</p>
                        </blockquote>
                        <p>end</p>"""),

            // a multi-line code span in a multi-line block quote
            BLOCKQUOTE_PARA(
                    """

                        > abc `p {@code TEXT}
                          q` def

                        end""",
                            """
                        <blockquote>
                        <p>abc <code>p {@code TEXT} q</code> def</p>
                        </blockquote>
                        <p>end</p>"""),

            // a block quote with a heavily indented continuation line,
            // including a multi-line code span
            BLOCKQUOTE_INDENT(
                    """

                        > abc `p {@code TEXT}
                                    q` def

                        end""",
                    """
                <blockquote>
                <p>abc <code>p {@code TEXT} q</code> def</p>
                </blockquote>
                <p>end</p>"""),

            SIMPLE_ATX(
                    """

                        # abc `p2 {@code TEXT} q2` def

                        end""",
                    """
                        <h4 id="abc-p2-code-text-q2-def-heading">abc <code>p2 {@code TEXT} q2</code> def</h4>
                        <p>end</p>"""),

            // two ATX headings; no code spans, the tag is processed
            ATX_ATX(
                    """

                        # abc `p3 {@code TAG}
                        # q3` def

                        end""",
                    """
                        <h4 id="abc-p3--heading">abc `p3 <code>TAG</code></h4>
                        <h4 id="q3-def-heading">q3` def</h4>
                        <p>end</p>""");

            final String srcFragment;
            final String expect;

            TestCase(String srcFragment, String expect) {
                this.srcFragment = srcFragment;
                this.expect = expect;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("""
                    package p;
                    /** Dummy. */
                    public class C {
                    private C() { }
                    """);

        for (var tc: TestCase.values()) {
            sb.append("""
                        /// First sentence.
                        #FRAG#
                        public void #NAME#() { }

                    """
                    .replace("#FRAG#", tc.srcFragment.lines()
                            .map(l -> "/// " + l)
                            .collect(Collectors.joining("\n    ")))
                    .replace("#NAME#", tc.name().toLowerCase(Locale.ROOT)));
        }

        sb.append("}");

        Path src = base.resolve("src");
        tb.writeJavaFiles(src, sb.toString());

        javadoc("-d", base.resolve("api").toString(),
                "--no-platform-links",
                "--source-path", src.toString(),
                "p");
        checkExit(Exit.OK);

        // any/all instances of "{@code TAG}" should be translated to "<code>TAG</code>"
        checkOutput("p/C.html", false,
                "{@code TAG}");

        checkOutput(Output.OUT, false,
                "unknown tag");

        for (var tc : TestCase.values()) {
            checkOutput("p/C.html", true,
                    """
                        <span class="element-name">#NAME#</span>()</div>
                        <div class="block"><p>First sentence.</p>
                        #FRAG#
                        </div>"""
                            .replace("#NAME#", tc.name().toLowerCase(Locale.ROOT))
                            .replace("#FRAG#", tc.expect));
        }
    }
}
