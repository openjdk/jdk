/*
 * Copyright (c) 2003, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4852280 4517115 4973608 4994589 8026567 8071982 8196202 8234746
 * @summary  Perform tests on index.html file.
 *           Also test that index-all.html has the appropriate output.
 *           Test for unnamed package in index.
 * @library  ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    javadoc.tester.*
 * @run main TestIndex
 */

import javadoc.tester.JavadocTester;

public class TestIndex extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestIndex tester = new TestIndex();
        tester.runTests();
    }

    @Test
    public void test() {
        javadoc("-d", "out",
                "-sourcepath", testSrc,
                "pkg", testSrc("NoPackage.java"));
        checkExit(Exit.OK);

        //Test index-all.html
        checkOutput("index-all.html", true,
                "<a href=\"pkg/C.html\" title=\"class in pkg\"><span class=\"type-name-link\">C</span></a>"
                + " - Class in <a href=\"pkg/package-summary.html\">pkg</a>",
                "<a href=\"pkg/Interface.html\" title=\"interface in pkg\">"
                + "<span class=\"type-name-link\">Interface</span></a> - Interface in "
                + "<a href=\"pkg/package-summary.html\">pkg</a>",
                "<a href=\"pkg/AnnotationType.html\" title=\"annotation in pkg\">"
                + "<span class=\"type-name-link\">AnnotationType</span></a> - Annotation Type in "
                + "<a href=\"pkg/package-summary.html\">pkg</a>",
                "<a href=\"pkg/Coin.html\" title=\"enum in pkg\">"
                + "<span class=\"type-name-link\">Coin</span></a> - Enum in "
                + "<a href=\"pkg/package-summary.html\">pkg</a>",
                "Class in <a href=\"package-summary.html\">&lt;Unnamed&gt;</a>",
                "<dl class=\"index\">\n"
                + "<dt><span class=\"member-name-link\"><a href=\"pkg/C.html#Java\">"
                + "Java</a></span> - Static variable in class pkg.<a href=\"pkg/C.html\" "
                + "title=\"class in pkg\">C</a></dt>\n"
                + "<dd>&nbsp;</dd>\n"
                + "<dt><span class=\"member-name-link\"><a href=\"pkg/C.html#JDK\">JDK</a></span> "
                + "- Static variable in class pkg.<a href=\"pkg/C.html\" title=\"class in pkg\">"
                + "C</a></dt>\n"
                + "<dd>&nbsp;</dd>\n"
                + "</dl>",
                "<dt><span class=\"search-tag-link\"><a href=\"pkg/Coin.html#Enum\">Enum</a>"
                + "</span> - Search tag in enum pkg.Coin</dt>");
    }
}
