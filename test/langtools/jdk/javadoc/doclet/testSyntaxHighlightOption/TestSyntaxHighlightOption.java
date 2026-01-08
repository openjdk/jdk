/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8348282
 * @summary Add option for syntax highlighting in javadoc snippets
 * @library /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build toolbox.ToolBox javadoc.tester.*
 * @run main TestSyntaxHighlightOption
 */

import java.io.IOException;
import java.nio.file.Path;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestSyntaxHighlightOption extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestSyntaxHighlightOption();
        tester.runTests();
    }

    ToolBox tb = new ToolBox();
    Path src = Path.of("src");


    TestSyntaxHighlightOption() throws IOException {
        tb.writeJavaFiles(src, """
                    package p;
                    /** Class C. */
                    public class C {
                        /**
                         * Method m.
                         */
                        public void m() {
                        }
                    }
                    """);

    }

    @Test
    public void testSyntaxHighlightOption(Path base) {
        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "--syntax-highlight",
                "p");
        checkExit(Exit.OK);
        checkOutput("resource-files/highlight.css", true, "Syntax highlight style sheet");
        checkOutput("script-files/highlight.js", true, "Highlight.js v11.11.1 (git: 08cb242e7d)");
        checkOutput("index-all.html", true, """
                <link rel="stylesheet" type="text/css" href="resource-files/highlight.css">
                <script type="text/javascript" src="script-files/highlight.js"></script>""");
        checkOutput("p/package-summary.html", true, """
                <link rel="stylesheet" type="text/css" href="../resource-files/highlight.css">
                <script type="text/javascript" src="../script-files/highlight.js"></script>""");
        checkOutput("p/C.html", true, """
                <link rel="stylesheet" type="text/css" href="../resource-files/highlight.css">
                <script type="text/javascript" src="../script-files/highlight.js"></script>""");
    }

    @Test
    public void testNoSyntaxHighlightOption(Path base) {
        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "p");
        checkExit(Exit.OK);
        checkFiles(false, "resource-files/highlight.css", "script-files/highlight.js");
        checkOutput("index-all.html", false, "highlight.css", "highlight.js");
        checkOutput("p/package-summary.html", false, "highlight.css", "highlight.js");
        checkOutput("p/C.html", false, "highlight.css", "highlight.js");
    }
}
