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
 * @bug 8284037 8352249
 * @summary Snippet-files subdirectory not automatically detected when in unnamed package
 * @library /tools/lib ../../lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.javadoc/jdk.javadoc.internal.tool
 * @build javadoc.tester.* toolbox.ToolBox toolbox.ModuleBuilder builder.ClassBuilder
 * @run main TestSnippetUnnamedPackage
 */

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TestSnippetUnnamedPackage extends SnippetTester {

    public static void main(String... args) throws Exception {
        new TestSnippetUnnamedPackage().runTests();
    }

    @Test
    public void testNoSourcePath(Path base) throws IOException {
        test(base, false);
    }

    @Test
    public void testSourcePath(Path base) throws IOException {
        test(base, true);
    }

    void test(Path base, boolean useSourcePath) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                        /**
                         * Comment.
                         * Before.
                         * {@snippet class=S}
                         * After.
                         */
                        public class C {
                          private C() { }
                        }
                        """);
        tb.writeFile(src.resolve("snippet-files").resolve("S.java"),
        "public class S { }");

        var args = new ArrayList<String>();
        args.addAll(List.of("-d", base.resolve("out").toString()));
        if (useSourcePath) {
            args.addAll(List.of("--source-path", src.toString()));
        }
        args.add(src.resolve("C.java").toString());

        javadoc(args.toArray(String[]::new));
        checkExit(useSourcePath ? Exit.OK : Exit.ERROR);
        checkOutput(Output.OUT, !useSourcePath,
                "C.java:4: error: file not found on source path or snippet path: S.java");

        checkOutput("C.html", useSourcePath,
                """
                        Before.
                        %s

                        After.""".formatted(getSnippetHtmlRepresentation("C.html",
                        "public class S { }", Optional.of("java"), Optional.of("snippet-C1"))));
    }
}
