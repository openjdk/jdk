/*
 * Copyright (c) 2003, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4951228 6290760 8025633 8026567 8081854 8162363 8175200 8177417 8186332 8182765
 * @summary  Test the case where the overridden method returns a different
 *           type than the method in the child class.  Make sure the
 *           documentation is inherited but the return type isn't.
 * @library  ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    javadoc.tester.*
 * @run main TestMemberSummary
 */

import javadoc.tester.JavadocTester;

public class TestMemberSummary extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestMemberSummary tester = new TestMemberSummary();
        tester.runTests();
    }

    @Test
    public void test() {
        javadoc("-d", "out",
                "-private",
                "-sourcepath", testSrc,
                "pkg","pkg2");
        checkExit(Exit.OK);

        checkOutput("pkg/PublicChild.html", true,
                // Check return type in member summary.
                """
                    <code><a href="PublicChild.html" title="class in pkg">PublicChild</a></code></div>
                    <div class="col-second even-row-color method-summary-table method-summary-table-\
                    tab2 method-summary-table-tab4"><code><a href="#returnTypeTest()" class="member-\
                    name-link">returnTypeTest</a>()</code></div>""",
                // Check return type in member detail.
                """
                    <div class="member-signature"><span class="modifiers">public</span>&nbsp;<span c\
                    lass="return-type"><a href="PublicChild.html" title="class in pkg">PublicChild</\
                    a></span>&nbsp;<span class="element-name">returnTypeTest</span>()</div>""",
                """
                    <div class="col-constructor-name even-row-color"><code><a href="#%3Cinit%3E()" c\
                    lass="member-name-link">PublicChild</a>()</code></div>
                    <div class="col-last even-row-color">&nbsp;</div>""");

        checkOutput("pkg/PrivateParent.html", true,
                """
                    <div class="col-first even-row-color"><code>private </code></div>
                    <div class="col-constructor-name even-row-color"><code><a href="#%3Cinit%3E(int)\
                    " class="member-name-link">PrivateParent</a>&#8203;(int&nbsp;i)</code></div>""");

        // Legacy anchor dimensions (6290760)
        checkOutput("pkg2/A.html", true,
                """
                    <section class="detail" id="f(T[])">
                    <h3 id="f(java.lang.Object[])">f</h3>
                    """);
    }
}
