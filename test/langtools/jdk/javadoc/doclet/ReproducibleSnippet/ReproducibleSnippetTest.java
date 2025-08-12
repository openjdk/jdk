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
 * @bug      8346128 8346659
 * @summary  Check that snippet generation is reproducible
 * @library  /tools/lib ../../lib
 * @modules  jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @run main ReproducibleSnippetTest
 */

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

import java.nio.file.Path;

public class ReproducibleSnippetTest extends JavadocTester {
    ToolBox tb = new ToolBox();

    public static void main(String... args) throws Exception {
        var tester = new ReproducibleSnippetTest();
        tester.runTests();
    }

    @Test
    public void test(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                        package p;
                        public interface One {
                            /**
                             * {@code One obj1}
                             * {@snippet lang = java:
                             * // @link substring="ab" target="One#ab" :
                             * obj1.ab(a()); // @link substring="a" target="#a"
                             *} class comment
                             */
                            int a();
                            void ab(int i);
                        }
                        """);
        javadoc("-d",
                "out",
                "-sourcepath",
                src.toString(),
                "p");
        checkExit(Exit.ERROR);

        checkOutput(Output.OUT, true,
                "One.java:5: error: snippet link tags:",
                "#a",
                "One#ab",
                "overlap in obj1.ab(a());\n     * {@snippet lang = java:\n       ^");
    }
}
