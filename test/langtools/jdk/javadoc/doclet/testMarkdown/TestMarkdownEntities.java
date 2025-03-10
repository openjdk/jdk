/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8332545
 * @summary  Fix handling of HTML5 entities in Markdown comments
 * @library  /tools/lib ../../lib
 * @modules  jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @run main TestMarkdownEntities
 */

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

import java.nio.file.Path;

/**
 * Tests for use of HTML5 entities in Markdown.
 */
public class TestMarkdownEntities extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestMarkdownEntities();
        tester.runTests();
    }

    ToolBox tb = new ToolBox();

    @Test
    public void testEntities(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    /// Simple common &lt;entities&gt; &amp;
                    /// less common &ldquo;entities&rdquo;.
                    public class C {
                        private C() { }
                    }
                    """);

        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--source-path", src.toString(),
                "p");

        checkOutput("p/C.html", true,
                """
                    <div class="block">Simple common &lt;entities&gt; &amp;
                    less common \u201centities\u201d.</div>
                    """);

    }

}