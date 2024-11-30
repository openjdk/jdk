/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      7025314 8023700 7198273 8025633 8026567 8081854 8196027 8182765
 *           8196200 8196202 8223378 8258659 8261976 8320458 8329537
 * @summary  Make sure the Next/Prev Class links iterate through all types.
 *           Make sure the navagation is 2 columns, not 3.
 * @library  /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.* builder.ClassBuilder
 * @run main TestNavigation
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javadoc.tester.JavadocTester;
import builder.ClassBuilder;
import toolbox.ToolBox;

public class TestNavigation extends JavadocTester {

    public final ToolBox tb;
    public static void main(String... args) throws Exception {
        var tester = new TestNavigation();
        tester.runTests();
    }

    public TestNavigation() {
        tb = new ToolBox();
    }

    @Test
    public void testOverview(Path base) {
        javadoc("-d", base.resolve("api").toString(),
                "-overview", testSrc("overview.html"),
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.OK);
        checkSubNav();

        checkOutput("index.html", true,
                """
                    <ul id="navbar-top-firstrow" class="nav-list" title="Navigation">
                    <li class="nav-bar-cell1-rev">Overview</li>
                    <li><a href="pkg/package-tree.html">Tree</a></li>
                    <li><a href="index-all.html">Index</a></li>
                    <li><a href="search.html">Search</a></li>
                    <li><a href="help-doc.html#overview">Help</a></li>
                    </ul>""");

        checkOutput("pkg/package-summary.html", true,
                """
                    <ul id="navbar-top-firstrow" class="nav-list" title="Navigation">
                    <li><a href="../index.html">Overview</a></li>
                    <li class="nav-bar-cell1-rev">Package</li>
                    <li><a href="package-tree.html">Tree</a></li>
                    <li><a href="../index-all.html">Index</a></li>
                    <li><a href="../search.html">Search</a></li>
                    <li><a href="../help-doc.html#package">Help</a></li>
                    </ul>""");

        checkOutput("pkg/A.html", true,
                """
                    <ul id="navbar-top-firstrow" class="nav-list" title="Navigation">
                    <li><a href="../index.html">Overview</a></li>
                    <li class="nav-bar-cell1-rev">Class</li>
                    <li><a href="package-tree.html">Tree</a></li>
                    <li><a href="../index-all.html">Index</a></li>
                    <li><a href="../search.html">Search</a></li>
                    <li><a href="../help-doc.html#class">Help</a></li>
                    </ul>""");

        checkOutput("pkg/C.html", true,
                """
                    <ul id="navbar-top-firstrow" class="nav-list" title="Navigation">
                    <li><a href="../index.html">Overview</a></li>
                    <li class="nav-bar-cell1-rev">Class</li>
                    <li><a href="package-tree.html">Tree</a></li>
                    <li><a href="../index-all.html">Index</a></li>
                    <li><a href="../search.html">Search</a></li>
                    <li><a href="../help-doc.html#class">Help</a></li>
                    </ul>""");

        checkOutput("pkg/E.html", true,
                """
                    <ul id="navbar-top-firstrow" class="nav-list" title="Navigation">
                    <li><a href="../index.html">Overview</a></li>
                    <li class="nav-bar-cell1-rev">Class</li>
                    <li><a href="package-tree.html">Tree</a></li>
                    <li><a href="../index-all.html">Index</a></li>
                    <li><a href="../search.html">Search</a></li>
                    <li><a href="../help-doc.html#class">Help</a></li>
                    </ul>""");

        checkOutput("pkg/I.html", true,
                // Test for 4664607
                """
                    <div class="skip-nav"><a href="#skip-navbar-top" title="Skip navigation links">Skip navigation links</a></div>
                    <ul id="navbar-top-firstrow" class="nav-list" title="Navigation">
                    """,
                """
                    <li><a href="../index.html">Overview</a></li>""");
    }

    @Test
    public void testNavLinks(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package pkg1; public class A {
                        /**
                         * Class with members.
                         */
                        public static class X {
                            /**
                             * A ctor
                             */
                            public X() {
                            }
                            /**
                             * A field
                             */
                            public int field;
                            /**
                             * A method
                             */
                            public void method() {
                            }
                            /**
                             * An inner class
                             */
                            public static class IC {
                            }
                        }
                        /**
                         * Class with all inherited members.
                         */
                        public static class Y extends X {
                        }
                    }""");

        tb.writeJavaFiles(src,
                "package pkg1; public class C {\n"
                + "}");

        tb.writeJavaFiles(src,
                """
                    package pkg1; public interface InterfaceWithNoMembers {
                    }""");

        javadoc("-d", base.resolve("api").toString(),
                "-sourcepath", src.toString(),
                "pkg1");
        checkExit(Exit.OK);

        checkOrder("pkg1/A.X.html",
                """
                    <ol class="sub-nav-list">
                    <li><a href="package-summary.html">pkg1</a></li>
                    <li><a href="A.html">A</a></li>
                    <li><a href="A.X.html" class="current-selection">X</a></li>
                    </ol>""",
                """
                    <ol class="toc-list">
                    <li><a href="#" tabindex="0">Description</a></li>
                    <li><a href="#nested-class-summary" tabindex="0">Nested Class Summary</a></li>
                    <li><a href="#field-summary" tabindex="0">Field Summary</a></li>
                    <li><a href="#constructor-summary" tabindex="0">Constructor Summary</a></li>
                    <li><a href="#method-summary" tabindex="0">Method Summary</a></li>
                    <li><a href="#field-detail" tabindex="0">Field Details</a>
                    <ol class="toc-list">
                    <li><a href="#field" tabindex="0">field</a></li>
                    </ol>
                    </li>
                    <li><a href="#constructor-detail" tabindex="0">Constructor Details</a>
                    <ol class="toc-list">
                    <li><a href="#%3Cinit%3E()" tabindex="0">X()</a></li>
                    </ol>
                    </li>
                    <li><a href="#method-detail" tabindex="0">Method Details</a>
                    <ol class="toc-list">
                    <li><a href="#method()" tabindex="0">method()</a></li>
                    </ol>
                    </li>
                    </ol>""");

        checkOrder("pkg1/A.Y.html",
                """
                    <ol class="sub-nav-list">
                    <li><a href="package-summary.html">pkg1</a></li>
                    <li><a href="A.html">A</a></li>
                    <li><a href="A.Y.html" class="current-selection">Y</a></li>
                    </ol>""",
                """
                    <ol class="toc-list">
                    <li><a href="#" tabindex="0">Description</a></li>
                    <li><a href="#nested-class-summary" tabindex="0">Nested Class Summary</a></li>
                    <li><a href="#field-summary" tabindex="0">Field Summary</a></li>
                    <li><a href="#constructor-summary" tabindex="0">Constructor Summary</a></li>
                    <li><a href="#method-summary" tabindex="0">Method Summary</a></li>
                    <li><a href="#constructor-detail" tabindex="0">Constructor Details</a>
                    <ol class="toc-list">
                    <li><a href="#%3Cinit%3E()" tabindex="0">Y()</a></li>
                    </ol>
                    </li>
                    </ol>""");

        checkOrder("pkg1/A.X.IC.html",
                """
                    <ol class="sub-nav-list">
                    <li><a href="package-summary.html">pkg1</a></li>
                    <li><a href="A.html">A</a></li>
                    <li><a href="A.X.html">X</a></li>
                    <li><a href="A.X.IC.html" class="current-selection">IC</a></li>
                    </ol>""",
                """
                    <ol class="toc-list">
                    <li><a href="#" tabindex="0">Description</a></li>
                    <li><a href="#constructor-summary" tabindex="0">Constructor Summary</a></li>
                    <li><a href="#method-summary" tabindex="0">Method Summary</a></li>
                    <li><a href="#constructor-detail" tabindex="0">Constructor Details</a>
                    <ol class="toc-list">
                    <li><a href="#%3Cinit%3E()" tabindex="0">IC()</a></li>
                    </ol>
                    </li>
                    </ol>""");

        checkOrder("pkg1/C.html",
                """
                    <ol class="toc-list">
                    <li><a href="#" tabindex="0">Description</a></li>
                    <li><a href="#constructor-summary" tabindex="0">Constructor Summary</a></li>
                    <li><a href="#method-summary" tabindex="0">Method Summary</a></li>
                    <li><a href="#constructor-detail" tabindex="0">Constructor Details</a>
                    <ol class="toc-list">
                    <li><a href="#%3Cinit%3E()" tabindex="0">C()</a></li>
                    </ol>
                    </li>
                    </ol>""");

        checkOrder("pkg1/InterfaceWithNoMembers.html",
                """
                    <ol class="toc-list">
                    <li><a href="#" tabindex="0">Description</a></li>
                    </ol>""");
    }

    private void checkSubNav() {

        checkOutput("pkg/A.html", false,
                "All&nbsp;Classes",
                """
                    <script type="text/javascript"><!--
                      allClassesLink = document.getElementById("allclasses_navbar_top");""",
                """
                    <script type="text/javascript"><!--
                      allClassesLink = document.getElementById("allclasses_navbar_bottom");""");

        checkOutput("pkg/C.html", false,
                "All&nbsp;Classes",
                """
                    <script type="text/javascript"><!--
                      allClassesLink = document.getElementById("allclasses_navbar_top");""",
                """
                    <script type="text/javascript"><!--
                      allClassesLink = document.getElementById("allclasses_navbar_bottom");""");

        checkOutput("pkg/E.html", false,
                "All&nbsp;Classes",
                """
                    <script type="text/javascript"><!--
                      allClassesLink = document.getElementById("allclasses_navbar_top");""",
                """
                    <script type="text/javascript"><!--
                      allClassesLink = document.getElementById("allclasses_navbar_bottom");""");

        checkOutput("pkg/I.html", false,
                "All&nbsp;Classes",
                """
                    <script type="text/javascript"><!--
                      allClassesLink = document.getElementById("allclasses_navbar_top");""",
                """
                    <script type="text/javascript"><!--
                      allClassesLink = document.getElementById("allclasses_navbar_bottom");""");

        checkOutput("pkg/package-summary.html", false,
                "All&nbsp;Classes",
                """
                    <script type="text/javascript"><!--
                      allClassesLink = document.getElementById("allclasses_navbar_top");""",
                """
                    <script type="text/javascript"><!--
                      allClassesLink = document.getElementById("allclasses_navbar_bottom");""");
    }

    @Test
    public void testSinglePackage(Path base) {
        javadoc("-d", base.resolve("api").toString(),
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.OK);
        checkSubNav();

        checkOutput("index.html", true,
                """
                    <meta http-equiv="Refresh" content="0;pkg/package-summary.html">""",
                """
                 <p><a href="pkg/package-summary.html">pkg/package-summary.html</a></p>""");

        checkOutput("pkg/package-summary.html", true,
                """
                    <ul id="navbar-top-firstrow" class="nav-list" title="Navigation">
                    <li class="nav-bar-cell1-rev">Package</li>
                    <li><a href="package-tree.html">Tree</a></li>
                    <li><a href="../index-all.html">Index</a></li>
                    <li><a href="../search.html">Search</a></li>
                    <li><a href="../help-doc.html#package">Help</a></li>
                    </ul>""");

        checkOutput("pkg/A.html", true,
                """
                    <ul id="navbar-top-firstrow" class="nav-list" title="Navigation">
                    <li><a href="package-summary.html">Package</a></li>
                    <li class="nav-bar-cell1-rev">Class</li>
                    <li><a href="package-tree.html">Tree</a></li>
                    <li><a href="../index-all.html">Index</a></li>
                    <li><a href="../search.html">Search</a></li>
                    <li><a href="../help-doc.html#class">Help</a></li>
                    </ul>""");

        checkOutput("pkg/C.html", true,
                """
                    <ul id="navbar-top-firstrow" class="nav-list" title="Navigation">
                    <li><a href="package-summary.html">Package</a></li>
                    <li class="nav-bar-cell1-rev">Class</li>
                    <li><a href="package-tree.html">Tree</a></li>
                    <li><a href="../index-all.html">Index</a></li>
                    <li><a href="../search.html">Search</a></li>
                    <li><a href="../help-doc.html#class">Help</a></li>
                    </ul>""");

        checkOutput("pkg/E.html", true,
                """
                    <ul id="navbar-top-firstrow" class="nav-list" title="Navigation">
                    <li><a href="package-summary.html">Package</a></li>
                    <li class="nav-bar-cell1-rev">Class</li>
                    <li><a href="package-tree.html">Tree</a></li>
                    <li><a href="../index-all.html">Index</a></li>
                    <li><a href="../search.html">Search</a></li>
                    <li><a href="../help-doc.html#class">Help</a></li>
                    </ul>""");

        checkOutput("pkg/I.html", true,
                // Test for 4664607
                """
                    <div class="skip-nav"><a href="#skip-navbar-top" title="Skip navigation links">Skip navigation links</a></div>
                    <ul id="navbar-top-firstrow" class="nav-list" title="Navigation">
                    """,
                """
                    <li><a href="package-summary.html">Package</a></li>
                    <li class="nav-bar-cell1-rev">Class</li>""");
    }

    @Test
    public void testUnnamedPackage(Path base) throws Exception {
        Path src = Files.createDirectories(base.resolve("src"));
        new ClassBuilder(tb, "C")
                .setModifiers("public", "class")
                .write(src);

        javadoc("-d", base.resolve("api").toString(),
                src.resolve("C.java").toString());
        checkExit(Exit.OK);

        checkOutput("index.html", true,
                """
                    <script type="text/javascript">window.location.replace('package-summary.html')</script>
                    <noscript>
                    <meta http-equiv="Refresh" content="0;package-summary.html">
                    </noscript>""");

        checkOutput("package-summary.html", true,
                """
                    <ul id="navbar-top-firstrow" class="nav-list" title="Navigation">
                    <li class="nav-bar-cell1-rev">Package</li>
                    <li><a href="package-tree.html">Tree</a></li>
                    <li><a href="index-all.html">Index</a></li>
                    <li><a href="search.html">Search</a></li>
                    <li><a href="help-doc.html#package">Help</a></li>
                    </ul>""",
                """
                    <ol class="sub-nav-list">
                    <li><a href="package-summary.html" class="current-selection">Unnamed Package</a></li>
                    </ol>""");

        checkOutput("C.html", true,
                """
                    <ul id="navbar-top-firstrow" class="nav-list" title="Navigation">
                    <li><a href="package-summary.html">Package</a></li>
                    <li class="nav-bar-cell1-rev">Class</li>
                    <li><a href="package-tree.html">Tree</a></li>
                    <li><a href="index-all.html">Index</a></li>
                    <li><a href="search.html">Search</a></li>
                    <li><a href="help-doc.html#class">Help</a></li>
                    </ul>""",
                """
                    <ol class="sub-nav-list">
                    <li><a href="package-summary.html">Unnamed Package</a></li>
                    <li><a href="C.html" class="current-selection">C</a></li>
                    </ol>""");
    }

}
