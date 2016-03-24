/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8025524 8031625 8081854
 * @summary Test for constructor name which should be a non-qualified name.
 * @author Bhavesh Patel
 * @library ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build JavadocTester
 * @run main TestConstructors
 */

public class TestConstructors extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestConstructors tester = new TestConstructors();
        tester.runTests();
    }

    @Test
    void test() {
        javadoc("-d", "out",
                "-sourcepath", testSrc,
                "pkg1");
        checkExit(Exit.OK);

        checkOutput("pkg1/Outer.html", true,
                "<dt><span class=\"seeLabel\">See Also:</span></dt>\n"
                + "<dd><a href=\"../pkg1/Outer.Inner.html#Inner--\"><code>Inner()</code></a>, \n"
                + "<a href=\"../pkg1/Outer.Inner.html#Inner-int-\"><code>Inner(int)</code></a>, \n"
                + "<a href=\"../pkg1/Outer.Inner.NestedInner.html#NestedInner--\"><code>NestedInner()</code></a>, \n"
                + "<a href=\"../pkg1/Outer.Inner.NestedInner.html#NestedInner-int-\"><code>NestedInner(int)</code></a>, \n"
                + "<a href=\"../pkg1/Outer.html#Outer--\"><code>Outer()</code></a>, \n"
                + "<a href=\"../pkg1/Outer.html#Outer-int-\"><code>Outer(int)</code></a>",
                "Link: <a href=\"../pkg1/Outer.Inner.html#Inner--\"><code>Inner()</code></a>, "
                + "<a href=\"../pkg1/Outer.html#Outer-int-\"><code>Outer(int)</code></a>, "
                + "<a href=\"../pkg1/Outer.Inner.NestedInner.html#NestedInner-int-\"><code>"
                + "NestedInner(int)</code></a>",
                "<a href=\"../pkg1/Outer.html#Outer--\">Outer</a></span>()",
                "<a name=\"Outer--\">",
                "<a href=\"../pkg1/Outer.html#Outer-int-\">Outer</a></span>(int&nbsp;i)",
                "<a name=\"Outer-int-\">");

        checkOutput("pkg1/Outer.Inner.html", true,
                "<a href=\"../pkg1/Outer.Inner.html#Inner--\">Inner</a></span>()",
                "<a name=\"Inner--\">",
                "<a href=\"../pkg1/Outer.Inner.html#Inner-int-\">Inner</a></span>(int&nbsp;i)",
                "<a name=\"Inner-int-\">");

        checkOutput("pkg1/Outer.Inner.NestedInner.html", true,
                "<a href=\"../pkg1/Outer.Inner.NestedInner.html#NestedInner--\">NestedInner</a></span>()",
                "<a name=\"NestedInner--\">",
                "<a href=\"../pkg1/Outer.Inner.NestedInner.html#NestedInner-int-\">NestedInner</a></span>(int&nbsp;i)",
                "<a name=\"NestedInner-int-\">");

        checkOutput("pkg1/Outer.Inner.html", false,
                "Outer.Inner--",
                "Outer.Inner-int-");

        checkOutput("pkg1/Outer.Inner.NestedInner.html", false,
                "Outer.Inner.NestedInner--",
                "Outer.Inner.NestedInner-int-");

        checkOutput("pkg1/Outer.html", false,
                "<a href=\"../pkg1/Outer.Inner.html#Outer.Inner--\"><code>Outer.Inner()</code></a>",
                "<a href=\"../pkg1/Outer.Inner.html#Outer.Inner-int-\"><code>Outer.Inner(int)</code></a>",
                "<a href=\"../pkg1/Outer.Inner.NestedInner.html#Outer.Inner.NestedInner--\"><code>Outer.Inner.NestedInner()</code></a>",
                "<a href=\"../pkg1/Outer.Inner.NestedInner.html#Outer.Inner.NestedInner-int-\"><code>Outer.Inner.NestedInner(int)</code></a>");
    }
}
