/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @library  ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    javadoc.tester.*
 * @run main TestMethodTypes
 */

import javadoc.tester.JavadocTester;

public class TestMethodTypes extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestMethodTypes tester = new TestMethodTypes();
        tester.runTests();
    }

    @Test
    public void test() {
        javadoc("-d", "out",
                "-sourcepath", testSrc,
                "pkg1");
        checkExit(Exit.OK);

        checkOutput("pkg1/A.html", true,
                """
                    <div class="table-tabs" role="tablist" aria-orientation="horizontal"><button rol\
                    e="tab" aria-selected="true" aria-controls="method-summary-table.tabpanel" tabin\
                    dex="0" onkeydown="switchTab(event)" id="method-summary-table-tab0" onclick="sho\
                    w('method-summary-table', 'method-summary-table', 3)" class="active-table-tab">A\
                    ll Methods</button><button role="tab" aria-selected="false" aria-controls="metho\
                    d-summary-table.tabpanel" tabindex="-1" onkeydown="switchTab(event)" id="method-\
                    summary-table-tab1" onclick="show('method-summary-table', 'method-summary-table-\
                    tab1', 3)" class="table-tab">Static Methods</button><button role="tab" aria-sele\
                    cted="false" aria-controls="method-summary-table.tabpanel" tabindex="-1" onkeydo\
                    wn="switchTab(event)" id="method-summary-table-tab2" onclick="show('method-summa\
                    ry-table', 'method-summary-table-tab2', 3)" class="table-tab">Instance Methods</\
                    button><button role="tab" aria-selected="false" aria-controls="method-summary-ta\
                    ble.tabpanel" tabindex="-1" onkeydown="switchTab(event)" id="method-summary-tabl\
                    e-tab4" onclick="show('method-summary-table', 'method-summary-table-tab4', 3)" c\
                    lass="table-tab">Concrete Methods</button><button role="tab" aria-selected="fals\
                    e" aria-controls="method-summary-table.tabpanel" tabindex="-1" onkeydown="switch\
                    Tab(event)" id="method-summary-table-tab6" onclick="show('method-summary-table',\
                     'method-summary-table-tab6', 3)" class="table-tab">Deprecated Methods</button><\
                    /div>""",
                "<div class=\"col-first alt-color method-summary-table-tab2 method-summary-table-tab4 method-summary-table\">");

        checkOutput("pkg1/B.html", true,
                """
                    <div class="table-tabs" role="tablist" aria-orientation="horizontal"><button rol\
                    e="tab" aria-selected="true" aria-controls="method-summary-table.tabpanel" tabin\
                    dex="0" onkeydown="switchTab(event)" id="method-summary-table-tab0" onclick="sho\
                    w('method-summary-table', 'method-summary-table', 3)" class="active-table-tab">A\
                    ll Methods</button><button role="tab" aria-selected="false" aria-controls="metho\
                    d-summary-table.tabpanel" tabindex="-1" onkeydown="switchTab(event)" id="method-\
                    summary-table-tab1" onclick="show('method-summary-table', 'method-summary-table-\
                    tab1', 3)" class="table-tab">Static Methods</button><button role="tab" aria-sele\
                    cted="false" aria-controls="method-summary-table.tabpanel" tabindex="-1" onkeydo\
                    wn="switchTab(event)" id="method-summary-table-tab2" onclick="show('method-summa\
                    ry-table', 'method-summary-table-tab2', 3)" class="table-tab">Instance Methods</\
                    button><button role="tab" aria-selected="false" aria-controls="method-summary-ta\
                    ble.tabpanel" tabindex="-1" onkeydown="switchTab(event)" id="method-summary-tabl\
                    e-tab3" onclick="show('method-summary-table', 'method-summary-table-tab3', 3)" c\
                    lass="table-tab">Abstract Methods</button><button role="tab" aria-selected="fals\
                    e" aria-controls="method-summary-table.tabpanel" tabindex="-1" onkeydown="switch\
                    Tab(event)" id="method-summary-table-tab5" onclick="show('method-summary-table',\
                     'method-summary-table-tab5', 3)" class="table-tab">Default Methods</button></di\
                    v>""");

        checkOutput("pkg1/D.html", true,
                """
                    <div class="table-tabs" role="tablist" aria-orientation="horizontal"><button rol\
                    e="tab" aria-selected="true" aria-controls="method-summary-table.tabpanel" tabin\
                    dex="0" onkeydown="switchTab(event)" id="method-summary-table-tab0" onclick="sho\
                    w('method-summary-table', 'method-summary-table', 3)" class="active-table-tab">A\
                    ll Methods</button><button role="tab" aria-selected="false" aria-controls="metho\
                    d-summary-table.tabpanel" tabindex="-1" onkeydown="switchTab(event)" id="method-\
                    summary-table-tab2" onclick="show('method-summary-table', 'method-summary-table-\
                    tab2', 3)" class="table-tab">Instance Methods</button><button role="tab" aria-se\
                    lected="false" aria-controls="method-summary-table.tabpanel" tabindex="-1" onkey\
                    down="switchTab(event)" id="method-summary-table-tab3" onclick="show('method-sum\
                    mary-table', 'method-summary-table-tab3', 3)" class="table-tab">Abstract Methods\
                    </button><button role="tab" aria-selected="false" aria-controls="method-summary-\
                    table.tabpanel" tabindex="-1" onkeydown="switchTab(event)" id="method-summary-ta\
                    ble-tab4" onclick="show('method-summary-table', 'method-summary-table-tab4', 3)"\
                     class="table-tab">Concrete Methods</button><button role="tab" aria-selected="fa\
                    lse" aria-controls="method-summary-table.tabpanel" tabindex="-1" onkeydown="swit\
                    chTab(event)" id="method-summary-table-tab6" onclick="show('method-summary-table\
                    ', 'method-summary-table-tab6', 3)" class="table-tab">Deprecated Methods</button\
                    ></div>""",
                "<div class=\"col-first alt-color method-summary-table-tab2 method-summary-table-tab6 method-summary-table-tab4 method-summary-table\">");

        checkOutput("pkg1/A.html", false,
                "<div class=\"caption\"><span>Methods</span></div>");

        checkOutput("pkg1/B.html", false,
                "<div class=\"caption\"><span>Methods</span></div>");

        checkOutput("pkg1/D.html", false,
                "<div class=\"caption\"><span>Methods</span></div>");
    }
}
