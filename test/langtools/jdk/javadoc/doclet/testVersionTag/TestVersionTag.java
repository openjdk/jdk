/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8202947 8239804 8324342
 * @summary  test the at-version tag, and corresponding option
 * @library  /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @run main TestVersionTag
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestVersionTag extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestVersionTag();
        tester.runTests();
    }

    ToolBox tb = new ToolBox();
    Path src;

    TestVersionTag() throws Exception {
        src = Files.createDirectories(Paths.get("src"));
        tb.writeJavaFiles(src,
                  """
                      package pkg;
                      /** Introduction.
                       * @version 1.2.3
                       */
                      public class Test { }
                      """);
    }

    @Test
    public void testVersion() {
        javadoc("-d", "out-version",
                "-sourcepath", src.toString(),
                "-version",
                "pkg");
        checkExit(Exit.OK);

        checkVersion(true);
    }

    @Test
    public void testNoVersion() {
        javadoc("-d", "out-noversion",
                "-sourcepath", src.toString(),
                "pkg");
        checkExit(Exit.OK);

        checkVersion(false);
    }

    @Test
    public void testBadVersion_NoWarning(Path base) throws IOException {
        testBadVersion(base, false);
    }

    @Test
    public void testBadVersion_Warning(Path base) throws IOException {
        testBadVersion(base, true);
    }

    public void testBadVersion(Path base, boolean useVersionOption) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package pkg;
                    /** Comment. */
                    public class Test {
                        private Test() { }
                        /**
                         * Comment.
                         * @version 1.2.3
                         */
                        public void m() { }
                    }""");

        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "-Xdoclint:none",
                (useVersionOption ? "-version" : "-XDdummy=dummy"),
                "pkg");
        checkExit(Exit.OK);

        // bad tags never cause corresponding output, whether the option is enabled or not
        checkVersion(false);

        checkOutput(Output.OUT, useVersionOption,
                "warning: Tag @version cannot be used in method documentation.");
    }

    void checkVersion(boolean on) {
        checkOutput("pkg/Test.html", on,
                """
                    <dl class="notes">
                    <dt>Version:</dt>
                    <dd>1.2.3</dd>
                    </dl>""");
    }

    @Test
    public void testVersionDefault(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package p;
                /**
                 * Class C.
                 * @version 42
                 */
                 public class C {
                     /** Class Nested, with no explicit at-version. */
                     public class Nested { }
                 }""");
        javadoc("-d", base.resolve("api").toString(),
                "-version",
                "-sourcepath", src.toString(),
                "p");
        checkExit(Exit.OK);

        checkOutput("p/C.html", true,
                """
                    <dl class="notes">
                    <dt>Version:</dt>
                    <dd>42</dd>""");

        checkOutput("p/C.Nested.html", true,
                """
                    <dl class="notes">
                    <dt>Version:</dt>
                    <dd>42</dd>""");

    }
}
