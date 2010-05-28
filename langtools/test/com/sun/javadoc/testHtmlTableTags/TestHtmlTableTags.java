/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
            "<TABLE BORDER=\"1\" WIDTH=\"100%\" CELLPADDING=\"3\" " +
            "CELLSPACING=\"0\" SUMMARY=\"Class Summary table, " +
            "listing classes, and an explanation\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "package-summary.html",
            "<TABLE BORDER=\"1\" WIDTH=\"100%\" CELLPADDING=\"3\" " +
            "CELLSPACING=\"0\" SUMMARY=\"Interface Summary table, " +
            "listing interfaces, and an explanation\">"
        },
        {BUG_ID + FS + "pkg2" + FS + "package-summary.html",
            "<TABLE BORDER=\"1\" WIDTH=\"100%\" CELLPADDING=\"3\" " +
            "CELLSPACING=\"0\" SUMMARY=\"Enum Summary table, " +
            "listing enums, and an explanation\">"
        },
        {BUG_ID + FS + "pkg2" + FS + "package-summary.html",
            "<TABLE BORDER=\"1\" WIDTH=\"100%\" CELLPADDING=\"3\" " +
            "CELLSPACING=\"0\" SUMMARY=\"Annotation Types Summary table, " +
            "listing annotation types, and an explanation\">"
        },
        // Class documentation
        {BUG_ID + FS + "pkg1" + FS + "C1.html",
            "<TABLE BORDER=\"1\" WIDTH=\"100%\" CELLPADDING=\"3\" " +
            "CELLSPACING=\"0\" SUMMARY=\"Field Summary table, " +
            "listing fields, and an explanation\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "C1.html",
            "<TABLE BORDER=\"1\" WIDTH=\"100%\" CELLPADDING=\"3\" " +
            "CELLSPACING=\"0\" SUMMARY=\"Method Summary table, " +
            "listing methods, and an explanation\">"
        },
        {BUG_ID + FS + "pkg2" + FS + "C2.html",
            "<TABLE BORDER=\"1\" WIDTH=\"100%\" CELLPADDING=\"3\" " +
            "CELLSPACING=\"0\" SUMMARY=\"Nested Class Summary table, " +
            "listing nested classes, and an explanation\">"
        },
        {BUG_ID + FS + "pkg2" + FS + "C2.html",
            "<TABLE BORDER=\"1\" WIDTH=\"100%\" CELLPADDING=\"3\" " +
            "CELLSPACING=\"0\" SUMMARY=\"Constructor Summary table, " +
            "listing constructors, and an explanation\">"
        },
        {BUG_ID + FS + "pkg2" + FS + "C2.ModalExclusionType.html",
            "<TABLE BORDER=\"1\" WIDTH=\"100%\" CELLPADDING=\"3\" " +
            "CELLSPACING=\"0\" SUMMARY=\"Enum Constant Summary table, " +
            "listing enum constants, and an explanation\">"
        },
        {BUG_ID + FS + "pkg2" + FS + "C3.html",
            "<TABLE BORDER=\"1\" WIDTH=\"100%\" CELLPADDING=\"3\" " +
            "CELLSPACING=\"0\" SUMMARY=\"Required Element Summary table, " +
            "listing required elements, and an explanation\">"
        },
        {BUG_ID + FS + "pkg2" + FS + "C4.html",
            "<TABLE BORDER=\"1\" WIDTH=\"100%\" CELLPADDING=\"3\" " +
            "CELLSPACING=\"0\" SUMMARY=\"Optional Element Summary table, " +
            "listing optional elements, and an explanation\">"
        },
        // Class use documentation
        {BUG_ID + FS + "pkg1" + FS + "class-use" + FS + "I1.html",
            "<TABLE BORDER=\"1\" WIDTH=\"100%\" CELLPADDING=\"3\" " +
            "CELLSPACING=\"0\" SUMMARY=\"Use table, " +
            "listing packages, and an explanation\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "class-use" + FS + "C1.html",
            "<TABLE BORDER=\"1\" WIDTH=\"100%\" CELLPADDING=\"3\" " +
            "CELLSPACING=\"0\" SUMMARY=\"Use table, " +
            "listing fields, and an explanation\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "class-use" + FS + "C1.html",
            "<TABLE BORDER=\"1\" WIDTH=\"100%\" CELLPADDING=\"3\" " +
            "CELLSPACING=\"0\" SUMMARY=\"Use table, " +
            "listing methods, and an explanation\">"
        },
        {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "C2.html",
            "<TABLE BORDER=\"1\" WIDTH=\"100%\" CELLPADDING=\"3\" " +
            "CELLSPACING=\"0\" SUMMARY=\"Use table, " +
            "listing fields, and an explanation\">"
        },
        {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "C2.html",
            "<TABLE BORDER=\"1\" WIDTH=\"100%\" CELLPADDING=\"3\" " +
            "CELLSPACING=\"0\" SUMMARY=\"Use table, " +
            "listing methods, and an explanation\">"
        },
        {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "C2.ModalExclusionType.html",
            "<TABLE BORDER=\"1\" WIDTH=\"100%\" CELLPADDING=\"3\" " +
            "CELLSPACING=\"0\" SUMMARY=\"Use table, " +
            "listing packages, and an explanation\">"
        },
        {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "C2.ModalExclusionType.html",
            "<TABLE BORDER=\"1\" WIDTH=\"100%\" CELLPADDING=\"3\" " +
            "CELLSPACING=\"0\" SUMMARY=\"Use table, " +
            "listing methods, and an explanation\">"
        },
        // Package use documentation
        {BUG_ID + FS + "pkg1" + FS + "package-use.html",
            "<TABLE BORDER=\"1\" WIDTH=\"100%\" CELLPADDING=\"3\" " +
            "CELLSPACING=\"0\" SUMMARY=\"Use table, " +
            "listing packages, and an explanation\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "package-use.html",
            "<TABLE BORDER=\"1\" WIDTH=\"100%\" CELLPADDING=\"3\" " +
            "CELLSPACING=\"0\" SUMMARY=\"Use table, " +
            "listing classes, and an explanation\">"
        },
        {BUG_ID + FS + "pkg2" + FS + "package-use.html",
            "<TABLE BORDER=\"1\" WIDTH=\"100%\" CELLPADDING=\"3\" " +
            "CELLSPACING=\"0\" SUMMARY=\"Use table, " +
            "listing packages, and an explanation\">"
        },
        {BUG_ID + FS + "pkg2" + FS + "package-use.html",
            "<TABLE BORDER=\"1\" WIDTH=\"100%\" CELLPADDING=\"3\" " +
            "CELLSPACING=\"0\" SUMMARY=\"Use table, " +
            "listing classes, and an explanation\">"
        },
        // Deprecated
        {BUG_ID + FS + "deprecated-list.html",
            "<TABLE BORDER=\"1\" WIDTH=\"100%\" CELLPADDING=\"3\" " +
            "CELLSPACING=\"0\" SUMMARY=\"Deprecated Fields table, " +
            "listing deprecated fields, and an explanation\">"
        },
        {BUG_ID + FS + "deprecated-list.html",
            "<TABLE BORDER=\"1\" WIDTH=\"100%\" CELLPADDING=\"3\" " +
            "CELLSPACING=\"0\" SUMMARY=\"Deprecated Methods table, " +
            "listing deprecated methods, and an explanation\">"
        },
        // Constant values
        {BUG_ID + FS + "constant-values.html",
            "<TABLE BORDER=\"1\" CELLPADDING=\"3\" CELLSPACING=\"0\" " +
            "SUMMARY=\"Constant Field Values table, listing " +
            "constant fields, and values\">"
        },
        // Overview Summary
        {BUG_ID + FS + "overview-summary.html",
            "<TABLE BORDER=\"1\" WIDTH=\"100%\" CELLPADDING=\"3\" " +
            "CELLSPACING=\"0\" SUMMARY=\"Packages table, " +
            "listing packages, and an explanation\">"
        },

        /*
         * Test for validating caption for HTML tables
         */

        //Package summary
        {BUG_ID + FS + "pkg1" + FS + "package-summary.html",
            "<CAPTION CLASS=\"TableCaption\">" + NL +
            "Class Summary</CAPTION>"
        },
        {BUG_ID + FS + "pkg1" + FS + "package-summary.html",
            "<CAPTION CLASS=\"TableCaption\">" + NL +
            "Interface Summary</CAPTION>"
        },
        {BUG_ID + FS + "pkg2" + FS + "package-summary.html",
            "<CAPTION CLASS=\"TableCaption\">" + NL +
            "Enum Summary</CAPTION>"
        },
        {BUG_ID + FS + "pkg2" + FS + "package-summary.html",
            "<CAPTION CLASS=\"TableCaption\">" + NL +
            "Annotation Types Summary</CAPTION>"
        },
        // Class documentation
        {BUG_ID + FS + "pkg1" + FS + "C1.html",
            "<CAPTION CLASS=\"TableCaption\">" + NL +
            "Field Summary</CAPTION>"
        },
        {BUG_ID + FS + "pkg1" + FS + "C1.html",
            "<CAPTION CLASS=\"TableCaption\">" + NL +
            "Method Summary</CAPTION>"
        },
        {BUG_ID + FS + "pkg2" + FS + "C2.html",
            "<CAPTION CLASS=\"TableCaption\">" + NL +
            "Nested Class Summary</CAPTION>"
        },
        {BUG_ID + FS + "pkg2" + FS + "C2.html",
            "<CAPTION CLASS=\"TableCaption\">" + NL +
            "Constructor Summary</CAPTION>"
        },
        {BUG_ID + FS + "pkg2" + FS + "C2.ModalExclusionType.html",
            "<CAPTION CLASS=\"TableCaption\">" + NL +
            "Enum Constant Summary</CAPTION>"
        },
        {BUG_ID + FS + "pkg2" + FS + "C3.html",
            "<CAPTION CLASS=\"TableCaption\">" + NL +
            "Required Element Summary</CAPTION>"
        },
        {BUG_ID + FS + "pkg2" + FS + "C4.html",
            "<CAPTION CLASS=\"TableCaption\">" + NL +
            "Optional Element Summary</CAPTION>"
        },
        // Class use documentation
        {BUG_ID + FS + "pkg1" + FS + "class-use" + FS + "I1.html",
            "<CAPTION CLASS=\"TableCaption\">" + NL +
            "Packages that use <A HREF=\"../../pkg1/I1.html\" " +
            "title=\"interface in pkg1\">I1</A></CAPTION>"
        },
        {BUG_ID + FS + "pkg1" + FS + "class-use" + FS + "C1.html",
            "<CAPTION CLASS=\"TableSubCaption\">" + NL +
            "Fields in <A HREF=\"../../pkg2/package-summary.html\">pkg2</A> " +
            "declared as <A HREF=\"../../pkg1/C1.html\" title=\"class in pkg1\">" +
            "C1</A></CAPTION>"
        },
        {BUG_ID + FS + "pkg1" + FS + "class-use" + FS + "C1.html",
            "<CAPTION CLASS=\"TableSubCaption\">" + NL +
            "Methods in <A HREF=\"../../pkg2/package-summary.html\">pkg2</A> " +
            "with parameters of type <A HREF=\"../../pkg1/C1.html\" " +
            "title=\"class in pkg1\">C1</A></CAPTION>"
        },
        {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "C2.html",
            "<CAPTION CLASS=\"TableSubCaption\">" + NL +
            "Fields in <A HREF=\"../../pkg1/package-summary.html\">pkg1</A> " +
            "declared as <A HREF=\"../../pkg2/C2.html\" title=\"class in pkg2\">" +
            "C2</A></CAPTION>"
        },
        {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "C2.html",
            "<CAPTION CLASS=\"TableSubCaption\">" + NL +
            "Methods in <A HREF=\"../../pkg1/package-summary.html\">pkg1</A> " +
            "with parameters of type <A HREF=\"../../pkg2/C2.html\" " +
            "title=\"class in pkg2\">C2</A></CAPTION>"
        },
        {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "C2.ModalExclusionType.html",
            "<CAPTION CLASS=\"TableSubCaption\">" + NL +
            "Methods in <A HREF=\"../../pkg2/package-summary.html\">pkg2</A> " +
            "that return <A HREF=\"../../pkg2/C2.ModalExclusionType.html\" " +
            "title=\"enum in pkg2\">C2.ModalExclusionType</A></CAPTION>"
        },
        // Package use documentation
        {BUG_ID + FS + "pkg1" + FS + "package-use.html",
            "<CAPTION CLASS=\"TableCaption\">" + NL +
            "Packages that use <A HREF=\"../pkg1/package-summary.html\">" +
            "pkg1</A></CAPTION>"
        },
        {BUG_ID + FS + "pkg1" + FS + "package-use.html",
            "<CAPTION CLASS=\"TableCaption\">" + NL +
            "Classes in <A HREF=\"../pkg1/package-summary.html\">pkg1</A> " +
            "used by <A HREF=\"../pkg1/package-summary.html\">pkg1</A></CAPTION>"
        },
        {BUG_ID + FS + "pkg2" + FS + "package-use.html",
            "<CAPTION CLASS=\"TableCaption\">" + NL +
            "Packages that use <A HREF=\"../pkg2/package-summary.html\">" +
            "pkg2</A></CAPTION>"
        },
        {BUG_ID + FS + "pkg2" + FS + "package-use.html",
            "<CAPTION CLASS=\"TableCaption\">" + NL +
            "Classes in <A HREF=\"../pkg2/package-summary.html\">pkg2</A> " +
            "used by <A HREF=\"../pkg1/package-summary.html\">pkg1</A></CAPTION>"
        },
        // Deprecated
        {BUG_ID + FS + "deprecated-list.html",
            "<CAPTION CLASS=\"TableCaption\">" + NL +
            "Deprecated Fields</CAPTION>"
        },
        {BUG_ID + FS + "deprecated-list.html",
            "<CAPTION CLASS=\"TableCaption\">" + NL +
            "Deprecated Methods</CAPTION>"
        },
        // Constant values
        {BUG_ID + FS + "constant-values.html",
            "<CAPTION CLASS=\"TableSubCaption\">" + NL +
            "pkg1.<A HREF=\"pkg1/C1.html\" title=\"class in pkg1\">C1</A></CAPTION>"
        },
        // Overview Summary
        {BUG_ID + FS + "overview-summary.html",
            "<CAPTION CLASS=\"TableCaption\">" + NL +
            "Packages</CAPTION>"
        },

        /*
         * Test for validating headers for HTML tables
         */

        //Package summary
        {BUG_ID + FS + "pkg1" + FS + "package-summary.html",
            "<TH CLASS=\"TableHeader\" SCOPE=\"col\" NOWRAP>" +
            "Class</TH>" + NL + "<TH CLASS=\"TableHeader\" SCOPE=\"col\"" +
            " NOWRAP>Description</TH>"
        },
        {BUG_ID + FS + "pkg1" + FS + "package-summary.html",
            "<TH CLASS=\"TableHeader\" SCOPE=\"col\" NOWRAP>" +
            "Interface</TH>" + NL + "<TH CLASS=\"TableHeader\" SCOPE=\"col\"" +
            " NOWRAP>Description</TH>"
        },
        {BUG_ID + FS + "pkg2" + FS + "package-summary.html",
            "<TH CLASS=\"TableHeader\" SCOPE=\"col\" NOWRAP>" +
            "Enum</TH>" + NL + "<TH CLASS=\"TableHeader\" SCOPE=\"col\"" +
            " NOWRAP>Description</TH>"
        },
        {BUG_ID + FS + "pkg2" + FS + "package-summary.html",
            "<TH CLASS=\"TableHeader\" SCOPE=\"col\" NOWRAP>" +
            "Annotation Type</TH>" + NL + "<TH CLASS=\"TableHeader\"" +
            " SCOPE=\"col\" NOWRAP>Description</TH>"
        },
        // Class documentation
        {BUG_ID + FS + "pkg1" + FS + "C1.html",
            "<TH CLASS=\"TableHeader\" SCOPE=\"col\" NOWRAP>" +
            "Modifier and Type</TH>" + NL + "<TH CLASS=\"TableHeader\"" +
            " SCOPE=\"col\" NOWRAP>Field and Description</TH>"
        },
        {BUG_ID + FS + "pkg1" + FS + "C1.html",
            "<TH CLASS=\"TableHeader\" SCOPE=\"col\" NOWRAP>" +
            "Modifier and Type</TH>" + NL + "<TH CLASS=\"TableHeader\"" +
            " SCOPE=\"col\" NOWRAP>Method and Description</TH>"
        },
        {BUG_ID + FS + "pkg2" + FS + "C2.html",
            "<TH CLASS=\"TableHeader\" SCOPE=\"col\" NOWRAP>" +
            "Modifier and Type</TH>" + NL + "<TH CLASS=\"TableHeader\"" +
            " SCOPE=\"col\" NOWRAP>Class and Description</TH>"
        },
        {BUG_ID + FS + "pkg2" + FS + "C2.html",
            "<TH CLASS=\"TableHeader\" SCOPE=\"col\" NOWRAP>" +
            "Constructor and Description</TH>"
        },
        {BUG_ID + FS + "pkg2" + FS + "C2.ModalExclusionType.html",
            "<TH CLASS=\"TableHeader\" SCOPE=\"col\" NOWRAP>" +
            "Enum Constant and Description</TH>"
        },
        {BUG_ID + FS + "pkg2" + FS + "C3.html",
            "<TH CLASS=\"TableHeader\" SCOPE=\"col\" NOWRAP>" +
            "Modifier and Type</TH>" + NL + "<TH CLASS=\"TableHeader\"" +
            " SCOPE=\"col\" NOWRAP>Required Element and Description</TH>"
        },
        {BUG_ID + FS + "pkg2" + FS + "C4.html",
            "<TH CLASS=\"TableHeader\" SCOPE=\"col\" NOWRAP>" +
            "Modifier and Type</TH>" + NL + "<TH CLASS=\"TableHeader\"" +
            " SCOPE=\"col\" NOWRAP>Optional Element and Description</TH>"
        },
        // Class use documentation
        {BUG_ID + FS + "pkg1" + FS + "class-use" + FS + "I1.html",
            "<TH CLASS=\"TableHeader\" SCOPE=\"col\" NOWRAP>" +
            "Package</TH>" + NL + "<TH CLASS=\"TableHeader\" SCOPE=\"col\"" +
            " NOWRAP>Description</TH>"
        },
        {BUG_ID + FS + "pkg1" + FS + "class-use" + FS + "C1.html",
            "<TH CLASS=\"TableHeader\" SCOPE=\"col\" NOWRAP>" +
            "Modifier and Type</TH>" + NL + "<TH CLASS=\"TableHeader\"" +
            " SCOPE=\"col\" NOWRAP>Field and Description</TH>"
        },
        {BUG_ID + FS + "pkg1" + FS + "class-use" + FS + "C1.html",
            "<TH CLASS=\"TableHeader\" SCOPE=\"col\" NOWRAP>" +
            "Modifier and Type</TH>" + NL + "<TH CLASS=\"TableHeader\"" +
            " SCOPE=\"col\" NOWRAP>Method and Description</TH>"
        },
        {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "C2.html",
            "<TH CLASS=\"TableHeader\" SCOPE=\"col\" NOWRAP>" +
            "Modifier and Type</TH>" + NL + "<TH CLASS=\"TableHeader\"" +
            " SCOPE=\"col\" NOWRAP>Field and Description</TH>"
        },
        {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "C2.html",
            "<TH CLASS=\"TableHeader\" SCOPE=\"col\" NOWRAP>" +
            "Modifier and Type</TH>" + NL + "<TH CLASS=\"TableHeader\"" +
            " SCOPE=\"col\" NOWRAP>Method and Description</TH>"
        },
        {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "C2.ModalExclusionType.html",
            "<TH CLASS=\"TableHeader\" SCOPE=\"col\" NOWRAP>" +
            "Package</TH>" + NL + "<TH CLASS=\"TableHeader\" SCOPE=\"col\"" +
            " NOWRAP>Description</TH>"
        },
        {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "C2.ModalExclusionType.html",
            "<TH CLASS=\"TableHeader\" SCOPE=\"col\" NOWRAP>" +
            "Modifier and Type</TH>" + NL + "<TH CLASS=\"TableHeader\"" +
            " SCOPE=\"col\" NOWRAP>Method and Description</TH>"
        },
        // Package use documentation
        {BUG_ID + FS + "pkg1" + FS + "package-use.html",
            "<TH CLASS=\"TableHeader\" SCOPE=\"col\" NOWRAP>" +
            "Package</TH>" + NL + "<TH CLASS=\"TableHeader\" SCOPE=\"col\"" +
            " NOWRAP>Description</TH>"
        },
        {BUG_ID + FS + "pkg1" + FS + "package-use.html",
            "<TH CLASS=\"TableHeader\" SCOPE=\"col\" NOWRAP>" +
            "Class and Description</TH>"
        },
        {BUG_ID + FS + "pkg2" + FS + "package-use.html",
            "<TH CLASS=\"TableHeader\" SCOPE=\"col\" NOWRAP>" +
            "Package</TH>" + NL + "<TH CLASS=\"TableHeader\" SCOPE=\"col\"" +
            " NOWRAP>Description</TH>"
        },
        {BUG_ID + FS + "pkg2" + FS + "package-use.html",
            "<TH CLASS=\"TableHeader\" SCOPE=\"col\" NOWRAP>" +
            "Class and Description</TH>"
        },
        // Deprecated
        {BUG_ID + FS + "deprecated-list.html",
            "<TH CLASS=\"TableHeader\" SCOPE=\"col\" NOWRAP>" +
            "Field and Description</TH>"
        },
        {BUG_ID + FS + "deprecated-list.html",
            "<TH CLASS=\"TableHeader\" SCOPE=\"col\" NOWRAP>" +
            "Method and Description</TH>"
        },
        // Constant values
        {BUG_ID + FS + "constant-values.html",
            "<TH CLASS=\"TableHeader\" SCOPE=\"col\" NOWRAP>" +
            "Modifier and Type</TH>" + NL + "<TH CLASS=\"TableHeader\"" +
            " SCOPE=\"col\" NOWRAP>Constant Field</TH>" + NL +
            "<TH CLASS=\"TableHeader\" SCOPE=\"col\" NOWRAP>Value</TH>"
        },
        // Overview Summary
        {BUG_ID + FS + "overview-summary.html",
            "<TH CLASS=\"TableHeader\" SCOPE=\"col\" NOWRAP>" +
            "Package</TH>" + NL + "<TH CLASS=\"TableHeader\" SCOPE=\"col\"" +
            " NOWRAP>Description</TH>"
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
