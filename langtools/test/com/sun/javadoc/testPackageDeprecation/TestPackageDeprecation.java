/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      6492694 8026567
 * @summary  Test package deprecation.
 * @author   bpatel
 * @library  ../lib/
 * @build    JavadocTester TestPackageDeprecation
 * @run main TestPackageDeprecation
 */

public class TestPackageDeprecation extends JavadocTester {

    //Javadoc arguments.
    private static final String[] ARGS1 = new String[]{
        "-d", OUTPUT_DIR + "-1", "-sourcepath", SRC_DIR, "-use", "pkg", "pkg1",
        SRC_DIR + "/C2.java", SRC_DIR + "/FooDepr.java"
    };
    private static final String[] ARGS2 = new String[]{
        "-d", OUTPUT_DIR + "-2", "-sourcepath", SRC_DIR, "-use", "-nodeprecated",
        "pkg", "pkg1", SRC_DIR + "/C2.java", SRC_DIR + "/FooDepr.java"
    };

    //Input for string search tests.
    private static final String[][] TEST1 = {
        { "pkg1/package-summary.html",
            "<div class=\"deprecatedContent\"><span class=\"deprecatedLabel\">Deprecated.</span>\n" +
            "<div class=\"block\"><span class=\"deprecationComment\">This package is Deprecated." +
            "</span></div>"
        },
        { "deprecated-list.html",
            "<li><a href=\"#package\">Deprecated Packages</a></li>"
        }
    };
    private static final String[][] NEGATED_TEST2 = {
        { "overview-summary.html", "pkg1"},
        { "allclasses-frame.html", "FooDepr"}
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestPackageDeprecation tester = new TestPackageDeprecation();
        tester.run(ARGS1, TEST1, NO_TEST);
        tester.run(ARGS2, NO_TEST, NEGATED_TEST2);
        if ((new java.io.File(OUTPUT_DIR + "-2/pkg1/" +
                "package-summary.html")).exists()) {
            throw new Error("Test Fails: packages summary should not be" +
                    "generated for deprecated package.");
        } else {
            System.out.println("Test passes:  package-summary.html not found.");
        }
        if ((new java.io.File(OUTPUT_DIR + "-2/FooDepr.html")).exists()) {
            throw new Error("Test Fails: FooDepr should not be" +
                    "generated as it is deprecated.");
        } else {
            System.out.println("Test passes:  FooDepr.html not found.");
        }
        tester.printSummary();
    }
}
