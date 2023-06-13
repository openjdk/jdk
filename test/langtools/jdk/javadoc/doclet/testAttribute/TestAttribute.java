/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8309150
 * @summary Need to escape " inside attribute values
 * @library /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build toolbox.ToolBox javadoc.tester.*
 * @run main TestAttribute
 */

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestAttribute extends JavadocTester {

    public final ToolBox tb;
    public static void main(String... args) throws Exception {
        var tester = new TestAttribute();
        tester.runTests();
    }

    public TestAttribute() {
        tb = new ToolBox();
    }

    @Test
    public void testQuote(Path base) throws IOException {
        var src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package p;
                /**
                 * First sentence.
                 * @spec http://example.com title with "quotes"
                 */
                public class C { private C() { } }""");

        javadoc("-d", base.resolve("api").toString(),
                "-sourcepath", src.toString(),
                "p");
        checkExit(Exit.OK);

        // Test some section markers and links to these markers
        checkOutput("p/C.html", true,
                """
                    <a href="http://example.com"><span id="titlewith&quot;quotes&quot;" \
                    class="search-tag-result">title with "quotes"</span></a>""");
    }
}
