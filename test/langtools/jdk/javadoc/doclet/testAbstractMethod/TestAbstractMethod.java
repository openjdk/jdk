/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8004891
 * @summary  Make sure that the abstract method is identified correctly
 *           if the abstract modifier is present explicitly or implicitly.
 * @author   bpatel
 * @library  ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    JavadocTester
 * @run main TestAbstractMethod
 */

public class TestAbstractMethod extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestAbstractMethod tester = new TestAbstractMethod();
        tester.runTests();
    }

    @Test
    void test() {
        javadoc("-d", "out",
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.OK);

        checkOutput("pkg/A.html", true,
                "<td class=\"colFirst\"><code>default void</code></td>",
                "<caption><span id=\"t0\" class=\"activeTableTab\"><span>"
                + "All Methods</span><span class=\"tabEnd\">&nbsp;</span></span>"
                + "<span id=\"t2\" class=\"tableTab\"><span>"
                + "<a href=\"javascript:show(2);\">Instance Methods</a></span>"
                + "<span class=\"tabEnd\">&nbsp;</span></span><span id=\"t3\" "
                + "class=\"tableTab\"><span><a href=\"javascript:show(4);\">"
                + "Abstract Methods</a></span><span class=\"tabEnd\">&nbsp;</span>"
                + "</span><span id=\"t5\" class=\"tableTab\"><span>"
                + "<a href=\"javascript:show(16);\">Default Methods</a></span>"
                + "<span class=\"tabEnd\">&nbsp;</span></span></caption>");

        checkOutput("pkg/B.html", true,
                "<caption><span id=\"t0\" class=\"activeTableTab\"><span>"
                + "All Methods</span><span class=\"tabEnd\">&nbsp;</span></span>"
                + "<span id=\"t2\" class=\"tableTab\"><span>"
                + "<a href=\"javascript:show(2);\">Instance Methods</a></span>"
                + "<span class=\"tabEnd\">&nbsp;</span></span><span id=\"t3\" "
                + "class=\"tableTab\"><span><a href=\"javascript:show(4);\">Abstract "
                + "Methods</a></span><span class=\"tabEnd\">&nbsp;</span></span>"
                + "<span id=\"t4\" class=\"tableTab\"><span>"
                + "<a href=\"javascript:show(8);\">Concrete Methods</a></span>"
                + "<span class=\"tabEnd\">&nbsp;</span></span></caption>",
                "<td class=\"colFirst\"><code>abstract void</code></td>");

        checkOutput("pkg/C.html", true,
                "<caption><span id=\"t0\" class=\"activeTableTab\"><span>"
                + "All Methods</span><span class=\"tabEnd\">&nbsp;</span></span>"
                + "<span id=\"t2\" class=\"tableTab\"><span>"
                + "<a href=\"javascript:show(2);\">Instance Methods</a></span>"
                + "<span class=\"tabEnd\">&nbsp;</span></span>"
                + "<span id=\"t5\" class=\"tableTab\"><span>"
                + "<a href=\"javascript:show(16);\">Default Methods</a></span>"
                + "<span class=\"tabEnd\">&nbsp;</span></span></caption>",
                "<td class=\"colFirst\"><code>default void</code></td>");

        checkOutput("pkg/A.html", false,
                "<td class=\"colFirst\"><code>abstract void</code></td>");

        checkOutput("pkg/B.html", false,
                "<span><a href=\"javascript:show(16);\">Default Methods</a></span>"
                + "<span class=\"tabEnd\">&nbsp;</span>",
                "<td class=\"colFirst\"><code>default void</code></td>");

        checkOutput("pkg/C.html", false,
                "<span><a href=\"javascript:show(4);\">Abstract Methods</a></span>"
                + "<span class=\"tabEnd\">&nbsp;</span>");
    }
}
