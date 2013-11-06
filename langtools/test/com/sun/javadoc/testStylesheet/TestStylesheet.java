/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4494033 7028815 7052425 8007338 8023608 8008164
 * @summary  Run tests on doclet stylesheet.
 * @author   jamieh
 * @library  ../lib/
 * @build    JavadocTester TestStylesheet
 * @run main TestStylesheet
 */

public class TestStylesheet extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "4494033-7028815-7052425-8007338";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg"
    };

    //Input for string search tests.
    private static final String[][] TEST = {
        {BUG_ID + FS + "stylesheet.css",
            "/* Javadoc style sheet */"},
        {BUG_ID + FS + "stylesheet.css",
            "/*" + NL + "Overall document style" + NL + "*/"},
        {BUG_ID + FS + "stylesheet.css",
            "/*" + NL + "Heading styles" + NL + "*/"},
        {BUG_ID + FS + "stylesheet.css",
            "/*" + NL + "Navigation bar styles" + NL + "*/"},
        {BUG_ID + FS + "stylesheet.css",
            "body {" + NL + "    background-color:#ffffff;" + NL +
            "    color:#353833;" + NL +
            "    font-family:Arial, Helvetica, sans-serif;" + NL +
            "    font-size:76%;" + NL + "    margin:0;" + NL + "}"},
        {BUG_ID + FS + "stylesheet.css",
            "ul {" + NL + "    list-style-type:disc;" + NL + "}"},
        {BUG_ID + FS + "stylesheet.css",
            ".overviewSummary caption, .memberSummary caption, .typeSummary caption," + NL +
            ".useSummary caption, .constantsSummary caption, .deprecatedSummary caption {" + NL +
            "    position:relative;" + NL +
            "    text-align:left;" + NL +
            "    background-repeat:no-repeat;" + NL +
            "    color:#FFFFFF;" + NL +
            "    font-weight:bold;" + NL +
            "    clear:none;" + NL +
            "    overflow:hidden;" + NL +
            "    padding:0px;" + NL +
            "    margin:0px;" + NL +
            "}"},
        {BUG_ID + FS + "stylesheet.css",
            ".overviewSummary caption span, .memberSummary caption span, .typeSummary caption span," + NL +
            ".useSummary caption span, .constantsSummary caption span, .deprecatedSummary caption span {" + NL +
            "    white-space:nowrap;" + NL +
            "    padding-top:8px;" + NL +
            "    padding-left:8px;" + NL +
            "    display:inline-block;" + NL +
            "    float:left;" + NL +
            "    background-image:url(resources/titlebar.gif);" + NL +
            "}"},
        {BUG_ID + FS + "stylesheet.css",
            ".memberSummary caption span.activeTableTab span {" + NL +
            "    white-space:nowrap;" + NL +
            "    padding-top:8px;" + NL +
            "    padding-left:8px;" + NL +
            "    display:inline-block;" + NL +
            "    float:left;" + NL +
            "    background-image:url(resources/activetitlebar.gif);" + NL +
            "}"},
        {BUG_ID + FS + "stylesheet.css",
            ".memberSummary caption span.tableTab span {" + NL +
            "    white-space:nowrap;" + NL +
            "    padding-top:8px;" + NL +
            "    padding-left:8px;" + NL +
            "    display:inline-block;" + NL +
            "    float:left;" + NL +
            "    background-image:url(resources/titlebar.gif);" + NL +
            "}"},
        {BUG_ID + FS + "stylesheet.css",
            ".memberSummary caption span.tableTab, .memberSummary caption span.activeTableTab {" + NL +
            "    padding-top:0px;" + NL +
            "    padding-left:0px;" + NL +
            "    background-image:none;" + NL +
            "    float:none;" + NL +
            "    display:inline-block;" + NL +
            "}"},
        // Test whether a link to the stylesheet file is inserted properly
        // in the class documentation.
        {BUG_ID + FS + "pkg" + FS + "A.html",
            "<link rel=\"stylesheet\" type=\"text/css\" " +
            "href=\"../stylesheet.css\" title=\"Style\">"}
    };
    private static final String[][] NEGATED_TEST = {
        {BUG_ID + FS + "stylesheet.css",
            "* {" + NL + "    margin:0;" + NL + "    padding:0;" + NL + "}"}
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestStylesheet tester = new TestStylesheet();
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
