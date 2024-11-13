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
 * @bug      8322708
 * @summary  Test to make sure global tags work properly
 * @library  /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @run main TestGlobalHtml
 */

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

import java.nio.file.Path;

public class TestGlobalHtml extends JavadocTester {
    ToolBox tb = new ToolBox();

    public static void main(String... args) throws Exception {
        var tester = new TestGlobalHtml();
        tester.runTests();
    }

    @Test
    public void testGlobalTags() {
        javadoc("--allow-script-in-comments",
                "-d",
                "out-global",
                "-sourcepath",
                testSrc,
                "pkg1");
        checkExit(Exit.OK);
    }

    @Test
    public void testNegative(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                package p;
                /**
                 * class comment
                 * <a href="https://openjdk.org/">Hyperlink to the OpenJDK website</a>
                 */
                public class C {
                    /**
                     * <form>
                     *   <label for="methodname">Method name:</label><br>
                     *   <input type="text" id="methodname" name="methodname"><br>
                     *   <label for="paramname">Method Parameter:</label><br>
                     *   <input type="text" id="paramname" name="paramname">
                     * </form>
                     */
                    public C() {
                    }
                }
                """);

        javadoc("--allow-script-in-comments",
                "-d",
                "out-negative",
                "-sourcepath",
                src.toString(),
                "p");
        checkExit(Exit.ERROR);
    }
}
