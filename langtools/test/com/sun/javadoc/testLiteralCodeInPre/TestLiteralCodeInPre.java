/*
 * Copyright (c) 2002, 2013, Oracle and/or its affiliates. All rights reserved.
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

    //Test information.
    private static final String BUG_ID = "8002387-8014636";
    private static final String OUTPUT_DIR = BUG_ID;

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", OUTPUT_DIR, "-sourcepath", SRC_DIR, "-Xdoclint:none", "pkg"
    };

    //Input for string search tests.
    private static final String[][] TEST = {
        { BUG_ID + FS + "pkg" + FS + "Test.html",
            "no_pre()</pre>" + NL +
            "<div class=\"block\">abc<code>def</code>ghi</div>" },
        { BUG_ID + FS + "pkg" + FS + "Test.html",
            "no_pre_extra_whitespace()</pre>" + NL +
            "<div class=\"block\">abc<code>def  </code>ghi</div>" },
        { BUG_ID + FS + "pkg" + FS + "Test.html",
            "in_pre()</pre>" + NL +
            "<div class=\"block\"><pre> abc<code>  def  </code>ghi</pre></div>" },
        { BUG_ID + FS + "pkg" + FS + "Test.html",
            "pre_after_text()</pre>" + NL +
            "<div class=\"block\">xyz <pre> abc<code>  def  </code>ghi</pre></div>" },
        { BUG_ID + FS + "pkg" + FS + "Test.html",
            "after_pre()</pre>" + NL +
            "<div class=\"block\">xyz <pre> pqr </pre> abc<code>def  </code>ghi</div>" },
        { BUG_ID + FS + "pkg" + FS + "Test.html",
            "back_in_pre()</pre>" + NL +
            "<div class=\"block\">xyz <pre> pqr </pre> mno <pre> abc<code>  def  </code>ghi</pre></div>" },
        { BUG_ID + FS + "pkg" + FS + "Test.html",
            "typical_usage_code()</pre>" + NL +
            "<div class=\"block\">Lorem ipsum dolor sit amet, consectetur adipiscing elit." + NL +
            " Example:  <pre><code>" + NL +
            "   line 1 &lt;T&gt; void m(T t) {" + NL +
            "   line 2     // do something with T" + NL +
            "   line 3 }" + NL +
            " </code></pre>" + NL +
            " and so it goes.</div>" },
        { BUG_ID + FS + "pkg" + FS + "Test.html",
            "typical_usage_literal()</pre>" + NL +
            "<div class=\"block\">Lorem ipsum dolor sit amet, consectetur adipiscing elit." + NL +
            " Example:  <pre>" + NL +
            "   line 1 &lt;T&gt; void m(T t) {" + NL +
            "   line 2     // do something with T" + NL +
            "   line 3 }" + NL +
            " </pre>" + NL +
            " and so it goes.</div>" },
        { BUG_ID + FS + "pkg" + FS + "Test.html",
            "recommended_usage_literal()</pre>" + NL +
            "<div class=\"block\">Lorem ipsum dolor sit amet, consectetur adipiscing elit." + NL +
            " Example:  <pre>" + NL +
            "   line 1 &lt;T&gt; void m(T t) {" + NL +
            "   line 2     // do something with T" + NL +
            "   line 3 } </pre>" + NL +
            " and so it goes.</div>" }
    };

    private static final String[][] NEGATED_TEST = NO_TEST;

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestLiteralCodeInPre tester = new TestLiteralCodeInPre();
        run(tester, ARGS, TEST, NEGATED_TEST);
        tester.printSummary();
    }

    /**
     * {@inheritDoc}
     */
    public String getBugId() {
        return BUG_ID;
    }

    /**
     * {@inheritDoc}
     */
    public String getBugName() {
        return getClass().getName();
    }
}
