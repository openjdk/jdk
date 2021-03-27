/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6251738 8226279
 * @summary JDK-8226279 javadoc should support a new at-spec tag
 * @library /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build toolbox.ToolBox javadoc.tester.*
 * @run main TestSpecTag
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestSpecTag extends JavadocTester {
    public static void main(String... args) throws Exception {
        TestSpecTag tester = new TestSpecTag();
        tester.runTests(m -> new Object[] { Path.of(m.getName()) });
    }

    ToolBox tb = new ToolBox();

    enum PlaceKind { FIRST_SENTENCE, OTHER_INLINE, BLOCK }
    enum LinkKind { ABSOLUTE, RELATIVE }

    @Test
    public void testBadSpecBaseURI(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, "package p; public class C { }");

        javadoc("-d", base.resolve("out").toString(),
                "--spec-base-uri", "http://[",
                "--source-path", src.toString(),
                "p");
        checkExit(Exit.CMDERR);
        checkOutput(Output.OUT, true,
                "javadoc: error - invalid URI: Expected closing bracket for IPv6 address at index 8: http://[");
    }

    @Test
    public void testBadSpecURI(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, "package p; /** @spec http://[ label */ public class C { }");

        javadoc("-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "p");
        checkExit(Exit.ERROR);

        checkOutput(Output.OUT, true,
                "testBadSpecURI/src/p/C.java:1:".replace('/', File.separatorChar)
                    + " error - invalid URI: Expected closing bracket for IPv6 address at index 8: http://[");

        checkOutput("p/C.html", true,
                """
                    <dl class="notes">
                    <dt>External Specifications</dt>
                    <dd><span id="label" class="search-tag-result">label</span></dd>
                    </dl>
                    """);

        checkOutput("external-specs.html", true,
                """
                    <div class="col-first even-row-color">label</div>
                    <div class="col-last even-row-color">
                    <ul class="ref-list">
                    <li><code><a href="p/C.html#label">class p.C</a></code></li>
                    </ul>
                    </div>""");
    }

    @Test
    public void testNavigation(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, "package p; /** @spec http://example.com label */ public class C { }");

        javadoc("-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "p");
        checkExit(Exit.OK);

        checkOutput("external-specs.html", true,
                """
                    <!-- ========= START OF TOP NAVBAR ======= -->
                    <div class="top-nav" id="navbar-top">
                    <div class="skip-nav"><a href="#skip-navbar-top" title="Skip navigation links">Skip navigation links</a></div>
                    <ul id="navbar-top-firstrow" class="nav-list" title="Navigation">
                    <li><a href="p/package-summary.html">Package</a></li>
                    <li>Class</li>
                    <li><a href="p/package-tree.html">Tree</a></li>
                    <li><a href="index-all.html">Index</a></li>
                    <li><a href="help-doc.html#external-specs">Help</a></li>
                    </ul>
                    </div>
                    <div class="sub-nav">
                    <div class="nav-list-search"><label for="search">SEARCH:</label>
                    <input type="text" id="search-input" value="search" disabled="disabled">
                    <input type="reset" id="reset-button" value="reset" disabled="disabled">
                    </div>
                    </div>
                    <!-- ========= END OF TOP NAVBAR ========= -->
                    """);
    }

    @Test
    public void testEncodedURI(Path base) throws IOException {
        Path src = base.resolve("src");
        // The default encoding for OpenJDK source files is ASCII.
        // The following writes a file using UTF-8 containing a non-ASCII character (section)
        // and a Unicode escape for another character (plus or minus)
        tb.writeJavaFiles(src, """
                package p;
                /**
                 * @spec http://example.com/a+b         space: plus
                 * @spec http://example.com/a%20b       space: percent
                 * @spec http://example.com/a\u00A7b    other: section; U+00A7, UTF-8 c2 a7
                 * @spec http://example.com/a\\u00B1b   unicode: plus or minus; U+00B1, UTF-8 c2 b1
                 */
                public class C { }
                """);

        // Ensure the source file is read using UTF-8
        javadoc("-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "-encoding", "UTF-8",
                "p");
        checkExit(Exit.OK);

        checkOutput("p/C.html", true,
                """
                    <dl class="notes">
                    <dt>External Specifications</dt>
                    <dd><a href="http://example.com/a+b"><span id="space:plus" class="search-tag-result">space: plus</span></a>,\s
                    <a href="http://example.com/a%20b"><span id="space:percent" class="search-tag-result">space: percent</span></a>,\s
                    <a href="http://example.com/a%C2%A7b"><span id="other:section;U+00A7,UTF-8c2a7" class="search-tag-result">other: section; U+00A7, UTF-8 c2 a7</span></a>,\s
                    <a href="http://example.com/a%C2%B1b"><span id="unicode:plusorminus;U+00B1,UTF-8c2b1" class="search-tag-result">unicode: plus or minus; U+00B1, UTF-8 c2 b1</span></a></dd>
                    </dl>
                    """);

        checkOutput("external-specs.html", true,
                """
                    <div class="table-header col-first">Specification</div>
                    <div class="table-header col-last">Referenced In</div>
                    <div class="col-first even-row-color"><a href="http://example.com/a%C2%A7b">other: section; U+00A7, UTF-8 c2 a7</a></div>
                    <div class="col-last even-row-color">
                    <ul class="ref-list">
                    <li><code><a href="p/C.html#other:section;U+00A7,UTF-8c2a7">class p.C</a></code></li>
                    </ul>
                    </div>
                    <div class="col-first odd-row-color"><a href="http://example.com/a%20b">space: percent</a></div>
                    <div class="col-last odd-row-color">
                    <ul class="ref-list">
                    <li><code><a href="p/C.html#space:percent">class p.C</a></code></li>
                    </ul>
                    </div>
                    <div class="col-first even-row-color"><a href="http://example.com/a+b">space: plus</a></div>
                    <div class="col-last even-row-color">
                    <ul class="ref-list">
                    <li><code><a href="p/C.html#space:plus">class p.C</a></code></li>
                    </ul>
                    </div>
                    <div class="col-first odd-row-color"><a href="http://example.com/a%C2%B1b">unicode: plus or minus; U+00B1, UTF-8 c2 b1</a></div>
                    <div class="col-last odd-row-color">
                    <ul class="ref-list">
                    <li><code><a href="p/C.html#unicode:plusorminus;U+00B1,UTF-8c2b1">class p.C</a></code></li>
                    </ul>
                    </div>""");
    }

    @Test
    public void testDuplicateRefs(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package p;
                /**
                 * @spec http://example.com/ example
                 */
                public class C {
                    /**
                     * @spec http://example.com/ example
                     */
                     public void m() { }
                    /**
                     * @spec http://example.com/ example
                     */
                     public int f;
                }
                """);

        javadoc("-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "p");
        checkExit(Exit.OK);

        checkOrder("p/C.html",
                "<h1 title=\"Class C\" class=\"title\">Class C</h1>",
                """
                    <dt>External Specifications</dt>
                    <dd><a href="http://example.com/"><span id="example" class="search-tag-result">example</span></a></dd>
                    """,
                "<section class=\"field-details\" id=\"field-detail\">",
                """
                    <dt>External Specifications</dt>
                    <dd><a href="http://example.com/"><span id="example-1" class="search-tag-result">example</span></a></dd>
                    """,
                "<section class=\"detail\" id=\"m()\">",
                """
                    <dt>External Specifications</dt>
                    <dd><a href="http://example.com/"><span id="example-2" class="search-tag-result">example</span></a></dd>
                    """);

        checkOutput("external-specs.html", true,
                """
                    <div class="col-first even-row-color"><a href="http://example.com/">example</a></div>
                    <div class="col-last even-row-color">
                    <ul class="ref-list">
                    <li><code><a href="p/C.html#example">class p.C</a></code></li>
                    <li><code><a href="p/C.html#example-1">p.C.f</a></code></li>
                    <li><code><a href="p/C.html#example-2">p.C.m()</a></code></li>
                    </ul>
                    </div>""");

    }

    @Test
    public void testMultiple(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package p;
                /**
                 * First sentence.
                 * First reference: {@spec http://example.com/1 example-1}.
                 * Another reference: {@spec http://example.com/2 example-2}.
                 * @spec http://example.com/3 example-3
                 * @spec http://example.com/4 example-4
                 */
                public class C { }
                """);

        javadoc("-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "p");
        checkExit(Exit.OK);

        checkOutput("p/C.html", true,
                """
                    <div class="block">First sentence.
                     First reference: <a href="http://example.com/1"><span id="example-1" class="search-tag-result">example-1</span></a>.
                     Another reference: <a href="http://example.com/2"><span id="example-2" class="search-tag-result">example-2</span></a>.</div>
                    """,
                """
                    <dt>External Specifications</dt>
                    <dd><a href="http://example.com/3"><span id="example-3" class="search-tag-result">example-3</span></a>,\s
                    <a href="http://example.com/4"><span id="example-4" class="search-tag-result">example-4</span></a></dd>
                    """);

        checkOutput("external-specs.html", true,
                """
                    <div class="col-first even-row-color"><a href="http://example.com/1">example-1</a></div>
                    <div class="col-last even-row-color">
                    <ul class="ref-list">
                    <li><code><a href="p/C.html#example-1">class p.C</a></code></li>
                    </ul>
                    </div>
                    <div class="col-first odd-row-color"><a href="http://example.com/2">example-2</a></div>
                    <div class="col-last odd-row-color">
                    <ul class="ref-list">
                    <li><code><a href="p/C.html#example-2">class p.C</a></code></li>
                    </ul>
                    </div>
                    <div class="col-first even-row-color"><a href="http://example.com/3">example-3</a></div>
                    <div class="col-last even-row-color">
                    <ul class="ref-list">
                    <li><code><a href="p/C.html#example-3">class p.C</a></code></li>
                    </ul>
                    </div>
                    <div class="col-first odd-row-color"><a href="http://example.com/4">example-4</a></div>
                    <div class="col-last odd-row-color">
                    <ul class="ref-list">
                    <li><code><a href="p/C.html#example-4">class p.C</a></code></li>
                    </ul>
                    </div>
                    """);

    }

    @Test
    public void testCombo(Path base) throws IOException {
        for (PlaceKind pk : PlaceKind.values()) {
            for (LinkKind lk : LinkKind.values()) {
                test(base, pk, lk);
            }
        }
    }

    void test(Path base, PlaceKind pk, LinkKind lk) throws IOException {
        Path dir = Files.createDirectories(base.resolve(pk.toString()).resolve(lk.toString()));
        Path src = genSource(dir, pk, lk);

        javadoc("-d", dir.resolve("out").toString(),
                "--spec-base-uri", "http://example.com/",
                "--source-path", src.toString(),
                "p");
        checkExit(Exit.OK);

        switch (pk) {
            case FIRST_SENTENCE, OTHER_INLINE ->
                checkOutput("p/C.html", true,
                        """
                                before <a href="http://example.com/#LK#">\
                                <span id="#LK#reference" class="search-tag-result">#LK# \
                                reference</span></a> after."""
                        .replaceAll("#LK#", lk.toString().toLowerCase()));

            case BLOCK ->
                checkOutput("p/C.html", true,
                        """
                            <dl class="notes">
                            <dt>External Specifications</dt>
                            <dd><a href="http://example.com/#LK#"><span id="#LK#reference" \
                            class="search-tag-result">#LK# reference</span></a></dd>
                            </dl>"""
                        .replaceAll("#LK#", lk.toString().toLowerCase()));

        }

        checkOutput("external-specs.html", true,
                """
                        <div class="col-first even-row-color"><a href="http://example.com/#LK#">#LK# reference</a></div>
                        <div class="col-last even-row-color">
                        <ul class="ref-list">
                        <li><code><a href="p/C.html##LK#reference">class p.C</a></code></li>
                        </ul>
                        </div>"""
                        .replaceAll("#LK#", lk.toString().toLowerCase()));
    }

    Path genSource(Path base, PlaceKind pk, LinkKind lk) throws IOException {
        Path src = base.resolve("src");
        String template = switch (pk) {
            case FIRST_SENTENCE -> """
                /**
                 * before {@spec #SPEC#} after.
                 */
                """;
            case OTHER_INLINE -> """
                /**
                 * First sentence. before {@spec #SPEC#} after.
                 */
                """;
            case BLOCK ->  """
                /**
                 * First sentence.
                 * @spec #SPEC#
                 */
                """;
        };
        String spec = switch (lk) {
            case ABSOLUTE -> "http://example.com/absolute absolute reference";
            case RELATIVE -> "relative                    relative reference";
        };
        String comment = template.replace("#SPEC#", spec);
        tb.writeJavaFiles(src,
                "package p;\n" + comment + "public class C { }");

        return src;
    }
}
