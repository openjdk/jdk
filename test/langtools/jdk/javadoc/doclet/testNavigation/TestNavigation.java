/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
 *           8196200 8196202 8223378 8258659 8261976 8320458
 * @summary  Make sure the Next/Prev Class links iterate through all types.
 *           Make sure the navagation is 2 columns, not 3.
 * @library  /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @run main TestNavigation
 */

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javadoc.tester.JavadocTester;
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
    public void test(Path ignore) {
        javadoc("-d", "out",
                "-overview", testSrc("overview.html"),
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.OK);
        checkSubNav();

        checkOutput("pkg/A.html", true,
                """
                    <ul id="navbar-top-firstrow" class="nav-list" title="Navigation">
                    <li><a href="../index.html">Overview</a></li>""");

        checkOutput("pkg/C.html", true,
                """
                    <ul id="navbar-top-firstrow" class="nav-list" title="Navigation">
                    <li><a href="../index.html">Overview</a></li>""");

        checkOutput("pkg/E.html", true,
                """
                    <ul id="navbar-top-firstrow" class="nav-list" title="Navigation">
                    <li><a href="../index.html">Overview</a></li>""");

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

        javadoc("-d", "out-navlinks",
                "-sourcepath", src.toString(),
                "pkg1");
        checkExit(Exit.OK);

        checkOrder("pkg1/A.X.html",
                """
                    <ul class="toc-list">
                    <li><a href="#" tabindex="0">Description</a></li>
                    <li><a href="#nested-class-summary" tabindex="0">Nested Class Summary</a></li>
                    <li><a href="#field-summary" tabindex="0">Field Summary</a></li>
                    <li><a href="#constructor-summary" tabindex="0">Constructor Summary</a></li>
                    <li><a href="#method-summary" tabindex="0">Method Summary</a></li>
                    <li><a href="#field-detail" tabindex="0">Field Details</a>
                    <ul class="toc-list">
                    <li><a href="#field" tabindex="0">field</a></li>
                    </ul>
                    </li>
                    <li><a href="#constructor-detail" tabindex="0">Constructor Details</a>
                    <ul class="toc-list">
                    <li><a href="#%3Cinit%3E()" tabindex="0">X()</a></li>
                    </ul>
                    </li>
                    <li><a href="#method-detail" tabindex="0">Method Details</a>
                    <ul class="toc-list">
                    <li><a href="#method()" tabindex="0">method()</a></li>
                    </ul>
                    </li>
                    </ul>""");

        checkOrder("pkg1/A.Y.html",
                """
                    <ul class="toc-list">
                    <li><a href="#" tabindex="0">Description</a></li>
                    <li><a href="#nested-class-summary" tabindex="0">Nested Class Summary</a></li>
                    <li><a href="#field-summary" tabindex="0">Field Summary</a></li>
                    <li><a href="#constructor-summary" tabindex="0">Constructor Summary</a></li>
                    <li><a href="#method-summary" tabindex="0">Method Summary</a></li>
                    <li><a href="#constructor-detail" tabindex="0">Constructor Details</a>
                    <ul class="toc-list">
                    <li><a href="#%3Cinit%3E()" tabindex="0">Y()</a></li>
                    </ul>
                    </li>
                    </ul>""");

        checkOrder("pkg1/A.X.IC.html",
                """
                    <ul class="toc-list">
                    <li><a href="#" tabindex="0">Description</a></li>
                    <li><a href="#constructor-summary" tabindex="0">Constructor Summary</a></li>
                    <li><a href="#method-summary" tabindex="0">Method Summary</a></li>
                    <li><a href="#constructor-detail" tabindex="0">Constructor Details</a>
                    <ul class="toc-list">
                    <li><a href="#%3Cinit%3E()" tabindex="0">IC()</a></li>
                    </ul>
                    </li>
                    </ul>""");

        checkOrder("pkg1/C.html",
                """
                    <ul class="toc-list">
                    <li><a href="#" tabindex="0">Description</a></li>
                    <li><a href="#constructor-summary" tabindex="0">Constructor Summary</a></li>
                    <li><a href="#method-summary" tabindex="0">Method Summary</a></li>
                    <li><a href="#constructor-detail" tabindex="0">Constructor Details</a>
                    <ul class="toc-list">
                    <li><a href="#%3Cinit%3E()" tabindex="0">C()</a></li>
                    </ul>
                    </li>
                    </ul>""");

        checkOrder("pkg1/InterfaceWithNoMembers.html",
                """
                    <ul class="toc-list">
                    <li><a href="#" tabindex="0">Description</a></li>
                    </ul>""");
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
}
