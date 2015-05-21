/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      6786688 8008164
 * @summary  HTML tables should have table summary, caption and table headers.
 * @author   Bhavesh Patel
 * @library  ../lib
 * @modules jdk.javadoc
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
                "pkg1", "pkg2");
        checkExit(Exit.OK);

        checkHtmlTableSummaries();
        checkHtmlTableCaptions();
        checkHtmlTableHeaders();
    }

    /*
     * Tests for validating summary for HTML tables
     */
    void checkHtmlTableSummaries() {
        //Package summary
        checkOutput("pkg1/package-summary.html", true,
                "<table class=\"typeSummary\" summary=\"Class Summary table, "
                + "listing classes, and an explanation\">",
                "<table class=\"typeSummary\" summary=\"Interface Summary table, "
                + "listing interfaces, and an explanation\">");

        checkOutput("pkg2/package-summary.html", true,
                "<table class=\"typeSummary\" summary=\"Enum Summary table, "
                + "listing enums, and an explanation\">",
                "<table class=\"typeSummary\" summary=\"Annotation Types Summary table, "
                + "listing annotation types, and an explanation\">");

        // Class documentation
        checkOutput("pkg1/C1.html", true,
                "<table class=\"memberSummary\" summary=\"Field Summary table, listing fields, "
                + "and an explanation\">",
                "<table class=\"memberSummary\" summary=\"Method Summary table, listing methods, "
                + "and an explanation\">");

        checkOutput("pkg2/C2.html", true,
                "<table class=\"memberSummary\" summary=\"Nested Class Summary table, listing "
                + "nested classes, and an explanation\">",
                "<table class=\"memberSummary\" summary=\"Constructor Summary table, listing "
                + "constructors, and an explanation\">");

        checkOutput("pkg2/C2.ModalExclusionType.html", true,
                "<table class=\"memberSummary\" summary=\"Enum Constant Summary table, listing "
                + "enum constants, and an explanation\">");

        checkOutput("pkg2/C3.html", true,
                "<table class=\"memberSummary\" summary=\"Required Element Summary table, "
                + "listing required elements, and an explanation\">");

        checkOutput("pkg2/C4.html", true,
                "<table class=\"memberSummary\" summary=\"Optional Element Summary table, "
                + "listing optional elements, and an explanation\">");

        // Class use documentation
        checkOutput("pkg1/class-use/I1.html", true,
                "<table class=\"useSummary\" summary=\"Use table, listing packages, and an explanation\">");

        checkOutput("pkg1/class-use/C1.html", true,
                "<table class=\"useSummary\" summary=\"Use table, listing fields, and an explanation\">",
                "<table class=\"useSummary\" summary=\"Use table, listing methods, and an explanation\">");

        checkOutput("pkg2/class-use/C2.html", true,
                "<table class=\"useSummary\" summary=\"Use table, listing fields, and an explanation\">",
                "<table class=\"useSummary\" summary=\"Use table, listing methods, and an explanation\">");

        checkOutput("pkg2/class-use/C2.ModalExclusionType.html", true,
                "<table class=\"useSummary\" summary=\"Use table, listing packages, and an explanation\">");

        checkOutput("pkg2/class-use/C2.ModalExclusionType.html", true,
                "<table class=\"useSummary\" summary=\"Use table, listing methods, and an explanation\">");

        // Package use documentation
        checkOutput("pkg1/package-use.html", true,
                "<table class=\"useSummary\" summary=\"Use table, listing packages, and an explanation\">",
                "<table class=\"useSummary\" summary=\"Use table, listing classes, and an explanation\">");

        checkOutput("pkg2/package-use.html", true,
                "<table class=\"useSummary\" summary=\"Use table, listing packages, and an explanation\">",
                "<table class=\"useSummary\" summary=\"Use table, listing classes, and an explanation\">");

        // Deprecated
        checkOutput("deprecated-list.html", true,
                "<table class=\"deprecatedSummary\" summary=\"Deprecated Fields table, listing deprecated fields, "
                + "and an explanation\">",
                "<table class=\"deprecatedSummary\" summary=\"Deprecated Methods table, listing deprecated methods, "
                + "and an explanation\">");

        // Constant values
        checkOutput("constant-values.html", true,
                "<table class=\"constantsSummary\" summary=\"Constant Field Values table, listing "
                + "constant fields, and values\">");

        // Overview Summary
        checkOutput("overview-summary.html", true,
                "<table class=\"overviewSummary\" "
                + "summary=\"Packages table, listing packages, and an explanation\">");
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
                "<caption><span id=\"t0\" class=\"activeTableTab\"><span>All "
                + "Methods</span><span class=\"tabEnd\">&nbsp;</span></span>"
                + "<span id=\"t2\" class=\"tableTab\"><span><a href=\"javascript:show(2);\">"
                + "Instance Methods</a></span><span class=\"tabEnd\">&nbsp;</span></span>"
                + "<span id=\"t4\" class=\"tableTab\"><span><a href=\"javascript:show(8);\">"
                + "Concrete Methods</a></span><span class=\"tabEnd\">&nbsp;</span></span>"
                + "<span id=\"t6\" class=\"tableTab\"><span><a href=\"javascript:show(32);\">"
                + "Deprecated Methods</a></span><span class=\"tabEnd\">&nbsp;</span></span>"
                + "</caption>");

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
                "<caption><span>Packages that use <a href=\"../../pkg1/I1.html\" "
                + "title=\"interface in pkg1\">I1</a></span><span class=\"tabEnd\">"
                + "&nbsp;</span></caption>");

        checkOutput("pkg1/class-use/C1.html", true,
                "<caption><span>Fields in <a href=\"../../pkg2/package-summary.html\">"
                + "pkg2</a> declared as <a href=\"../../pkg1/C1.html\" "
                + "title=\"class in pkg1\">C1</a></span><span class=\"tabEnd\">&nbsp;"
                + "</span></caption>",
                "<caption><span>Methods in <a href=\"../../pkg2/package-summary.html\">"
                + "pkg2</a> that return <a href=\"../../pkg1/C1.html\" "
                + "title=\"class in pkg1\">C1</a></span><span class=\"tabEnd\">"
                + "&nbsp;</span></caption>");

        checkOutput("pkg2/class-use/C2.html", true,
                "<caption><span>Fields in <a href=\"../../pkg1/package-summary.html\">"
                + "pkg1</a> declared as <a href=\"../../pkg2/C2.html\" "
                + "title=\"class in pkg2\">C2</a></span><span class=\"tabEnd\">"
                + "&nbsp;</span></caption>",
                "<caption><span>Methods in <a href=\"../../pkg1/package-summary.html\">"
                + "pkg1</a> that return <a href=\"../../pkg2/C2.html\" "
                + "title=\"class in pkg2\">C2</a></span><span class=\"tabEnd\">"
                + "&nbsp;</span></caption>");

        checkOutput("pkg2/class-use/C2.ModalExclusionType.html", true,
                "<caption><span>Methods in <a href=\"../../pkg2/package-summary.html\">"
                + "pkg2</a> that return <a href=\"../../pkg2/C2.ModalExclusionType.html\" "
                + "title=\"enum in pkg2\">C2.ModalExclusionType</a></span>"
                + "<span class=\"tabEnd\">&nbsp;</span></caption>");

        // Package use documentation
        checkOutput("pkg1/package-use.html", true,
                "<caption><span>Packages that use <a href=\"../pkg1/package-summary.html\">"
                + "pkg1</a></span><span class=\"tabEnd\">&nbsp;</span></caption>",
                "<caption><span>Classes in <a href=\"../pkg1/package-summary.html\">"
                + "pkg1</a> used by <a href=\"../pkg1/package-summary.html\">pkg1</a>"
                + "</span><span class=\"tabEnd\">&nbsp;</span></caption>");

        checkOutput("pkg2/package-use.html", true,
                "<caption><span>Packages that use <a href=\"../pkg2/package-summary.html\">"
                + "pkg2</a></span><span class=\"tabEnd\">&nbsp;</span></caption>",
                "<caption><span>Classes in <a href=\"../pkg2/package-summary.html\">"
                + "pkg2</a> used by <a href=\"../pkg1/package-summary.html\">pkg1</a>"
                + "</span><span class=\"tabEnd\">&nbsp;</span></caption>");

        // Deprecated
        checkOutput("deprecated-list.html", true,
                "<caption><span>Deprecated Fields</span><span class=\"tabEnd\">"
                + "&nbsp;</span></caption>",
                "<caption><span>Deprecated Methods</span><span class=\"tabEnd\">"
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
                + "<th class=\"colLast\" scope=\"col\">Field and Description</th>",
                "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Method and Description</th>");

        checkOutput("pkg2/C2.html", true,
                "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Class and Description</th>",
                "<th class=\"colOne\" scope=\"col\">Constructor and Description</th>");

        checkOutput("pkg2/C2.ModalExclusionType.html", true,
                "<th class=\"colOne\" scope=\"col\">Enum Constant and Description</th>");

        checkOutput("pkg2/C3.html", true,
                "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Required Element and Description</th>");

        checkOutput("pkg2/C4.html", true,
                "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Optional Element and Description</th>");

        // Class use documentation
        checkOutput("pkg1/class-use/I1.html", true,
                "<th class=\"colFirst\" scope=\"col\">Package</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>");

        checkOutput("pkg1/class-use/C1.html", true,
                "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Field and Description</th>",
                "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Method and Description</th>");

        checkOutput("pkg2/class-use/C2.html", true,
                "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Field and Description</th>",
                "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Method and Description</th>");

        checkOutput("pkg2/class-use/C2.ModalExclusionType.html", true,
                "<th class=\"colFirst\" scope=\"col\">Package</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>",
                "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Method and Description</th>");

        // Package use documentation
        checkOutput("pkg1/package-use.html", true,
                "<th class=\"colFirst\" scope=\"col\">Package</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>",
                "<th class=\"colOne\" scope=\"col\">Class and Description</th>");

        checkOutput("pkg2/package-use.html", true,
                "<th class=\"colFirst\" scope=\"col\">Package</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>",
                "<th class=\"colOne\" scope=\"col\">Class and Description</th>");

        // Deprecated
        checkOutput("deprecated-list.html", true,
                "<th class=\"colOne\" scope=\"col\">Field and Description</th>",
                "<th class=\"colOne\" scope=\"col\">Method and Description</th>");

        // Constant values
        checkOutput("constant-values.html", true,
                "<th class=\"colFirst\" scope=\"col\">"
                + "Modifier and Type</th>\n"
                + "<th"
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
