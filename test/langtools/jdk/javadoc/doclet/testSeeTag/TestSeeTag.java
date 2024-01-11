/*
 * Copyright (c) 2002, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8017191 8182765 8200432 8239804 8250766 8262992 8281944 8307377
 * @summary  Javadoc is confused by at-link to imported classes outside of the set of generated packages
 * @library  /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @run main TestSeeTag
 */

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

import java.io.IOException;
import java.nio.file.Path;

public class TestSeeTag extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestSeeTag();
        tester.runTests();
    }

    @Test
    public void test() {
        javadoc("-d", "out",
                "--no-platform-links",
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.OK);

        checkOutput("pkg/Test.html", true,
            "<code>List</code>",
            """
                <dl class="notes">
                <dt>See Also:</dt>
                <dd>
                <ul class="tag-list-long">
                <li><a href="Test.InnerOne.html#foo()"><code>Test.InnerOne.foo()</code></a></li>
                <li><a href="Test.InnerOne.html#bar(java.lang.Object)"><code>Test.InnerOne.bar(Object)</code></a></li>
                <li><a href="http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#see">Javadoc</a></li>
                <li><a href="Test.InnerOne.html#baz(float)">something</a></li>
                <li><a href="Test.InnerOne.html#format(java.lang.String,java.lang.Object...)"><code>\
                Test.InnerOne.format(java.lang.String, java.lang.Object...)</code></a></li>
                </ul>
                </dd>
                </dl>""");

        checkOutput("pkg/Test.html", false,
          "&lt;code&gt;List&lt;/code&gt;");

        checkOutput("pkg/Test2.html", true,
           "<code>Serializable</code>",
           """
                <dl class="notes">
                <dt>See Also:</dt>
                <dd>
                <ul class="tag-list-long">
                <li><code>Serializable</code></li>
                <li><a href="Test.html" title="class in pkg">See tag with very long label text</a></li>
                </ul>
                </dd>
                </dl>""");

        checkOutput("pkg/Test2.html", false,
           ">Serialized Form<");
    }

    @Test
    public void testBadReference() {
        javadoc("-d", "out-badref",
                "-sourcepath", testSrc,
                "--no-platform-links",
                "badref");
        checkExit(Exit.ERROR);

        checkOutput("badref/Test.html", true,
                """
                    <dl class="notes">
                    <dt>See Also:</dt>
                    <dd>
                    <ul class="tag-list-long">
                    <li><code>Object</code></li>
                    <li>
                    <details class="invalid-tag">
                    <summary>invalid reference</summary>
                    <pre><code>Foo&lt;String&gt;</code></pre>
                    </details>
                    </li>
                    </ul>
                    </dd>
                    </dl>""");
    }

    ToolBox tb = new ToolBox();

    @Test
    public void testErroneous() throws IOException {
        Path src = Path.of("erroneous", "src");
        tb.writeJavaFiles(src, """
                package erroneous;
                /**
                 * Comment.
                 * @see <a href="
                 */
                public class C {
                    private C() { }
                }
                """);

        javadoc("-d", Path.of("erroneous", "api").toString(),
                "-sourcepath", src.toString(),
                "--no-platform-links",
                "erroneous");
        checkExit(Exit.ERROR);

        checkOutput("erroneous/C.html", true,
                """
                    <dl class="notes">
                    <dt>See Also:</dt>
                    <dd>
                    <ul class="tag-list">
                    <li><span class="invalid-tag">invalid input: '&lt;'</span></li>
                    </ul>
                    </dd>
                    </dl>
                    """);
    }

    @Test
    public void testSeeLongCommas(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package p;
                /** Comment. */
                public class C {
                    private C() { }

                    /**
                     * Comment.
                     * @see #noArgs() no args
                     * @see #oneArg(int) one arg
                     * @see #twoArgs(int, int) two args
                     */
                    public void noComma() { }

                    /**
                     * Comment.
                     * @see #noArgs() no args
                     * @see #oneArg(int) one arg
                     * @see #twoArgs(int, int) two args with a comma , in the description
                     */
                    public void commaInDescription() { }

                    /**
                     * Comment.
                     * @see #noArgs()
                     * @see #oneArg(int)
                     * @see #twoArgs(int, int)
                     */
                    public void commaInDefaultDescription() { }

                    /**
                     * No arg method.
                     */
                    public void noArgs() { }

                    /**
                     * One arg method.
                     * @param a1 an arg
                     */
                    public void oneArg(int a1) { }

                    /**
                     * Two arg method.
                     * @param a1 an arg
                     * @param a2 an arg
                     */
                    public void twoArgs(int a1, int a2) { }
                }
                """);

        javadoc("-d", base.resolve("api").toString(),
                "-sourcepath", src.toString(),
                "--no-platform-links",
                "p");
        checkExit(Exit.OK);

        checkOrder("p/C.html",
                "<section class=\"detail\" id=\"noComma()\">",
                """
                    <ul class="tag-list">
                    <li><a href="#noArgs()">no args</a></li>
                    <li><a href="#oneArg(int)">one arg</a></li>
                    <li><a href="#twoArgs(int,int)">two args</a></li>
                    </ul>""",

                "<section class=\"detail\" id=\"commaInDescription()\">",
                """
                    <ul class="tag-list-long">
                    <li><a href="#noArgs()">no args</a></li>
                    <li><a href="#oneArg(int)">one arg</a></li>
                    <li><a href="#twoArgs(int,int)">two args with a comma , in the description</a></li>
                    </ul>""",

                "<section class=\"detail\" id=\"commaInDefaultDescription()\">",
                """
                    <ul class="tag-list-long">
                    <li><a href="#noArgs()"><code>noArgs()</code></a></li>
                    <li><a href="#oneArg(int)"><code>oneArg(int)</code></a></li>
                    <li><a href="#twoArgs(int,int)"><code>twoArgs(int, int)</code></a></li>
                    </ul>""");
    }
}
