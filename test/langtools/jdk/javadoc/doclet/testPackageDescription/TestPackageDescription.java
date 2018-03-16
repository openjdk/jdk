/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8185194 8182765
 * @summary  Test anchor for package description in package summary page
  * @library  ../lib/
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    JavadocTester TestPackageDescription
 * @run main TestPackageDescription
 */

public class TestPackageDescription extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestPackageDescription tester = new TestPackageDescription();
        tester.runTests();
    }

    @Test
    void test1() {
        javadoc("-d", "out",
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.OK);

        checkOutput("pkg/package-summary.html", true,
                "<a id=\"package.description\">\n"
                + "<!--   -->\n"
                + "</a>\n"
                + "<div class=\"block\">package description</div>\n");
    }

    @Test
    void test2() {
        javadoc("-d", "out-2",
                "-html4",
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.OK);

        checkOutput("pkg/package-summary.html", true,
                "<a name=\"package.description\">\n"
                + "<!--   -->\n"
                + "</a>\n"
                + "<div class=\"block\">package description</div>\n");
    }
}
