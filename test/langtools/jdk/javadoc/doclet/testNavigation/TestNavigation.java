/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4131628 4664607 7025314 8023700 7198273 8025633 8026567 8081854 8150188 8151743 8196027 8182765
 * @summary  Make sure the Next/Prev Class links iterate through all types.
 *           Make sure the navagation is 2 columns, not 3.
 * @author   jamieh
 * @library  ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
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
        javadoc("-d", "out",
                "-overview", testSrc("overview.html"),
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.OK);

        checkOutput("pkg/A.html", true,
                "<ul class=\"navList\" title=\"Navigation\">\n" +
                "<li><a href=\"../overview-summary.html\">Overview</a></li>");

        checkOutput("pkg/C.html", true,
                "<ul class=\"navList\" title=\"Navigation\">\n" +
                "<li><a href=\"../overview-summary.html\">Overview</a></li>");

        checkOutput("pkg/E.html", true,
                "<ul class=\"navList\" title=\"Navigation\">\n" +
                "<li><a href=\"../overview-summary.html\">Overview</a></li>");

        checkOutput("pkg/I.html", true,
                // Test for 4664607
                "<div class=\"skipNav\"><a href=\"#skip.navbar.top\" title=\"Skip navigation links\">Skip navigation links</a></div>\n"
                + "<a id=\"navbar.top.firstrow\">\n"
                + "<!--   -->\n"
                + "</a>",
                "<li><a href=\"../overview-summary.html\">Overview</a></li>");

        // Remaining tests check for additional padding to offset the fixed navigation bar.
        checkOutput("pkg/A.html", true,
                "<!-- ========= END OF TOP NAVBAR ========= -->\n"
                + "</div>\n"
                + "<div class=\"navPadding\">&nbsp;</div>\n"
                + "<script type=\"text/javascript\"><!--\n"
                + "$('.navPadding').css('padding-top', $('.fixedNav').css(\"height\"));\n"
                + "//-->\n"
                + "</script>\n"
                + "</nav>\n"
                + "</header>\n"
                + "<!-- ======== START OF CLASS DATA ======== -->");

        checkOutput("pkg/package-summary.html", true,
                "<!-- ========= END OF TOP NAVBAR ========= -->\n"
                + "</div>\n"
                + "<div class=\"navPadding\">&nbsp;</div>\n"
                + "<script type=\"text/javascript\"><!--\n"
                + "$('.navPadding').css('padding-top', $('.fixedNav').css(\"height\"));\n"
                + "//-->\n"
                + "</script>\n"
                + "</nav>\n"
                + "</header>\n"
                + "<main role=\"main\">\n"
                + "<div class=\"header\">");
    }

    @Test
    void test_html4() {
        javadoc("-d", "out-html4",
                "-html4",
                "-overview", testSrc("overview.html"),
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.OK);

        checkOutput("pkg/I.html", true,
                // Test for 4664607
                "<div class=\"skipNav\"><a href=\"#skip.navbar.top\" title=\"Skip navigation links\">Skip navigation links</a></div>\n"
                + "<a name=\"navbar.top.firstrow\">\n"
                + "<!--   -->\n"
                + "</a>");

        // Remaining tests check for additional padding to offset the fixed navigation bar.
        checkOutput("pkg/A.html", true,
                "<!-- ========= END OF TOP NAVBAR ========= -->\n"
                + "</div>\n"
                + "<div class=\"navPadding\">&nbsp;</div>\n"
                + "<script type=\"text/javascript\"><!--\n"
                + "$('.navPadding').css('padding-top', $('.fixedNav').css(\"height\"));\n"
                + "//-->\n"
                + "</script>\n"
                + "<!-- ======== START OF CLASS DATA ======== -->");

        checkOutput("pkg/package-summary.html", true,
                "<!-- ========= END OF TOP NAVBAR ========= -->\n"
                + "</div>\n"
                + "<div class=\"navPadding\">&nbsp;</div>\n"
                + "<script type=\"text/javascript\"><!--\n"
                + "$('.navPadding').css('padding-top', $('.fixedNav').css(\"height\"));\n"
                + "//-->\n"
                + "</script>\n"
                + "<div class=\"header\">");
    }

    // Test for checking additional padding to offset the fixed navigation bar in HTML5.
    @Test
    void test1() {
        javadoc("-d", "out-1",
                "-html5",
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.OK);

        checkOutput("pkg/A.html", true,
                "<!-- ========= END OF TOP NAVBAR ========= -->\n"
                + "</div>\n"
                + "<div class=\"navPadding\">&nbsp;</div>\n"
                + "<script type=\"text/javascript\"><!--\n"
                + "$('.navPadding').css('padding-top', $('.fixedNav').css(\"height\"));\n"
                + "//-->\n"
                + "</script>\n"
                + "</nav>\n"
                + "</header>\n"
                + "<!-- ======== START OF CLASS DATA ======== -->");

        checkOutput("pkg/package-summary.html", true,
                "<!-- ========= END OF TOP NAVBAR ========= -->\n"
                + "</div>\n"
                + "<div class=\"navPadding\">&nbsp;</div>\n"
                + "<script type=\"text/javascript\"><!--\n"
                + "$('.navPadding').css('padding-top', $('.fixedNav').css(\"height\"));\n"
                + "//-->\n"
                + "</script>\n"
                + "</nav>");
    }

    // Test to make sure that no extra padding for nav bar gets generated if -nonavbar is specified for HTML4.
    @Test
    void test2() {
        javadoc("-d", "out-2",
                "-nonavbar",
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.OK);

        checkOutput("pkg/A.html", false,
                "<!-- ========= END OF TOP NAVBAR ========= -->\n"
                + "</div>\n"
                + "<div class=\"navPadding\">&nbsp;</div>\n"
                + "<script type=\"text/javascript\"><!--\n"
                + "$('.navPadding').css('padding-top', $('.fixedNav').css(\"height\"));\n"
                + "//-->\n"
                + "</script>\n"
                + "<!-- ======== START OF CLASS DATA ======== -->");

        checkOutput("pkg/package-summary.html", false,
                "<!-- ========= END OF TOP NAVBAR ========= -->\n"
                + "</div>\n"
                + "<div class=\"navPadding\">&nbsp;</div>\n"
                + "<script type=\"text/javascript\"><!--\n"
                + "$('.navPadding').css('padding-top', $('.fixedNav').css(\"height\"));\n"
                + "//-->\n"
                + "</script>\n"
                + "<div class=\"header\">");
    }

    // Test to make sure that no extra padding for nav bar gets generated if -nonavbar is specified for HTML5.
    @Test
    void test3() {
        javadoc("-d", "out-3",
                "-html5",
                "-nonavbar",
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.OK);

        checkOutput("pkg/A.html", false,
                "<!-- ========= END OF TOP NAVBAR ========= -->\n"
                + "</div>\n"
                + "<div class=\"navPadding\">&nbsp;</div>\n"
                + "<script type=\"text/javascript\"><!--\n"
                + "$('.navPadding').css('padding-top', $('.fixedNav').css(\"height\"));\n"
                + "//-->\n"
                + "</script>\n"
                + "</nav>\n"
                + "</header>\n"
                + "<!-- ======== START OF CLASS DATA ======== -->");

        checkOutput("pkg/package-summary.html", false,
                "<!-- ========= END OF TOP NAVBAR ========= -->\n"
                + "</div>\n"
                + "<div class=\"navPadding\">&nbsp;</div>\n"
                + "<script type=\"text/javascript\"><!--\n"
                + "$('.navPadding').css('padding-top', $('.fixedNav').css(\"height\"));\n"
                + "//-->\n"
                + "</script>\n"
                + "</nav>");
    }
}
