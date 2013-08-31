/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8006124 8009684 8015663
 * @summary  Test javadoc options support for profiles.
 * @author   Evgeniya Stepanova
 * @library  ../lib/
 * @build    JavadocTester TestProfilesConfiguration
 * @run main TestProfilesConfiguration
 */
public class TestProfilesConfiguration extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "8006124-8009684";
    private static final String PROFILE_CONFIGURATION_BUG_ID = BUG_ID + "-3";
    //Javadoc arguments.
    private static final String[] ARGS3 = new String[]{
        "-d", PROFILE_CONFIGURATION_BUG_ID, "-sourcepath", SRC_DIR, "-nocomment",
        "-keywords", "-Xprofilespath", SRC_DIR + FS + "profile-rtjar-includes.txt",
        "-doctitle", "Simple doctitle", "-use", "pkg3", "pkg1", "pkg2", "pkg4",
        "pkg5", "-packagesheader", "Simple packages header","pkgDeprecated"
    };
    private static final String[][] PROFILES_CONFIGURATION_TEST = {
        //-use option test string fo profile view page
        {PROFILE_CONFIGURATION_BUG_ID + FS + "compact1-summary.html","<li>Use</li>"
        },
        //-doctitle option test string
        {PROFILE_CONFIGURATION_BUG_ID + FS + "overview-summary.html",
            "<div class=\"header\">" + NL + "<h1 class=\"title\">Simple doctitle</h1>"
        },
        //-packagesheader option test string fo profiles
        {PROFILE_CONFIGURATION_BUG_ID + FS + "profile-overview-frame.html",
            "<h1 title=\"Simple packages header\" class=\"bar\">Simple packages header</h1>"
        },
        //-keywords option test string for profiles
        {PROFILE_CONFIGURATION_BUG_ID + FS + "compact1-summary.html",
            "<meta name=\"keywords\" content=\"compact1 profile\">"
        }
    };
    private static final String[][] PROFILES_CONFIGURATION_NEGATED_TEST = {
        //-nocomments option test string
        {PROFILE_CONFIGURATION_BUG_ID + FS + "compact1-summary.html",
            "<div class=\"block\"><i>Class1Pkg2.</i></div>"
        }
    };

    /**
     * The entry point of the test.
     *
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestProfilesConfiguration tester = new TestProfilesConfiguration();
        run(tester, ARGS3, PROFILES_CONFIGURATION_TEST,
        PROFILES_CONFIGURATION_NEGATED_TEST);
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
