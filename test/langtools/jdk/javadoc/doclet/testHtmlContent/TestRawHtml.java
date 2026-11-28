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
 * @summary Test implementation of javadoc RawHtml content methods
 * @library ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.doclets.formats.html.markup
 *          jdk.javadoc/jdk.javadoc.internal.doclets.toolkit.util
 *          jdk.javadoc/jdk.javadoc.internal.html
 *          jdk.javadoc/jdk.javadoc.internal.tool
 * @build javadoc.tester.*
 * @run main TestRawHtml
 */

import jdk.javadoc.internal.html.*;

import javadoc.tester.JavadocTester;

import java.util.Objects;

public class TestRawHtml extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestRawHtml();
        tester.runTests();
    }

    @Test
    public void testPlainText() {
        testPlainText("inline tags", """
                before <i>italic <b>bold</b></I> after
                """, """
                before italic bold after
                """);
        testPlainText("quoted attributes", """
                before <a href="https://example.org" title='2 > 1 "quoted"' id=" <'>">link</a> after
                """, """
                before link after
                """);
        testPlainText("unquoted attribute", """
                before <span id=a&amp;b>inside</span> after
                """, """
                before inside after
                """);
        testPlainText("line breaks", """
                one<br>two<BR>three<br title=">">four<br/>five
                """, """
                one two three four five
                """);
        testPlainText("named references", """
                &amp;&lt;&gt; &quot;&excl;
                """, """
                &<> "!
                """);
        testPlainText("numeric references", """
                &#65;&#x42;&#X43; &#128512;&#x1F600;
                """, """
                ABC \ud83d\ude00\ud83d\ude00
                """);
        testPlainText("multi codepoint references", """
                &NotEqualTilde;
                """, """
                \u2242\u0338
                """);
        testPlainText("escaped references", """
                &amp;lt;&amp;amp;&amp;gt;
                """, """
                &lt;&amp;&gt;
                """);
        testPlainText("unknown references", """
                &unknown; &#x110000; &#xD800;
                """, """
                &unknown; &#x110000; &#xD800;
                """);
        testPlainText("consecutive ampersands", """
                &&amp;
                """, """
                &&
                """);
        testPlainText("comments", """
                <!---->before <!-- comment --><!-- 2 > 1 &amp; <p> -->after
                """, """
                before after
                """);
        testPlainText("whitespace", """
                <pre>    void foo()
                    throws\tException;
                """, """
                    void foo()
                    throws\tException;
                """);
        testPlainText("unclosed tag", """
                before <span title="
                """, """
                before <span title="
                """);
        testPlainText("unclosed comment", """
                before <!-- comment
                """, """
                before <!-- comment
                """);
        testPlainText("unclosed reference", """
                before &amp
                """, """
                before &amp
                """);
        testPlainText("long unquoted attribute", """
                before <span title=%s>inside</span> after
                """.formatted("x".repeat(100_000)), """
                before inside after
                """);
        testPlainText("long quoted attribute", """
                before<span  id="%s" title="abc"> inside </span  >after
                """.formatted(" x ".repeat(100_000)), """
                before inside after
                """);
    }

    private void testPlainText(String name, String html, String expected) {
        checking(name + " toPlainText()");
        RawHtml rawHtml = RawHtml.of(html);
        String actual = rawHtml.toPlainText();
        if (Objects.equals(actual, expected)) {
            passed("Strings are equal.");
        } else {
            failed("Strings differ:"
                    + "\nActual: " + actual
                    + "\nExpected: " + expected);
        }

        checking(name + " charCount()");
        int count = rawHtml.charCount();
        if (expected.length() == count) {
            passed("charCount() return value is correct.");
        } else {
            failed("charCount() returned " + count + ", expected " + expected.length());
        }
    }
}
