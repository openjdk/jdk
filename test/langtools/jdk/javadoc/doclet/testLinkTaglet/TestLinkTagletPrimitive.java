/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8284030
 * @summary  LinkFactory should not attempt to link to primitive types
 * @library  /tools/lib ../../lib
 * @modules  jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @run main TestLinkTagletPrimitive
 */

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

import java.io.IOException;
import java.nio.file.Path;

public class TestLinkTagletPrimitive extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestLinkTagletPrimitive tester = new TestLinkTagletPrimitive();
        tester.runTests();
    }

    ToolBox tb = new ToolBox();

    @Test
    public void testSimple(Path base) throws IOException {
        Path src = base.resolve("src");

        tb.writeJavaFiles(src, """
                /**
                 * Comment.
                 * Byte: {@link byte}
                 * Void: {@link void}
                 */
                public class C {\s
                    private C() { }
                }
                """);

        javadoc("-Xdoclint:none",
                "-d", base.resolve("api").toString(),
                "-sourcepath", src.toString(),
                src.resolve("C.java").toString());
        checkExit(Exit.OK);

        checkOutput(Output.OUT, true,
                "C.java:3: warning: Tag @link: reference not found: byte",
                "C.java:4: warning: Tag @link: reference not found: void");

        checkOutput("C.html", true,
                """
                    <div class="block">Comment.
                     Byte:\s
                    <details class="invalid-tag">
                    <summary>invalid @link</summary>
                    <pre><code>byte</code></pre>
                    </details>

                     Void:\s
                    <details class="invalid-tag">
                    <summary>invalid @link</summary>
                    <pre><code>void</code></pre>
                    </details>
                    </div>
                    """);
    }
    @Test
    public void testArray(Path base) throws IOException {
        Path src = base.resolve("src");

        tb.writeJavaFiles(src, """
                /**
                 * Comment.
                 * Byte[]: {@link byte[]}
                 */
                public class C {\s
                    private C() { }
                }
                """);

        javadoc("-Xdoclint:none",
                "-d", base.resolve("api").toString(),
                "-sourcepath", src.toString(),
                src.resolve("C.java").toString());
        checkExit(Exit.OK);

        checkOutput(Output.OUT, true,
                "C.java:3: warning: Tag @link: reference not found: byte[]");

        checkOutput("C.html", true,
                """
                    <div class="block">Comment.
                     Byte[]:\s
                    <details class="invalid-tag">
                    <summary>invalid @link</summary>
                    <pre><code>byte[]</code></pre>
                    </details>
                    </div>
                    """);
    }
}
