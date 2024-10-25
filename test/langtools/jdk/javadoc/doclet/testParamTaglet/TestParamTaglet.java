/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4802275 4967243 8026567 8239804 8234682 8279931
 * @summary  Make sure param tags are still printed even though they do not
 *           match up with a real parameters.
 *           Make sure inheritDoc cannot be used in an invalid param tag.
 * @library  /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @run main TestParamTaglet
 */

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

import java.nio.file.Path;

public class TestParamTaglet extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestParamTaglet();
        tester.runTests();
    }

    private final ToolBox tb = new ToolBox();

    @Test
    public void test() {
        javadoc("-d", "out",
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.ERROR);
        checkOutput(Output.OUT, true,
                "warning: no @param for param1",
                "error: @param name not found",
                "warning: @param \"b\" has already been specified");
        checkOutput("pkg/C.html", true,
                // Regular param tags.
                """
                    <dt>Parameters:</dt>
                    <dd><code>param1</code> - testing 1 2 3.</dd>
                    <dd><code>param2</code> - testing 1 2 3.</dd>
                    </dl>""",
                // Param tags that don't match with any real parameters.
                // {@inheritDoc} misuse does not cause doclet to throw exception.
                // Param is printed with nothing inherited.
                """
                    <dt>Parameters:</dt>
                    <dd><code>p1</code> - testing 1 2 3.</dd>
                    <dd><code>p2</code> - testing 1 2 3.</dd>
                    <dd><code>inheritBug</code> - </dd>
                    </dl>""",
                """
                    <dt>Parameters:</dt>
                    <dd><code>i</code> - an int</dd>
                    <dd><code>d</code> - a double</dd>
                    <dd><code>b</code> - a boolean</dd>
                    <dd><code>x</code> - does not exist</dd>
                    <dd><code>x</code> - duplicate</dd>
                    <dd><code>b</code> - another duplicate</dd>
                    </dl>""",
                """
                    <dt>Type Parameters:</dt>
                    <dd><span id="genericMethod(T1,T2,T3)-type-param-T2"><code>T2</code> - type 2</span></dd>
                    <dt>Parameters:</dt>
                    <dd><code>t1</code> - param 1</dd>
                    <dd><code>t3</code> - param 3</dd>
                    </dl>""");
        checkOutput("pkg/C.Point.html", true,
                """
                    <dt>Record Components:</dt>
                    <dd><code><span id="param-y">y</span></code> - the y coordinate</dd>
                    </dl>""");
        checkOutput("pkg/C.Nested.html", true,
                """
                    <dt>Type Parameters:</dt>
                    <dd><span id="type-param-T1"><code>T1</code> - type 1</span></dd>
                    </dl>""");
    }

    @Test
    public void testParamOrder(Path base) throws Exception {
        String contents = """
                package pkg;

                /**
                 * Class with missing and unsorted param tags.
                 *
                 * @param <V> third type param
                 * @param <T> second type param
                 */
                public class C<S, T, V>{
                    /**
                      * Method with unsorted param tags.
                      *
                      * @param i1 first param
                      * @param d1 third param
                      * @param i2 second param
                      * @param d2 fourth param
                      */
                    public void paramOrder(int i1, int i2, double d1, double d2) {
                    }
                }
                """;
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, contents);

        // Run with all doclint groups enabled
        javadoc("-d", base.resolve("out-warn").toString(),
                "-sourcepath", src.toString(),
                "-Xdoclint:all",
                "pkg");
        checkExit(Exit.OK);
        checkOutput(Output.OUT, true,
                "C.java:9: warning: no @param for <S>",
                "C.java:9: warning: wrong order of @param tags",
                "C.java:18: warning: wrong order of @param tags");
        checkOutput("pkg/C.html", true,
                """
                    <dt>Type Parameters:</dt>
                    <dd><span id="type-param-T"><code>T</code> - second type param</span></dd>
                    <dd><span id="type-param-V"><code>V</code> - third type param</span></dd>
                    </dl>""",
                """
                    <dt>Parameters:</dt>
                    <dd><code>i1</code> - first param</dd>
                    <dd><code>i2</code> - second param</dd>
                    <dd><code>d1</code> - third param</dd>
                    <dd><code>d2</code> - fourth param</dd>
                    </dl>""");

        // Run with doclint enabled except for reference group
        javadoc("-d", base.resolve("out-nowarn").toString(),
                "-sourcepath", src.toString(),
                "-Xdoclint:all,-reference",
                "pkg");
        checkExit(Exit.OK);
        checkOutput(Output.OUT, true,
                "C.java:9: warning: no @param for <S>");
        checkOutput(Output.OUT, false,
                "warning: wrong order");
        checkOutput("pkg/C.html", true,
                """
                    <dt>Type Parameters:</dt>
                    <dd><span id="type-param-T"><code>T</code> - second type param</span></dd>
                    <dd><span id="type-param-V"><code>V</code> - third type param</span></dd>
                    </dl>""",
                """
                    <dt>Parameters:</dt>
                    <dd><code>i1</code> - first param</dd>
                    <dd><code>i2</code> - second param</dd>
                    <dd><code>d1</code> - third param</dd>
                    <dd><code>d2</code> - fourth param</dd>
                    </dl>""");
    }
}
