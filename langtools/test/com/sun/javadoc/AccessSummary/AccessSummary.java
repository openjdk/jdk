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
 * @bug      4637604 4775148
 * @summary  Test the tables for summary=""
 * @author   dkramer
 * @library  ../lib/
 * @build    JavadocTester
 * @build    AccessSummary
 * @run main AccessSummary
 */

public class AccessSummary extends JavadocTester {

    private static final String BUG_ID = "4637604-4775148";
    private static final String OUTPUT_DIR1 = "docs1-" + BUG_ID + FS;

    /**
     * Assign value for [ fileToSearch, stringToFind ]
     */
    private static final String[][] TESTARRAY1 = {

        // Test that the summary attribute appears
        { OUTPUT_DIR1 + "overview-summary.html",
                 "SUMMARY=\"\"" },

        // Test that the summary attribute appears
        { OUTPUT_DIR1 + "p1" + FS + "C1.html",
                 "SUMMARY=\"\"" },

        // Test that the summary attribute appears
        { OUTPUT_DIR1 + "constant-values.html",
                 "SUMMARY=\"\"" }
    };

    // First test with -header only
    private static final String[] JAVADOC_ARGS = new String[] {
            "-d", OUTPUT_DIR1,
            "-sourcepath", SRC_DIR,
            "p1", "p2"};

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        JavadocTester tester = new AccessSummary();
        run(tester, JAVADOC_ARGS,  TESTARRAY1, new String[][] {});
        tester.printSummary();       // Necessary for string search
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
