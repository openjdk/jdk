/*
 * Copyright (c) 2003, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4905786 6259611
 * @summary  Make sure that headings use the TH tag instead of the TD tag.
 * @author   jamieh
 * @library ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    JavadocTester
 * @build    TestHeadings
 * @run main TestHeadings
 */

public class TestHeadings extends JavadocTester {

    private static final String[][] TEST = {

        {
        },
        { "serialized-form.html"
        },
        { "serialized-form.html"
        },

        {
        },
        { "overview-frame.html"
        },
        {
        }
    };

    public static void main(String... args) throws Exception {
        TestHeadings tester = new TestHeadings();
        tester.runTests();
    }

    @Test
    void test() {
        javadoc("-d", "out",
                "-sourcepath", testSrc,
                "-use",
                "-header", "Test Files",
                "pkg1", "pkg2");
        checkExit(Exit.OK);

        //Package summary
        checkOutput("pkg1/package-summary.html", true,
                "<th class=\"colFirst\" scope=\"col\">"
                + "Class</th>\n"
                + "<th class=\"colLast\" scope=\"col\""
                + ">Description</th>");

        // Class documentation
        checkOutput("pkg1/C1.html", true,
                "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Field and Description</th>",
                "<h3>Methods inherited from class&nbsp;java.lang.Object</h3>");

        // Class use documentation
        checkOutput("pkg1/class-use/C1.html", true,
                "<th class=\"colFirst\" scope=\"col\">Package</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>",
                "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Field and Description</th>");

        // Deprecated
        checkOutput("deprecated-list.html", true,
                "<th class=\"colOne\" scope=\"col\">Method and Description</th>");

        // Constant values
        checkOutput("constant-values.html", true,
                "<th class=\"colFirst\" scope=\"col\">"
                + "Modifier and Type</th>\n"
                + "<th scope=\"col\">Constant Field</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Value</th>");

        // Serialized Form
        checkOutput("serialized-form.html", true,
                "<h2 title=\"Package\">Package&nbsp;pkg1</h2>",
                "<h3>Class <a href=\"pkg1/C1.html\" title=\"class in pkg1\">"
                + "pkg1.C1</a> extends java.lang.Object implements Serializable</h3>",
                "<h3>Serialized Fields</h3>");

        // Overview Frame
        checkOutput("overview-frame.html", true,
                "<h1 title=\"Test Files\" class=\"bar\">Test Files</h1>",
                "<title>Overview List</title>");

        // Overview Summary
        checkOutput("overview-summary.html", true,
                "<title>Overview</title>");
    }
}
