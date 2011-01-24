/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      6786688
 * @summary  HTML tables should have table summary, caption and table headers.
 * @author   Bhavesh Patel
 * @library  ../lib/
 * @build    JavadocTester
 * @build    TestHtmlTableTags
 * @run main TestHtmlTableTags
 */

public class TestHtmlTableTags extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "6786688";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "-use", "pkg1", "pkg2"
    };

    //Input for string tests for HTML table tags.
    private static final String[][] TABLE_TAGS_TEST = {
        /*
         * Test for validating summary for HTML tables
         */

        //Package summary
        {BUG_ID + FS + "pkg1" + FS + "package-summary.html",
            "<table class=\"packageSummary\" border=\"0\" cellpadding=\"3\"" +
            " cellspacing=\"0\" summary=\"Class Summary table, " +
            "listing classes, and an explanation\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "package-summary.html",
            "<table class=\"packageSummary\" border=\"0\" cellpadding=\"3\"" +
            " cellspacing=\"0\" summary=\"Interface Summary table, " +
            "listing interfaces, and an explanation\">"
        },
        {BUG_ID + FS + "pkg2" + FS + "package-summary.html",
            "<table class=\"packageSummary\" border=\"0\" cellpadding=\"3\"" +
            " cellspacing=\"0\" summary=\"Enum Summary table, " +
            "listing enums, and an explanation\">"
        },
        {BUG_ID + FS + "pkg2" + FS + "package-summary.html",
            "<table class=\"packageSummary\" border=\"0\" cellpadding=\"3\"" +
            " cellspacing=\"0\" summary=\"Annotation Types Summary table, " +
            "listing annotation types, and an explanation\">"
        },
        // Class documentation
        {BUG_ID + FS + "pkg1" + FS + "C1.html",
            "<table class=\"overviewSummary\" border=\"0\" cellpadding=\"3\" " +
            "cellspacing=\"0\" summary=\"Field Summary table, listing fields, " +
            "and an explanation\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "C1.html",
            "<table class=\"overviewSummary\" border=\"0\" cellpadding=\"3\" " +
            "cellspacing=\"0\" summary=\"Method Summary table, listing methods, " +
            "and an explanation\">"
        },
        {BUG_ID + FS + "pkg2" + FS + "C2.html",
            "<table class=\"overviewSummary\" border=\"0\" cellpadding=\"3\" " +
            "cellspacing=\"0\" summary=\"Nested Class Summary table, listing " +
            "nested classes, and an explanation\">"
        },
        {BUG_ID + FS + "pkg2" + FS + "C2.html",
            "<table class=\"overviewSummary\" border=\"0\" cellpadding=\"3\" " +
            "cellspacing=\"0\" summary=\"Constructor Summary table, listing " +
            "constructors, and an explanation\">"
        },
        {BUG_ID + FS + "pkg2" + FS + "C2.ModalExclusionType.html",
            "<table class=\"overviewSummary\" border=\"0\" cellpadding=\"3\" " +
            "cellspacing=\"0\" summary=\"Enum Constant Summary table, listing " +
            "enum constants, and an explanation\">"
        },
        {BUG_ID + FS + "pkg2" + FS + "C3.html",
            "<table class=\"overviewSummary\" border=\"0\" cellpadding=\"3\" " +
            "cellspacing=\"0\" summary=\"Required Element Summary table, " +
            "listing required elements, and an explanation\">"
        },
        {BUG_ID + FS + "pkg2" + FS + "C4.html",
            "<table class=\"overviewSummary\" border=\"0\" cellpadding=\"3\" " +
            "cellspacing=\"0\" summary=\"Optional Element Summary table, " +
            "listing optional elements, and an explanation\">"
        },
        // Class use documentation
        {BUG_ID + FS + "pkg1" + FS + "class-use" + FS + "I1.html",
            "<table border=\"0\" cellpadding=\"3\" cellspacing=\"0\" summary=\"Use " +
            "table, listing packages, and an explanation\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "class-use" + FS + "C1.html",
            "<table border=\"0\" cellpadding=\"3\" cellspacing=\"0\" summary=\"Use " +
            "table, listing fields, and an explanation\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "class-use" + FS + "C1.html",
            "<table border=\"0\" cellpadding=\"3\" cellspacing=\"0\" summary=\"Use " +
            "table, listing methods, and an explanation\">"
        },
        {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "C2.html",
            "<table border=\"0\" cellpadding=\"3\" cellspacing=\"0\" summary=\"Use " +
            "table, listing fields, and an explanation\">"
        },
        {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "C2.html",
            "<table border=\"0\" cellpadding=\"3\" cellspacing=\"0\" summary=\"Use " +
            "table, listing methods, and an explanation\">"
        },
        {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "C2.ModalExclusionType.html",
            "<table border=\"0\" cellpadding=\"3\" cellspacing=\"0\" summary=\"Use " +
            "table, listing packages, and an explanation\">"
        },
        {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "C2.ModalExclusionType.html",
            "<table border=\"0\" cellpadding=\"3\" cellspacing=\"0\" summary=\"Use " +
            "table, listing methods, and an explanation\">"
        },
        // Package use documentation
        {BUG_ID + FS + "pkg1" + FS + "package-use.html",
            "<table border=\"0\" cellpadding=\"3\" cellspacing=\"0\" summary=\"Use " +
            "table, listing packages, and an explanation\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "package-use.html",
            "<table border=\"0\" cellpadding=\"3\" cellspacing=\"0\" summary=\"Use " +
            "table, listing classes, and an explanation\">"
        },
        {BUG_ID + FS + "pkg2" + FS + "package-use.html",
            "<table border=\"0\" cellpadding=\"3\" cellspacing=\"0\" summary=\"Use " +
            "table, listing packages, and an explanation\">"
        },
        {BUG_ID + FS + "pkg2" + FS + "package-use.html",
            "<table border=\"0\" cellpadding=\"3\" cellspacing=\"0\" summary=\"Use " +
            "table, listing classes, and an explanation\">"
        },
        // Deprecated
        {BUG_ID + FS + "deprecated-list.html",
            "<table border=\"0\" cellpadding=\"3\" cellspacing=\"0\" " +
            "summary=\"Deprecated Fields table, listing deprecated fields, " +
            "and an explanation\">"
        },
        {BUG_ID + FS + "deprecated-list.html",
            "<table border=\"0\" cellpadding=\"3\" cellspacing=\"0\" " +
            "summary=\"Deprecated Methods table, listing deprecated methods, " +
            "and an explanation\">"
        },
        // Constant values
        {BUG_ID + FS + "constant-values.html",
            "<table border=\"0\" cellpadding=\"3\" cellspacing=\"0\" " +
            "summary=\"Constant Field Values table, listing " +
            "constant fields, and values\">"
        },
        // Overview Summary
        {BUG_ID + FS + "overview-summary.html",
            "<table class=\"overviewSummary\" border=\"0\" cellpadding=\"3\" " +
            "cellspacing=\"0\" summary=\"Packages table, " +
            "listing packages, and an explanation\">"
        },

        /*
         * Test for validating caption for HTML tables
         */

        //Package summary
        {BUG_ID + FS + "pkg1" + FS + "package-summary.html",
            "<caption><span>Class Summary</span><span class=\"tabEnd\">" +
            "&nbsp;</span></caption>"
        },
        {BUG_ID + FS + "pkg1" + FS + "package-summary.html",
            "<caption><span>Interface Summary</span><span class=\"tabEnd\">" +
            "&nbsp;</span></caption>"
        },
        {BUG_ID + FS + "pkg2" + FS + "package-summary.html",
            "<caption><span>Enum Summary</span><span class=\"tabEnd\">" +
            "&nbsp;</span></caption>"
        },
        {BUG_ID + FS + "pkg2" + FS + "package-summary.html",
            "<caption><span>Annotation Types Summary</span><span class=\"tabEnd\">" +
            "&nbsp;</span></caption>"
        },
        // Class documentation
        {BUG_ID + FS + "pkg1" + FS + "C1.html",
            "<caption><span>Fields</span><span class=\"tabEnd\">&nbsp;</span></caption>"
        },
        {BUG_ID + FS + "pkg1" + FS + "C1.html",
            "<caption><span>Methods</span><span class=\"tabEnd\">&nbsp;</span></caption>"
        },
        {BUG_ID + FS + "pkg2" + FS + "C2.html",
            "<caption><span>Nested Classes</span><span class=\"tabEnd\">&nbsp;</span></caption>"
        },
        {BUG_ID + FS + "pkg2" + FS + "C2.html",
            "<caption><span>Constructors</span><span class=\"tabEnd\">&nbsp;</span></caption>"
        },
        {BUG_ID + FS + "pkg2" + FS + "C2.ModalExclusionType.html",
            "<caption><span>Enum Constants</span><span class=\"tabEnd\">&nbsp;</span></caption>"
        },
        {BUG_ID + FS + "pkg2" + FS + "C3.html",
            "<caption><span>Required Elements</span><span class=\"tabEnd\">&nbsp;" +
            "</span></caption>"
        },
        {BUG_ID + FS + "pkg2" + FS + "C4.html",
            "<caption><span>Optional Elements</span><span class=\"tabEnd\">&nbsp;" +
            "</span></caption>"
        },
        // Class use documentation
        {BUG_ID + FS + "pkg1" + FS + "class-use" + FS + "I1.html",
            "<caption><span>Packages that use <a href=\"../../pkg1/I1.html\" " +
            "title=\"interface in pkg1\">I1</a></span><span class=\"tabEnd\">" +
            "&nbsp;</span></caption>"
        },
        {BUG_ID + FS + "pkg1" + FS + "class-use" + FS + "C1.html",
            "<caption><span>Fields in <a href=\"../../pkg2/package-summary.html\">" +
            "pkg2</a> declared as <a href=\"../../pkg1/C1.html\" " +
            "title=\"class in pkg1\">C1</a></span><span class=\"tabEnd\">&nbsp;" +
            "</span></caption>"
        },
        {BUG_ID + FS + "pkg1" + FS + "class-use" + FS + "C1.html",
            "<caption><span>Methods in <a href=\"../../pkg2/package-summary.html\">" +
            "pkg2</a> that return <a href=\"../../pkg1/C1.html\" " +
            "title=\"class in pkg1\">C1</a></span><span class=\"tabEnd\">" +
            "&nbsp;</span></caption>"
        },
        {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "C2.html",
            "<caption><span>Fields in <a href=\"../../pkg1/package-summary.html\">" +
            "pkg1</a> declared as <a href=\"../../pkg2/C2.html\" " +
            "title=\"class in pkg2\">C2</a></span><span class=\"tabEnd\">" +
            "&nbsp;</span></caption>"
        },
        {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "C2.html",
            "<caption><span>Methods in <a href=\"../../pkg1/package-summary.html\">" +
            "pkg1</a> that return <a href=\"../../pkg2/C2.html\" " +
            "title=\"class in pkg2\">C2</a></span><span class=\"tabEnd\">" +
            "&nbsp;</span></caption>"
        },
        {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "C2.ModalExclusionType.html",
            "<caption><span>Methods in <a href=\"../../pkg2/package-summary.html\">" +
            "pkg2</a> that return <a href=\"../../pkg2/C2.ModalExclusionType.html\" " +
            "title=\"enum in pkg2\">C2.ModalExclusionType</a></span>" +
            "<span class=\"tabEnd\">&nbsp;</span></caption>"
        },
        // Package use documentation
        {BUG_ID + FS + "pkg1" + FS + "package-use.html",
            "<caption><span>Packages that use <a href=\"../pkg1/package-summary.html\">" +
            "pkg1</a></span><span class=\"tabEnd\">&nbsp;</span></caption>"
        },
        {BUG_ID + FS + "pkg1" + FS + "package-use.html",
            "<caption><span>Classes in <a href=\"../pkg1/package-summary.html\">" +
            "pkg1</a> used by <a href=\"../pkg1/package-summary.html\">pkg1</a>" +
            "</span><span class=\"tabEnd\">&nbsp;</span></caption>"
        },
        {BUG_ID + FS + "pkg2" + FS + "package-use.html",
            "<caption><span>Packages that use <a href=\"../pkg2/package-summary.html\">" +
            "pkg2</a></span><span class=\"tabEnd\">&nbsp;</span></caption>"
        },
        {BUG_ID + FS + "pkg2" + FS + "package-use.html",
            "<caption><span>Classes in <a href=\"../pkg2/package-summary.html\">" +
            "pkg2</a> used by <a href=\"../pkg1/package-summary.html\">pkg1</a>" +
            "</span><span class=\"tabEnd\">&nbsp;</span></caption>"
        },
        // Deprecated
        {BUG_ID + FS + "deprecated-list.html",
            "<caption><span>Deprecated Fields</span><span class=\"tabEnd\">" +
            "&nbsp;</span></caption>"
        },
        {BUG_ID + FS + "deprecated-list.html",
            "<caption><span>Deprecated Methods</span><span class=\"tabEnd\">" +
            "&nbsp;</span></caption>"
        },
        // Constant values
        {BUG_ID + FS + "constant-values.html",
            "<caption><span>pkg1.<a href=\"pkg1/C1.html\" title=\"class in pkg1\">" +
            "C1</a></span><span class=\"tabEnd\">&nbsp;</span></caption>"
        },
        // Overview Summary
        {BUG_ID + FS + "overview-summary.html",
            "<caption><span>Packages</span><span class=\"tabEnd\">&nbsp;</span></caption>"
        },

        /*
         * Test for validating headers for HTML tables
         */

        //Package summary
        {BUG_ID + FS + "pkg1" + FS + "package-summary.html",
            "<th class=\"colFirst\" scope=\"col\">" +
            "Class</th>" + NL + "<th class=\"colLast\" scope=\"col\"" +
            ">Description</th>"
        },
        {BUG_ID + FS + "pkg1" + FS + "package-summary.html",
            "<th class=\"colFirst\" scope=\"col\">" +
            "Interface</th>" + NL + "<th class=\"colLast\" scope=\"col\"" +
            ">Description</th>"
        },
        {BUG_ID + FS + "pkg2" + FS + "package-summary.html",
            "<th class=\"colFirst\" scope=\"col\">" +
            "Enum</th>" + NL + "<th class=\"colLast\" scope=\"col\"" +
            ">Description</th>"
        },
        {BUG_ID + FS + "pkg2" + FS + "package-summary.html",
            "<th class=\"colFirst\" scope=\"col\">" +
            "Annotation Type</th>" + NL + "<th class=\"colLast\"" +
            " scope=\"col\">Description</th>"
        },
        // Class documentation
        {BUG_ID + FS + "pkg1" + FS + "C1.html",
            "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>" + NL +
            "<th class=\"colLast\" scope=\"col\">Field and Description</th>"
        },
        {BUG_ID + FS + "pkg1" + FS + "C1.html",
            "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>" + NL +
            "<th class=\"colLast\" scope=\"col\">Method and Description</th>"
        },
        {BUG_ID + FS + "pkg2" + FS + "C2.html",
            "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>" + NL +
            "<th class=\"colLast\" scope=\"col\">Class and Description</th>"
        },
        {BUG_ID + FS + "pkg2" + FS + "C2.html",
            "<th class=\"colOne\" scope=\"col\">Constructor and Description</th>"
        },
        {BUG_ID + FS + "pkg2" + FS + "C2.ModalExclusionType.html",
            "<th class=\"colOne\" scope=\"col\">Enum Constant and Description</th>"
        },
        {BUG_ID + FS + "pkg2" + FS + "C3.html",
            "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>" + NL +
            "<th class=\"colLast\" scope=\"col\">Required Element and Description</th>"
        },
        {BUG_ID + FS + "pkg2" + FS + "C4.html",
            "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>" + NL +
            "<th class=\"colLast\" scope=\"col\">Optional Element and Description</th>"
        },
        // Class use documentation
        {BUG_ID + FS + "pkg1" + FS + "class-use" + FS + "I1.html",
            "<th class=\"colFirst\" scope=\"col\">Package</th>" + NL +
            "<th class=\"colLast\" scope=\"col\">Description</th>"
        },
        {BUG_ID + FS + "pkg1" + FS + "class-use" + FS + "C1.html",
            "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>" + NL +
            "<th class=\"colLast\" scope=\"col\">Field and Description</th>"
        },
        {BUG_ID + FS + "pkg1" + FS + "class-use" + FS + "C1.html",
            "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>" + NL +
            "<th class=\"colLast\" scope=\"col\">Method and Description</th>"
        },
        {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "C2.html",
            "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>" + NL +
            "<th class=\"colLast\" scope=\"col\">Field and Description</th>"
        },
        {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "C2.html",
            "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>" + NL +
            "<th class=\"colLast\" scope=\"col\">Method and Description</th>"
        },
        {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "C2.ModalExclusionType.html",
            "<th class=\"colFirst\" scope=\"col\">Package</th>" + NL +
            "<th class=\"colLast\" scope=\"col\">Description</th>"
        },
        {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "C2.ModalExclusionType.html",
            "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>" + NL +
            "<th class=\"colLast\" scope=\"col\">Method and Description</th>"
        },
        // Package use documentation
        {BUG_ID + FS + "pkg1" + FS + "package-use.html",
            "<th class=\"colFirst\" scope=\"col\">Package</th>" + NL +
            "<th class=\"colLast\" scope=\"col\">Description</th>"
        },
        {BUG_ID + FS + "pkg1" + FS + "package-use.html",
            "<th class=\"colOne\" scope=\"col\">Class and Description</th>"
        },
        {BUG_ID + FS + "pkg2" + FS + "package-use.html",
            "<th class=\"colFirst\" scope=\"col\">Package</th>" + NL +
            "<th class=\"colLast\" scope=\"col\">Description</th>"
        },
        {BUG_ID + FS + "pkg2" + FS + "package-use.html",
            "<th class=\"colOne\" scope=\"col\">Class and Description</th>"
        },
        // Deprecated
        {BUG_ID + FS + "deprecated-list.html",
            "<th class=\"colOne\" scope=\"col\">Field and Description</th>"
        },
        {BUG_ID + FS + "deprecated-list.html",
            "<th class=\"colOne\" scope=\"col\">Method and Description</th>"
        },
        // Constant values
        {BUG_ID + FS + "constant-values.html",
            "<th class=\"colFirst\" scope=\"col\">" +
            "Modifier and Type</th>" + NL + "<th" +
            " scope=\"col\">Constant Field</th>" + NL +
            "<th class=\"colLast\" scope=\"col\">Value</th>"
        },
        // Overview Summary
        {BUG_ID + FS + "overview-summary.html",
            "<th class=\"colFirst\" scope=\"col\">" +
            "Package</th>" + NL + "<th class=\"colLast\" scope=\"col\"" +
            ">Description</th>"
        }
    };
    private static final String[][] NEGATED_TEST = NO_TEST;

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestHtmlTableTags tester = new TestHtmlTableTags();
        run(tester, ARGS, TABLE_TAGS_TEST, NEGATED_TEST);
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
