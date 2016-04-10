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
 * @bug 8073100
 * @summary ensure the hidden tag works as intended
 * @library ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build JavadocTester
 * @run main TestHiddenTag
 */

public class TestHiddenTag extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestHiddenTag tester = new TestHiddenTag();
        tester.runTests();
    }


    /**
     * Perform tests on &#64;hidden tags
     */
    @Test
    public void test1() {
        javadoc("-d", "out1",
                "-sourcepath", testSrc,
                "-package",
                "pkg1");
        checkExit(Exit.OK);

        checkOutput("pkg1/A.html", true,
                "<a name=\"visibleField\">",
                "<a name=\"visibleMethod--\">",
                "<dt>Direct Known Subclasses:</dt>\n" +
                "<dd><a href=\"../pkg1/A.VisibleInner.html\" title=\"class in pkg1\">" +
                "A.VisibleInner</a>, <a href=\"../pkg1/A.VisibleInnerExtendsInvisibleInner.html\" " +
                "title=\"class in pkg1\">A.VisibleInnerExtendsInvisibleInner</a></dd>");

        checkOutput("pkg1/A.html", false,
                "<a name=\"inVisibleField\">",
                "<a name=\"inVisibleMethod--\">");

        checkOutput("pkg1/A.VisibleInner.html", true,
                "<code><a href=\"../pkg1/A.html#visibleField\">visibleField</a></code>",
                "<code><a href=\"../pkg1/A.html#visibleMethod--\">visibleMethod</a></code>",
                "<h3>Nested classes/interfaces inherited from class&nbsp;pkg1." +
                "<a href=\"../pkg1/A.html\" title=\"class in pkg1\">A</a></h3>\n" +
                "<code><a href=\"../pkg1/A.VisibleInner.html\" title=\"class in pkg1\">" +
                "A.VisibleInner</a>, <a href=\"../pkg1/A.VisibleInnerExtendsInvisibleInner.html\" " +
                "title=\"class in pkg1\">A.VisibleInnerExtendsInvisibleInner</a></code></li>\n" +
                "</ul>");

        checkOutput("pkg1/A.VisibleInner.html", false,
                "../pkg1/A.VisibleInner.html#VisibleInner--",
                "<a name=\"inVisibleField\">",
                "<a name=\"inVisibleMethod--\">");

        checkOutput("pkg1/A.VisibleInnerExtendsInvisibleInner.html", true,
                "<pre>public static class <span class=\"typeNameLabel\">" +
                "A.VisibleInnerExtendsInvisibleInner</span>\n" +
                "extends <a href=\"../pkg1/A.html\" title=\"class in pkg1\">A</a></pre>",
                "<code><a href=\"../pkg1/A.html#visibleField\">visibleField</a></code></li>",
                "<code><a href=\"../pkg1/A.html#visibleMethod--\">visibleMethod</a></code>");

        checkOutput("pkg1/A.VisibleInnerExtendsInvisibleInner.html", false,
                "invisibleField",
                "invisibleMethod",
                "A.InvisibleInner");

        checkOutput("pkg1/package-frame.html", false, "A.InvisibleInner");

        checkOutput("pkg1/package-summary.html", false, "A.InvisibleInner");

        checkOutput("pkg1/package-tree.html", false, "A.InvisibleInner");

        checkFiles(false,
                "pkg1/A.InvisibleInner.html",
                "pkg1/A.InvisibleInnerExtendsVisibleInner.html");

    }
}
