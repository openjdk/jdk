/*
 * Copyright (c) 2003, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4131628 4664607 7025314 8023700 7198273 8025633 8026567 8081854
 * @summary  Make sure the Next/Prev Class links iterate through all types.
 *           Make sure the navagation is 2 columns, not 3.
 * @author   jamieh
 * @library  ../lib
 * @modules jdk.javadoc
 * @build    JavadocTester
 * @run main TestNavigation
 */

public class TestNavigation extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestNavigation tester = new TestNavigation();
        tester.runTests();
    }

    @Test
    void test() {
        javadoc("-d", "out", "-overview", testSrc("overview.html"),
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.OK);

        checkOutput("pkg/A.html", true,
                "<li>Prev&nbsp;Class</li>",
                "<a href=\"../pkg/C.html\" title=\"class in pkg\"><span class=\"typeNameLink\">Next&nbsp;Class</span></a>",
                "<li><a href=\"../overview-summary.html\">Overview</a></li>");

        checkOutput("pkg/C.html", true,
                "<a href=\"../pkg/A.html\" title=\"annotation in pkg\"><span class=\"typeNameLink\">Prev&nbsp;Class</span></a>",
                "<a href=\"../pkg/E.html\" title=\"enum in pkg\"><span class=\"typeNameLink\">Next&nbsp;Class</span></a>",
                "<li><a href=\"../overview-summary.html\">Overview</a></li>");

        checkOutput("pkg/E.html", true,
                "<a href=\"../pkg/C.html\" title=\"class in pkg\"><span class=\"typeNameLink\">Prev&nbsp;Class</span></a>",
                "<a href=\"../pkg/I.html\" title=\"interface in pkg\"><span class=\"typeNameLink\">Next&nbsp;Class</span></a>",
                "<li><a href=\"../overview-summary.html\">Overview</a></li>");

        checkOutput("pkg/I.html", true,
                "<a href=\"../pkg/E.html\" title=\"enum in pkg\"><span class=\"typeNameLink\">Prev&nbsp;Class</span></a>",
                "<li>Next&nbsp;Class</li>",
                // Test for 4664607
                "<div class=\"skipNav\"><a href=\"#skip.navbar.top\" title=\"Skip navigation links\">Skip navigation links</a></div>\n"
                + "<a name=\"navbar.top.firstrow\">\n"
                + "<!--   -->\n"
                + "</a>",
                "<li><a href=\"../overview-summary.html\">Overview</a></li>");
    }
}
