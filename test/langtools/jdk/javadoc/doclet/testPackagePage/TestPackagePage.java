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
 * @bug 4492643 4689286 8196201 8184205
 * @summary Test that a package page is properly generated when a .java file
 * passed to Javadoc.  Also test that the proper package links are generated
 * when single or multiple packages are documented.
 * @author jamieh
 * @library ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build JavadocTester
 * @run main TestPackagePage
 */

public class TestPackagePage extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestPackagePage tester = new TestPackagePage();
        tester.runTests();
    }

    @Test
    void testSinglePackage() {
        javadoc("-d", "out-1",
                "-sourcepath", testSrc,
                testSrc("com/pkg/C.java"));
        checkExit(Exit.OK);

        checkOutput("com/pkg/package-summary.html", true,
            "This is a package page.");

        // With just one package, all general pages link to the single package page.
        checkOutput("com/pkg/C.html", true,
            "<a href=\"package-summary.html\">Package</a>");
        checkOutput("com/pkg/package-tree.html", true,
            "<li><a href=\"package-summary.html\">Package</a></li>");
        checkOutput("deprecated-list.html", true,
            "<li><a href=\"com/pkg/package-summary.html\">Package</a></li>");
        checkOutput("index-all.html", true,
            "<li><a href=\"com/pkg/package-summary.html\">Package</a></li>");
        checkOutput("help-doc.html", true,
            "<li><a href=\"com/pkg/package-summary.html\">Package</a></li>");
    }

    private static final String[][] TEST1 = {
    };


    @Test
    void testMultiplePackages() {
        javadoc("-d", "out-2",
                "-sourcepath", testSrc,
                "com.pkg", "pkg2");
        checkExit(Exit.OK);

        //With multiple packages, there is no package link in general pages.
        checkOutput("deprecated-list.html", true,
            "<li>Package</li>");
        checkOutput("index-all.html", true,
            "<li>Package</li>");
        checkOutput("help-doc.html", true,
            "<li>Package</li>");
        checkOutput("allclasses-index.html", true,
                "<div class=\"typeSummary\">\n<table>\n"
                + "<caption><span>Class Summary</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Class</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>\n");
        checkOutput("allpackages-index.html", true,
                "<div class=\"packagesSummary\">\n<table>\n"
                + "<caption><span>Package Summary</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Package</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>\n");
        checkOutput("type-search-index.js", true,
                "{\"l\":\"All Classes\",\"url\":\"allclasses-index.html\"}");
        checkOutput("package-search-index.js", true,
                "{\"l\":\"All Packages\",\"url\":\"allpackages-index.html\"}");
        checkOutput("index-all.html", true,
                "<br><a href=\"allclasses-index.html\">All&nbsp;Classes</a>&nbsp;"
                + "<a href=\"allpackages-index.html\">All&nbsp;Packages</a>");
    }
}
