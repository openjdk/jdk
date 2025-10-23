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
 * @bug 8351332
 * @summary Line breaks in the description of `{@index}` tags may corrupt JSON search index
 * @library /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build toolbox.ToolBox javadoc.tester.*
 * @run main TestIndexLineBreaks
 */

import java.io.IOException;
import java.nio.file.Path;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestIndexLineBreaks extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestIndexLineBreaks ();
        tester.runTests();
    }

    ToolBox tb = new ToolBox();

    @Test
    public void test() throws IOException {
        Path src = Path.of("src");
        tb.writeJavaFiles(src,
                """
                            package p;
                            public interface I {
                                /**
                                 *
                                 * The {@index "phrase1
                                 * phrase2" description1
                                 * description2 }
                                 */
                                int a();
                            }
                        """);

        javadoc("-d",
                "out",
                "-sourcepath",
                src.toString(),
                "p");

        checkExit(Exit.OK);

        checkOutput("tag-search-index.js", true,
                """
                        {"l":"phrase1 phrase2","h":"p.I.a()","d":"description1 description2 ","u":"p/I.html#phrase1phrase2"},{"l":"Search Tags","h":"","k":"18","u":"search-tags.html"}""");

        checkOutput("tag-search-index.js", false,
                """
                        {"l":"phrase1 phrase2","h":"p.I.a()","d":"description1
                        description2 ","u":"p/I.html#phrase1phrase2"},{"l":"Search Tags","h":"","k":"18","u":"search-tags.html"}""");
    }
}
