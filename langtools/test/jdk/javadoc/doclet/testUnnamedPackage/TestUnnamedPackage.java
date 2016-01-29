/*
 * Copyright (c) 2003, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4904075 4774450 5015144 8043698
 * @summary  Reference unnamed package as "Unnamed", not empty string.
 *           Generate a package summary for the unnamed package.
 * @author   jamieh
 * @library  ../lib
 * @modules jdk.javadoc
 * @build    JavadocTester
 * @run main TestUnnamedPackage
 */

public class TestUnnamedPackage extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestUnnamedPackage tester = new TestUnnamedPackage();
        tester.runTests();
    }

    @Test
    void test() {
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

        checkOutput(Output.OUT, false,
                "BadSource");
    }
}
