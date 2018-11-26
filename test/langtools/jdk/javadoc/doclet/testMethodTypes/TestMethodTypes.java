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
 * @bug      8002304 8024096 8193671 8196201 8203791 8184205
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
                "<div role=\"tablist\" aria-orientation=\"horizontal\"><button role=\"tab\""
                + " aria-selected=\"true\" aria-controls=\"memberSummary_tabpanel\" tabindex=\"0\""
                + " onkeydown=\"switchTab(event)\" id=\"t0\" class=\"activeTableTab\">All Methods</button>"
                + "<button role=\"tab\" aria-selected=\"false\" aria-controls=\"memberSummary_tabpanel\""
                + " tabindex=\"-1\" onkeydown=\"switchTab(event)\" id=\"t1\" class=\"tableTab\""
                + " onclick=\"show(1);\">Static Methods</button><button role=\"tab\" aria-selected=\"false\""
                + " aria-controls=\"memberSummary_tabpanel\" tabindex=\"-1\" onkeydown=\"switchTab(event)\""
                + " id=\"t2\" class=\"tableTab\" onclick=\"show(2);\">Instance Methods</button>"
                + "<button role=\"tab\" aria-selected=\"false\" aria-controls=\"memberSummary_tabpanel\""
                + " tabindex=\"-1\" onkeydown=\"switchTab(event)\" id=\"t4\" class=\"tableTab\""
                + " onclick=\"show(8);\">Concrete Methods</button><button role=\"tab\""
                + " aria-selected=\"false\" aria-controls=\"memberSummary_tabpanel\" tabindex=\"-1\""
                + " onkeydown=\"switchTab(event)\" id=\"t6\" class=\"tableTab\" onclick=\"show(32);\">"
                + "Deprecated Methods</button></div>",
                "<tr class=\"altColor\" id=\"i0\">");

        checkOutput("pkg1/B.html", true,
                "var data = {\"i0\":6,\"i1\":18,\"i2\":18,\"i3\":1,\"i4\":1,"
                + "\"i5\":6,\"i6\":6,\"i7\":6,\"i8\":6};\n",
                "<div role=\"tablist\" aria-orientation=\"horizontal\"><button role=\"tab\""
                + " aria-selected=\"true\" aria-controls=\"memberSummary_tabpanel\" tabindex=\"0\""
                + " onkeydown=\"switchTab(event)\" id=\"t0\" class=\"activeTableTab\">All Methods"
                + "</button><button role=\"tab\" aria-selected=\"false\""
                + " aria-controls=\"memberSummary_tabpanel\" tabindex=\"-1\""
                + " onkeydown=\"switchTab(event)\" id=\"t1\" class=\"tableTab\" onclick=\"show(1);\">"
                + "Static Methods</button><button role=\"tab\" aria-selected=\"false\""
                + " aria-controls=\"memberSummary_tabpanel\" tabindex=\"-1\" onkeydown=\"switchTab(event)\""
                + " id=\"t2\" class=\"tableTab\" onclick=\"show(2);\">Instance Methods</button>"
                + "<button role=\"tab\" aria-selected=\"false\" aria-controls=\"memberSummary_tabpanel\""
                + " tabindex=\"-1\" onkeydown=\"switchTab(event)\" id=\"t3\" class=\"tableTab\""
                + " onclick=\"show(4);\">Abstract Methods</button><button role=\"tab\""
                + " aria-selected=\"false\" aria-controls=\"memberSummary_tabpanel\" tabindex=\"-1\""
                + " onkeydown=\"switchTab(event)\" id=\"t5\" class=\"tableTab\" onclick=\"show(16);\">"
                + "Default Methods</button></div>");

        checkOutput("pkg1/D.html", true,
                "var data = {",
                "<div role=\"tablist\" aria-orientation=\"horizontal\"><button role=\"tab\""
                + " aria-selected=\"true\" aria-controls=\"memberSummary_tabpanel\" tabindex=\"0\""
                + " onkeydown=\"switchTab(event)\" id=\"t0\" class=\"activeTableTab\">All Methods</button>"
                + "<button role=\"tab\" aria-selected=\"false\" aria-controls=\"memberSummary_tabpanel\""
                + " tabindex=\"-1\" onkeydown=\"switchTab(event)\" id=\"t2\" class=\"tableTab\""
                + " onclick=\"show(2);\">Instance Methods</button><button role=\"tab\" aria-selected=\"false\""
                + " aria-controls=\"memberSummary_tabpanel\" tabindex=\"-1\" onkeydown=\"switchTab(event)\""
                + " id=\"t3\" class=\"tableTab\" onclick=\"show(4);\">Abstract Methods</button>"
                + "<button role=\"tab\" aria-selected=\"false\" aria-controls=\"memberSummary_tabpanel\""
                + " tabindex=\"-1\" onkeydown=\"switchTab(event)\" id=\"t4\" class=\"tableTab\""
                + " onclick=\"show(8);\">Concrete Methods</button><button role=\"tab\" aria-selected=\"false\""
                + " aria-controls=\"memberSummary_tabpanel\" tabindex=\"-1\" onkeydown=\"switchTab(event)\""
                + " id=\"t6\" class=\"tableTab\" onclick=\"show(32);\">Deprecated Methods</button></div>",
                "<tr class=\"altColor\" id=\"i0\">");

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
