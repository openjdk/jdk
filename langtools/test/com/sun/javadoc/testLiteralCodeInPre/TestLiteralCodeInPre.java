/*
 * Copyright (c) 2002, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8002387 8014636
 * @summary  Improve rendered HTML formatting for {@code}
 * @library  ../lib/
 * @build    JavadocTester TestLiteralCodeInPre
 * @run main TestLiteralCodeInPre
 */

public class TestLiteralCodeInPre extends JavadocTester {

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", OUTPUT_DIR, "-sourcepath", SRC_DIR, "-Xdoclint:none", "pkg"
    };

    //Input for string search tests.
    private static final String[][] TEST = {
        { "pkg/Test.html",
            "no_pre()</pre>\n" +
            "<div class=\"block\">abc<code>def</code>ghi</div>" },
        { "pkg/Test.html",
            "no_pre_extra_whitespace()</pre>\n" +
            "<div class=\"block\">abc<code>def  </code>ghi</div>" },
        { "pkg/Test.html",
            "in_pre()</pre>\n" +
            "<div class=\"block\"><pre> abc<code>  def  </code>ghi</pre></div>" },
        { "pkg/Test.html",
            "pre_after_text()</pre>\n" +
            "<div class=\"block\">xyz <pre> abc<code>  def  </code>ghi</pre></div>" },
        { "pkg/Test.html",
            "after_pre()</pre>\n" +
            "<div class=\"block\">xyz <pre> pqr </pre> abc<code>def  </code>ghi</div>" },
        { "pkg/Test.html",
            "back_in_pre()</pre>\n" +
            "<div class=\"block\">xyz <pre> pqr </pre> mno <pre> abc<code>  def  </code>ghi</pre></div>" },
        { "pkg/Test.html",
            "typical_usage_code()</pre>\n" +
            "<div class=\"block\">Lorem ipsum dolor sit amet, consectetur adipiscing elit.\n" +
            " Example:  <pre><code>\n" +
            "   line 1 &lt;T&gt; void m(T t) {\n" +
            "   line 2     // do something with T\n" +
            "   line 3 }\n" +
            " </code></pre>\n" +
            " and so it goes.</div>" },
        { "pkg/Test.html",
            "typical_usage_literal()</pre>\n" +
            "<div class=\"block\">Lorem ipsum dolor sit amet, consectetur adipiscing elit.\n" +
            " Example:  <pre>\n" +
            "   line 1 &lt;T&gt; void m(T t) {\n" +
            "   line 2     // do something with T\n" +
            "   line 3 }\n" +
            " </pre>\n" +
            " and so it goes.</div>" },
        { "pkg/Test.html",
            "recommended_usage_literal()</pre>\n" +
            "<div class=\"block\">Lorem ipsum dolor sit amet, consectetur adipiscing elit.\n" +
            " Example:  <pre>\n" +
            "   line 1 &lt;T&gt; void m(T t) {\n" +
            "   line 2     // do something with T\n" +
            "   line 3 } </pre>\n" +
            " and so it goes.</div>" }
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestLiteralCodeInPre tester = new TestLiteralCodeInPre();
        tester.run(ARGS, TEST, NO_TEST);
        tester.printSummary();
    }
}
