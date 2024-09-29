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
 * @bug 8298405
 * @summary Proper lexing of comments, especially /// comments
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.parser
 *          jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.TestRunner
 * @run main CommentTest
 */

import java.util.Objects;

import com.sun.tools.javac.parser.ScannerFactory;
import com.sun.tools.javac.parser.Tokens;
import com.sun.tools.javac.util.Context;

import toolbox.TestRunner;

public class CommentTest extends TestRunner {
    public static void main(String... args) throws Exception {
        new CommentTest().runTests();
    }

    CommentTest() {
        super(System.err);
    }

    /**
     * Control: a simple comment with no blank lines or incidental whitespace.
     */
    @Test
    public void testControl() {
        test("""
                [

                ///abc
                ///def
                ///ghi

                ]
                """, """
                abc
                def
                ghi""");
    }

    /**
     * Whitespace before the comment is completely ignored.
     */
    @Test
    public void testRaggedInitialIndent() {
        test("""
                [

                  ///abc
                      ///def
                    ///ghi

                ]
                """, """
                abc
                def
                ghi""");
    }

    /**
     * Leading blank lines are preserved.
     */
    @Test
    public void testLeadingBlankLine_1() {
        test("""
                [

                  ///
                  ///abc
                  ///def
                  ///ghi

                ]
                """, """

                abc
                def
                ghi""");
    }

    /**
     * Leading blank lines do not affect the amount of incidental whitespace.
     */
    @Test
    public void testLeadingBlankLine_2() {
        test("""
                [

                  ///
                  ///    abc
                  ///    def
                  ///    ghi

                ]
                """, """

                abc
                def
                ghi""");
    }

    /**
     * Inner blank lines are preserved.
     */
    @Test
    public void testInnerBlankLine_1() {
        test("""
                [

                  ///abc
                  ///
                  ///def
                  ///ghi

                ]
                """, """
                abc

                def
                ghi""");
    }

    /**
     * Inner blank lines do not affect the amount of incidental whitespace.
     */
    @Test
    public void testInnerBlankLine_2() {
        test("""
                [

                  ///    abc
                  ///
                  ///    def
                  ///    ghi

                ]
                """, """
                abc

                def
                ghi""");
    }

    /**
     * Inner blank lines do not affect the amount of incidental whitespace,
     * but may have whitespace removed, perhaps resulting in an empty line.
     */
    @Test
    public void testInnerBlankLine_3() {
        test("""
                [

                  ///    abc
                  ///  \s
                  ///    def
                  ///    ghi

                ]
                """, """
                abc

                def
                ghi""");
    }

    /**
     * Inner blank lines do not affect the amount of incidental whitespace,
     * but may have whitespace removed.
     */
    @Test
    public void testInnerBlankLine_4() {
        test("""
                [

                  ///    abc
                  ///          \s
                  ///    def
                  ///    ghi

                ]
                """, """
                abc
                      \s
                def
                ghi""");
    }

    /**
     * Trailing blank lines are preserved.
     */
    @Test
    public void testTrailingBlankLine_1() {
        test("""
                [

                  ///abc
                  ///def
                  ///ghi
                  ///

                ]
                """, """
                abc
                def
                ghi
                """);
    }

    /**
     * Trailing blank lines do not affect the amount of incidental whitespace.
     */
    @Test
    public void testTrailingBlankLine_2() {
        test("""
                [

                  ///    abc
                  ///    def
                  ///    ghi
                  ///

                ]
                """, """
                abc
                def
                ghi
                """);
    }

    /**
     * Small amounts of incidental whitespace are removed.
     */
    @Test
    public void testIncidental_small() {
        test("""
                [

                /// abc
                /// def
                /// ghi

                ]
                """, """
                abc
                def
                ghi""");
    }

    /**
     * Large amounts of incidental whitespace are removed.
     */
    @Test
    public void testIncidental_large() {
        test("""
                [

                ///        abc
                ///        def
                ///        ghi

                ]
                """, """
                abc
                def
                ghi""");
    }

    /**
     * Additional leading whitespace may remain after incidental whitespace is removed.
     */
    @Test
    public void testIncidental_mixed() {
        test("""
                [

                ///        abc
                ///            def
                ///          ghi

                ]
                """, """
                abc
                    def
                  ghi""");
    }

    /**
     * Tabs and spaces are treated equally, as whitespace characters.
     */
    @Test
    public void testIncidental_withTabs() {
        test("""
                [

                ///        abc
                ///\t       def
                ///\t\t      ghi

                ]
                """, """
                abc
                def
                ghi""");
    }

    /**
     * Leading tabs may remain after incidental whitespace is removed.
     */
    @Test
    public void testTabAfterIncidental() {
        test("""
                [

                ///        abc
                ///        \tdef
                ///        ghi

                ]
                """, """
                abc
                \tdef
                ghi""");
    }

    /**
     * Trailing spaces are never removed.
     */
    @Test
    public void testTrailingSpaces() {
        test("""
                [

                ///abc
                ///def    \s
                ///ghi

                ]
                """, """
                abc
                def    \s
                ghi""");

    }

    /**
     * Trailing tabs are never removed.
     */
    @Test
    public void testTrailingTabs() {
        test("""
                [

                ///abc
                ///def    \t
                ///ghi

                ]
                """, """
                abc
                def    \t
                ghi""");

    }

    /**
     * Tabs may appear in incidental whitespace, and may remain in the leading
     * whitespace after incidental whitespace is removed.
     */
    @Test
    public void testMixedTabs() {
        test("""
                [

                ///\t \t abc
                ///\t \t \tdef
                ///\t \t ghi

                ]
                """, """
                abc
                \tdef
                ghi""");

    }

    /**
     * A blank line between two /// comments is significant, and separates the two comments.
     */
    @Test
    public void testMultipleComments() {
        // When there is more than one comment, the most recent comment is first in the list
        // stored in the token.
        //
        // (For JavaDoc, only the most recent comment is used; any preceding comments are ignored.)
        test("""
                [

                ///abc

                ///ghi

                ]
                """, """
                ghi""", """
                abc""");

    }

    /**
     * An example of pseudo-typical Markdown, containing various Markdown constructs,
     * like lists and code blocks.
     */
    @Test
    public void testSampleMarkdown() {
        test("""
                [

                /// Lorem ipsum dolor sit amet,
                /// consectetur adipiscing elit.
                ///
                /// * item 1
                /// * item 2
                ///
                /// ```
                /// fenced code block
                /// ```
                ///
                /// Ut enim ad minim veniam, quis nostrud
                /// exercitation ullamco laboris nisi ut
                /// aliquip ex ea commodo consequat.
                ///
                ///     indented code block
                ///     ...
                ///
                /// Duis aute irure dolor in reprehenderit
                /// in voluptate velit esse cillum dolore
                /// eu fugiat nulla pariatur.
                ]
                """, """
                Lorem ipsum dolor sit amet,
                consectetur adipiscing elit.

                * item 1
                * item 2

                ```
                fenced code block
                ```

                Ut enim ad minim veniam, quis nostrud
                exercitation ullamco laboris nisi ut
                aliquip ex ea commodo consequat.

                    indented code block
                    ...

                Duis aute irure dolor in reprehenderit
                in voluptate velit esse cillum dolore
                eu fugiat nulla pariatur.""");

    }

    private void test(String input, String... expect) {
        var ctx = new Context();
        var sf = ScannerFactory.instance(ctx);
        var s = sf.newScanner(input, true);
        s. nextToken();
        var skipToken = s.token();
        checkEqual(skipToken.kind, Tokens.TokenKind.LBRACKET);

        s.nextToken();
        var t = s.token();
        var comments = t.comments;
        if (comments == null) {
            error("no comments");
        } else if (comments.size() == expect.length) {
            for (var i = 0; i < comments.size(); i++) {
                checkEqual(comments.get(i).getText(), expect[i]);
            }
        } else {
            error("Unexpected comments: " + comments);
            out.println("  expected " + expect.length + " comments");
            out.println("     found " + comments.size() + " comments");
        }
    }

    private void checkEqual(Object found, Object expect) {
        if (!Objects.equals(found, expect)) {
            error("mismatch");
            out.println("  expect: " + String.valueOf(expect).replace("\n", "|"));
            out.println("   found: " + String.valueOf(found).replace("\n", "|"));
        }
    }
}