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
 * @bug      8288545
 * @summary  Missing space in error message
 * @library  /tools/lib ../../lib
 * @modules  jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @run main TestLinkNotFound
 */

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

import java.nio.file.Path;

public class TestLinkNotFound extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestLinkNotFound tester = new TestLinkNotFound();
        tester.runTests();
    }

    ToolBox tb = new ToolBox();

    @Test
    public void test(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    /**
                     * Comment.
                     * {@link nowhere label}
                     */
                    public class C{ }
                    """);

        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "-Werror",
                "-sourcepath", src.toString(),
                src.resolve("C.java").toString());
        checkExit(Exit.ERROR);

        // the use of '\n' in the following check implies that the label does not appear after the reference
        checkOutput(Output.OUT, true,
                "reference not found: nowhere\n");
    }
}
