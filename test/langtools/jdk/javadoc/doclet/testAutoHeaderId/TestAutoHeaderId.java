/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8289332 8286470 8309471 8345555
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
        var tester = new TestAutoHeaderId();
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
                         * <h2>1.0 First Header</h2>
                         *
                         * <h3 id="fixed-id-1">1.1 Header with ID</h3>
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
                         * <h2>2.0 Extra (#*!. chars</h2>
                         *
                         * <h3 style="color: red;" class="some-class">Other attributes</h3>
                         *
                         * <h4></h4>
                         *
                         * <h2> 3.0 Multi-line
                         *       heading   with extra
                         *                 whitespace</h2>
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
        checkIds();
        checkSearchIndex();
        checkHtmlIndex();
    }

    private void checkIds() {
        checkOutput("p/C.html", true,
                """
                    <h2 id="1-0-first-header-heading">1.0 First Header</h2>
                    """,
                """
                    <h3 id="fixed-id-1">1.1 Header with ID</h3>
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
                    <h2 id="2-0-extra-chars-heading">2.0 Extra (#*!. chars</h2>
                    """,
                """
                    <h3 id="other-attributes-heading" style="color: red;" class="some-class">Other attributes</h3>
                    """,
                """
                    <h4 id="-heading"></h4>
                    """,
                """
                    <h2 id="3-0-multi-line-heading-with-extra-whitespace-heading"> 3.0 Multi-line
                          heading   with extra
                                    whitespace</h2>""");
    }

    private void checkSearchIndex() {
        checkOutput("tag-search-index.js", true,
                """
                    {"l":"Duplicate Text","h":"class p.C","k":"16","u":"p/C.html#duplicate-text-heading"}""",
                """
                    {"l":"Duplicate Text","h":"class p.C","k":"16","u":"p/C.html#duplicate-text-heading1"}""",
                """
                    {"l":"Embedded A-Tag with ID","h":"class p.C","k":"16","u":"p/C.html#fixed-id-2"}""",
                """
                    {"l":"Embedded Code Tag","h":"class p.C","k":"16","u":"p/C.html#embedded-code-tag-heading"}""",
                """
                    {"l":"Embedded Link Tag","h":"class p.C","k":"16","u":"p/C.html#embedded-link-tag-heading"}""",
                """
                    {"l":"2.0 Extra (#*!. chars","h":"class p.C","k":"16","u":"p/C.html#2-0-extra-chars-heading"}""",
                """
                    {"l":"1.0 First Header","h":"class p.C","k":"16","u":"p/C.html#1-0-first-header-heading"}""",
                """
                    {"l":"1.1 Header with ID","h":"class p.C","k":"16","u":"p/C.html#fixed-id-1"}""",
                """
                    {"l":"3.0 Multi-line heading with extra whitespace","h":"class p.C","k":"16","u":"p/C.html\
                    #3-0-multi-line-heading-with-extra-whitespace-heading"}""",
                """
                    {"l":"Other attributes","h":"class p.C","k":"16","u":"p/C.html#other-attributes-heading"}""");
    }

    private void checkHtmlIndex() {
        // Make sure section links are not included in static index pages
        checkOutput("index-all.html", true,
                """
                    <a href="#I:C">C</a>&nbsp;<a href="#I:D">D</a>&nbsp;<a href="#I:E">E</a>&nbsp;<a href="#I\
                    :F">F</a>&nbsp;<a href="#I:H">H</a>&nbsp;<a href="#I:M">M</a>&nbsp;<a href="#I:O">O</a>&n\
                    bsp;<a href="#I:P">P</a>&nbsp;<br><a href="allclasses-index.html">All&nbsp;Classes&nbsp;a\
                    nd&nbsp;Interfaces</a><span class="vertical-separator">|</span><a href="allpackages-index\
                    .html">All&nbsp;Packages</a>
                    <h2 class="title" id="I:C">C</h2>
                    <dl class="index">
                    <dt><a href="p/C.html" class="type-name-link" title="class in p">C</a> - Class in <a href\
                    ="p/package-summary.html">p</a></dt>
                    <dd>
                    <div class="block">First sentence.</div>
                    </dd>
                    </dl>
                    <h2 class="title" id="I:D">D</h2>
                    <dl class="index">
                    <dt><a href="p/C.html#duplicate-text-heading" class="search-tag-link">Duplicate Text</a> \
                    - Section in class p.C</dt>
                    <dd>&nbsp;</dd>
                    <dt><a href="p/C.html#duplicate-text-heading1" class="search-tag-link">Duplicate Text</a>\
                     - Section in class p.C</dt>
                    <dd>&nbsp;</dd>
                    </dl>
                    <h2 class="title" id="I:E">E</h2>
                    <dl class="index">
                    <dt><a href="p/C.html#2-0-extra-chars-heading" class="search-tag-link">2.0 Extra (#*!. ch\
                    ars</a> - Section in class p.C</dt>
                    <dd>&nbsp;</dd>
                    <dt><a href="p/C.html#fixed-id-2" class="search-tag-link">Embedded A-Tag with ID</a> - Se\
                    ction in class p.C</dt>
                    <dd>&nbsp;</dd>
                    <dt><a href="p/C.html#embedded-code-tag-heading" class="search-tag-link">Embedded Code Ta\
                    g</a> - Section in class p.C</dt>
                    <dd>&nbsp;</dd>
                    <dt><a href="p/C.html#embedded-link-tag-heading" class="search-tag-link">Embedded Link Ta\
                    g</a> - Section in class p.C</dt>
                    <dd>&nbsp;</dd>
                    </dl>
                    <h2 class="title" id="I:F">F</h2>
                    <dl class="index">
                    <dt><a href="p/C.html#1-0-first-header-heading" class="search-tag-link">1.0 First Header<\
                    /a> - Section in class p.C</dt>
                    <dd>&nbsp;</dd>
                    </dl>
                    <h2 class="title" id="I:H">H</h2>
                    <dl class="index">
                    <dt><a href="p/C.html#fixed-id-1" class="search-tag-link">1.1 Header with ID</a> - Sectio\
                    n in class p.C</dt>
                    <dd>&nbsp;</dd>
                    </dl>
                    <h2 class="title" id="I:M">M</h2>
                    <dl class="index">
                    <dt><a href="p/C.html#3-0-multi-line-heading-with-extra-whitespace-heading" class="search\
                    -tag-link">3.0 Multi-line heading with extra whitespace</a> - Section in class p.C</dt>
                    <dd>&nbsp;</dd>
                    </dl>
                    <h2 class="title" id="I:O">O</h2>
                    <dl class="index">
                    <dt><a href="p/C.html#other-attributes-heading" class="search-tag-link">Other attributes<\
                    /a> - Section in class p.C</dt>
                    <dd>&nbsp;</dd>
                    </dl>
                    <h2 class="title" id="I:P">P</h2>
                    <dl class="index">
                    <dt><a href="p/package-summary.html">p</a> - package p</dt>
                    <dd>&nbsp;</dd>
                    </dl>""");
    }
}
