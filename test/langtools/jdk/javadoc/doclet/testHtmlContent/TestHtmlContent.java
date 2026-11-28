/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8397227
 * @summary Test conversion of javadoc content classes to string
 * @library ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.doclets.formats.html.markup
 *          jdk.javadoc/jdk.javadoc.internal.doclets.toolkit.util
 *          jdk.javadoc/jdk.javadoc.internal.html
 *          jdk.javadoc/jdk.javadoc.internal.tool
 * @build javadoc.tester.*
 * @run main TestHtmlContent
 */

import jdk.javadoc.internal.html.*;

import javadoc.tester.JavadocTester;

import java.util.Objects;

public class TestHtmlContent extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestHtmlContent();
        tester.runTests();
    }

    final static String TEXT =           "Text: <img src=foo> & &lt; &amp;\n";
    final static String TEXT_TO_STRING = "Text: &lt;img src=foo&gt; &amp; &amp;lt; &amp;amp;\n";
    final static String TEXT_TO_PLAIN =  TEXT;

    final static String TEXT_BUILDER =           "TextBuilder: <img src=foo> & &lt; &amp;\n";
    final static String TEXT_BUILDER_TO_STRING = "TextBuilder: &lt;img src=foo&gt; &amp; &amp;lt; &amp;amp;\n";
    final static String TEXT_BUILDER_TO_PLAIN  = TEXT_BUILDER;

    final static String RAW_HTML =           "RawHtml: <img src=foo> & &lt; &amp;\n";
    final static String RAW_HTML_TO_STRING = RAW_HTML;
    final static String RAW_HTML_TO_PLAIN  = "RawHtml:  & < &\n";

    final static String COMMENT =           "Comment: <img src=foo> & &lt; &amp;\n";
    final static String COMMENT_TO_STRING = "<!-- Comment: <img src=foo> & &lt; &amp;\n -->\n";
    final static String COMMENT_TO_PLAIN =  "";

    final static String ENTITY           = "lt";
    final static String ENTITY_TO_STRING = "&" + ENTITY + ";";
    final static String ENTITY_TO_PLAIN  = "<";

    final static String CONTENT_BUILDER_TO_STRING =
            TEXT_TO_STRING
            + TEXT_BUILDER_TO_STRING
            + RAW_HTML_TO_STRING
            + COMMENT_TO_STRING
            + ENTITY_TO_STRING;
    final static String CONTENT_BUILDER_TO_PLAIN =
            TEXT_TO_PLAIN
            + TEXT_BUILDER_TO_PLAIN
            + RAW_HTML_TO_PLAIN
            + COMMENT_TO_PLAIN
            + ENTITY_TO_PLAIN;

    final static String HTML_TREE_TO_STRING = "<p id=\"paragraph1\">" + CONTENT_BUILDER_TO_STRING + "</p>\n";
    final static String HTML_TREE_TO_PLAIN = CONTENT_BUILDER_TO_PLAIN;

    Text text = Text.of(TEXT);
    TextBuilder textBuilder = new TextBuilder(TEXT_BUILDER);
    RawHtml rawHtml = RawHtml.of(RAW_HTML);
    Comment comment = new Comment(COMMENT);
    Entity entity = Entity.of(ENTITY);
    ContentBuilder contentBuilder = new ContentBuilder()
            .add(text)
            .add(textBuilder)
            .add(rawHtml)
            .add(comment)
            .add(entity);
    HtmlTree html = HtmlTree.P(contentBuilder).setId(HtmlId.of("paragraph1"));

    @Test
    public void testContentToString() {
        check("Text.toString()", text.toString(), TEXT_TO_STRING);
        check("TextBuilder.toString()", textBuilder.toString(), TEXT_BUILDER_TO_STRING);
        check("RawHtml.toString()", rawHtml.toString(), RAW_HTML_TO_STRING);
        check("Comment.toString()", comment.toString(), COMMENT_TO_STRING);
        check("ContentBuilder.toString()", contentBuilder.toString(), CONTENT_BUILDER_TO_STRING);
        check("HtmlTree.toString()", html.toString(), HTML_TREE_TO_STRING);
    }

    @Test
    public void testContentToPlainText() {
        check("Text.toPlainText()", text.toPlainText(), TEXT_TO_PLAIN);
        check("TextBuilder.toPlainText()", textBuilder.toPlainText(), TEXT_BUILDER_TO_PLAIN);
        check("RawHtml.toPlainText()", rawHtml.toPlainText(), RAW_HTML_TO_PLAIN);
        check("Comment.toPlainText()", comment.toPlainText(), COMMENT_TO_PLAIN);
        check("ContentBuilder.toPlainText()", contentBuilder.toPlainText(), CONTENT_BUILDER_TO_PLAIN);
        check("HtmlTree.toPlainText()", html.toPlainText(), HTML_TREE_TO_PLAIN);
    }

    void check(String name, String result, String expected) {
        checking(name);
        if (Objects.equals(result, expected)) {
            passed("Strings are equal.");
        } else {
            failed("Strings are different." +
                    "\nActual:\n" + format(result) +
                    "\nExpected:\n" + format(expected));
        }
    }

    String format(String text) {
        return text == null ? "null" : text;
    }
}
