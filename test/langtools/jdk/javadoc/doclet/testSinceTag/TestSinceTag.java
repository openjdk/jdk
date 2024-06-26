/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      7180906 8026567 8239804 8324342 8332039
 * @summary  Test to make sure that the since tag works correctly
 * @library  /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @run main TestSinceTag
 */

import java.nio.file.Path;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestSinceTag extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestSinceTag();
        tester.runTests();
        tester.printSummary();
    }

    private final ToolBox tb = new ToolBox();

    @Test
    public void testSince() {
        javadoc("-d", "out-since",
                "-sourcepath", testSrc,
                "pkg1");
        checkExit(Exit.OK);

        checkSince(true);
    }

    @Test
    public void testNoSince() {
        javadoc("-d", "out-nosince",
                "-sourcepath", testSrc,
                "-nosince",
                "pkg1");
        checkExit(Exit.OK);

        checkSince(false);
    }

    void checkSince(boolean on) {
        checkOutput("pkg1/C1.html", on,
                """
                    <dl class="notes">
                    <dt>Since:</dt>
                    <dd>JDK1.0</dd>""");

        checkOutput("serialized-form.html", on,
                """
                    <dl class="notes">
                    <dt>Since:</dt>
                    <dd>1.4</dd>""");
    }

    @Test
    public void testSinceDefault(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package p;
                /**
                 * Class C.
                 * @since 99
                 */
                 public class C {
                     /** Class Nested, with no explicit at-since. */
                     public class Nested { }
                 }""");
        javadoc("-d", base.resolve("api").toString(),
                "-sourcepath", src.toString(),
                "p");
        checkExit(Exit.OK);

        checkOutput("p/C.html", true,
                """
                    <dl class="notes">
                    <dt>Since:</dt>
                    <dd>99</dd>""");

        checkOutput("p/C.Nested.html", true,
                """
                    <dl class="notes">
                    <dt>Since:</dt>
                    <dd>99</dd>""");

    }

    @Test
    public void testSinceDefault_Nested(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package p;
                /**
                 * Class C.
                 * @since 99
                 */
                 public class C {
                     public class Nested1 {
                         /** Class Nested, with no explicit at-since. */
                         public class Nested { }
                     }
                 }""");
        javadoc("-d", base.resolve("api").toString(),
                "-sourcepath", src.toString(),
                "p");
        checkExit(Exit.OK);

        checkOutput("p/C.html", true,
                """
                    <dl class="notes">
                    <dt>Since:</dt>
                    <dd>99</dd>""");

        checkOutput("p/C.Nested1.Nested.html", true,
                """
                    <dl class="notes">
                    <dt>Since:</dt>
                    <dd>99</dd>""");

    }

    @Test
    public void testSinceDefault_NestedTag(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package p;
                /**
                 * Class C.
                 * @since 99 {@link C}
                 */
                 public class C {
                     public static class Nested1 {
                         /** Class Nested, with no explicit at-since. */
                         public static class Nested { }
                     }
                 }""");
        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "-sourcepath", src.toString(),
                "p");
        checkExit(Exit.OK);

        checkOutput("p/C.html", true,
                """
                    <dl class="notes">
                    <dt>Since:</dt>
                    <dd>99 <a href="C.html" title="class in p"><code>C</code></a></dd>""");

        checkOutput("p/C.Nested1.html", true,
                """
                    <dl class="notes">
                    <dt>Since:</dt>
                    <dd>99 <a href="C.html" title="class in p"><code>C</code></a></dd>""");

        checkOutput("p/C.Nested1.Nested.html", true,
                """
                    <dl class="notes">
                    <dt>Since:</dt>
                    <dd>99 <a href="C.html" title="class in p"><code>C</code></a></dd>""");

    }
}
