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
 * @bug      4927167 4974929 7010344 8025633 8081854
 * @summary  When the type parameters are more than 10 characters in length,
 *           make sure there is a line break between type params and return type
 *           in member summary. Also, test for type parameter links in package-summary and
 *           class-use pages. The class/annotation pages should check for type
 *           parameter links in the class/annotation signature section when -linksource is set.
 * @author   jamieh
 * @library  ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    JavadocTester
 * @run main TestTypeParameters
 */

public class TestTypeParameters extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestTypeParameters tester = new TestTypeParameters();
        tester.runTests();
    }

    @Test
    void test1() {
        javadoc("-d", "out-1",
                "-use",
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.OK);

        checkOutput("pkg/C.html", true,
                "<td class=\"colFirst\"><code>&lt;W extends java.lang.String,V extends "
                + "java.util.List&gt;<br>java.lang.Object</code></td>",
                "<code>&lt;T&gt;&nbsp;java.lang.Object</code>");

        checkOutput("pkg/package-summary.html", true,
                "C</a>&lt;E extends <a href=\"../pkg/Parent.html\" "
                + "title=\"class in pkg\">Parent</a>&gt;");

        checkOutput("pkg/class-use/Foo4.html", true,
                "<a href=\"../../pkg/ClassUseTest3.html\" title=\"class in pkg\">"
                + "ClassUseTest3</a>&lt;T extends <a href=\"../../pkg/ParamTest2.html\" "
                + "title=\"class in pkg\">ParamTest2</a>&lt;java.util.List&lt;? extends "
                + "<a href=\"../../pkg/Foo4.html\" title=\"class in pkg\">Foo4</a>&gt;&gt;&gt;");

        // Nested type parameters
        checkOutput("pkg/C.html", true,
                "<a name=\"formatDetails-java.util.Collection-java.util.Collection-\">\n"
                + "<!--   -->\n"
                + "</a>");
    }


    @Test
    void test2() {
        javadoc("-d", "out-2",
                "-linksource",
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.OK);

        checkOutput("pkg/ClassUseTest3.html", true,
            "public class <a href=\"../src-html/pkg/ClassUseTest3.html#line.28\">" +
            "ClassUseTest3</a>&lt;T extends <a href=\"../pkg/ParamTest2.html\" " +
            "title=\"class in pkg\">ParamTest2</a>&lt;java.util.List&lt;? extends " +
            "<a href=\"../pkg/Foo4.html\" title=\"class in pkg\">Foo4</a>&gt;&gt;&gt;");
    }
}
