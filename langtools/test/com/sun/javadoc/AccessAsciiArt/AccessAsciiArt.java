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
 * @bug 4706779 4956908
 * @summary  Add text equivalent of class tree ASCII art for accessibility
 * @author dkramer
 * @library ../lib
 * @modules jdk.javadoc
 * @build JavadocTester
 * @run main AccessAsciiArt
 */

public class AccessAsciiArt extends JavadocTester {

    public static void main(String... args) throws Exception {
        AccessAsciiArt tester = new AccessAsciiArt();
        tester.runTests();
    }

    @Test
    void test() {
        javadoc("-d", "out",
                "-sourcepath", testSrc,
                "p1", "p1.subpkg");
        checkExit(Exit.OK);

        checkOutput("p1/subpkg/SSC.html", true,
                // Test the top line of the class tree
                "<li><a href=\"../../p1/C.html\" title=\"class in p1\">p1.C</a></li>",
                // Test the second line of the class tree
                "<li><a href=\"../../p1/SC.html\" title=\"class in p1\">p1.SC</a></li>",
                // Test the third line of the class tree
                "<li>p1.subpkg.SSC</li>");
    }
}
