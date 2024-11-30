/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4681599 8288058
 * @summary Tests for the Constant Values page.
 * @library /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build toolbox.ToolBox javadoc.tester.*
 * @run main TestConstantValuesPage
 */

import java.nio.file.Path;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestConstantValuesPage extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestConstantValuesPage();
        tester.runTests();
    }

    ToolBox tb = new ToolBox();

    /**
     * Test to make sure that constant values page does not get
     * generated when doclet has nothing to document.
     */
    @Test
    public void testNoPage() {
        javadoc("-d", "out",
                "-sourcepath", testSrc,
                "foo");
        checkExit(Exit.CMDERR);

        checkOutput(Output.OUT, false,
                "constant-values.html...");
        checkFiles(false, "constant-values.html");
    }

    /**
     * Tests the "contents" list for a group of named packages in the unnamed module.
     */
    @Test
    public void testIndexNamed(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p1.p2a.p3a;
                    public class CA {
                        public static final int ia = 1;
                        public static final String sa = "string";
                    }
                    """,
                """
                    package p1.p2a.p3b;
                    public class CB {
                        public static final int ib = 1;
                        public static final String sb = "string";
                    }
                    """,
                """
                    package p1.p2b.p3c;
                    public class CC {
                        public static final int ic = 1;
                        public static final String sc = "string";
                    }
                    """,
                """
                    package p2;
                    public class CD {
                        public static final int id = 1;
                        public static final String sd = "string";
                    }
                    """);

        setAutomaticCheckLinks(true); // ensure link-checking enabled for this test

        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "-sourcepath", src.toString(),
                "p1.p2a.p3a", "p1.p2a.p3b", "p1.p2b.p3c");
        checkExit(Exit.OK);

        checkOutput("constant-values.html", true,
                """
                    <nav role="navigation" class="toc" aria-label="Table of contents">
                    <div class="toc-header">Contents&nbsp;""",
                """
                    <li><a href="#" tabindex="0">Constant Field Values</a>
                    <ol class="toc-list">
                    <li><a href="#p1.p2a" tabindex="0">p1.p2a.*</a></li>
                    <li><a href="#p1.p2b" tabindex="0">p1.p2b.*</a></li>
                    </ol>
                    </li>
                    </ol>
                    </nav>""");
    }

    /**
     * Tests the "contents" list for the unnamed package in the unnamed module.
     */
    @Test
    public void testIndexUnnamed(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    public class C {
                        public static final int ia = 1;
                        public static final String sa = "string";
                    }
                    """);

        setAutomaticCheckLinks(true); // ensure link-checking enabled for this test

        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "-sourcepath", src.toString(),
                src.resolve("C.java").toString());
        checkExit(Exit.OK);

        checkOutput("constant-values.html", true,
                """
                    <nav role="navigation" class="toc" aria-label="Table of contents">
                    <div class="toc-header">Contents&nbsp;""",
                """
                    <li><a href="#" tabindex="0">Constant Field Values</a>
                    <ol class="toc-list">
                    <li><a href="#unnamed-package" tabindex="0">Unnamed Package</a></li>
                    </ol>
                    </li>
                    </ol>
                    </nav>""");
    }

    /**
     * Tests the "contents" list for a group of named and unnamed packages in the unnamed module.
     */
    @Test
    public void testMixed(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p1.p2a.p3a;
                    public class CA {
                        public static final int ia = 1;
                        public static final String sa = "string";
                    }
                    """,
                """
                    public class C {
                        public static final int ia = 1;
                        public static final String sa = "string";
                    }
                    """);

        setAutomaticCheckLinks(true); // ensure link-checking enabled for this test

        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "-sourcepath", src.toString(),
                "p1.p2a.p3a", src.resolve("C.java").toString());
        checkExit(Exit.OK);

        checkOutput("constant-values.html", true,
                """
                    <nav role="navigation" class="toc" aria-label="Table of contents">
                    <div class="toc-header">Contents&nbsp;""",
                """
                   <li><a href="#" tabindex="0">Constant Field Values</a>
                   <ol class="toc-list">
                   <li><a href="#unnamed-package" tabindex="0">Unnamed Package</a></li>
                   <li><a href="#p1.p2a" tabindex="0">p1.p2a.*</a></li>
                   </ol>
                   </li>
                   </ol>
                   </nav>""");
    }

    /**
     * Tests the "contents" list for a group of named packages in named modules.
     */
    @Test
    public void testModules(Path base) throws Exception {
        Path src = base.resolve("src");
        Path src_mA = src.resolve("mA");
        tb.writeJavaFiles(src_mA,
                """
                    module mA {
                        exports p.a;
                        exports p.q.r1;
                    }
                    """,
                """
                    package p.a;
                    public class CA {
                        public static final int iA = 1;
                    }
                    """,
                """
                    package p.q.r1;
                    public class C1 {
                        public static final int i1 = 1;
                    }
                    """);
        Path src_mB = src.resolve("mB");
        tb.writeJavaFiles(src_mB,
                """
                    module mB {
                        exports p.b;
                        exports p.q.r2;
                    }
                    """,
                """
                    package p.b;
                    public class CB {
                        public static final int iB = 1;
                    }
                    """,
                """
                    package p.q.r2;
                    public class C2 {
                        public static final int i2 = 1;
                    }
                    """);

        setAutomaticCheckLinks(true); // ensure link-checking enabled for this test

        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--module-source-path", src.toString(),
                "--module", "mA,mB");
        checkExit(Exit.OK);

        checkOutput("constant-values.html", true,
                """
                    <nav role="navigation" class="toc" aria-label="Table of contents">
                    <div class="toc-header">Contents&nbsp;""",
                """
                    <li><a href="#" tabindex="0">Constant Field Values</a>
                    <ol class="toc-list">
                    <li><a href="#p.a" tabindex="0">p.a.*</a></li>
                    <li><a href="#p.b" tabindex="0">p.b.*</a></li>
                    <li><a href="#p.q" tabindex="0">p.q.*</a></li>
                    </ol>
                    </li>
                    </ol>
                    </nav>""");
    }
}
