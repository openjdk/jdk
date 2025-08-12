/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8196027 8196202 8320458
 * @summary test navigation links
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.javadoc/jdk.javadoc.internal.api
 *          jdk.javadoc/jdk.javadoc.internal.tool
 * @library ../../lib /tools/lib
 * @build toolbox.ToolBox toolbox.ModuleBuilder javadoc.tester.*
 * @run main TestModuleNavigation
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javadoc.tester.JavadocTester;
import toolbox.ModuleBuilder;
import toolbox.ToolBox;

public class TestModuleNavigation extends JavadocTester {

    public final ToolBox tb;
    public static void main(String... args) throws Exception {
        var tester = new TestModuleNavigation ();
        tester.runTests();
    }

    public TestModuleNavigation () {
        tb = new ToolBox();
    }

    @Test
    public void testSingleModule(Path base) throws Exception {
        Path src = Files.createDirectories(base.resolve("src"));
        ModuleBuilder mb = new ModuleBuilder(tb, "m")
                .comment("Module m.")
                .exports("p1")
                .classes("package p1; @Deprecated public class A {}")
                .classes("package p1; public class B {}");
        mb.write(src);

        javadoc("-d", base.resolve("out-single-module").toString(),
                "--module-source-path", src.toString(),
                "--module", "m");
        checkExit(Exit.OK);

        checkOutput("index.html", true,
                """
                    <script type="text/javascript">window.location.replace('m/module-summary.html')</script>
                    <noscript>
                    <meta http-equiv="Refresh" content="0;m/module-summary.html">
                    </noscript>""",
                """
                    <p><a href="m/module-summary.html">m/module-summary.html</a></p>""");

        checkOutput("m/module-summary.html", true,
                """
                    <ul id="navbar-top-firstrow" class="nav-list" title="Navigation">
                    <li class="nav-bar-cell1-rev">Module</li>
                    <li><a href="../overview-tree.html">Tree</a></li>
                    <li><a href="../deprecated-list.html">Deprecated</a></li>
                    <li><a href="../index-all.html">Index</a></li>
                    <li><a href="../search.html">Search</a></li>
                    <li><a href="../help-doc.html#module">Help</a></li>
                    </ul>""");

        checkOutput("overview-tree.html", true,
                """
                    <ul id="navbar-top-firstrow" class="nav-list" title="Navigation">
                    <li><a href="m/module-summary.html">Module</a></li>
                    <li class="nav-bar-cell1-rev">Tree</li>
                    <li><a href="deprecated-list.html">Deprecated</a></li>
                    <li><a href="index-all.html">Index</a></li>
                    <li><a href="search.html">Search</a></li>
                    <li><a href="help-doc.html#tree">Help</a></li>
                    </ul>""");

        checkOutput("deprecated-list.html", true,
                """
                    <ul id="navbar-top-firstrow" class="nav-list" title="Navigation">
                    <li><a href="m/module-summary.html">Module</a></li>
                    <li><a href="overview-tree.html">Tree</a></li>
                    <li class="nav-bar-cell1-rev">Deprecated</li>
                    <li><a href="index-all.html">Index</a></li>
                    <li><a href="search.html">Search</a></li>
                    <li><a href="help-doc.html#deprecated">Help</a></li>
                    </ul>""");

        checkOutput("index-all.html", true,
                """
                    <ul id="navbar-top-firstrow" class="nav-list" title="Navigation">
                    <li><a href="m/module-summary.html">Module</a></li>
                    <li><a href="overview-tree.html">Tree</a></li>
                    <li><a href="deprecated-list.html">Deprecated</a></li>
                    <li class="nav-bar-cell1-rev">Index</li>
                    <li><a href="search.html">Search</a></li>
                    <li><a href="help-doc.html#index">Help</a></li>
                    </ul>""");

        checkOutput("search.html", true,
                """
                    <ul id="navbar-top-firstrow" class="nav-list" title="Navigation">
                    <li><a href="m/module-summary.html">Module</a></li>
                    <li><a href="overview-tree.html">Tree</a></li>
                    <li><a href="deprecated-list.html">Deprecated</a></li>
                    <li><a href="index-all.html">Index</a></li>
                    <li class="nav-bar-cell1-rev">Search</li>
                    <li><a href="help-doc.html#search">Help</a></li>
                    </ul>""");

        checkOutput("help-doc.html", true,
                """
                    <ul id="navbar-top-firstrow" class="nav-list" title="Navigation">
                    <li><a href="m/module-summary.html">Module</a></li>
                    <li><a href="overview-tree.html">Tree</a></li>
                    <li><a href="deprecated-list.html">Deprecated</a></li>
                    <li><a href="index-all.html">Index</a></li>
                    <li><a href="search.html">Search</a></li>
                    <li class="nav-bar-cell1-rev">Help</li>
                    </ul>""");

        checkOutput("m/p1/package-summary.html", true,
                """
                    <ul id="navbar-top-firstrow" class="nav-list" title="Navigation">
                    <li><a href="../module-summary.html">Module</a></li>
                    <li class="nav-bar-cell1-rev">Package</li>
                    <li><a href="package-tree.html">Tree</a></li>
                    <li><a href="../../deprecated-list.html">Deprecated</a></li>
                    <li><a href="../../index-all.html">Index</a></li>
                    <li><a href="../../search.html">Search</a></li>
                    <li><a href="../../help-doc.html#package">Help</a></li>
                    </ul>""");

        checkOutput("m/p1/A.html", true,
                """
                    <ul id="navbar-top-firstrow" class="nav-list" title="Navigation">
                    <li><a href="../module-summary.html">Module</a></li>
                    <li class="nav-bar-cell1-rev">Class</li>
                    <li><a href="package-tree.html">Tree</a></li>
                    <li><a href="../../deprecated-list.html">Deprecated</a></li>
                    <li><a href="../../index-all.html">Index</a></li>
                    <li><a href="../../search.html">Search</a></li>
                    <li><a href="../../help-doc.html#class">Help</a></li>
                    </ul>""");
    }


    @Test
    public void checkNavbar(Path base) throws Exception {
        Path src = Files.createDirectories(base.resolve("src"));
        ModuleBuilder mb = new ModuleBuilder(tb, "m")
                .comment("module m.\n@uses p1.A")
                .uses("p1.A")
                .uses("p1.B")
                .exports("p1")
                .classes("package p1; @Deprecated public class A {}")
                .classes("package p1; public class B {}");
        mb.write(src);
        ModuleBuilder mb1 = new ModuleBuilder(tb, "m2")
                .comment("module m2.\n@uses m2p1.Am2")
                .uses("m2p1.Am2")
                .uses("m2p1.Bm2")
                .exports("m2p1")
                .classes("package m2p1; public class Am2 {}")
                .classes("package m2p1; public class Bm2 {}");
        mb1.write(src);

        javadoc("-d", base.resolve("out").toString(), "-use",
                "-quiet",
                "--module-source-path", src.toString(),
                "--module", "m,m2");
        checkExit(Exit.OK);

        checkOutput("index.html", false,
                "Prev",
                "Next",
                "All&nbsp;Classes",
                """
                    <script type="text/javascript"><!--
                      allClassesLink = document.getElementById("allclasses_navbar_top");""",
                """
                    <script type="text/javascript"><!--
                      allClassesLink = document.getElementById("allclasses_navbar_bottom");""");

        checkOutput("m/module-summary.html", false,
                "Prev&nbsp;Module",
                "Next&nbsp;Module",
                "All&nbsp;Classes",
                """
                    <script type="text/javascript"><!--
                      allClassesLink = document.getElementById("allclasses_navbar_top");""",
                """
                    <script type="text/javascript"><!--
                      allClassesLink = document.getElementById("allclasses_navbar_bottom");""");

        checkOutput("m2/m2p1/package-summary.html", false,
                "Prev&nbsp;Package",
                "Next&nbsp;Package",
                "All&nbsp;Classes",
                """
                    <script type="text/javascript"><!--
                      allClassesLink = document.getElementById("allclasses_navbar_top");""",
                """
                    <script type="text/javascript"><!--
                      allClassesLink = document.getElementById("allclasses_navbar_bottom");""");

        checkOutput("m2/m2p1/Am2.html", false,
                "Prev&nbsp;Class",
                "Next&nbsp;Class",
                "All&nbsp;Classes",
                """
                    <script type="text/javascript"><!--
                      allClassesLink = document.getElementById("allclasses_navbar_top");""",
                """
                    <script type="text/javascript"><!--
                      allClassesLink = document.getElementById("allclasses_navbar_bottom");""");

        checkOutput("m2/m2p1/class-use/Am2.html", false,
                "Prev",
                "Next",
                "All&nbsp;Classes",
                """
                    <script type="text/javascript"><!--
                      allClassesLink = document.getElementById("allclasses_navbar_top");""",
                """
                    <script type="text/javascript"><!--
                      allClassesLink = document.getElementById("allclasses_navbar_bottom");""");

        checkOutput("m2/m2p1/package-tree.html", false,
                "Prev",
                "Next",
                "All&nbsp;Classes",
                """
                    <script type="text/javascript"><!--
                      allClassesLink = document.getElementById("allclasses_navbar_top");""",
                """
                    <script type="text/javascript"><!--
                      allClassesLink = document.getElementById("allclasses_navbar_bottom");""");

        checkOutput("deprecated-list.html", false,
                "Prev",
                "Next",
                "All&nbsp;Classes",
                """
                    <script type="text/javascript"><!--
                      allClassesLink = document.getElementById("allclasses_navbar_top");""",
                """
                    <script type="text/javascript"><!--
                      allClassesLink = document.getElementById("allclasses_navbar_bottom");""");

        checkOutput("index-all.html", false,
                "Prev",
                "Next",
                """
                    <script type="text/javascript"><!--
                      allClassesLink = document.getElementById("allclasses_navbar_top");""",
                """
                    <script type="text/javascript"><!--
                      allClassesLink = document.getElementById("allclasses_navbar_bottom");""");
    }
}
