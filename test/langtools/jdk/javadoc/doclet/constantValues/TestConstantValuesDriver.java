/*
 * Copyright (c) 2002, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4504730 4526070 5077317 8162363
 * @summary Test the generation of constant-values.html.
 * @library ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build javadoc.tester.*
 * @run main TestConstantValuesDriver
 */
import javadoc.tester.JavadocTester;

public class TestConstantValuesDriver extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestConstantValuesDriver tester = new TestConstantValuesDriver();
        tester.runTests();
    }

    @Test
    public void test() {
        javadoc("-d", "out",
                testSrc("TestConstantValues.java"),
                testSrc("TestConstantValues2.java"),
                testSrc("A.java"));
        checkExit(Exit.OK);

        checkOutput("constant-values.html", true,
                "TEST1PASSES",
                "TEST2PASSES",
                "TEST3PASSES",
                "TEST4PASSES",
                """
                    <code>"&lt;Hello World&gt;"</code>""",
                """
                    <code id="TestConstantValues.BYTE_MAX_VALUE">public&nbsp;static&nbsp;final&nbsp;byte</code></td>
                    <th class="col-second" scope="row"><code><a href="TestConstantValues.html#BYTE_MAX_VALUE">BYTE_MAX_VALUE</a></code></th>
                    <td class="col-last"><code>127</code></td>""",
                """
                    <code id="TestConstantValues.BYTE_MIN_VALUE">public&nbsp;static&nbsp;final&nbsp;byte</code></td>
                    <th class="col-second" scope="row"><code><a href="TestConstantValues.html#BYTE_MIN_VALUE">BYTE_MIN_VALUE</a></code></th>
                    <td class="col-last"><code>-127</code></td>""",
                """
                    <code id="TestConstantValues.CHAR_MAX_VALUE">public&nbsp;static&nbsp;final&nbsp;char</code></td>
                    <th class="col-second" scope="row"><code><a href="TestConstantValues.html#CHAR_MAX_VALUE">CHAR_MAX_VALUE</a></code></th>
                    <td class="col-last"><code>65535</code></td>""",
                """
                    <code id="TestConstantValues.DOUBLE_MAX_VALUE">public&nbsp;static&nbsp;final&nbsp;double</code></td>""",
                    """
                        <th class="col-second" scope="row"><code><a href="TestConstantValues.html#DOUBLE\
                        _MAX_VALUE">DOUBLE_MAX_VALUE</a></code></th>
                        <td class="col-last"><code>1.7976931348623157E308</code></td>""",
                """
                    <code id="TestConstantValues.DOUBLE_MIN_VALUE">public&nbsp;static&nbsp;final&nbsp;double</code></td>
                    <th class="col-second" scope="row"><code><a href="TestConstantValues.html#DOUBLE\
                    _MIN_VALUE">DOUBLE_MIN_VALUE</a></code></th>""",
                """
                    <code id="TestConstantValues.GOODBYE">public&nbsp;static&nbsp;final&nbsp;boolean</code></td>
                    <th class="col-second" scope="row"><code><a href="TestConstantValues.html#GOODBYE">GOODBYE</a></code></th>""",
                """
                    <code id="TestConstantValues.HELLO">public&nbsp;static&nbsp;final&nbsp;boolean</code></td>
                    <th class="col-second" scope="row"><code><a href="TestConstantValues.html#HELLO">HELLO</a></code></th>
                    <td class="col-last"><code>true</code></td>"""
        );
    }
}
