/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8289332
 * @summary Auto-generate ids for user-defined headings
 * @library /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build javadoc.tester.* toolbox.ToolBox
 * @run main TestAutoHeaderId
 */

import java.nio.file.Path;

import toolbox.ToolBox;

import javadoc.tester.JavadocTester;

public class TestAutoHeaderId extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestAutoHeaderId tester = new TestAutoHeaderId();
        tester.runTests();
    }

    private final ToolBox tb;

    TestAutoHeaderId() {
        tb = new ToolBox();
    }

    @Test
    public void testAutoHeaderId(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                        package p;
                        /**
                         * First sentence.
                         *
                         * <h2>First Header</h2>
                         *
                         * <h3 id="fixed-id-1">Header with ID</h3>
                         *
                         * <h4><a id="fixed-id-2">Embedded A-Tag with ID</a></h4>
                         *
                         * <h5>{@code Embedded Code Tag}</h5>
                         *
                         * <h6>{@linkplain C Embedded Link Tag}</h6>
                         *
                         * <h3>Duplicate Text</h3>
                         *
                         * <h4>Duplicate Text</h4>
                         *
                         * <h2>Extra (#*!. chars</h2>
                         *
                         * <h3 style="color: red;" class="some-class">Other attributes</h3>
                         *
                         * <h4></h4>
                         *
                         * Last sentence.
                         */
                        public class C {
                            /** Comment. */
                            C() { }
                        }
                        """);

        javadoc("-d", base.resolve("api").toString(),
                "-sourcepath", src.toString(),
                "--no-platform-links", "p");

        checkOutput("p/C.html", true,
                """
                    <h2 id="first-header-heading">First Header</h2>
                    """,
                """
                    <h3 id="fixed-id-1">Header with ID</h3>
                    """,
                """
                    <h4><a id="fixed-id-2">Embedded A-Tag with ID</a></h4>
                    """,
                """
                    <h5 id="embedded-code-tag-heading"><code>Embedded Code Tag</code></h5>
                    """,
                """
                    <h6 id="embedded-link-tag-heading"><a href="C.html" title="class in p">Embedded Link Tag</a></h6>
                    """,
                """
                    <h3 id="duplicate-text-heading">Duplicate Text</h3>
                    """,
                """
                    <h4 id="duplicate-text-heading1">Duplicate Text</h4>
                    """,
                """
                    <h2 id="extra-chars-heading">Extra (#*!. chars</h2>
                    """,
                """
                    <h3 id="other-attributes-heading" style="color: red;" class="some-class">Other attributes</h3>
                    """,
                """
                    <h4 id="-heading"></h4>
                    """);
    }
}
