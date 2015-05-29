/*
 * Copyright (c) 2002, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @summary  Test the tables for summary attribute
 * @author   dkramer
 * @library ../lib
 * @modules jdk.javadoc
 * @build    JavadocTester
 * @run main AccessSummary
 */

public class AccessSummary extends JavadocTester {
    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     * @throws Exception if the test fails
     */
    public static void main(String... args) throws Exception {
        AccessSummary tester = new AccessSummary();
        tester.runTests();
    }

    @Test
    void testAccessSummary() {
        javadoc("-d", "out", "-sourcepath", testSrc, "p1", "p2");
        checkExit(Exit.OK);
        checkOutput("overview-summary.html", true,
                 "summary=\"Packages table, listing packages, and an explanation\"");

        // Test that the summary attribute appears
        checkOutput("p1/C1.html", true,
                 "summary=\"Constructor Summary table, listing constructors, and an explanation\"");

        // Test that the summary attribute appears
        checkOutput("constant-values.html", true,
                 "summary=\"Constant Field Values table, listing constant fields, and values\"");
    }
}
