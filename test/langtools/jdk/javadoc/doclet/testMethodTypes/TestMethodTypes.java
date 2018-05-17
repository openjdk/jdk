/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8002304 8024096 8193671 8196201
 * @summary  Test for various method type tabs in the method summary table
 * @author   Bhavesh Patel
 * @library  ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    JavadocTester
 * @run main TestMethodTypes
 */

public class TestMethodTypes extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestMethodTypes tester = new TestMethodTypes();
        tester.runTests();
    }

    @Test
    void test() {
        javadoc("-d", "out",
                "-sourcepath", testSrc,
                "pkg1");
        checkExit(Exit.OK);

        checkOutput("pkg1/A.html", true,
                "var data = {",
                "<caption><span id=\"t0\" class=\"activeTableTab\"><span>All "
                + "Methods</span><span class=\"tabEnd\">&nbsp;</span></span>"
                + "<span id=\"t1\" class=\"tableTab\"><span><a href=\"javascript:show(1);\">"
                + "Static Methods</a></span><span class=\"tabEnd\">&nbsp;</span></span>"
                + "<span id=\"t2\" class=\"tableTab\"><span><a href=\"javascript:show(2);\">"
                + "Instance Methods</a></span><span class=\"tabEnd\">&nbsp;</span></span>"
                + "<span id=\"t4\" class=\"tableTab\"><span><a href=\"javascript:show(8);\">"
                + "Concrete Methods</a></span><span class=\"tabEnd\">&nbsp;</span></span>"
                + "<span id=\"t6\" class=\"tableTab\"><span><a href=\"javascript:show(32);\">"
                + "Deprecated Methods</a></span><span class=\"tabEnd\">&nbsp;</span></span>"
                + "</caption>",
                "<tr id=\"i0\" class=\"altColor\">");

        checkOutput("pkg1/B.html", true,
                "var data = {\"i0\":6,\"i1\":18,\"i2\":18,\"i3\":1,\"i4\":1,"
                + "\"i5\":6,\"i6\":6,\"i7\":6,\"i8\":6};\n",
                "<caption><span id=\"t0\" class=\"activeTableTab\"><span>All Methods</span>"
                + "<span class=\"tabEnd\">&nbsp;</span></span><span id=\"t1\" class=\"tableTab\">"
                + "<span><a href=\"javascript:show(1);\">Static Methods</a></span>"
                + "<span class=\"tabEnd\">&nbsp;</span></span><span id=\"t2\" class=\"tableTab\">"
                + "<span><a href=\"javascript:show(2);\">Instance Methods</a></span>"
                + "<span class=\"tabEnd\">&nbsp;</span></span><span id=\"t3\" class=\"tableTab\">"
                + "<span><a href=\"javascript:show(4);\">Abstract Methods</a></span>"
                + "<span class=\"tabEnd\">&nbsp;</span></span><span id=\"t5\" class=\"tableTab\">"
                + "<span><a href=\"javascript:show(16);\">Default Methods</a></span>"
                + "<span class=\"tabEnd\">&nbsp;</span></span></caption>\n");

        checkOutput("pkg1/D.html", true,
                "var data = {",
                "<caption><span id=\"t0\" class=\"activeTableTab\"><span>All "
                + "Methods</span><span class=\"tabEnd\">&nbsp;</span></span>"
                + "<span id=\"t2\" class=\"tableTab\"><span><a href=\"javascript:show(2);\">"
                + "Instance Methods</a></span><span class=\"tabEnd\">&nbsp;</span></span>"
                + "<span id=\"t3\" class=\"tableTab\"><span><a href=\"javascript:show(4);\">"
                + "Abstract Methods</a></span><span class=\"tabEnd\">&nbsp;</span></span>"
                + "<span id=\"t4\" class=\"tableTab\"><span><a href=\"javascript:show(8);\">"
                + "Concrete Methods</a></span><span class=\"tabEnd\">&nbsp;</span></span>"
                + "<span id=\"t6\" class=\"tableTab\"><span><a href=\"javascript:show(32);\">"
                + "Deprecated Methods</a></span><span class=\"tabEnd\">&nbsp;</span></span>"
                + "</caption>",
                "<tr id=\"i0\" class=\"altColor\">");

        checkOutput("pkg1/A.html", false,
                "<caption><span>Methods</span><span class=\"tabEnd\">&nbsp;</span>"
                + "</caption>");

        checkOutput("pkg1/B.html", false,
                "<caption><span>Methods</span><span class=\"tabEnd\">&nbsp;</span>"
                + "</caption>");

        checkOutput("pkg1/D.html", false,
                "<caption><span>Methods</span><span class=\"tabEnd\">&nbsp;</span>"
                + "</caption>");
    }
}
