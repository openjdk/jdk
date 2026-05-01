/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8358754
 * @summary Rich Notes in Java API Documentation
 * @library /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build toolbox.ToolBox javadoc.tester.*
 * @run main TestCustomNoteTag
 */

import java.io.IOException;
import java.nio.file.Path;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestCustomNoteTag extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestCustomNoteTag();
        tester.runTests();
    }

    ToolBox tb = new ToolBox();

    @Test
    public void testMarkdown(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package p;
                /// First sentence. {@warning abc {@linkplain C _emphasized_} def}
                ///
                /// @example [id=example] __xyz__ {@snippet :
                ///    code ...
                /// }
                public class C { }
                """);

        javadoc("-d", base.resolve("out").toString(),
                "-tag", "warning:a:Warning:",
                "-tag", "example:a:Example:",
                "--source-path", src.toString(),
                "p");
        checkExit(Exit.OK);

        checkOrder("p/C.html", """
                     <div class="block"><p>First sentence.</p>
                     <div class="inline-note note-tag-warning" id="warning-p.C1"><span class="note-header">Warning:</span>
                     abc <a href="C.html" title="class in p"><em>emphasized</em></a> def</div>""",
                """
                     <dl class="notes">
                     <div class="block-note note-tag-example" id="example">
                     <dt>Example:</dt>
                     <dd><p><strong>xyz</strong></p>""",
                """
                     <pre class="snippet" id="snippet-p.C1"><code class="language-java">   code ...
                     </code></pre>
                     </div>""");
    }


    @Test
    public void testNewLocationFlags(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package p;
                /**
                 * First sentence. {@custom inline note}
                 * @custom block note
                 */
                public class C {
                }
                """);
        testWithTagOption(src, base.resolve("out-all"), "custom:t:Custom Note:", "",
                """
                        <div class="block">First sentence.\s
                        <div class="inline-note note-tag-custom" id="custom-p.C1"><span class="note\
                        -header">Custom Note:</span>
                        inline note</div>
                        </div>
                        <dl class="notes">
                        <div class="block-note note-tag-custom" id="custom-p.C">
                        <dt>Custom Note:</dt>
                        <dd>block note</dd>
                        </div>
                        </dl>
                        </div>""");
        testWithTagOption(src, base.resolve("out-inline"), "custom:ti:Custom Note:",
                "warning: Tag custom is used as a block tag. It can only be used as an inline tag.",
                """
                        <div class="block">First sentence.\s
                        <div class="inline-note note-tag-custom" id="custom-p.C1"><span class="note\
                        -header">Custom Note:</span>
                        inline note</div>
                        </div>
                        </div>""");
        testWithTagOption(src, base.resolve("out-block"), "custom:tb:Custom Note:",
                "warning: Tag custom is used as an inline tag. It can only be used as a block tag.",
                """
                        <div class="block">First sentence. </div>
                        <dl class="notes">
                        <div class="block-note note-tag-custom" id="custom-p.C">
                        <dt>Custom Note:</dt>
                        <dd>block note</dd>
                        </div>
                        </dl>
                        </div>""");
    }

    private void testWithTagOption(Path src, Path out, String tagOption,
                                   String expectedWarning, String... expectedOutput) {
        javadoc("-d", out.toString(),
                "-tag", tagOption,
                "--source-path", src.toString(),
                "p");
        checkExit(Exit.OK);

        if (expectedWarning != null) {
            checkOutput(Output.OUT, true, expectedWarning);
        }

        checkOrder("p/C.html", expectedOutput);
    }


}
