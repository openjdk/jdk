/*
 * Copyright (c) 2003, 2005, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4905786 6259611
 * @summary  Make sure that headings use the TH tag instead of the TD tag.
 * @author   jamieh
 * @library  ../lib/
 * @build    JavadocTester
 * @build    TestHeadings
 * @run main TestHeadings
 */

public class TestHeadings extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "4905786-6259611";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "-use", "-header", "Test Files",
        "pkg1", "pkg2"
    };

    //Input for string search tests.
    private static final String[][] TEST = {
        //Package summary
        {BUG_ID + FS + "pkg1" + FS + "package-summary.html",
            "<TH CLASS=\"TableHeader\" SCOPE=\"col\" NOWRAP>" +
            "Class</TH>" + NL + "<TH CLASS=\"TableHeader\" SCOPE=\"col\"" +
            " NOWRAP>Description</TH>"
        },

        // Class documentation
        {BUG_ID + FS + "pkg1" + FS + "C1.html",
            "<TH CLASS=\"TableHeader\" SCOPE=\"col\" NOWRAP>" +
            "Modifier and Type</TH>" + NL + "<TH CLASS=\"TableHeader\"" +
            " SCOPE=\"col\" NOWRAP>Field and Description</TH>"
        },
        {BUG_ID + FS + "pkg1" + FS + "C1.html",
            "<TH ALIGN=\"left\"><STRONG>Methods inherited from class " +            "java.lang.Object</STRONG></TH>"
        },

        // Class use documentation
        {BUG_ID + FS + "pkg1" + FS + "class-use" + FS + "C1.html",
            "<TH CLASS=\"TableHeader\" SCOPE=\"col\" NOWRAP>" +
            "Package</TH>" + NL + "<TH CLASS=\"TableHeader\"" +
            " SCOPE=\"col\" NOWRAP>Description</TH>"
        },
        {BUG_ID + FS + "pkg1" + FS + "class-use" + FS + "C1.html",
            "<TH ALIGN=\"left\" COLSPAN=\"2\"><FONT SIZE=\"+2\">" + NL +
            "Uses of <A HREF=\"../../pkg1/C1.html\" " +            "title=\"class in pkg1\">C1</A> in " +            "<A HREF=\"../../pkg2/package-summary.html\">pkg2</A></FONT></TH>"
        },
        {BUG_ID + FS + "pkg1" + FS + "class-use" + FS + "C1.html",
            "<TH CLASS=\"TableHeader\" SCOPE=\"col\" NOWRAP>" +
            "Modifier and Type</TH>" + NL + "<TH CLASS=\"TableHeader\"" +
            " SCOPE=\"col\" NOWRAP>Field and Description</TH>"
        },

        // Deprecated
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

        // Serialized Form
        {BUG_ID + FS + "serialized-form.html",
            "<TH ALIGN=\"center\"><FONT SIZE=\"+2\">" + NL +
            "<STRONG>Package</STRONG> <STRONG>pkg1</STRONG></FONT></TH>"
        },
        {BUG_ID + FS + "serialized-form.html",
            "<TH ALIGN=\"left\" COLSPAN=\"2\"><FONT SIZE=\"+2\">" + NL +
            "<STRONG>Class <A HREF=\"pkg1/C1.html\" " +            "title=\"class in pkg1\">pkg1.C1</A> extends java.lang.Object " +            "implements Serializable</STRONG></FONT></TH>"
        },
        {BUG_ID + FS + "serialized-form.html",
            "<TH ALIGN=\"left\" COLSPAN=\"1\"><FONT SIZE=\"+2\">" + NL +
            "<STRONG>Serialized Fields</STRONG></FONT></TH>"
        },

        // Overview Frame
        {BUG_ID + FS + "overview-frame.html",
            "<TH ALIGN=\"left\" NOWRAP><FONT size=\"+1\" " +            "CLASS=\"FrameTitleFont\">" + NL + "<STRONG>Test Files</STRONG></FONT></TH>"
        },
        {BUG_ID + FS + "overview-frame.html",
            "<TITLE>" + NL +
            "Overview List" + NL +
            "</TITLE>"
        },

        // Overview Summary
        {BUG_ID + FS + "overview-summary.html",
            "<TITLE>" + NL +
            "Overview" + NL +
            "</TITLE>"
        },

    };
    private static final String[][] NEGATED_TEST = NO_TEST;

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestHeadings tester = new TestHeadings();
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
