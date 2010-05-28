/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4852280 4517115 4973608 4994589
 * @summary  Perform tests on index.html file.
 *           Also test that index-all.html has the appropriate output.
 *           Test for unnamed package in index.
 * @author   jamieh
 * @library  ../lib/
 * @build    JavadocTester
 * @build    TestIndex
 * @run main TestIndex
 */

public class TestIndex extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "4852280-4517115-4973608-4994589";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg", SRC_DIR + FS + "NoPackage.java"
    };

    //Input for string search tests.
    private static final String[][] TEST = {
        //Make sure the horizontal scroll bar does not appear in class frame.
        {BUG_ID + FS + "index.html",
            "<FRAME src=\"overview-summary.html\" name=\"classFrame\" " +
            "title=\"Package, class and interface descriptions\" " +
            "scrolling=\"yes\">"},

        //Test index-all.html
        {BUG_ID + FS + "index-all.html",
            "<A HREF=\"./pkg/C.html\" title=\"class in pkg\"><STRONG>C</STRONG></A>" +
            " - Class in <A HREF=\"./pkg/package-summary.html\">pkg</A>"},
        {BUG_ID + FS + "index-all.html",
            "<A HREF=\"./pkg/Interface.html\" title=\"interface in pkg\">" +
            "<STRONG>Interface</STRONG></A> - Interface in " +
            "<A HREF=\"./pkg/package-summary.html\">pkg</A>"},
        {BUG_ID + FS + "index-all.html",
            "<A HREF=\"./pkg/AnnotationType.html\" title=\"annotation in pkg\">" +
            "<STRONG>AnnotationType</STRONG></A> - Annotation Type in " +
            "<A HREF=\"./pkg/package-summary.html\">pkg</A>"},
        {BUG_ID + FS + "index-all.html",
            "<A HREF=\"./pkg/Coin.html\" title=\"enum in pkg\">" +
            "<STRONG>Coin</STRONG></A> - Enum in " +
            "<A HREF=\"./pkg/package-summary.html\">pkg</A>"},
        {BUG_ID + FS + "index-all.html",
            "Class in <A HREF=\"./package-summary.html\">&lt;Unnamed&gt;</A>"},
        {BUG_ID + FS + "index-all.html",
            "<DT><A HREF=\"./pkg/C.html#Java\"><STRONG>Java</STRONG></A> - " + NL +
            "Static variable in class pkg.<A HREF=\"./pkg/C.html\" title=\"class in pkg\">C</A>" + NL +
            "</DT><DD>&nbsp;</DD>" + NL + NL +
            "<DT><A HREF=\"./pkg/C.html#JDK\"><STRONG>JDK</STRONG></A> - " + NL +
            "Static variable in class pkg.<A HREF=\"./pkg/C.html\" title=\"class in pkg\">C</A>" + NL +
            "</DT><DD>&nbsp;</DD>"},
    };
    private static final String[][] NEGATED_TEST = NO_TEST;

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestIndex tester = new TestIndex();
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
