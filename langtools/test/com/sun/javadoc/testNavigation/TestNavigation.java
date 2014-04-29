/*
 * Copyright (c) 2003, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4131628 4664607 7025314 8023700 7198273 8025633 8026567
 * @summary  Make sure the Next/Prev Class links iterate through all types.
 *           Make sure the navagation is 2 columns, not 3.
 * @author   jamieh
 * @library  ../lib/
 * @build    JavadocTester TestNavigation
 * @run main TestNavigation
 */

public class TestNavigation extends JavadocTester {

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", OUTPUT_DIR, "-sourcepath", SRC_DIR, "pkg"
    };

    //Input for string search tests.
    private static final String[][] TEST = {
        { "pkg/A.html", "<li>Prev&nbsp;Class</li>"},
        { "pkg/A.html",
            "<a href=\"../pkg/C.html\" title=\"class in pkg\"><span class=\"typeNameLink\">Next&nbsp;Class</span></a>"},
        { "pkg/C.html",
            "<a href=\"../pkg/A.html\" title=\"annotation in pkg\"><span class=\"typeNameLink\">Prev&nbsp;Class</span></a>"},
        { "pkg/C.html",
            "<a href=\"../pkg/E.html\" title=\"enum in pkg\"><span class=\"typeNameLink\">Next&nbsp;Class</span></a>"},
        { "pkg/E.html",
            "<a href=\"../pkg/C.html\" title=\"class in pkg\"><span class=\"typeNameLink\">Prev&nbsp;Class</span></a>"},
        { "pkg/E.html",
            "<a href=\"../pkg/I.html\" title=\"interface in pkg\"><span class=\"typeNameLink\">Next&nbsp;Class</span></a>"},
        { "pkg/I.html",
            "<a href=\"../pkg/E.html\" title=\"enum in pkg\"><span class=\"typeNameLink\">Prev&nbsp;Class</span></a>"},
        { "pkg/I.html", "<li>Next&nbsp;Class</li>"},
        // Test for 4664607
        { "pkg/I.html",
            "<div class=\"skipNav\"><a href=\"#skip.navbar.top\" title=\"Skip navigation links\">Skip navigation links</a></div>\n" +
            "<a name=\"navbar.top.firstrow\">\n" +
            "<!--   -->\n" +
            "</a>"}
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestNavigation tester = new TestNavigation();
        tester.run(ARGS, TEST, NO_TEST);
        tester.printSummary();
    }
}
