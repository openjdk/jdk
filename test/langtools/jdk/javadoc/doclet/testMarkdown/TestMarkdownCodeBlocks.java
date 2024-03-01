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
 * @run main TestMarkdownCodeBlocks
 */

import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Collectors;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

/**
 * Tests Markdown code blocks.
 *
 * Code blocks may be either "fenced code blocks" or "indented code blocks".
 * Within a code block, all text is "literal text" and not interpreted as
 * Markdown, HTML or javadoc tags.
 *
 * In the various test cases, javadoc tags are used as a way to determine
 * if the parser is correctly interpreting the text in its surrounding context.
 * Within a code block, character sequences resembling a tag are treated as
 * literal text, and appear "as is" in the generated output. Outside a
 * code block, character sequences resembling a tag are treated as tags and
 * are translated as appropriate in the generated output.
 *
 * A primary consideration in the test cases is use and handling of indentation,
 * especially as it relates to lists and nested lists.
 */
public class TestMarkdownCodeBlocks extends JavadocTester {
    public static void main(String... args) throws Exception {
        var tester = new TestMarkdownCodeBlocks();
        tester.runTests();
    }

    ToolBox tb = new ToolBox();

    @Test
    public void testCodeBlocks(Path base) throws Exception {
        // Test cases provide a fragment of content for a documentation comment
        // and a corresponding fragment of content to be found in the generated output.
        // The name of each member is used to generate the name of a declaration
        // with which the documentation comment is associated.
        enum TestCase {
            SIMPLE_INDENT(
                    """

                            {@code CODE}
                            @Anno

                        end""",
                    """
                        <pre><code>{@code CODE}
                        @Anno
                        </code></pre>
                        <p>end</p>"""),

            SIMPLE_FENCE_BACKTICK(
                    """
                        ```
                        {@code CODE}
                        @Anno
                        ```
                        end""",
                    """
                        <pre><code>{@code CODE}
                        @Anno
                        </code></pre>
                        <p>end</p>"""),

            SIMPLE_FENCE_TILDE(
                    """
                        ~~~
                        {@code CODE}
                        @Anno
                        ~~~
                        end""",
                    """
                        <pre><code>{@code CODE}
                        @Anno
                        </code></pre>
                        <p>end</p>"""),

            INDENT_TABS(
                    """

                        \ttab
                         \t1-space tab
                          \t2-space tab
                           \t3-space tab
                            \t4-space tab
                        \t\t2-tab

                        end
                        """,
                    """
                        <pre><code>tab
                        1-space tab
                        2-space tab
                        3-space tab
                        \t4-space tab
                        \t2-tab
                        </code></pre>
                        <p>end</p>"""
            ),

            UNCLOSED_FENCE(
                    """
                        ```
                        {@code}
                        @Anno
                        """,
                    """
                        <pre><code>{@code}
                        @Anno
                        </code></pre>"""
            ),

            LIST_INDENT(
                    """
                        * list item

                              {@code CODE}
                              @Anno

                        end""",
                    """
                        <ul>
                        <li>
                        <p>list item</p>
                        <pre><code>{@code CODE}
                        @Anno
                        </code></pre>
                        </li>
                        </ul>
                        <p>end</p>"""),

            LIST_FENCE_BACKTICK(
                    """
                        * list item
                          ```
                          {@code CODE}
                          @Anno
                          ```
                        end""",
                    """
                        <ul>
                        <li>list item
                        <pre><code>{@code CODE}
                        @Anno
                        </code></pre>
                        </li>
                        </ul>
                        <p>end</p>"""),

            LIST_FENCE_TILDE(
                    """
                        1. list item
                           ~~~
                           {@code CODE}
                           @Anno
                           ~~~

                        end""",
                    """
                        <ol>
                        <li>list item
                        <pre><code>{@code CODE}
                        @Anno
                        </code></pre>
                        </li>
                        </ol>
                        <p>end</p>"""),

            LIST_UNCLOSED_FENCE(
                    """
                        1.  list item
                            ```
                            fenced-code
                            @Anno

                        end""",
                    """
                        <ol>
                        <li>list item
                        <pre><code>fenced-code
                        @Anno

                        </code></pre>
                        </li>
                        </ol>
                        <p>end</p>"""),

            // in the following, note the indentation of the list item is 5 spaces
            // and the block that follows is indented by just 4 spaces
            POST_LIST_INDENT(
                    """
                         1.  list item

                             second paragraph

                            {@code CODE}
                            @Anno

                        end""",
                    """
                        <ol>
                        <li>
                        <p>list item</p>
                        <p>second paragraph</p>
                        </li>
                        </ol>
                        <pre><code>{@code CODE}
                        @Anno
                        </code></pre>
                        <p>end</p>"""),

            BLOCK_FENCE(
                    """
                        > ```
                        > fenced code
                        > @Anno
                        > ```
                        end""",
                    """
                        <blockquote>
                        <pre><code>fenced code
                        @Anno
                        </code></pre>
                        </blockquote>
                        <p>end</p>"""),

            BLOCK_UNCLOSED_FENCE(
                    """
                        > ```
                        > fenced code
                        > @Anno
                        end""",
                    """
                        <blockquote>
                        <pre><code>fenced code
                        @Anno
                        </code></pre>
                        </blockquote>
                        <p>end</p>"""),

            NOT_INDENT_CONTINUATION(
                    """

                        paragraph
                                indented continuation {@code CODE}
                                more.

                        end""",
                    """
                        <p>paragraph
                        indented continuation <code>CODE</code>
                        more.</p>
                        <p>end</p>"""
            ),

            NOT_INDENT_IN_LIST(
                    """
                        1.  list item

                            list para, not indented block
                            {@code CODE}

                        end""",
                    """
                        <ol>
                        <li>
                        <p>list item</p>
                        <p>list para, not indented block
                        <code>CODE</code></p>
                        </li>
                        </ol>
                        <p>end</p>"""),

            COMBO(
                    """
                        1. list item {@code TAG} lorem ipsum

                           * nested list {@code TAG} lorem ipsum

                               nested list para {@code TAG} lorem ipsum

                                 nested indented block {@code TEXT} lorem ipsum

                            outer list para {@code TAG} lorem ipsum
                            ```
                            outer list fenced block {@code TEXT} lorem ipsum
                            ```

                        end""",
                    """
                        <ol>
                        <li>
                        <p>list item <code>TAG</code> lorem ipsum</p>
                        <ul>
                        <li>
                        <p>nested list <code>TAG</code> lorem ipsum</p>
                        <p>nested list para <code>TAG</code> lorem ipsum</p>
                        <pre><code>nested indented block {@code TEXT} lorem ipsum
                        </code></pre>
                        </li>
                        </ul>
                        <p>outer list para <code>TAG</code> lorem ipsum</p>
                        <pre><code>outer list fenced block {@code TEXT} lorem ipsum
                        </code></pre>
                        </li>
                        </ol>
                        <p>end</p>"""
            );

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

        checkOutput(Output.OUT, false,
                "unknown tag");

        for (var tc : TestCase.values()) {
            out.println("Test case: " + tc);
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

    @Test
    public void testTypical(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    /// stand-in for the standard class, to avoid platform links
                    public class NullPointerException extends RuntimeException  { }
                    """,
                """
                    package p;
                    /// A class containing examples of "typical" usages.
                    public class C {
                        ///
                        /// Here is an example:
                        ///
                        ///     @Deprecated
                        ///     public class Old { }
                        ///
                        /// Here are some more examples:
                        ///
                        /// 1.  ```
                        ///     @Deprecated(forRemoval=true)
                        ///     public class VeryOld { }
                        ///     ```
                        /// 2.  ```
                        ///     public class C {
                        ///         @Override
                        ///         public boolean equals(Object other) {
                        ///             return false;
                        ///         }
                        ///         @Override
                        ///         public int hashCode() {
                        ///             return 0;
                        ///         }
                        ///     }
                        ///     ```
                        ///
                        /// @param other another instance
                        /// @throws NullPointerException if other is {@code null}
                        public C(C other) { }
                    }
                    """);

        javadoc("-d", base.resolve("api").toString(),
                "--no-platform-links",
                "--source-path", src.toString(),
                "p");
        checkExit(Exit.OK);

        checkOutput("p/C.html", true,
                """
                    <p>Here is an example:</p>
                    <pre><code>@Deprecated
                    public class Old { }
                    </code></pre>""",

                """
                    <p>Here are some more examples:</p>
                    <ol>
                    <li>
                    <pre><code>@Deprecated(forRemoval=true)
                    public class VeryOld { }
                    </code></pre>
                    </li>
                    <li>
                    <pre><code>public class C {
                        @Override
                        public boolean equals(Object other) {
                            return false;
                        }
                        @Override
                        public int hashCode() {
                            return 0;
                        }
                    }
                    </code></pre>
                    </li>
                    </ol>""",

                """
                    <dl class="notes">
                    <dt>Parameters:</dt>
                    <dd><code>other</code> - another instance</dd>
                    <dt>Throws:</dt>
                    <dd><code><a href="NullPointerException.html" title="class in p">NullPointerException</a></code> - if other is <code>null</code></dd>
                    </dl>""");
    }
}
