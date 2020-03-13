/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8214126
 * @summary  Method signatures not formatted correctly in browser
 * @library  ../../lib/
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build javadoc.tester.*
 * @run main TestMethodSignature
 */

import javadoc.tester.JavadocTester;

public class TestMethodSignature extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestMethodSignature tester = new TestMethodSignature();
        tester.runTests();
    }

    @Test
    public void test() {
        javadoc("-d", "out",
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.OK);

        checkOutput("pkg/C.html", true,
                "<div class=\"member-signature\"><span class=\"annotations\">"
                + "@Generated(\"GeneratedConstructor\")\n"
                + "</span><span class=\"modifiers\">public</span>&nbsp;"
                + "<span class=\"member-name\">C</span>()</div>",

                "<div class=\"member-signature\"><span class=\"modifiers\">public static</span>"
                + "&nbsp;<span class=\"return-type\">void</span>&nbsp;<span class=\"member-name\">"
                + "simpleMethod</span>&#8203;(<span class=\"arguments\">int&nbsp;i,\n"
                + "java.lang.String&nbsp;s,\nboolean&nbsp;b)</span></div>",

                "<div class=\"member-signature\"><span class=\"annotations\">@Generated"
                + "(value=\"SomeGeneratedName\",\n           date=\"a date\",\n"
                + "           comments=\"some comment about the method below\")\n"
                + "</span><span class=\"modifiers\">public static</span>&nbsp;<span "
                + "class=\"return-type\">void</span>&nbsp;<span class=\"member-name\">annotatedMethod"
                + "</span>&#8203;(<span class=\"arguments\">int&nbsp;i,\n"
                + "java.lang.String&nbsp;s,\nboolean&nbsp;b)</span></div>",

                "<div class=\"member-signature\"><span class=\"modifiers\">public static</span>"
                + "&nbsp;<span class=\"type-parameters-long\">&lt;T1 extends java.lang.AutoCloseable,&#8203;\n"
                + "T2 extends java.lang.AutoCloseable,&#8203;\n"
                + "T3 extends java.lang.AutoCloseable,&#8203;\n"
                + "T4 extends java.lang.AutoCloseable,&#8203;\n"
                + "T5 extends java.lang.AutoCloseable,&#8203;\n"
                + "T6 extends java.lang.AutoCloseable,&#8203;\n"
                + "T7 extends java.lang.AutoCloseable,&#8203;\n"
                + "T8 extends java.lang.AutoCloseable&gt;</span>\n"
                + "<span class=\"return-type\"><a href=\"C.With8Types.html\" "
                + "title=\"class in pkg\">C.With8Types</a>&lt;T1,&#8203;T2,&#8203;T3,"
                + "&#8203;T4,&#8203;T5,&#8203;T6,&#8203;T7,&#8203;T8&gt;</span>&nbsp;"
                + "<span class=\"member-name\">bigGenericMethod</span>&#8203;("
                + "<span class=\"arguments\"><a href=\"C.F0.html\" "
                + "title=\"interface in pkg\">C.F0</a>&lt;? extends T1&gt;&nbsp;t1,\n"
                + "<a href=\"C.F0.html\" title=\"interface in pkg\">"
                + "C.F0</a>&lt;? extends T2&gt;&nbsp;t2,\n"
                + "<a href=\"C.F0.html\" title=\"interface in pkg\">"
                + "C.F0</a>&lt;? extends T3&gt;&nbsp;t3,\n"
                + "<a href=\"C.F0.html\" title=\"interface in pkg\">"
                + "C.F0</a>&lt;? extends T4&gt;&nbsp;t4,\n"
                + "<a href=\"C.F0.html\" title=\"interface in pkg\">"
                + "C.F0</a>&lt;? extends T5&gt;&nbsp;t5,\n"
                + "<a href=\"C.F0.html\" title=\"interface in pkg\">"
                + "C.F0</a>&lt;? extends T6&gt;&nbsp;t6,\n"
                + "<a href=\"C.F0.html\" title=\"interface in pkg\">"
                + "C.F0</a>&lt;? extends T7&gt;&nbsp;t7,\n"
                + "<a href=\"C.F0.html\" title=\"interface in pkg\">"
                + "C.F0</a>&lt;? extends T8&gt;&nbsp;t8)</span>\n"
                + "                                                "
                + "throws <span class=\"exceptions\">java.lang.IllegalArgumentException,\n"
                + "java.lang.IllegalStateException</span></div>",

                "<div class=\"member-signature\"><span class=\"annotations\">"
                + "@Generated(value=\"SomeGeneratedName\",\n"
                + "           date=\"a date\",\n"
                + "           comments=\"some comment about the method below\")\n"
                + "</span><span class=\"modifiers\">public static</span>&nbsp;"
                + "<span class=\"type-parameters-long\">"
                + "&lt;T1 extends java.lang.AutoCloseable,&#8203;\n"
                + "T2 extends java.lang.AutoCloseable,&#8203;\n"
                + "T3 extends java.lang.AutoCloseable,&#8203;\n"
                + "T4 extends java.lang.AutoCloseable,&#8203;\n"
                + "T5 extends java.lang.AutoCloseable,&#8203;\n"
                + "T6 extends java.lang.AutoCloseable,&#8203;\n"
                + "T7 extends java.lang.AutoCloseable,&#8203;\n"
                + "T8 extends java.lang.AutoCloseable&gt;</span>\n"
                + "<span class=\"return-type\"><a href=\"C.With8Types.html\" "
                + "title=\"class in pkg\">C.With8Types</a>&lt;T1,&#8203;T2,&#8203;T3,"
                + "&#8203;T4,&#8203;T5,&#8203;T6,&#8203;T7,&#8203;T8&gt;</span>&nbsp;"
                + "<span class=\"member-name\">bigGenericAnnotatedMethod</span>&#8203;("
                + "<span class=\"arguments\"><a href=\"C.F0.html\" "
                + "title=\"interface in pkg\">C.F0</a>&lt;? extends T1&gt;&nbsp;t1,\n"
                + "<a href=\"C.F0.html\" title=\"interface in pkg\">"
                + "C.F0</a>&lt;? extends T2&gt;&nbsp;t2,\n"
                + "<a href=\"C.F0.html\" title=\"interface in pkg\">"
                + "C.F0</a>&lt;? extends T3&gt;&nbsp;t3,\n"
                + "<a href=\"C.F0.html\" title=\"interface in pkg\">"
                + "C.F0</a>&lt;? extends T4&gt;&nbsp;t4,\n"
                + "<a href=\"C.F0.html\" title=\"interface in pkg\">"
                + "C.F0</a>&lt;? extends T5&gt;&nbsp;t5,\n"
                + "<a href=\"C.F0.html\" title=\"interface in pkg\">"
                + "C.F0</a>&lt;? extends T6&gt;&nbsp;t6,\n"
                + "<a href=\"C.F0.html\" title=\"interface in pkg\">"
                + "C.F0</a>&lt;? extends T7&gt;&nbsp;t7,\n"
                + "<a href=\"C.F0.html\" title=\"interface in pkg\">"
                + "C.F0</a>&lt;? extends T8&gt;&nbsp;t8)</span>\n"
                + "                                                         "
                + "throws <span class=\"exceptions\">java.lang.IllegalArgumentException,\n"
                + "java.lang.IllegalStateException</span></div>\n"
                + "<div class=\"block\">Generic method with eight type args and annotation.</div>");

    }
}
