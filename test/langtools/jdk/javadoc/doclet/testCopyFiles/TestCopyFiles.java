/*
 * Copyright (c) 2016, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug  8157349 8185985 8194953 8214738 8347112
 * @summary  test copy of doc-files, and its contents for HTML meta content.
 * @library  ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    javadoc.tester.*
 * @run main TestCopyFiles
 */

import javadoc.tester.JavadocTester;

public class TestCopyFiles extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestCopyFiles();
        tester.runTests();
    }

    @Test
    public void testDocFilesInModulePackages() {
        javadoc("-d", "modules-out",
                "-top", "phi-TOP-phi",
                "-bottom", "phi-BOTTOM-phi",
                "-header", "phi-HEADER-phi",
                "-footer", "phi-FOOTER-phi",
                "-windowtitle", "phi-WINDOW-TITLE-phi",
                "--module-source-path", testSrc("modules"),
                "--module", "acme.mdle");
        checkExit(Exit.OK);

        checkOutput(Output.OUT, true,
                """
                    warning: The -footer option is no longer supported and will be ignored.
                      It may be removed in a future release.""");
        checkOrder("acme.mdle/p/doc-files/inpackage.html",
                """
                    "Hello World" (phi-WINDOW-TITLE-phi)""",
                "phi-TOP-phi",
                // check top navbar
                """
                    <a href="../package-tree.html">Tree</a>""",
                """
                    <a href="../../../deprecated-list.html">Deprecated</a>""",
                """
                    <a href="../../../index-all.html">Index</a>""",
                "phi-HEADER-phi",
                """
                    <a href="../../module-summary.html" title="Module acme.mdle">acme.mdle</a>""",
                """
                    <a href="../package-summary.html" title="Package p" class="current-selection">p</a>""",
                """
                    In a named module acme.module and named package <a href="../package-summary.html"><code>p</code></a>.""",
                "<dt>Since:</",
                "forever",
                // check bottom
                "phi-BOTTOM-phi"
        );

        checkOrder("acme.mdle/p/doc-files/sub-dir/SubReadme.html",
                """
                    SubReadme (phi-WINDOW-TITLE-phi)""",
                "phi-TOP-phi",
                // check top navbar
                """
                    <a href="../../package-tree.html">Tree</a>""",
                """
                    <a href="../../../../deprecated-list.html">Deprecated</a>""",
                """
                    <a href="../../../../index-all.html">Index</a>""",
                "phi-HEADER-phi",
                """
                    <a href="../../../module-summary.html" title="Module acme.mdle">acme.mdle</a>""",
                """
                    <a href="../../package-summary.html" title="Package p" class="current-selection">p</a>""",
                """
                   SubReadme.html at second level of doc-file directory for acme.module.""",
                // check footer
                "phi-BOTTOM-phi"
        );

        checkOrder("acme.mdle/p/doc-files/sub-dir/sub-dir-1/SubSubReadme.html",
                """
                    SubSubReadme (phi-WINDOW-TITLE-phi)""",
                "phi-TOP-phi",
                // check top navbar
                """
                    <a href="../../../package-tree.html">Tree</a>""",
                """
                    <a href="../../../../../deprecated-list.html">Deprecated</a>""",
                """
                    <a href="../../../../../index-all.html">Index</a>""",
                "phi-HEADER-phi",
                """
                    <a href="../../../../module-summary.html" title="Module acme.mdle">acme.mdle</a>""",
                """
                    <a href="../../../package-summary.html" title="Package p" class="current-selection">p</a>""",
                """
                   SubSubReadme.html at third level of doc-file directory.""",
                // check bottom
                "phi-BOTTOM-phi"
        );
    }

    @Test
    public void testDocFilesInMultiModulePackages() {
        javadoc("-d", "multi-modules-out",
                "-docfilessubdirs",
                "-top", "phi-TOP-phi",
                "-bottom", "phi-BOTTOM-phi",
                "-header", "phi-HEADER-phi",
                "-footer", "phi-FOOTER-phi",
                "-windowtitle", "phi-WINDOW-TITLE-phi",
                "--module-source-path", testSrc("modules"),
                "--module", "acme.mdle,acme2.mdle");
        checkExit(Exit.OK);

        checkOutput(Output.OUT, true,
                """
                    The -docfilessubdirs option is no longer required""",
                """
                    The -footer option is no longer supported and will be ignored""");

        checkOrder("acme.mdle/p/doc-files/inpackage.html",
                """
                    "Hello World" (phi-WINDOW-TITLE-phi)""",
                "phi-TOP-phi",
                // check top navbar
                """
                    <a href="../package-tree.html">Tree</a>""",
                """
                    <a href="../../../deprecated-list.html">Deprecated</a>""",
                """
                    <a href="../../../index-all.html">Index</a>""",
                "phi-HEADER-phi",
                """
                    <a href="../../module-summary.html" title="Module acme.mdle">acme.mdle</a>""",
                """
                    <a href="../package-summary.html" title="Package p" class="current-selection">p</a>""",
                """
                    In a named module acme.module and named package <a href="../package-summary.html"><code>p</code></a>.""",
                "<dt>Since:</",
                "forever",
                // check bottom
                "phi-BOTTOM-phi"
        );

        // check the bottom most doc file
        checkOrder("acme2.mdle/p2/doc-files/sub-dir/sub-dir-1/SubSubReadme.html",
                "SubSubReadme (phi-WINDOW-TITLE-phi)",
                "phi-TOP-phi",
                // check top navbar
                """
                    <a href="../../../package-tree.html">Tree</a>""",
                """
                    <a href="../../../../../deprecated-list.html">Deprecated</a>""",
                """
                    <a href="../../../../../index-all.html">Index</a>""",
                "phi-HEADER-phi",
                """
                    <a href="../../../../module-summary.html" title="Module acme2.mdle">acme2.mdle</a>""",
                """
                    <a href="../../../package-summary.html" title="Package p2" class="current-selection">p2</a>""",
                "SubSubReadme.html at third level of doc-file directory.",
                // check bottom
                "phi-BOTTOM-phi"
        );
    }

    @Test
    public void testDocFilesInMultiModulePackagesWithExclusion() {
        javadoc("-d", "multi-modules-out-exclusion",
                "-excludedocfilessubdir", "sub-dir",
                "--module-source-path", testSrc("modules"),
                "--module", "acme.mdle,acme2.mdle");
        checkExit(Exit.OK);

        checkOutput("acme.mdle/p/doc-files/inpackage.html", true,
                """
                    In a named module acme.module and named package <a href="../package-summary.html"><code>p</code></a>."""
        );

        checkFiles(false, "acme.mdle/p/doc-files/sub-dir");

        checkOutput("acme2.mdle/p2/doc-files/inpackage.html", true,
                """
                    In a named module acme2.mdle and named package <a href="../package-summary.html"><code>p2</code></a>."""
        );

        checkFiles(false, "acme2.mdle/p2/doc-files/sub-dir");
    }

    @Test
    public void testDocFilesInModulePackagesWithExclusion() {
        javadoc("-d", "modules-out-exclusion",
                "-excludedocfilessubdir", "sub-dir-1",
                "--module-source-path", testSrc("modules"),
                "--module", "acme.mdle");
        checkExit(Exit.OK);

        checkOutput("acme.mdle/p/doc-files/inpackage.html", true,
                """
                    In a named module acme.module and named package <a href="../package-summary.html"><code>p</code></a>."""
        );

        checkOutput("acme.mdle/p/doc-files/sub-dir/SubReadme.html", true,
                """
                    SubReadme.html at second level of doc-file directory for acme.module."""
        );

        checkFiles(false, "acme.mdle/p/doc-files/sub-dir/sub-dir-1");

        checkFiles(false, "acme2.mdle/p2/doc-files");
    }

    @Test
    public void testDocFilesInModulePackagesWithWildcardExclusion() {
        javadoc("-d", "modules-out-wildcard-exclusion",
                "-excludedocfilessubdir", "*",
                "--module-source-path", testSrc("modules"),
                "--module", "acme.mdle");
        checkExit(Exit.OK);

        checkOutput("acme.mdle/p/doc-files/inpackage.html", true,
                """
                    In a named module acme.module and named package <a href="../package-summary.html"><code>p</code></a>."""
        );

        checkFiles(false, "acme.mdle/p/doc-files/sub-dir");
    }

    @Test
    public void testDocFilesInPackages() {
        javadoc("-d", "packages-out",
                "-sourcepath", testSrc("packages"),
                "p1");
        checkExit(Exit.OK);
        checkOutput("p1/doc-files/inpackage.html", true,
                "A named package in an unnamed module"
        );

        checkOutput("p1/doc-files/sub-dir/SubReadme.html", true,
                "<title>SubReadme</title>",
                "SubReadme.html at second level of doc-file directory."
        );
    }

    @Test
    public void testDocFilesInPackagesWithExclusion() {
        javadoc("-d", "packages-out-exclusion",
                "-excludedocfilessubdir", "sub-dir",
                "-sourcepath", testSrc("packages"),
                "p1");
        checkExit(Exit.OK);

        checkOutput("p1/doc-files/inpackage.html", true,
                "A named package in an unnamed module"
        );

        checkFiles(false, "p1/doc-files/sub-dir");
    }

    @Test
    public void testDocFilesInPackagesWithWildcardExclusion() {
        javadoc("-d", "packages-out-wildcard-exclusion",
                "-excludedocfilessubdir", "*",
                "-sourcepath", testSrc("packages"),
                "p1");
        checkExit(Exit.OK);

        checkOutput("p1/doc-files/inpackage.html", true,
                "A named package in an unnamed module"
        );

        checkFiles(false, "p1/doc-files/sub-dir");
    }

    @Test
    public void testDocFilesInUnnamedPackages() {
        javadoc("-d", "unnamed-out",
                "-windowtitle", "phi-WINDOW-TITLE-phi",
                "-sourcepath", testSrc("unnamed"),
                testSrc("unnamed/Foo.java")
        );
        checkExit(Exit.OK);
        checkOutput("doc-files/inpackage.html", true,
                """
                    <title>(phi-WINDOW-TITLE-phi)</title>
                    """,
                "In an unnamed package"
        );

        checkOutput("doc-files/doc-file/SubReadme.html", true,
                """
                    <title>Beep Beep (phi-WINDOW-TITLE-phi)</title>
                    """,
                "SubReadme.html at second level of doc-file directory for unnamed package."
        );
    }

    @Test
    public void testDocFilesInUnnamedPackagesWithExclusion() {
        javadoc("-d", "unnamed-out-exclusion",
                "-windowtitle", "phi-WINDOW-TITLE-phi",
                "-excludedocfilessubdir", "doc-file",
                "-sourcepath", testSrc("unnamed"),
                testSrc("unnamed/Foo.java")
        );
        checkExit(Exit.OK);
        checkOutput("doc-files/inpackage.html", true,
                """
                    <title>(phi-WINDOW-TITLE-phi)</title>
                    """,
                "In an unnamed package"
        );

        checkFiles(false, "doc-files/doc-file");
    }

    @Test
    public void testDocFilesInUnnamedPackagesWithWildcardExclusion() {
        javadoc("-d", "unnamed-out-wildcard-exclusion",
                "-windowtitle", "phi-WINDOW-TITLE-phi",
                "-excludedocfilessubdir", "*",
                "-sourcepath", testSrc("unnamed"),
                testSrc("unnamed/Foo.java")
        );
        checkExit(Exit.OK);
        checkOutput("doc-files/inpackage.html", true,
                """
                    <title>(phi-WINDOW-TITLE-phi)</title>
                    """,
                "In an unnamed package"
        );

        checkFiles(false, "doc-files/doc-file");
    }

    @Test
    public void testCopyThrough() {
        javadoc("-d", "copy",
                "-sourcepath", testSrc("packages"),
                "p2");
        checkExit(Exit.OK);
        checkOutput("p2/doc-files/case2.html", true,
                "<!-- Generated by javadoc",
                """
                    <style type="text/css">
                    body {
                            font-family: Helvetica, Arial, sans-serif;
                            font-size: 14px;
                          }
                        </style>""");
        checkOutput("p2/doc-files/case3.html", true,
                "<!-- Generated by javadoc",
                """
                    <style>
                    h1 {color:red;}
                            p {color:blue;}
                          </style>""");
        checkOutput("p2/doc-files/case4.html", true,
                "<!-- Generated by javadoc",
                """
                    <link rel="stylesheet" type="text/css" href="theme.css">""");
    }
}
