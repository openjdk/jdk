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
 * @bug      8313931
 * @summary  Javadoc: links to type parameters actually generate links to classes
 * @library  /tools/lib ../../lib
 * @modules  jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @run main TestLinkTagletTypeParam
 */

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

import java.io.IOException;
import java.nio.file.Path;

public class TestLinkTagletTypeParam extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestLinkTagletTypeParam();
        tester.runTests();
    }

    ToolBox tb = new ToolBox();

    @JavadocTester.Test
    public void testClassTypeParameterLink(Path base) throws IOException {
        Path src = base.resolve("src");

        tb.writeJavaFiles(src,
                """
                    /**
                     * Link to {@link F}.
                     *
                     * @param <F> the first type param
                     * @param <APND> an Appendable
                     *
                     * @see APND the second type parameter
                     */
                    public class Test<F, APND extends Appendable> {
                        private Test() {}
                    }
                    """);

        javadoc("-Xdoclint:none",
                "-d", base.resolve("api").toString(),
                "-sourcepath", src.toString(),
                src.resolve("Test.java").toString());
        checkExit(JavadocTester.Exit.OK);

        checkOrder("Test.html",
                """
                    <dt>Type Parameters:</dt>
                    <dd><span id="type-param-F"><code>F</code> - the first type param</span></dd>
                    <dd><span id="type-param-APND"><code>APND</code> - an Appendable</span></dd>""",
                """
                    Link to <a href="#type-param-F" title="type parameter in Test"><code>F</code></a>.""",
                """
                    <dt>See Also:</dt>
                    <dd>
                    <ul class="tag-list">
                    <li><a href="#type-param-APND" title="type parameter in Test">the second type parameter</a></li>
                    </ul>""");
    }

    @JavadocTester.Test
    public void testMethodTypeParameterLink(Path base) throws IOException {
        Path src = base.resolve("src");

        tb.writeJavaFiles(src,
               """
                    /**
                     * Class comment.
                     */
                    public class Test {
                        /**
                         * Link to {@link T} and {@linkplain T link with label}.
                         *
                         * @param <T> the T
                         * @param appendable the appendable
                         */
                        public <T extends Appendable> T append(final T appendable) {
                            return appendable;
                        }
                    }
                    """);

        javadoc("-Xdoclint:reference",
                "-d", base.resolve("api").toString(),
                "-sourcepath", src.toString(),
                src.resolve("Test.java").toString());

        checkOutput(JavadocTester.Output.OUT, true,
                "");

        checkOutput("Test.html", true,
                """
                    Link to <a href="#append(T)-type-param-T"><code>T</code></a> and <a href="#appe\
                    nd(T)-type-param-T">link with label</a>.""");
    }
}
