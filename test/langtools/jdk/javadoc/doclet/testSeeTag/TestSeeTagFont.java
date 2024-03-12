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
 * @bug      8320207
 * @summary  doclet incorrectly chooses code font for a See Also link
 * @library  /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @run main TestSeeTagFont
 */

import java.nio.file.Path;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestSeeTagFont extends JavadocTester {
    public static void main(String... args) throws Exception {
        var tester = new TestSeeTagFont();
        tester.runTests();
    }

    private final ToolBox tb = new ToolBox();

    @Test
    public void testPlain(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    import p2.Other;
                    /**
                     * Description.
                     * @see Other multi-word phrase
                     * @see Other <em>Other</em>
                     * @see Other Other() with trailing text
                     * @see Other simpleNameMismatch
                     *
                     * @see Other#Other() multi-word phrase
                     * @see Other#Other() Other#Other() with trailing text
                     * @see Other#Other() simpleNameMismatch
                     *
                     * @see Other#m() <code>Other.m</code> with formatting and trailing text
                     */
                    public class C { }
                    """,
                """
                    package p2;
                    /** Lorem ipsum. */
                    public class Other {
                        /** Lorem ipsum. */
                        public void m() { }
                    }
                    """);

        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "-sourcepath", src.toString(),
                "p", "p2");
        checkExit(Exit.OK);

        // none of the following should contain <code>...</code>
        checkOutput("p/C.html", true,
                """
                        <ul class="tag-list-long">
                        <li><a href="../p2/Other.html" title="class in p2">multi-word phrase</a></li>
                        <li><a href="../p2/Other.html" title="class in p2"><em>Other</em></a></li>
                        <li><a href="../p2/Other.html" title="class in p2">Other() with trailing text</a></li>
                        <li><a href="../p2/Other.html" title="class in p2">simpleNameMismatch</a></li>
                        <li><a href="../p2/Other.html#%3Cinit%3E()">multi-word phrase</a></li>
                        <li><a href="../p2/Other.html#%3Cinit%3E()">Other#Other() with trailing text</a></li>
                        <li><a href="../p2/Other.html#%3Cinit%3E()">simpleNameMismatch</a></li>
                        <li><a href="../p2/Other.html#m()"><code>Other.m</code> with formatting and trailing text</a></li>
                        </ul>
                        """);
    }

    @Test
    public void testCode(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package p;
                    import p2.Other;
                    /**
                     * Description.
                     * @see Other
                     * @see p2.Other Other
                     *
                     * @see Other#Other() Other
                     * @see Other#m() m
                     * @see Other#m() Other.m
                     */
                    public class C { }
                    """,
                """
                    package p2;
                    /** Lorem ipsum. */
                    public class Other {
                        /** Lorem ipsum. */
                        public void m() { }
                    }
                    """);

        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "-sourcepath", src.toString(),
                "p", "p2");
        checkExit(Exit.OK);

        // all of the following should contain <code>...</code>
        checkOutput("p/C.html", true,
                """
                    <ul class="tag-list">
                    <li><a href="../p2/Other.html" title="class in p2"><code>Other</code></a></li>
                    <li><a href="../p2/Other.html" title="class in p2"><code>Other</code></a></li>
                    <li><a href="../p2/Other.html#%3Cinit%3E()"><code>Other</code></a></li>
                    <li><a href="../p2/Other.html#m()"><code>m</code></a></li>
                    <li><a href="../p2/Other.html#m()"><code>Other.m</code></a></li>
                    </ul>
                    """);
    }
}
