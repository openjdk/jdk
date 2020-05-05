/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8176231 8189843 8182765 8203791
 * @summary  Test JavaFX property.
 * @library  ../../lib/
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    javadoc.tester.* TestProperty
 * @run main TestProperty
 */

import javadoc.tester.JavadocTester;

public class TestProperty extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestProperty tester = new TestProperty();
        tester.runTests();
    }

    @Test
    public void testArrays() {
        javadoc("-d", "out",
                "-javafx",
                "--disable-javafx-strict-checks",
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.OK);

        checkOutput("pkg/MyClass.html", true,
                """
                    <div class="member-signature"><span class="modifiers">public final</span>&nbsp;<\
                    span class="return-type"><a href="ObjectProperty.html" title="class in pkg">Obje\
                    ctProperty</a>&lt;<a href="MyObj.html" title="class in pkg">MyObj</a>&gt;</span>\
                    &nbsp;<span class="member-name">goodProperty</span></div>
                    <div class="block">This is an Object property where the Object is a single Object.</div>
                    <dl class="notes">
                    <dt>See Also:</dt>
                    <dd><a href="#getGood()"><code>getGood()</code></a>,\s
                    <a href="#setGood(pkg.MyObj)"><code>setGood(MyObj)</code></a></dd>
                    </dl>""",

                """
                    <div class="member-signature"><span class="modifiers">public final</span>&nbsp;<\
                    span class="return-type"><a href="ObjectProperty.html" title="class in pkg">Obje\
                    ctProperty</a>&lt;<a href="MyObj.html" title="class in pkg">MyObj</a>[]&gt;</spa\
                    n>&nbsp;<span class="member-name">badProperty</span></div>
                    <div class="block">This is an Object property where the Object is an array.</div>
                    <dl class="notes">
                    <dt>See Also:</dt>
                    <dd><a href="#getBad()"><code>getBad()</code></a>,\s
                    <a href="#setBad(pkg.MyObj%5B%5D)"><code>setBad(MyObj[])</code></a></dd>
                    </dl>""",

                // id should not be used in the property table
                """
                    <tr class="alt-color">
                    <td class="col-first"><code><a href="ObjectProperty.html" title="class in pkg">O\
                    bjectProperty</a>&lt;<a href="MyObj.html" title="class in pkg">MyObj</a>[]&gt;</\
                    code></td>
                    <th class="col-second" scope="row"><code><span class="member-name-link"><a href=\
                    "#badProperty">bad</a></span></code></th>""",

                // id should be used in the method table
                """
                    <tr class="alt-color" id="i0">
                    <td class="col-first"><code><a href="ObjectProperty.html" title="class in pkg">O\
                    bjectProperty</a>&lt;<a href="MyObj.html" title="class in pkg">MyObj</a>[]&gt;</\
                    code></td>
                    <th class="col-second" scope="row"><code><span class="member-name-link"><a href=\
                    "#badProperty()">badProperty</a></span>()</code></th>"""
        );

        checkOutput("pkg/MyClassT.html", true,
                """
                    <div class="member-signature"><span class="modifiers">public final</span>&nbsp;<\
                    span class="return-type"><a href="ObjectProperty.html" title="class in pkg">Obje\
                    ctProperty</a>&lt;java.util.List&lt;<a href="MyClassT.html" title="type paramete\
                    r in MyClassT">T</a>&gt;&gt;</span>&nbsp;<span class="member-name">listProperty<\
                    /span></div>
                    <div class="block">This is an Object property where the Object is a single <code>List&lt;T&gt;</code>.</div>
                    <dl class="notes">
                    <dt>See Also:</dt>
                    <dd><a href="#getList()"><code>getList()</code></a>,\s
                    <a href="#setList(java.util.List)"><code>setList(List)</code></a></dd>
                    </dl>"""
        );
    }
}

