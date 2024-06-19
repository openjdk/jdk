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
 * @summary  test the at-author tag, and corresponding option
 * @library  /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @run main TestAuthor
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestAuthor extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestAuthor();
        tester.runTests();
    }

    ToolBox tb = new ToolBox();
    Path src;

    TestAuthor() throws Exception {
        src = Files.createDirectories(Paths.get("src"));
        tb.writeJavaFiles(src,
                  """
                      package pkg;
                      /** Introduction.\s
                       * @author anonymous
                       */
                      public class Test { }
                      """);
    }

    @Test
    public void testAuthor() {
        javadoc("-d", "out-author",
                "-sourcepath", src.toString(),
                "-author",
                "pkg");
        checkExit(Exit.OK);

        checkAuthor(true);
    }

    @Test
    public void testNoAuthor() {
        javadoc("-d", "out-noauthor",
                "-sourcepath", src.toString(),
                "pkg");
        checkExit(Exit.OK);

        checkAuthor(false);
    }

    @Test
    public void testBadAuthor_NoWarning(Path base) throws IOException {
        testBadAuthor(base, false);
    }

    @Test
    public void testBadAuthor_Warning(Path base) throws IOException {
        testBadAuthor(base, true);
    }

    public void testBadAuthor(Path base, boolean useAuthorOption) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    package pkg;
                    /** Comment. */
                    public class Test {
                        private Test() { }
                        /**
                         * Comment.
                         * @author anonymous
                         */
                        public void m() { }
                    }""");

        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "-Xdoclint:none",
                (useAuthorOption ? "-author" : "-XDdummy=dummy"),
                "pkg");
        checkExit(Exit.OK);

        // bad tags never cause corresponding output, whether the option is enabled or not
        checkAuthor(false);

        checkOutput(Output.OUT, useAuthorOption,
                "warning: Tag @author cannot be used in method documentation.");
    }

    void checkAuthor(boolean on) {
        checkOutput("pkg/Test.html", on,
                """
                    <dl class="notes">
                    <dt>Author:</dt>
                    <dd>anonymous</dd>
                    </dl>""");
    }

    @Test
    public void testAuthorDefault(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package p;
                /**
                 * Class C.
                 * @author J. Duke
                 */
                 public class C {
                     /** Class Nested, with no explicit at-author. */
                     public class Nested { }
                 }""");
        javadoc("-d", base.resolve("api").toString(),
                "-author",
                "-sourcepath", src.toString(),
                "p");
        checkExit(Exit.OK);

        checkOutput("p/C.html", true,
                """
                    <dl class="notes">
                    <dt>Author:</dt>
                    <dd>J. Duke</dd>""");

        checkOutput("p/C.Nested.html", true,
                """
                    <dl class="notes">
                    <dt>Author:</dt>
                    <dd>J. Duke</dd>""");
    }

    @Test
    public void testAuthorDefault_Multiple(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package p;
                /**
                 * Class C.
                 * @author J. Duke
                 * @author A. N. Other
                 */
                 public class C {
                     /** Class Nested, with no explicit at-author. */
                     public class Nested { }
                 }""");
        javadoc("-d", base.resolve("api").toString(),
                "-author",
                "-sourcepath", src.toString(),
                "p");
        checkExit(Exit.OK);

        checkOutput("p/C.html", true,
                """
                    <dl class="notes">
                    <dt>Author:</dt>
                    <dd>J. Duke, A. N. Other</dd>""");

        checkOutput("p/C.Nested.html", true,
                """
                    <dl class="notes">
                    <dt>Author:</dt>
                    <dd>J. Duke, A. N. Other</dd>""");
    }

    @Test
    public void testAuthorDefault_Nested(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package p;
                /**
                 * Class C.
                 * @author J. Duke
                 * @author A. N. Other
                 */
                 public class C {
                     public class Nested1 {
                         /** Class Nested, with no explicit at-author. */
                         public class Nested { }
                     }
                 }""");
        javadoc("-d", base.resolve("api").toString(),
                "-author",
                "-sourcepath", src.toString(),
                "p");
        checkExit(Exit.OK);

        checkOutput("p/C.html", true,
                """
                    <dl class="notes">
                    <dt>Author:</dt>
                    <dd>J. Duke, A. N. Other</dd>""");

        checkOutput("p/C.Nested1.Nested.html", true,
                """
                    <dl class="notes">
                    <dt>Author:</dt>
                    <dd>J. Duke, A. N. Other</dd>""");
    }
}
