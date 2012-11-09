/*
 * Copyright (c) 2003, 2012, Oracle and/or its affiliates. All rights reserved.
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
            "<frame src=\"overview-summary.html\" name=\"classFrame\" title=\"" +
            "Package, class and interface descriptions\" scrolling=\"yes\">"},

        //Test index-all.html
        {BUG_ID + FS + "index-all.html",
            "<a href=\"pkg/C.html\" title=\"class in pkg\"><span class=\"strong\">C</span></a>" +
            " - Class in <a href=\"pkg/package-summary.html\">pkg</a>"},
        {BUG_ID + FS + "index-all.html",
            "<a href=\"pkg/Interface.html\" title=\"interface in pkg\">" +
            "<span class=\"strong\">Interface</span></a> - Interface in " +
            "<a href=\"pkg/package-summary.html\">pkg</a>"},
        {BUG_ID + FS + "index-all.html",
            "<a href=\"pkg/AnnotationType.html\" title=\"annotation in pkg\">" +
            "<span class=\"strong\">AnnotationType</span></a> - Annotation Type in " +
            "<a href=\"pkg/package-summary.html\">pkg</a>"},
        {BUG_ID + FS + "index-all.html",
            "<a href=\"pkg/Coin.html\" title=\"enum in pkg\">" +
            "<span class=\"strong\">Coin</span></a> - Enum in " +
            "<a href=\"pkg/package-summary.html\">pkg</a>"},
        {BUG_ID + FS + "index-all.html",
            "Class in <a href=\"package-summary.html\">&lt;Unnamed&gt;</a>"},
        {BUG_ID + FS + "index-all.html",
            "<dl>" + NL + "<dt><span class=\"strong\"><a href=\"pkg/C.html#Java\">" +
            "Java</a></span> - Static variable in class pkg.<a href=\"pkg/C.html\" " +
            "title=\"class in pkg\">C</a></dt>" + NL + "<dd>&nbsp;</dd>" + NL +
            "<dt><span class=\"strong\"><a href=\"pkg/C.html#JDK\">JDK</a></span> " +
            "- Static variable in class pkg.<a href=\"pkg/C.html\" title=\"class in pkg\">" +
            "C</a></dt>" + NL + "<dd>&nbsp;</dd>" + NL + "</dl>"},
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
