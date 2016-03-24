/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8071982
 * @summary Test for package-frame.html.
 * @library ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build JavadocTester
 * @run main TestClassDocCatalog
 */

public class TestClassDocCatalog extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestClassDocCatalog tester = new TestClassDocCatalog();
        tester.runTests();
    }

    @Test
    void test() {
        javadoc("-d", "out",
                testSrc("pkg1/EmptyAnnotation.java"),
                testSrc("pkg1/EmptyClass.java"),
                testSrc("pkg1/EmptyEnum.java"),
                testSrc("pkg1/EmptyError.java"),
                testSrc("pkg1/EmptyException.java"),
                testSrc("pkg1/EmptyInterface.java"),
                testSrc("pkg2/EmptyAnnotation.java"),
                testSrc("pkg2/EmptyClass.java"),
                testSrc("pkg2/EmptyEnum.java"),
                testSrc("pkg2/EmptyError.java"),
                testSrc("pkg2/EmptyException.java"),
                testSrc("pkg2/EmptyInterface.java"));
        checkExit(Exit.OK);

        checkOutput("overview-frame.html", true,
                "<li><a href=\"pkg1/package-frame.html\" target=\"packageFrame\">pkg1</a>"
                + "</li>\n<li><a href=\"pkg2/package-frame.html\" target=\"packageFrame\">pkg2</a></li>");

        checkOutput("pkg1/package-frame.html", true,
                "<li><a href=\"EmptyInterface.html\" title=\"interface in pkg1\" "
                + "target=\"classFrame\"><span class=\"interfaceName\">EmptyInterface"
                + "</span></a></li>",
                "<li><a href=\"EmptyClass.html\" title=\"class in pkg1\" "
                + "target=\"classFrame\">EmptyClass</a></li>",
                "<li><a href=\"EmptyEnum.html\" title=\"enum in pkg1\" "
                + "target=\"classFrame\">EmptyEnum</a></li>",
                "<li><a href=\"EmptyError.html\" title=\"class in pkg1\" "
                + "target=\"classFrame\">EmptyError</a></li>",
                "<li><a href=\"EmptyAnnotation.html\" title=\"annotation in pkg1\""
                + " target=\"classFrame\">EmptyAnnotation</a></li>");
    }
}
