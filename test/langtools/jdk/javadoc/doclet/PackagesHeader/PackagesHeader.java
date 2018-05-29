/*
 * Copyright (c) 2002, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4766385 8196202
 * @summary  Test that the header option for upper left frame
 *           is present for three sets of options: (1) -header,
 *           (2) -packagesheader, and (3) -header -packagesheader
 * @author   dkramer
 * @library  ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    JavadocTester
 * @run main PackagesHeader
 */

public class PackagesHeader extends JavadocTester {

    public static void main(String... args) throws Exception {
        JavadocTester tester = new PackagesHeader();
        tester.runTests();
    }

    @Test
    void testHeader() {
        // First test with -header only
        javadoc("-d", "out-header",
                "-header", "Main Frame Header",
                "--frames",
                "-sourcepath", testSrc,
                "p1", "p2");
        checkExit(Exit.OK);

        // Test that the -header shows up in the packages frame
        checkOutput("overview-frame.html", true,
                "Main Frame Header");
    }

    @Test
    void testPackagesHeader() {
        // Second test with -packagesheader only
        javadoc("-d", "out-packages-header",
                "-packagesheader", "Packages Frame Header",
                "--frames",
                "-sourcepath", testSrc,
                "p1", "p2");
        checkExit(Exit.OK);

        // Test that the -packagesheader string shows
        // up in the packages frame
        checkOutput("overview-frame.html", true,
                "Packages Frame Header");
    }

    @Test
    void testBothHeaders() {
        // Third test with both -packagesheader and -header
        javadoc("-d", "out-both",
                "-packagesheader", "Packages Frame Header",
                "-header", "Main Frame Header",
                "--frames",
                "-sourcepath", testSrc,
                "p1", "p2");
        checkExit(Exit.OK);

        // Test that the both headers show up and are different
        checkOutput("overview-frame.html", true,
                "Packages Frame Header");

        checkOutput("overview-summary.html", true,
                "Main Frame Header");
    }
}
