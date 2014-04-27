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
 * @bug      4460354 8014636
 * @summary  Test to make sure that relative paths are redirected in the
 *           output so that they are not broken.
 * @author   jamieh
 * @library  ../lib/
 * @build    JavadocTester
 * @build    TestRelativeLinks
 * @run main TestRelativeLinks
 */

public class TestRelativeLinks extends JavadocTester {

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", OUTPUT_DIR, "-use", "-sourcepath", SRC_DIR, "pkg", "pkg2"
    };

    //Input for string search tests.
    private static final String[][] TEST = {
        //These relative paths should stay relative because they appear
        //in the right places.
        { "pkg/C.html",
            "<a href=\"relative-class-link.html\">relative class link</a>"},
        { "pkg/C.html",
            "<a href=\"relative-field-link.html\">relative field link</a>"},
        { "pkg/C.html",
            "<a href=\"relative-method-link.html\">relative method link</a>"},
        { "pkg/package-summary.html",
            "<a href=\"relative-package-link.html\">relative package link</a>"},
        { "pkg/C.html",
            " <a\n" +
            " href=\"relative-multi-line-link.html\">relative-multi-line-link</a>."},

        //These relative paths should be redirected because they are in different
        //places.

        //INDEX PAGE
        { "index-all.html",
            "<a href=\"./pkg/relative-class-link.html\">relative class link</a>"},
        { "index-all.html",
            "<a href=\"./pkg/relative-field-link.html\">relative field link</a>"},
        { "index-all.html",
            "<a href=\"./pkg/relative-method-link.html\">relative method link</a>"},
        { "index-all.html",
            "<a href=\"./pkg/relative-package-link.html\">relative package link</a>"},
        { "index-all.html",
            " <a\n" +
            " href=\"./pkg/relative-multi-line-link.html\">relative-multi-line-link</a>."},


        //PACKAGE USE
        { "pkg/package-use.html",
            "<a href=\"../pkg/relative-package-link.html\">relative package link</a>."},
        { "pkg/package-use.html",
            "<a href=\"../pkg/relative-class-link.html\">relative class link</a>"},

        //CLASS_USE
        { "pkg/class-use/C.html",
            "<a href=\"../../pkg/relative-field-link.html\">relative field link</a>"},
        { "pkg/class-use/C.html",
            "<a href=\"../../pkg/relative-method-link.html\">relative method link</a>"},
        { "pkg/class-use/C.html",
            "<a href=\"../../pkg/relative-package-link.html\">relative package link</a>"},
        { "pkg/class-use/C.html",
            " <a\n" +
            " href=\"../../pkg/relative-multi-line-link.html\">relative-multi-line-link</a>."},

        //PACKAGE OVERVIEW
        { "overview-summary.html",
            "<a href=\"./pkg/relative-package-link.html\">relative package link</a>"},
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestRelativeLinks tester = new TestRelativeLinks();
        tester.run(ARGS, TEST, NO_TEST);
        tester.printSummary();
    }
}
