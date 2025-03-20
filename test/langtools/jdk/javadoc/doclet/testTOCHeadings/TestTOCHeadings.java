/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8352511
 * @summary  Show additional level of headings in table of contents
 * @library  /tools/lib ../../lib
 * @modules  jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @run main TestTOCHeadings
 */

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

import java.nio.file.Path;

public class TestTOCHeadings extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestTOCHeadings();
        tester.runTests();
    }

    ToolBox tb = new ToolBox();

    @Test
    public void testHeadings(Path base) throws Exception {
        Path src = base.resolve("src");

        tb.writeJavaFiles(src,
                """
                    package p;
                    /**
                     * <h1>First Level Heading</h1>
                     *
                     * Lorem ipsum
                     *
                     * <h2>Second Level Heading</h2>
                     *
                     * Lorem ipsum
                     *
                     * <h3>Third Level Heading</h3>
                     *
                     * Lorem ipsum
                     *
                     * <h2>Other Second Level Heading</h2>
                     *
                     * Lorem ipsum
                     *
                     * <h3>Other Third Level Heading</h3>
                     *
                     * Lorem ipsum
                     *
                     * <h4>Fourth Level Heading</h4>
                     *
                     * Lorem ipsum
                     *
                     * <h5>Fifth Level Heading</h5>
                     *
                     * Lorem ipsum
                     *
                     * <h6>Sixth Level Heading</h6>
                     *
                     * Lorem ipsum
                     */
                    public class C {
                        /**
                         * Method m.
                         *
                         * <h4>Subheading in m()</h4>
                         *
                         * Lorem ipsum
                         */
                        public void m() { }
                    }""");

        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--no-platform-links",
                "--source-path", src.toString(),
                "p");
        checkExit(Exit.OK);

        checkOutput("p/C.html", true,
                // note only the level 2 and 3 headings in the class description
                """
                    <ol class="toc-list" tabindex="-1">
                    <li><a href="#" tabindex="0">Description</a>
                    <ol class="toc-list">
                    <li><a href="#second-level-heading-heading" tabindex="0">Second Level Heading</a>
                    <ol class="toc-list">
                    <li><a href="#third-level-heading-heading" tabindex="0">Third Level Heading</a></li>
                    </ol>
                    </li>
                    <li><a href="#other-second-level-heading-heading" tabindex="0">Other Second Level Heading</a>
                    <ol class="toc-list">
                    <li><a href="#other-third-level-heading-heading" tabindex="0">Other Third Level Heading</a></li>
                    </ol>
                    </li>
                    </ol>
                    </li>
                    <li><a href="#constructor-summary" tabindex="0">Constructor Summary</a></li>
                    <li><a href="#method-summary" tabindex="0">Method Summary</a></li>
                    <li><a href="#constructor-detail" tabindex="0">Constructor Details</a>
                    <ol class="toc-list">
                    <li><a href="#%3Cinit%3E()" tabindex="0">C()</a></li>
                    </ol>
                    </li>
                    <li><a href="#method-detail" tabindex="0">Method Details</a>
                    <ol class="toc-list">
                    <li><a href="#m()" tabindex="0">m()</a></li>
                    </ol>
                    </li>
                    </ol>
                    """);
    }
}