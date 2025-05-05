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
 * @bug      8298405 8352511
 * @summary  Markdown support in the standard doclet
 * @library  /tools/lib ../../lib
 * @modules  jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @run main TestMarkdownHeadings
 */

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

import java.nio.file.Path;

/**
 * Tests for Markdown headings.
 *
 * Markdown headings are handled specially when rendering the content.
 * In particular:
 * 1. heading levels are adjusted according to the context
 * 2. ids are automatically added
 * 3. top-level headings are added to the TOC
 *
 * Note that ids on headings are always automatically generated.
 * You cannot specify ids, such as with the pandoc header-attributes
 * extension.
 */
public class TestMarkdownHeadings extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestMarkdownHeadings();
        tester.runTests();
    }

    ToolBox tb = new ToolBox();

    @Test
    public void testHeading_ATX(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    /// First sentence.
                    ///
                    /// # ATX-style heading for package
                    ///
                    /// Lorem ipsum.
                    ///
                    /// ## ATX-style subheading for package
                    ///
                    /// Lorem ipsum.
                    package p;
                    """,
                """
                    package p;
                    /// First sentence.
                    ///
                    /// # ATX-style heading for class
                    ///
                    /// Lorum ipsum.
                    ///
                    /// ## ATX-style subheading for class
                    ///
                    /// Lorem ipsum.
                    public class C {
                        /// Constructor.
                        ///
                        /// # ATX-style heading for executable
                        ///
                        /// Lorem ipsum.
                        ///
                        /// ## ATX-style subheading for executable
                        ///
                        /// Lorem ipsum.
                        public C() { }
                    }
                    """);

        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--source-path", src.toString(),
                "p");

        checkOutput("p/package-summary.html", true,
                """
                    <div class="block"><p>First sentence.</p>
                    <h2 id="atx-style-heading-for-package-heading">ATX-style heading for package</h2>
                    <p>Lorem ipsum.</p>
                    <h3 id="atx-style-subheading-for-package-heading">ATX-style subheading for package</h3>
                    <p>Lorem ipsum.</p>
                    </div>""");

        checkOutput("p/C.html", true,
                """
                    <div class="block"><p>First sentence.</p>
                    <h2 id="atx-style-heading-for-class-heading">ATX-style heading for class</h2>
                    <p>Lorum ipsum.</p>
                    <h3 id="atx-style-subheading-for-class-heading">ATX-style subheading for class</h3>
                    <p>Lorem ipsum.</p>
                    </div>
                    """, """
                    <div class="block"><p>Constructor.</p>
                    <h4 id="atx-style-heading-for-executable-heading">ATX-style heading for executable</h4>
                    <p>Lorem ipsum.</p>
                    <h5 id="atx-style-subheading-for-executable-heading">ATX-style subheading for executable</h5>
                    <p>Lorem ipsum.</p>
                    </div>
                    """);

    }

    @Test
    public void testHeading_Setext(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    /// First sentence.
                    ///
                    /// Setext-style heading for package
                    /// ================================
                    ///
                    /// Lorem ipsum.
                    ///
                    /// Setext-style subheading for package
                    /// -----------------------------------
                    ///
                    /// Lorem ipsum.
                    package p;
                    """,
                """
                    package p;
                    /// First sentence.
                    ///
                    /// Setext-style heading for class
                    /// ==============================
                    ///
                    /// Lorum ipsum.
                    ///
                    /// Setext-style subheading for class
                    /// ---------------------------------
                    ///
                    /// Lorem ipsum.
                    public class C {
                        /// Constructor.
                        ///
                        /// Setext-style heading for executable
                        /// ===================================
                        ///
                        /// Lorem ipsum.
                        ///
                        /// Setext-style subheading for executable
                        /// --------------------------------------
                        ///
                        /// Lorem ipsum.
                        public C() { }
                    }
                    """);

        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--source-path", src.toString(),
                "p");

        checkOutput("p/package-summary.html", true,
                """
                    <div class="block"><p>First sentence.</p>
                    <h2 id="setext-style-heading-for-package-heading">Setext-style heading for package</h2>
                    <p>Lorem ipsum.</p>
                    <h3 id="setext-style-subheading-for-package-heading">Setext-style subheading for package</h3>
                    <p>Lorem ipsum.</p>
                    </div>""");

        checkOutput("p/C.html", true,
                """
                    <div class="block"><p>First sentence.</p>
                    <h2 id="setext-style-heading-for-class-heading">Setext-style heading for class</h2>
                    <p>Lorum ipsum.</p>
                    <h3 id="setext-style-subheading-for-class-heading">Setext-style subheading for class</h3>
                    <p>Lorem ipsum.</p>
                    </div>
                    """, """
                    <div class="block"><p>Constructor.</p>
                    <h4 id="setext-style-heading-for-executable-heading">Setext-style heading for executable</h4>
                    <p>Lorem ipsum.</p>
                    <h5 id="setext-style-subheading-for-executable-heading">Setext-style subheading for executable</h5>
                    <p>Lorem ipsum.</p>
                    </div>
                    """);
    }

    @Test
    public void testMaxHeadings(Path base) throws Exception {

        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    /// First sentence.
                    ///
                    /// # Level 1 heading for class
                    /// ## Level 2 subheading for class
                    /// ### Level 3 subheading for class
                    /// #### Level 4 heading for class
                    /// ##### Level 5 subheading for class
                    /// ###### Level 6 subheading for class
                    ///
                    /// Lorem ipsum.
                    public class C {
                        /// Constructor.
                        ///
                        /// # Level 1 heading for member
                        /// ## Level 2 subheading for member
                        /// ### Level 3 subheading for member
                        /// #### Level 4 heading for member
                        /// ##### Level 5 subheading for member
                        /// ###### Level 6 subheading for member
                        ///
                        /// Lorem ipsum.
                        public C() { }
                    }
                    """);

        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--source-path", src.toString(),
                "p");
        checkOutput("p/C.html", true,
                """
                    <div class="block"><p>First sentence.</p>
                    <h2 id="level-1-heading-for-class-heading">Level 1 heading for class</h2>
                    <h3 id="level-2-subheading-for-class-heading">Level 2 subheading for class</h3>
                    <h4 id="level-3-subheading-for-class-heading">Level 3 subheading for class</h4>
                    <h5 id="level-4-heading-for-class-heading">Level 4 heading for class</h5>
                    <h6 id="level-5-subheading-for-class-heading">Level 5 subheading for class</h6>
                    <h6 id="level-6-subheading-for-class-heading">Level 6 subheading for class</h6>
                    <p>Lorem ipsum.</p>
                    </div>""",
                """
                    <div class="block"><p>Constructor.</p>
                    <h4 id="level-1-heading-for-member-heading">Level 1 heading for member</h4>
                    <h5 id="level-2-subheading-for-member-heading">Level 2 subheading for member</h5>
                    <h6 id="level-3-subheading-for-member-heading">Level 3 subheading for member</h6>
                    <h6 id="level-4-heading-for-member-heading">Level 4 heading for member</h6>
                    <h6 id="level-5-subheading-for-member-heading">Level 5 subheading for member</h6>
                    <h6 id="level-6-subheading-for-member-heading">Level 6 subheading for member</h6>
                    <p>Lorem ipsum.</p>
                    </div>""");
    }

    @Test
    public void testHeading_TOC(Path base) throws Exception {
        Path src = base.resolve("src");

        tb.writeJavaFiles(src,
                """
                    package p;
                    /// First sentence.
                    ///
                    /// # ATX heading `code` _underline_ text
                    ///
                    /// Lorem ipsum
                    ///
                    /// ## ATX Subheading
                    ///
                    /// Lorem ipsum
                    ///
                    /// ### ATX Level 3 Heading
                    ///
                    /// Lorem ipsum
                    ///
                    /// Setext heading
                    /// ==============
                    ///
                    /// Lorem ipsum
                    ///
                    /// Setext subheading
                    /// ------------------
                    ///
                    /// Lorem ipsum
                    public class C {
                        /// Method m.
                        /// # subheading in m()
                        ///
                        /// Lorem ipsum
                        public void m() { }
                    }""");

        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--no-platform-links",
                "--source-path", src.toString(),
                "p");
        checkExit(Exit.OK);

        checkOutput("p/C.html", true,
                // note only the level 1 and 2 headings in the class description
                """
                    <ol class="toc-list" tabindex="-1">
                    <li><a href="#" tabindex="0">Description</a>
                    <ol class="toc-list">
                    <li><a href="#atx-heading-code-underline-text-heading" tabindex="0">ATX heading code underline text</a>
                    <ol class="toc-list">
                    <li><a href="#atx-subheading-heading" tabindex="0">ATX Subheading</a></li>
                    </ol>
                    </li>
                    <li><a href="#setext-heading-heading" tabindex="0">Setext heading</a>
                    <ol class="toc-list">
                    <li><a href="#setext-subheading-heading" tabindex="0">Setext subheading</a></li>
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