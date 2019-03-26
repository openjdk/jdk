/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8173425 8186332 8182765 8196202
 * @summary  tests for the summary tag behavior
 * @library  ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    javadoc.tester.*
 * @run main TestSummaryTag
 */

import javadoc.tester.JavadocTester;

public class TestSummaryTag extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestSummaryTag tester = new TestSummaryTag();
        tester.runTests();
    }

    @Test
    public void test1() {
        javadoc("-d", "out1",
                "-sourcepath", testSrc,
                "p1");
        checkExit(Exit.OK);

        checkOutput("index-all.html", true,
            "<dl>\n"
            + "<dt><span class=\"memberNameLink\"><a href=\"p1/A.html#m()\">m()"
            + "</a></span> - Method in class p1.<a href=\"p1/A.html\" title=\"class in p1\">A</a></dt>\n"
            + "<dd>\n"
            + "<div class=\"block\">First sentence</div>\n"
            + "</dd>\n"
            + "<dt><span class=\"memberNameLink\"><a href=\"p1/B.html#m()\">m()"
            + "</a></span> - Method in class p1.<a href=\"p1/B.html\" title=\"class in p1\">B</a></dt>\n"
            + "<dd>\n"
            + "<div class=\"block\">First sentence</div>\n"
            + "</dd>\n"
            + "<dt><span class=\"memberNameLink\"><a href=\"p1/A.html#m1()\">m1()"
            + "</a></span> - Method in class p1.<a href=\"p1/A.html\" title=\"class in p1\">A</a></dt>\n"
            + "<dd>\n"
            + "<div class=\"block\"> First sentence </div>\n"
            + "</dd>\n"
            + "<dt><span class=\"memberNameLink\"><a href=\"p1/A.html#m2()\">m2()"
            + "</a></span> - Method in class p1.<a href=\"p1/A.html\" title=\"class in p1\">A</a></dt>\n"
            + "<dd>\n"
            + "<div class=\"block\">Some html &lt;foo&gt; &nbsp; codes</div>\n"
            + "</dd>\n"
            + "<dt><span class=\"memberNameLink\"><a href=\"p1/A.html#m3()\">m3()"
            + "</a></span> - Method in class p1.<a href=\"p1/A.html\" title=\"class in p1\">A</a></dt>\n"
            + "<dd>\n"
            + "<div class=\"block\">First sentence </div>\n"
            + "</dd>\n"
            + "<dt><span class=\"memberNameLink\"><a href=\"p1/A.html#m4()\">m4()"
            + "</a></span> - Method in class p1.<a href=\"p1/A.html\" title=\"class in p1\">A</a></dt>\n"
            + "<dd>\n"
            + "<div class=\"block\">First sentence i.e. the first sentence</div>\n"
            + "</dd>\n"
            + "</dl>\n",
            "<div class=\"block\">The first... line</div>\n"
        );

        // make sure the second @summary's content is displayed correctly
        checkOutput("p1/A.html", true,
             "<li class=\"blockList\">\n"
             + "<h3>m3</h3>\n"
             + "<pre class=\"methodSignature\">public&nbsp;void&nbsp;m3()</pre>\n"
             + "<div class=\"block\">First sentence  some text maybe second sentence.</div>\n"
             + "</li>\n"
        );

        checkOutput("p1/package-summary.html", true,
                "<div class=\"block\">The first... line second from ...</div>");
    }

    @Test
    public void test2() {
        javadoc("-d", "out2",
                "-sourcepath", testSrc,
                "p2");
        checkExit(Exit.OK);

        checkOutput(Output.OUT, true, "package.html:3: warning: invalid use of @summary");

        checkOutput("index-all.html", true, "<div class=\"block\">foo bar</div>\n");

        checkOutput("p2/package-summary.html", true, "<div class=\"block\">foo bar baz.</div>\n");
    }

    @Test
    public void test3() {
        javadoc("-d", "out3",
                "--frames",
                "-sourcepath", testSrc,
                "-overview", testSrc("p3/overview.html"),
                "p3");
        checkExit(Exit.OK);

        checkOutput("overview-summary.html", true,
                "<div class=\"block\">The first... line second from ...</div>");
    }
}
