/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8298405
 * @summary  Markdown support in the standard doclet
 * @library  /tools/lib ../../lib
 * @modules  jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @run main TestMarkdownDeprecated
 */

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

import java.nio.file.Path;
import java.util.List;

public class TestMarkdownDeprecated extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestMarkdownDeprecated();
        tester.runTests();
    }

    ToolBox tb = new ToolBox();

    @Test
    public void testDeprecated(Path base) throws Exception {
        Path src = base.resolve("src");

        tb.writeJavaFiles(src,
                """
                    package p;
                    public class Control {
                        /**
                         * First sentence. Second sentence.
                         */
                         @Deprecated
                         public void anno_noTag() { }
                        /**
                         * First sentence. Second sentence.
                         * @deprecated deprecated-text
                         */
                         public void noAnno_tag() { }
                        /**
                         * First sentence. Second sentence.
                         * @deprecated deprecated-text
                         */
                        @Deprecated
                        public void anno_tag() { }
                    }""",
                """
                    package p;
                    public class MarkdownComments {
                        /// First sentence. Second sentence.
                        @Deprecated
                        public void anno_noTag() { }
                        /// First sentence. Second sentence.
                        /// @deprecated deprecated-text.
                        public void noAnno_tag() { }
                        /// First sentence. Second sentence.
                        /// @deprecated deprecated-text
                        @Deprecated
                        public void anno_tag() { }
                    }""");

        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "--no-platform-links",
                "--source-path", src.toString(),
                "p");
        checkExit(Exit.OK);

        // Note: javadoc does not generate warnings about any mismatch
        // between @Deprecated annotations and @deprecated tags:
        // the mismatch is detected and reported by javac Attr phase,
        // when enabled by -Xlint:dep-ann.

        // the output for these two files should be the same, except where it is not
        for (var f : List.of("p/Control.html", "p/MarkdownComments.html")) {
            // in the following checks we check from the signature,
            // beginning at the name, through to the end of the main description.
            checkOutput(f, true,
                    """
                        <span class="element-name">anno_noTag</span>()</div>
                        <div class="deprecation-block"><span class="deprecated-label">Deprecated.</span></div>
                        <div class="block">First sentence. Second sentence.</div>""",

                    switch (f) {
                        // @deprecated but no annotation in a traditional comment implies deprecation
                        case "p/Control.html" -> """
                            <span class="element-name">noAnno_tag</span>()</div>
                            <div class="deprecation-block"><span class="deprecated-label">Deprecated.</span>
                            <div class="deprecation-comment">deprecated-text</div>
                            </div>
                            <div class="block">First sentence. Second sentence.</div>""";

                        // @deprecated but no annotation in a Markdown comment does not imply deprecation
                        case "p/MarkdownComments.html" -> """
                            <span class="element-name">noAnno_tag</span>()</div>
                            <div class="block">First sentence. Second sentence.</div>""";

                        default -> throw new Error();
                    },

                    """
                        <span class="element-name">anno_tag</span>()</div>
                        <div class="deprecation-block"><span class="deprecated-label">Deprecated.</span>
                        <div class="deprecation-comment">deprecated-text</div>
                        </div>
                        <div class="block">First sentence. Second sentence.</div>""");
        }
    }
}