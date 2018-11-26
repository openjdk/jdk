/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      6786688 8008164 8162363 8169819 8183037 8182765 8184205
 * @summary  HTML tables should have table summary, caption and table headers.
 * @author   Bhavesh Patel
 * @library  ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    JavadocTester
 * @run main TestHtmlTableTags
 */

public class TestHtmlTableTags extends JavadocTester {

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {

    };


    public static void main(String... args) throws Exception {
        TestHtmlTableTags tester = new TestHtmlTableTags();
        tester.runTests();
    }

    @Test
    void test() {
        javadoc("-d", "out",
                "-sourcepath", testSrc,
                "-use",
                "--frames",
                "pkg1", "pkg2");
        checkExit(Exit.OK);

        checkHtmlTableTag();
        checkHtmlTableCaptions();
        checkHtmlTableHeaders();
    }

    @Test
    void test_html4() {
        javadoc("-d", "out-html4",
                "-html4",
                "-sourcepath", testSrc,
                "-use",
                "--frames",
                "pkg1", "pkg2");
        checkExit(Exit.OK);

        checkHtmlTableSummaries();
    }

    /*
     * Tests for validating table tag for HTML tables
     */
    void checkHtmlTableTag() {
        //Package summary
        checkOutput("pkg1/package-summary.html", true,
                "<div class=\"typeSummary\">\n<table>",
                "<div class=\"typeSummary\">\n<table>");

        checkOutput("pkg2/package-summary.html", true,
                "<div class=\"typeSummary\">\n<table>",
                "<div class=\"typeSummary\">\n<table>");

        // Class documentation
        checkOutput("pkg1/C1.html", true,
                "<div class=\"memberSummary\">\n<table>",
                "<div class=\"memberSummary\">\n<table>");

        checkOutput("pkg2/C2.html", true,
                "<div class=\"memberSummary\">\n<table>",
                "<div class=\"memberSummary\">\n<table>");

        checkOutput("pkg2/C2.ModalExclusionType.html", true,
                "<div class=\"memberSummary\">\n<table>");

        checkOutput("pkg2/C3.html", true,
                "<div class=\"memberSummary\">\n<table>");

        checkOutput("pkg2/C4.html", true,
                "<div class=\"memberSummary\">\n<table>");

        // Class use documentation
        checkOutput("pkg1/class-use/I1.html", true,
                "<div class=\"useSummary\">\n<table>");

        checkOutput("pkg1/class-use/C1.html", true,
                "<div class=\"useSummary\">\n<table>",
                "<div class=\"useSummary\">\n<table>");

        checkOutput("pkg2/class-use/C2.html", true,
                "<div class=\"useSummary\">\n<table>",
                "<div class=\"useSummary\">\n<table>");

        checkOutput("pkg2/class-use/C2.ModalExclusionType.html", true,
                "<div class=\"useSummary\">\n<table>");

        checkOutput("pkg2/class-use/C2.ModalExclusionType.html", true,
                "<div class=\"useSummary\">\n<table>");

        // Package use documentation
        checkOutput("pkg1/package-use.html", true,
                "<div class=\"useSummary\">\n<table>",
                "<div class=\"useSummary\">\n<table>");

        checkOutput("pkg2/package-use.html", true,
                "<div class=\"useSummary\">\n<table>",
                "<div class=\"useSummary\">\n<table>");

        // Deprecated
        checkOutput("deprecated-list.html", true,
                "<div class=\"deprecatedSummary\">\n<table>",
                "<div class=\"deprecatedSummary\">\n<table>");

        // Constant values
        checkOutput("constant-values.html", true,
                "<div class=\"constantsSummary\">\n<table>");

        // Overview Summary
        checkOutput("overview-summary.html", true,
                "<div class=\"overviewSummary\">\n<table>");
    }

    /*
     * Tests for validating summary for HTML tables
     */
    void checkHtmlTableSummaries() {
        //Package summary
        checkOutput("pkg1/package-summary.html", true,
                "<div class=\"typeSummary\">\n"
                + "<table summary=\"Class Summary table, "
                + "listing classes, and an explanation\">",
                "<div class=\"typeSummary\">\n"
                + "<table summary=\"Interface Summary table, "
                + "listing interfaces, and an explanation\">");

        checkOutput("pkg2/package-summary.html", true,
                "<div class=\"typeSummary\">\n"
                + "<table summary=\"Enum Summary table, "
                + "listing enums, and an explanation\">",
                "<div class=\"typeSummary\">\n"
                + "<table summary=\"Annotation Types Summary table, "
                + "listing annotation types, and an explanation\">");

        // Class documentation
        checkOutput("pkg1/C1.html", true,
                "<div class=\"memberSummary\">\n"
                + "<table summary=\"Field Summary table, listing fields, "
                + "and an explanation\">",
                "<div class=\"memberSummary\">\n",
                "<table summary=\"Method Summary table, listing methods, "
                + "and an explanation\" aria-labelledby=\"t0\">");

        checkOutput("pkg2/C2.html", true,
                "<div class=\"memberSummary\">\n"
                + "<table summary=\"Nested Class Summary table, listing "
                + "nested classes, and an explanation\">",
                "<div class=\"memberSummary\">\n"
                + "<table summary=\"Constructor Summary table, listing "
                + "constructors, and an explanation\">");

        checkOutput("pkg2/C2.ModalExclusionType.html", true,
                "<div class=\"memberSummary\">\n"
                + "<table summary=\"Enum Constant Summary table, listing "
                + "enum constants, and an explanation\">");

        checkOutput("pkg2/C3.html", true,
                "<div class=\"memberSummary\">\n"
                + "<table summary=\"Required Element Summary table, "
                + "listing required elements, and an explanation\">");

        checkOutput("pkg2/C4.html", true,
                "<div class=\"memberSummary\">\n"
                + "<table summary=\"Optional Element Summary table, "
                + "listing optional elements, and an explanation\">");

        // Class use documentation
        checkOutput("pkg1/class-use/I1.html", true,
                "<div class=\"useSummary\">\n"
                + "<table summary=\"Use table, listing packages, and an explanation\">");

        checkOutput("pkg1/class-use/C1.html", true,
                "<div class=\"useSummary\">\n"
                + "<table summary=\"Use table, listing fields, and an explanation\">",
                "<div class=\"useSummary\">\n"
                + "<table summary=\"Use table, listing methods, and an explanation\">");

        checkOutput("pkg2/class-use/C2.html", true,
                "<div class=\"useSummary\">\n"
                + "<table summary=\"Use table, listing fields, and an explanation\">",
                "<div class=\"useSummary\">\n"
                + "<table summary=\"Use table, listing methods, and an explanation\">");

        checkOutput("pkg2/class-use/C2.ModalExclusionType.html", true,
                "<div class=\"useSummary\">\n"
                + "<table summary=\"Use table, listing packages, and an explanation\">");

        checkOutput("pkg2/class-use/C2.ModalExclusionType.html", true,
                "<div class=\"useSummary\">\n"
                + "<table summary=\"Use table, listing methods, and an explanation\">");

        // Package use documentation
        checkOutput("pkg1/package-use.html", true,
                "<div class=\"useSummary\">\n"
                + "<table summary=\"Use table, listing packages, and an explanation\">",
                "<div class=\"useSummary\">\n"
                + "<table summary=\"Use table, listing classes, and an explanation\">");

        checkOutput("pkg2/package-use.html", true,
                "<div class=\"useSummary\">\n"
                + "<table summary=\"Use table, listing packages, and an explanation\">",
                "<div class=\"useSummary\">\n"
                + "<table summary=\"Use table, listing classes, and an explanation\">");

        // Deprecated
        checkOutput("deprecated-list.html", true,
                "<div class=\"deprecatedSummary\">\n"
                + "<table summary=\"Fields table, listing fields, "
                + "and an explanation\">",
                "<div class=\"deprecatedSummary\">\n"
                + "<table summary=\"Methods table, listing methods, "
                + "and an explanation\">");

        // Constant values
        checkOutput("constant-values.html", true,
                "<div class=\"constantsSummary\">\n"
                + "<table summary=\"Constant Field Values table, listing "
                + "constant fields, and values\">");

        // Overview Summary
        checkOutput("overview-summary.html", true,
                "<div class=\"overviewSummary\">\n"
                + "<table summary=\"Package Summary table, listing packages, and an explanation\">");
    }

    /*
     * Tests for validating caption for HTML tables
     */
    void checkHtmlTableCaptions() {
        //Package summary
        checkOutput("pkg1/package-summary.html", true,
                "<caption><span>Class Summary</span><span class=\"tabEnd\">"
                + "&nbsp;</span></caption>",
                "<caption><span>Interface Summary</span><span class=\"tabEnd\">"
                + "&nbsp;</span></caption>");

        checkOutput("pkg2/package-summary.html", true,
                "<caption><span>Enum Summary</span><span class=\"tabEnd\">"
                + "&nbsp;</span></caption>",
                "<caption><span>Annotation Types Summary</span><span class=\"tabEnd\">"
                + "&nbsp;</span></caption>");

        // Class documentation
        checkOutput("pkg1/C1.html", true,
                "<caption><span>Fields</span><span class=\"tabEnd\">&nbsp;</span></caption>",
                "<div role=\"tablist\" aria-orientation=\"horizontal\"><button role=\"tab\""
                + " aria-selected=\"true\" aria-controls=\"memberSummary_tabpanel\" tabindex=\"0\""
                + " onkeydown=\"switchTab(event)\" id=\"t0\" class=\"activeTableTab\">All Methods"
                + "</button><button role=\"tab\" aria-selected=\"false\""
                + " aria-controls=\"memberSummary_tabpanel\" tabindex=\"-1\""
                + " onkeydown=\"switchTab(event)\" id=\"t2\" class=\"tableTab\" onclick=\"show(2);\">"
                + "Instance Methods</button><button role=\"tab\" aria-selected=\"false\""
                + " aria-controls=\"memberSummary_tabpanel\" tabindex=\"-1\""
                + " onkeydown=\"switchTab(event)\" id=\"t4\" class=\"tableTab\" onclick=\"show(8);\">"
                + "Concrete Methods</button><button role=\"tab\" aria-selected=\"false\""
                + " aria-controls=\"memberSummary_tabpanel\" tabindex=\"-1\" onkeydown=\"switchTab(event)\""
                + " id=\"t6\" class=\"tableTab\" onclick=\"show(32);\">Deprecated Methods</button></div>\n");

        checkOutput("pkg2/C2.html", true,
                "<caption><span>Nested Classes</span><span class=\"tabEnd\">&nbsp;</span></caption>",
                "<caption><span>Constructors</span><span class=\"tabEnd\">&nbsp;</span></caption>");

        checkOutput("pkg2/C2.ModalExclusionType.html", true,
                "<caption><span>Enum Constants</span><span class=\"tabEnd\">&nbsp;</span></caption>");

        checkOutput("pkg2/C3.html", true,
                "<caption><span>Required Elements</span><span class=\"tabEnd\">&nbsp;"
                + "</span></caption>");

        checkOutput("pkg2/C4.html", true,
                "<caption><span>Optional Elements</span><span class=\"tabEnd\">&nbsp;"
                + "</span></caption>");

        // Class use documentation
        checkOutput("pkg1/class-use/I1.html", true,
                "<caption><span>Packages that use <a href=\"../I1.html\" "
                + "title=\"interface in pkg1\">I1</a></span><span class=\"tabEnd\">"
                + "&nbsp;</span></caption>");

        checkOutput("pkg1/class-use/C1.html", true,
                "<caption><span>Fields in <a href=\"../../pkg2/package-summary.html\">"
                + "pkg2</a> declared as <a href=\"../C1.html\" "
                + "title=\"class in pkg1\">C1</a></span><span class=\"tabEnd\">&nbsp;"
                + "</span></caption>",
                "<caption><span>Methods in <a href=\"../../pkg2/package-summary.html\">"
                + "pkg2</a> that return <a href=\"../C1.html\" "
                + "title=\"class in pkg1\">C1</a></span><span class=\"tabEnd\">"
                + "&nbsp;</span></caption>");

        checkOutput("pkg2/class-use/C2.html", true,
                "<caption><span>Fields in <a href=\"../../pkg1/package-summary.html\">"
                + "pkg1</a> declared as <a href=\"../C2.html\" "
                + "title=\"class in pkg2\">C2</a></span><span class=\"tabEnd\">"
                + "&nbsp;</span></caption>",
                "<caption><span>Methods in <a href=\"../../pkg1/package-summary.html\">"
                + "pkg1</a> that return <a href=\"../C2.html\" "
                + "title=\"class in pkg2\">C2</a></span><span class=\"tabEnd\">"
                + "&nbsp;</span></caption>");

        checkOutput("pkg2/class-use/C2.ModalExclusionType.html", true,
                "<caption><span>Methods in <a href=\"../package-summary.html\">"
                + "pkg2</a> that return <a href=\"../C2.ModalExclusionType.html\" "
                + "title=\"enum in pkg2\">C2.ModalExclusionType</a></span>"
                + "<span class=\"tabEnd\">&nbsp;</span></caption>");

        // Package use documentation
        checkOutput("pkg1/package-use.html", true,
                "<caption><span>Packages that use <a href=\"package-summary.html\">"
                + "pkg1</a></span><span class=\"tabEnd\">&nbsp;</span></caption>",
                "<caption><span>Classes in <a href=\"package-summary.html\">"
                + "pkg1</a> used by <a href=\"package-summary.html\">pkg1</a>"
                + "</span><span class=\"tabEnd\">&nbsp;</span></caption>");

        checkOutput("pkg2/package-use.html", true,
                "<caption><span>Packages that use <a href=\"package-summary.html\">"
                + "pkg2</a></span><span class=\"tabEnd\">&nbsp;</span></caption>",
                "<caption><span>Classes in <a href=\"package-summary.html\">"
                + "pkg2</a> used by <a href=\"../pkg1/package-summary.html\">pkg1</a>"
                + "</span><span class=\"tabEnd\">&nbsp;</span></caption>");

        // Deprecated
        checkOutput("deprecated-list.html", true,
                "<caption><span>Fields</span><span class=\"tabEnd\">"
                + "&nbsp;</span></caption>",
                "<caption><span>Methods</span><span class=\"tabEnd\">"
                + "&nbsp;</span></caption>");

        // Constant values
        checkOutput("constant-values.html", true,
                "<caption><span>pkg1.<a href=\"pkg1/C1.html\" title=\"class in pkg1\">"
                + "C1</a></span><span class=\"tabEnd\">&nbsp;</span></caption>");

        // Overview Summary
        checkOutput("overview-summary.html", true,
                "<caption><span>Packages</span><span class=\"tabEnd\">&nbsp;</span></caption>");
    }

    /*
     * Test for validating headers for HTML tables
     */
    void checkHtmlTableHeaders() {
        //Package summary
        checkOutput("pkg1/package-summary.html", true,
                "<th class=\"colFirst\" scope=\"col\">"
                + "Class</th>\n"
                + "<th class=\"colLast\" scope=\"col\""
                + ">Description</th>",
                "<th class=\"colFirst\" scope=\"col\">"
                + "Interface</th>\n"
                + "<th class=\"colLast\" scope=\"col\""
                + ">Description</th>");

        checkOutput("pkg2/package-summary.html", true,
                "<th class=\"colFirst\" scope=\"col\">"
                + "Enum</th>\n"
                + "<th class=\"colLast\" scope=\"col\""
                + ">Description</th>",
                "<th class=\"colFirst\" scope=\"col\">"
                + "Annotation Type</th>\n"
                + "<th class=\"colLast\""
                + " scope=\"col\">Description</th>");

        // Class documentation
        checkOutput("pkg1/C1.html", true,
                "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>\n"
                + "<th class=\"colSecond\" scope=\"col\">Field</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>",
                "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>\n"
                + "<th class=\"colSecond\" scope=\"col\">Method</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>");

        checkOutput("pkg2/C2.html", true,
                "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>\n"
                + "<th class=\"colSecond\" scope=\"col\">Class</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>",
                "<th class=\"colFirst\" scope=\"col\">Constructor</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>");

        checkOutput("pkg2/C2.ModalExclusionType.html", true,
                "<th class=\"colFirst\" scope=\"col\">Enum Constant</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>");

        checkOutput("pkg2/C3.html", true,
                "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>\n"
                + "<th class=\"colSecond\" scope=\"col\">Required Element</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>");

        checkOutput("pkg2/C4.html", true,
                "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>\n"
                + "<th class=\"colSecond\" scope=\"col\">Optional Element</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>");

        // Class use documentation
        checkOutput("pkg1/class-use/I1.html", true,
                "<th class=\"colFirst\" scope=\"col\">Package</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>");

        checkOutput("pkg1/class-use/C1.html", true,
                "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>\n"
                + "<th class=\"colSecond\" scope=\"col\">Field</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>",
                "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>\n"
                + "<th class=\"colSecond\" scope=\"col\">Method</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>");

        checkOutput("pkg2/class-use/C2.html", true,
                "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>\n"
                + "<th class=\"colSecond\" scope=\"col\">Field</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>",
                "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>\n"
                + "<th class=\"colSecond\" scope=\"col\">Method</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>");

        checkOutput("pkg2/class-use/C2.ModalExclusionType.html", true,
                "<th class=\"colFirst\" scope=\"col\">Package</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>",
                "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>\n"
                + "<th class=\"colSecond\" scope=\"col\">Method</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>");

        // Package use documentation
        checkOutput("pkg1/package-use.html", true,
                "<th class=\"colFirst\" scope=\"col\">Package</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>",
                "<th class=\"colFirst\" scope=\"col\">Class</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>");

        checkOutput("pkg2/package-use.html", true,
                "<th class=\"colFirst\" scope=\"col\">Package</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>",
                "<th class=\"colFirst\" scope=\"col\">Class</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>");

        // Deprecated
        checkOutput("deprecated-list.html", true,
                "<th class=\"colFirst\" scope=\"col\">Field</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>",
                "<th class=\"colFirst\" scope=\"col\">Method</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>");

        // Constant values
        checkOutput("constant-values.html", true,
                "<th class=\"colFirst\" scope=\"col\">"
                + "Modifier and Type</th>\n"
                + "<th class=\"colSecond\""
                + " scope=\"col\">Constant Field</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Value</th>");

        // Overview Summary
        checkOutput("overview-summary.html", true,
                "<th class=\"colFirst\" scope=\"col\">"
                + "Package</th>\n"
                + "<th class=\"colLast\" scope=\"col\""
                + ">Description</th>");
    }
}
