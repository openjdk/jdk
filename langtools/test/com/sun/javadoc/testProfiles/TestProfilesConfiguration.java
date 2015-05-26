/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8006124 8009684 8015663 8015496 8026567
 * @summary  Test javadoc options support for profiles.
 * @author   Evgeniya Stepanova
 * @library  ../lib
 * @modules jdk.javadoc
 * @build    JavadocTester
 * @run main TestProfilesConfiguration
 */
public class TestProfilesConfiguration extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestProfilesConfiguration tester = new TestProfilesConfiguration();
        tester.runTests();
//        tester.run(ARGS3, PROFILES_CONFIGURATION_TEST, PROFILES_CONFIGURATION_NEGATED_TEST);
//        tester.run(ARGS4, NODEPR_NOPKGS_TEST, NODEPR_NOPKGS_NEGATED_TEST);
//        tester.printSummary();
    }

    @Test
    void testProfiles() {
        javadoc("-d", "out-profiles",
                "-sourcepath", testSrc,
                "-nocomment",
                "-keywords",
                "-Xprofilespath", testSrc("profile-rtjar-includes.txt"),
                "-doctitle", "Simple doctitle",
                "-use",
                "-packagesheader", "Simple packages header",
                "pkg3", "pkg1", "pkg2", "pkg4", "pkg5", "pkgDeprecated");
        checkExit(Exit.OK);

        checkOutput("compact1-summary.html", true,
                //-use option test string fo profile view page
                "<li>Use</li>",
                // -keywords option test string for profiles
                "<meta name=\"keywords\" content=\"compact1 profile\">",
                // Deprecated information on a package
                "<h3><a href=\"pkgDeprecated/compact1-package-summary.html\" target=\""
                + "classFrame\">pkgDeprecated</a></h3>\n"
                + "<div class=\"deprecatedContent\">"
                + "<span class=\"deprecatedLabel\">Deprecated.</span></div>"
        );

        //-nocomments option test string
        checkOutput("compact1-summary.html", false,
                "<div class=\"block\"><i>Class1Pkg2.</i></div>"
        );

        // -doctitle option test string
        checkOutput("overview-summary.html", true,
                "<div class=\"header\">\n"
                + "<h1 class=\"title\">Simple doctitle</h1>"
        );

        // -packagesheader option test string fo profiles
        checkOutput("profile-overview-frame.html", true,
                "<h1 title=\"Simple packages header\" class=\"bar\">Simple packages header</h1>"
        );
    }


    @Test
    void testNoDeprNoPackages() {
        javadoc("-d", "out-noDeprNoPackages",
                "-sourcepath", testSrc,
                "-nocomment",
                "-nodeprecated",
                "-keywords",
                "-Xprofilespath", testSrc("profile-rtjar-includes-nopkgs.txt"),
                "-doctitle", "Simple doctitle",
                "-use",
                "-packagesheader", "Simple packages header",
                "pkg1", "pkg2", "pkg3", "pkg4", "pkg5", "pkgDeprecated");
        checkExit(Exit.OK);

        checkOutput("overview-summary.html", true,
                "<ul>\n"
                + "<li><a href=\"compact2-summary.html\" target=\"classFrame\">"
                + "compact2</a></li>\n"
                + "<li><a href=\"compact3-summary.html\" target=\""
                + "classFrame\">compact3</a></li>\n"
                + "</ul>"
        );

        checkOutput("profile-overview-frame.html", true,
                "<ul title=\"Profiles\">\n"
                + "<li><a href=\"compact2-frame.html\" target=\"packageListFrame\">"
                + "compact2</a></li>\n"
                + "<li><a href=\"compact3-frame.html\" target=\""
                + "packageListFrame\">compact3</a></li>\n"
                + "</ul>"
        );

        checkOutput("overview-summary.html", false,
                "compact1"
        );

    }
}
