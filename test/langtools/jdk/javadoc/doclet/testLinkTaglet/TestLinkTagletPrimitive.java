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
 * @bug      8284030 8307377 8331579
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
        var tester = new TestLinkTagletPrimitive();
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
                "C.java:3: warning: reference not found: byte",
                "C.java:4: warning: reference not found: void");

        checkOutput("C.html", true,
                """
                    <div class="block">Comment.
                    Byte:\s
                    <details class="invalid-tag">
                    <summary>invalid reference</summary>
                    <pre><code>byte</code></pre>
                    </details>

                    Void:\s
                    <details class="invalid-tag">
                    <summary>invalid reference</summary>
                    <pre><code>void</code></pre>
                    </details>
                    </div>
                    """);
    }

    @Test
    public void testSimpleDocLint(Path base) throws IOException {
        Path src = base.resolve("src");

        tb.writeJavaFiles(src, """
                /**
                 * Comment.
                 * Double: {@link double}
                 * Void: {@link void}
                 * @see int
                 */
                public class C {\s
                    private C() { }
                }
                """);

        javadoc("-Xdoclint:reference",
                "-d", base.resolve("api").toString(),
                "-sourcepath", src.toString(),
                src.resolve("C.java").toString());
        checkExit(Exit.ERROR);

        checkOutput(Output.OUT, true,
                "C.java:3: error: reference not found",
                "C.java:4: error: reference not found",
                "C.java:5: error: reference not found");

        checkOutput("C.html", true,
                """
                    <div class="block">Comment.
                    Double:\s
                    <details class="invalid-tag">
                    <summary>invalid reference</summary>
                    <pre><code>double</code></pre>
                    </details>

                    Void:\s
                    <details class="invalid-tag">
                    <summary>invalid reference</summary>
                    <pre><code>void</code></pre>
                    </details>
                    </div>
                    """,
                """
                    <dt>See Also:</dt>
                    <dd>
                    <ul class="tag-list">
                    <li>
                    <details class="invalid-tag">
                    <summary>invalid reference</summary>
                    <pre><code>int</code></pre>
                    </details>
                    </li>
                    </ul>
                    </dd>""");
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
                "C.java:3: warning: reference not found: byte[]");

        checkOutput("C.html", true,
                """
                    <div class="block">Comment.
                    Byte[]:\s
                    <details class="invalid-tag">
                    <summary>invalid reference</summary>
                    <pre><code>byte[]</code></pre>
                    </details>
                    </div>
                    """);
    }

    @Test
    public void testArrayDocLint(Path base) throws IOException {
        Path src = base.resolve("src");

        tb.writeJavaFiles(src, """
                /**
                 * Comment.
                 * Double[]: {@link double[]}
                 * @see int[]
                 */
                public class C {\s
                    private C() { }
                }
                """);

        javadoc("-Xdoclint:reference",
                "-d", base.resolve("api").toString(),
                "-sourcepath", src.toString(),
                src.resolve("C.java").toString());
        checkExit(Exit.ERROR);

        checkOutput(Output.OUT, true,
                "C.java:3: error: reference not found",
                "C.java:4: error: reference not found");

        checkOutput("C.html", true,
                """
                    <div class="block">Comment.
                    Double[]:\s
                    <details class="invalid-tag">
                    <summary>invalid reference</summary>
                    <pre><code>double[]</code></pre>
                    </details>
                    </div>
                    """,
                """
                    <dt>See Also:</dt>
                    <dd>
                    <ul class="tag-list">
                    <li>
                    <details class="invalid-tag">
                    <summary>invalid reference</summary>
                    <pre><code>int[]</code></pre>
                    </details>
                    </li>
                    </ul>
                    </dd>
                    """);
    }
}
