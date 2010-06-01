/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4766385
 * @summary  Test that the header option for upper left frame
 *           is present for three sets of options: (1) -header,
 *           (2) -packagesheader, and (3) -header -packagesheader
 * @author   dkramer
 * @library  ../lib/
 * @build    JavadocTester
 * @build    PackagesHeader
 * @run main PackagesHeader
 */

public class PackagesHeader extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "4766385";
    private static final String OUTPUT_DIR = "docs-" + BUG_ID;

    private static final String OUTPUT_DIR1 = "docs1-" + BUG_ID + FS;
    private static final String OUTPUT_DIR2 = "docs2-" + BUG_ID + FS;
    private static final String OUTPUT_DIR3 = "docs3-" + BUG_ID + FS;

    /**
     * Assign value for [ fileToSearch, stringToFind ]
     */
    private static final String[][] TESTARRAY1 = {

        // Test that the -header shows up in the packages frame
        { OUTPUT_DIR1 + "overview-frame.html",
                 "Main Frame Header" }
    };

    private static final String[][] TESTARRAY2 = {

        // Test that the -packagesheader string shows
        // up in the packages frame

        {  OUTPUT_DIR2 + "overview-frame.html",
                 "Packages Frame Header" }
    };

    private static final String[][] TESTARRAY3 = {

        // Test that the both headers show up and are different

        { OUTPUT_DIR3 + "overview-frame.html",
                 "Packages Frame Header" },

        { OUTPUT_DIR3 + "overview-summary.html",
                 "Main Frame Header" }
    };

    // First test with -header only
    private static final String[] JAVADOC_ARGS1 = new String[] {
            "-d", OUTPUT_DIR1,
            "-header", "Main Frame Header",
            "-sourcepath", SRC_DIR,
            "p1", "p2"};

    // Second test with -packagesheader only
    private static final String[] JAVADOC_ARGS2 = new String[] {
            "-d", OUTPUT_DIR2,
            "-packagesheader", "Packages Frame Header",
            "-sourcepath", SRC_DIR,
            "p1", "p2"};

    // Third test with both -packagesheader and -header
    private static final String[] JAVADOC_ARGS3 = new String[] {
            "-d", OUTPUT_DIR3,
            "-packagesheader", "Packages Frame Header",
            "-header", "Main Frame Header",
            "-sourcepath", SRC_DIR,
            "p1", "p2"};


    //Input for string search tests.
    private static final String[][] NEGATED_TEST = NO_TEST;

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        JavadocTester tester = new PackagesHeader();

        run(tester, JAVADOC_ARGS1, TESTARRAY1, NEGATED_TEST);
        run(tester, JAVADOC_ARGS2, TESTARRAY2, NEGATED_TEST);
        run(tester, JAVADOC_ARGS3, TESTARRAY3, NEGATED_TEST);

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
