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
 * @bug      8004891 8184205
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
                "<div role=\"tablist\" aria-orientation=\"horizontal\"><button role=\"tab\" "
                + "aria-selected=\"true\" aria-controls=\"memberSummary_tabpanel\" tabindex=\"0\""
                + " onkeydown=\"switchTab(event)\" id=\"t0\" class=\"activeTableTab\">All Methods"
                + "</button><button role=\"tab\" aria-selected=\"false\""
                + " aria-controls=\"memberSummary_tabpanel\" tabindex=\"-1\" "
                + "onkeydown=\"switchTab(event)\" id=\"t2\" class=\"tableTab\""
                + " onclick=\"show(2);\">Instance Methods</button><button role=\"tab\""
                + " aria-selected=\"false\" aria-controls=\"memberSummary_tabpanel\" tabindex=\"-1\""
                + " onkeydown=\"switchTab(event)\" id=\"t3\" class=\"tableTab\" onclick=\"show(4);\">"
                + "Abstract Methods</button><button role=\"tab\" aria-selected=\"false\""
                + " aria-controls=\"memberSummary_tabpanel\" tabindex=\"-1\""
                + " onkeydown=\"switchTab(event)\" id=\"t5\" class=\"tableTab\" onclick=\"show(16);\">"
                + "Default Methods</button></div>");

        checkOutput("pkg/B.html", true,
                "<div role=\"tablist\" aria-orientation=\"horizontal\"><button role=\"tab\""
                + " aria-selected=\"true\" aria-controls=\"memberSummary_tabpanel\" tabindex=\"0\""
                + " onkeydown=\"switchTab(event)\" id=\"t0\" class=\"activeTableTab\">All Methods"
                + "</button><button role=\"tab\" aria-selected=\"false\""
                + " aria-controls=\"memberSummary_tabpanel\" tabindex=\"-1\""
                + " onkeydown=\"switchTab(event)\" id=\"t2\" class=\"tableTab\" onclick=\"show(2);\">"
                + "Instance Methods</button><button role=\"tab\" aria-selected=\"false\""
                + " aria-controls=\"memberSummary_tabpanel\" tabindex=\"-1\""
                + " onkeydown=\"switchTab(event)\" id=\"t3\" class=\"tableTab\" onclick=\"show(4);\">"
                + "Abstract Methods</button><button role=\"tab\" aria-selected=\"false\""
                + " aria-controls=\"memberSummary_tabpanel\" tabindex=\"-1\""
                + " onkeydown=\"switchTab(event)\" id=\"t4\" class=\"tableTab\""
                + " onclick=\"show(8);\">Concrete Methods</button></div>",
                "<td class=\"colFirst\"><code>abstract void</code></td>");

        checkOutput("pkg/C.html", true,
                "<div role=\"tablist\" aria-orientation=\"horizontal\"><button role=\"tab\""
                + " aria-selected=\"true\" aria-controls=\"memberSummary_tabpanel\" tabindex=\"0\""
                + " onkeydown=\"switchTab(event)\" id=\"t0\" class=\"activeTableTab\">All Methods"
                + "</button><button role=\"tab\" aria-selected=\"false\""
                + " aria-controls=\"memberSummary_tabpanel\" tabindex=\"-1\""
                + " onkeydown=\"switchTab(event)\" id=\"t2\" class=\"tableTab\" onclick=\"show(2);\">"
                + "Instance Methods</button><button role=\"tab\" aria-selected=\"false\""
                + " aria-controls=\"memberSummary_tabpanel\" tabindex=\"-1\""
                + " onkeydown=\"switchTab(event)\" id=\"t5\" class=\"tableTab\" onclick=\"show(16);\">"
                + "Default Methods</button></div>");

        checkOutput("pkg/A.html", false,
                "<td class=\"colFirst\"><code>abstract void</code></td>");

        checkOutput("pkg/B.html", false,
                "<button role=\"tab\" aria-selected=\"false\" aria-controls=\"memberSummary_tabpanel\""
                + " tabindex=\"-1\" onkeydown=\"switchTab(event)\" id=\"t5\" class=\"tableTab\""
                + " onclick=\"show(16);\">Default Methods</button>",
                "<td class=\"colFirst\"><code>default void</code></td>");

        checkOutput("pkg/C.html", false,
                "<button role=\"tab\" aria-selected=\"false\" aria-controls=\"memberSummary_tabpanel\""
                + " tabindex=\"-1\" onkeydown=\"switchTab(event)\" id=\"t3\" class=\"tableTab\""
                + " onclick=\"show(4);\">Abstract Methods</button>"
                + "<span class=\"tabEnd\">&nbsp;</span>");
    }
}
