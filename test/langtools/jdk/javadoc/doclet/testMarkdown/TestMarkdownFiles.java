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
 * @run main TestMarkdownFiles
 */

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

import java.nio.file.Path;
import java.util.List;

public class TestMarkdownFiles extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestMarkdownFiles();
        tester.runTests();
    }

    ToolBox tb = new ToolBox();

    @Test
    public void testDocFile(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                package p;
                /// First sentence.
                public class C { }
                """);
        tb.writeFile(src.resolve("p").resolve("doc-files").resolve("markdown.md"),
                """
                # This is a _Markdown_ heading

                Lorem ipsum""");

        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--source-path", src.toString(),
                "p");

        checkOutput("p/doc-files/markdown.html", true,
                """
                    <title>This is a Markdown heading</title>
                    """,
                """
                    <main role="main"><h1 id="this-is-a-markdown-heading-heading1">This is a <em>Markdown</em> heading</h1>
                    <p>Lorem ipsum</p>
                    </main>
                    """);
    }

    @Test
    public void testOverview(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                package p;
                /// First sentence.
                public class C { }
                """);
        var overviewFile = src.resolve("overview.md");
        tb.writeFile(overviewFile,
                """
                This is a _Markdown_ overview.
                Lorem ipsum""");

        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "-overview", overviewFile.toString(),
                "--source-path", src.toString(),
                "p");

        checkOutput("index.html", true,
                """
                    <div class="block">This is a <em>Markdown</em> overview.
                    Lorem ipsum</div>""");
    }
}