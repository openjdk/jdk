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
 * @run main TestSpecFile
 */

import java.io.IOException;
import java.nio.file.Path;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestSpecFile extends JavadocTester {
    public static void main(String... args) throws Exception {
        TestSpecFile tester = new TestSpecFile();
        tester.runTests(m -> new Object[] { Path.of(m.getName()) });
    }

    ToolBox tb = new ToolBox();

    @Test
    public void testMissingFile(Path base) throws IOException {
        Path src = base.resolve("src");
        Path specFile = base.resolve("spec-file.txt");

        tb.writeJavaFiles(src,
                """
                    package p;
                    /**
                     * First sentence.
                     * @spec http://example.com/spec1.txt silly text 1
                     * @spec http://example.com/spec2.txt silly text 2
                     */
                    public class C { }
                    """);

        javadoc("-d", base.resolve("out").toString(),
                "--spec-list-file", specFile.toString(), // not available
                "--source-path", src.toString(),
                "p");
        checkExit(Exit.ERROR);

        checkOutput(Output.OUT, true,
                "javadoc: error - Error reading file: testMissingFile/spec-file.txt"
                    .replace("/", FS));
    }

    @Test
    public void testBadLines(Path base) throws IOException {
        Path src = base.resolve("src");
        Path specFile = base.resolve("spec-file.txt");

        tb.writeFile(specFile,
                """
                    http://example.com/no-title.txt
                    http://example.com/bad-uri.html#fragment Fragment Not Allowed
                    http://example.com/bad-uri.html?query Query Not Allowed
                    http://[ Syntax Error""");

        tb.writeJavaFiles(src,
                "package p; /** Comment. */ public class C { }");

        javadoc("-d", base.resolve("out").toString(),
                "--spec-list-file", specFile.toString(),
                "--source-path", src.toString(),
                "p");
        checkExit(Exit.ERROR);
        checkOutput(Output.OUT, true,
                "spec-file.txt:1: no title for specification",
                "spec-file.txt:2: URI has a fragment",
                "spec-file.txt:3: URI has a query",
                "spec-file.txt:4: invalid URI:"); // don't expect the exact reason
    }

    @Test
    public void testIgnorableLines(Path base) throws IOException {
        Path src = base.resolve("src");
        Path specFile = base.resolve("spec-file.txt");

        tb.writeFile(specFile,
                """
                    # Example copyright line

                    # first example
                    http://example.com/spec1.txt Canonical Spec1 Title

                    #second example
                    http://example.com/spec2.txt Canonical Spec2 Title

                    # EOF
                    """);

        tb.writeJavaFiles(src,
                """
                    package p;
                    /**
                     * First sentence.
                     * @spec http://example.com/spec1.txt silly text 1
                     * @spec http://example.com/spec2.txt silly text 2
                     */
                    public class C { }
                    """);

        javadoc("-d", base.resolve("out").toString(),
                "--spec-list-file", specFile.toString(),
                "--source-path", src.toString(),
                "p");
        checkExit(Exit.OK);

        // The main generated output uses the same description as in the tag,
        // but the id is derived from the canonical title used in the search index
        checkOutput("p/C.html", true,
                """
                    <dl class="notes">
                    <dt>External Specifications</dt>
                    <dd><a href="http://example.com/spec1.txt"><span id="CanonicalSpec1Title" class="search-tag-result">silly text 1</span></a>,\s
                    <a href="http://example.com/spec2.txt"><span id="CanonicalSpec2Title" class="search-tag-result">silly text 2</span></a></dd>
                    </dl>
                    """);
    }

    @Test
    public void testFileRefs(Path base) throws IOException {
        Path src = base.resolve("src");
        Path specFile = base.resolve("spec-file.txt");

        tb.writeFile(specFile,
                """
                    http://example.com/spec1.txt Canonical Spec1 Title
                    http://example.com/spec2.txt Canonical Spec2 Title
                    """);

        tb.writeJavaFiles(src,
                """
                    package p;
                    /**
                     * First sentence.
                     * @spec http://example.com/spec1.txt silly text 1
                     * @spec http://example.com/spec2.txt silly text 2
                     */
                    public class C { }
                    """);

        javadoc("-d", base.resolve("out").toString(),
                "--spec-list-file", specFile.toString(),
                "--source-path", src.toString(),
                "p");
        checkExit(Exit.OK);

        // The main generated output uses the same description as in the tag,
        // but the id is derived from the canonical title used in the search index
        checkOutput("p/C.html", true,
                """
                    <dl class="notes">
                    <dt>External Specifications</dt>
                    <dd><a href="http://example.com/spec1.txt"><span id="CanonicalSpec1Title" class="search-tag-result">silly text 1</span></a>,\s
                    <a href="http://example.com/spec2.txt"><span id="CanonicalSpec2Title" class="search-tag-result">silly text 2</span></a></dd>
                    </dl>
                    """);

        // The tag search index uses the canonical form
        checkOutput("tag-search-index.js", false, "silly");
        checkOutput("tag-search-index.js", true,
                """
                    {"l":"Canonical Spec1 Title","h":"class p.C","d":"External Specification","u":"p/C.html#CanonicalSpec1Title"},\
                    {"l":"Canonical Spec2 Title","h":"class p.C","d":"External Specification","u":"p/C.html#CanonicalSpec2Title"}""");

        // The external page has separate references to separate specs
        checkOutput("external-specs.html", false, "silly");
        checkOutput("external-specs.html", true,
                """
                    <div class="col-first even-row-color"><a href="http://example.com/spec1.txt">Canonical Spec1 Title</a></div>
                    <div class="col-last even-row-color">
                    <ul class="ref-list">
                    <li><code><a href="p/C.html#CanonicalSpec1Title">class p.C</a></code></li>
                    </ul>
                    </div>
                    <div class="col-first odd-row-color"><a href="http://example.com/spec2.txt">Canonical Spec2 Title</a></div>
                    <div class="col-last odd-row-color">
                    <ul class="ref-list">
                    <li><code><a href="p/C.html#CanonicalSpec2Title">class p.C</a></code></li>
                    </ul>
                    </div>""");
    }

    @Test
    public void testDirRefs(Path base) throws IOException {
        Path src = base.resolve("src");
        Path specFile = base.resolve("spec-file.txt");

        tb.writeFile(specFile,
                """
                    http://example.com/spec/ Canonical Title
                    """);

        tb.writeJavaFiles(src,
                """
                    package p;
                    /**
                     * First sentence.
                     * @spec http://example.com/spec/file1.html silly text 1
                     * @spec http://example.com/spec/file2.html silly text 2
                     */
                    public class C { }
                    """);

        javadoc("-d", base.resolve("out").toString(),
                "--spec-list-file", specFile.toString(),
                "--source-path", src.toString(),
                "p");
        checkExit(Exit.OK);

        // The main generated output uses the same description as in the tag,
        // but the id is derived from the canonical title used in the search index
        checkOutput("p/C.html", true,
                """
                    <dl class="notes">
                    <dt>External Specifications</dt>
                    <dd><a href="http://example.com/spec/file1.html"><span id="CanonicalTitle" class="search-tag-result">silly text 1</span></a>,\s
                    <a href="http://example.com/spec/file2.html"><span id="CanonicalTitle-1" class="search-tag-result">silly text 2</span></a></dd>
                    </dl>
                    """);

        // The tag search index uses the canonical form
        checkOutput("tag-search-index.js", false, "silly");
        checkOutput("tag-search-index.js", true,
                """
                    {"l":"Canonical Title","h":"class p.C","d":"External Specification","u":"p/C.html#CanonicalTitle"},\
                    {"l":"Canonical Title","h":"class p.C","d":"External Specification","u":"p/C.html#CanonicalTitle-1"}""");

        // The external page has separate references to the one overall spec
        checkOutput("external-specs.html", false, "silly");
        checkOutput("external-specs.html", true,
                """
                    <div class="col-first even-row-color"><a href="http://example.com/spec/file1.html">Canonical Title</a></div>
                    <div class="col-last even-row-color">
                    <ul class="ref-list">
                    <li><code><a href="p/C.html#CanonicalTitle">class p.C</a></code></li>
                    <li><code><a href="p/C.html#CanonicalTitle-1">class p.C</a></code></li>
                    </ul>
                    </div>""");
    }

}
