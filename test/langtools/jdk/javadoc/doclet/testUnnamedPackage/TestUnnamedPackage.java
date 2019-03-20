/*
 * Copyright (c) 2003, 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4904075 4774450 5015144 8043698 8196201 8203791 8184205
 * @summary  Reference unnamed package as "Unnamed", not empty string.
 *           Generate a package summary for the unnamed package.
 * @author   jamieh
 * @library  ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    javadoc.tester.*
 * @run main TestUnnamedPackage
 */

import javadoc.tester.JavadocTester;

public class TestUnnamedPackage extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestUnnamedPackage tester = new TestUnnamedPackage();
        tester.runTests();
    }

    @Test
    public void test() {
        javadoc("-d", "out",
                "-sourcepath", testSrc,
                testSrc("C.java"));
        checkExit(Exit.OK);

        checkOutput("package-summary.html", true,
                "<h1 title=\"Package\" class=\"title\">Package&nbsp;&lt;Unnamed&gt;</h1>",
                "This is a package comment for the unnamed package.",
                "This is a class in the unnamed package.");

        checkOutput("package-summary.html", true,
                "<title>&lt;Unnamed&gt;</title>");

        checkOutput("package-tree.html", true,
                "<h1 class=\"title\">Hierarchy For Package &lt;Unnamed&gt;</h1>");

        checkOutput("index-all.html", true,
                "title=\"class in &lt;Unnamed&gt;\"");

        checkOutput("C.html", true,
                "<a href=\"package-summary.html\">");

        checkOutput("allclasses-index.html", true,
                "<div class=\"typeSummary\">\n<table>\n"
                + "<caption><span>Class Summary</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<thead>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Class</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>\n"
                + "</thead>\n"
                + "<tbody>\n"
                + "<tr class=\"altColor\" id=\"i0\">\n"
                + "<td class=\"colFirst\"><a href=\"C.html\" title=\"class in &lt;Unnamed&gt;\">C</a></td>\n"
                + "<th class=\"colLast\" scope=\"row\">\n"
                + "<div class=\"block\">This is a class in the unnamed package.</div>\n"
                + "</th>\n"
                + "</tr>\n"
                + "</tbody>\n"
                + "</table>");

        checkOutput("allpackages-index.html", true,
                "<div class=\"packagesSummary\">\n<table>\n"
                + "<caption><span>Package Summary</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<thead>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Package</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>\n"
                + "</thead>\n"
                + "<tbody>\n"
                + "<tr class=\"altColor\">\n"
                + "<th class=\"colFirst\" scope=\"row\"><a href=\"package-summary.html\">&lt;Unnamed&gt;</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"block\">This is a package comment for the unnamed package.</div>\n"
                + "</td>\n"
                + "</tr>\n"
                + "</tbody>\n"
                + "</table>");

        checkOutput("type-search-index.js", true,
                "{\"l\":\"All Classes\",\"url\":\"allclasses-index.html\"}");

        checkOutput("package-search-index.js", true,
                "{\"l\":\"All Packages\",\"url\":\"allpackages-index.html\"}");

        checkOutput("index-all.html", true,
                "<br><a href=\"allclasses-index.html\">All&nbsp;Classes</a>&nbsp;"
                + "<a href=\"allpackages-index.html\">All&nbsp;Packages</a>");

        checkOutput(Output.OUT, false,
                "BadSource");
    }
}
