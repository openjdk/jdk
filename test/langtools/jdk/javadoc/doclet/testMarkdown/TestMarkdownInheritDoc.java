/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8298405
 * @summary  Markdown support in the standard doclet
 * @library  /tools/lib ../../lib
 * @modules  jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @run main TestMarkdownInheritDoc
 */

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

import java.nio.file.Path;
import java.util.List;

public class TestMarkdownInheritDoc extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestMarkdownInheritDoc();
        tester.runTests();
    }

    ToolBox tb = new ToolBox();

    @Test
    public void testInherit_md_md(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    public class Base {
                        /// Markdown comment.
                        /// @throws Exception Base _Markdown_
                        public void m() throws Exception { }
                    }""",
                """
                    package p;
                    public class Derived extends Base {
                        /// Markdown comment.
                        /// @throws {@inheritDoc}
                        public void m() throws Exception { }
                    }
                    """);
        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--no-platform-links",
                "--source-path", src.toString(),
                "p");

        checkOutput("p/Derived.html", true,
                """
                    <dt>Throws:</dt>
                    <dd><code>java.lang.Exception</code> - Base <em>Markdown</em></dd>
                    """);
    }

    @Test
    public void testInherit_md_plain(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    public class Base {
                        /// Markdown comment.
                        /// @throws Exception Base _Markdown_
                        public void m() throws Exception { }
                    }""",
                """
                    package p;
                    public class Derived extends Base {
                        /**
                         * Plain comment.
                         * @throws {@inheritDoc}
                         */
                         public void m() throws Exception { }
                    }
                    """);
        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--no-platform-links",
                "--source-path", src.toString(),
                "p");

        checkOutput("p/Derived.html", true,
                """
                    <dt>Throws:</dt>
                    <dd><code>java.lang.Exception</code> - Base <em>Markdown</em></dd>
                    """);
    }

    @Test
    public void testInherit_plain_md(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    public class Base {
                        /**
                         * Plain comment.
                         * @throws Exception Base _Not Markdown_
                         */
                         public void m() throws Exception { }
                    }""",
                """
                    package p;
                    public class Derived extends Base {
                        /// Markdown comment.
                        /// @throws {@inheritDoc}
                        public void m() throws Exception { }
                    }
                    """);
        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--no-platform-links",
                "--source-path", src.toString(),
                "p");

        checkOutput("p/Derived.html", true,
                """
                    <dt>Throws:</dt>
                    <dd><code>java.lang.Exception</code> - Base _Not Markdown_</dd>
                    """);
    }
}
