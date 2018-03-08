/*
 * Copyright (c) 2002, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8017191 8182765
 * @summary  Javadoc is confused by at-link to imported classes outside of the set of generated packages
 * @author   jjg
 * @library  ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    JavadocTester
 * @run main TestSeeTag
 */

public class TestSeeTag extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestSeeTag tester = new TestSeeTag();
        tester.runTests();
    }

    @Test
    void test() {
        javadoc("-d", "out",
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.OK);

        checkOutput("pkg/Test.html", true,
            "<code>List</code>",
            "<dl>\n"
            + "<dt><span class=\"seeLabel\">See Also:</span></dt>\n"
            + "<dd><a href=\"Test.InnerOne.html#foo()\"><code>Test.InnerOne.foo()</code></a>, \n"
            + "<a href=\"Test.InnerOne.html#bar(java.lang.Object)\"><code>Test.InnerOne.bar(Object)</code></a>, \n"
            + "<a href=\"http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#see\">Javadoc</a>, \n"
            + "<a href=\"Test.InnerOne.html#baz(float)\"><code>something</code></a></dd>\n"
            + "</dl>");

        checkOutput("pkg/Test.html", false,
          "&lt;code&gt;List&lt;/code&gt;");

        checkOutput("pkg/Test2.html", true,
           "<code>Serializable</code>");

        checkOutput("pkg/Test2.html", false,
           ">Serialized Form<");
    }

    @Test
    void test_html4() {
        javadoc("-d", "out-html4",
                "-html4",
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.OK);

        checkOutput("pkg/Test.html", true,
            "<code>List</code>",
            "<dl>\n"
            + "<dt><span class=\"seeLabel\">See Also:</span></dt>\n"
            + "<dd><a href=\"Test.InnerOne.html#foo--\"><code>Test.InnerOne.foo()</code></a>, \n"
            + "<a href=\"Test.InnerOne.html#bar-java.lang.Object-\"><code>Test.InnerOne.bar(Object)</code></a>, \n"
            + "<a href=\"http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#see\">Javadoc</a>, \n"
            + "<a href=\"Test.InnerOne.html#baz-float-\"><code>something</code></a></dd>\n"
            + "</dl>");
}
}
