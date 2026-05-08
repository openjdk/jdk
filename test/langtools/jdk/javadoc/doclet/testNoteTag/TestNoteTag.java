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
 * @run main TestNoteTag
 */

import java.io.IOException;
import java.nio.file.Path;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestNoteTag extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestNoteTag();
        tester.runTests();
    }

    ToolBox tb = new ToolBox();

    // Test inline and block notes in various locations with default settings (no attributes)
    @Test
    public void testLocationDefaults(Path base) throws IOException {
        Path src = base.resolve("src");
        Path mdlSrc = src.resolve("m1");
        tb.writeJavaFiles(mdlSrc, """
                /**
                 * Module m1.
                 * {@warning Module warning}
                 * {@note Module note}
                 */
                module m1 {
                     exports p.q;
                     exports p.q.r;
                }
                """,
                """
                /**
                 * Package p.q.
                 * @warning Package warning
                 * @note Package note
                 */
                package p.q;
                """,
                """
                package p.q;
                /**
                 * An interface.
                 * {@warning Interface warning}
                 * {@warning Second interface warning}
                 */
                 public interface I {
                     /**
                      * An interface method.
                      * {@note Note in interface method}
                      * {@note Second note in interface method}
                      */
                     void m();
                 }
                """,
                """
                package p.q.r;
                /**
                 * A class.
                 */
                 public class C {
                    /**
                     * A class field.
                     * {@warning Warning in class field}
                     * @note Note in class field
                     */
                    public static int i;
                 }
                """);
        tb.writeFile(mdlSrc.resolve("p/q/doc-files/test.html"), """
                <html>
                <body>
                HTML file with notes.
                {@warning An inline warning}
                @note a block note
                </body>
                </html>
                """);
        tb.writeFile(mdlSrc.resolve("p/q/r/package.html"), """
                <html>
                <body>
                Package HTML file.
                {@note An inline note}
                @warning a block warning
                </body>
                </html>
                """);

        javadoc("-d", base.resolve("out").toString(),
                "-tag", "warning:A:Warning:",
                "--module-source-path", src.toString(),
                "--module", "m1");
        checkExit(Exit.OK);

        checkOrder("m1/module-summary.html", """
                <div class="inline-note note-tag-warning" id="m1-warning1"><span class="note-header">Warning:</span>
                Module warning</div>""",
                """
                <div class="inline-note note-tag" id="m1-note1"><span class="note-header">Note:</span>
                Module note</div>""");
        checkOrder("m1/p/q/package-summary.html", """
                <dt>Note:</dt>
                <dd id="p.q-note" class="note-tag">Package note</dd>""",
                """
                <dt>Warning:</dt>
                <dd id="p.q-warning" class="note-tag-warning">Package warning</dd>""");
        checkOrder("m1/p/q/I.html", """
                <div class="inline-note note-tag-warning" id="p.q.I-warning1"><span class="note-header">Warning:</span>
                Interface warning</div>""",
                """
                <div class="inline-note note-tag-warning" id="p.q.I-warning2"><span class="note-header">Warning:</span>
                Second interface warning</div>""");
        checkOrder("m1/p/q/doc-files/test.html", """
                <div class="inline-note note-tag-warning" id="unknown-element-warning1"><span class="note-header">Warning:</span>
                An inline warning</div>""",
                """
                <dl class="notes">
                <dt>Note:</dt>
                <dd id="unknown-element-note" class="note-tag">a block note</dd>
                </dl>""");
        checkOrder("m1/p/q/r/package-summary.html", """
                <div class="inline-note note-tag" id="p.q.r-note1"><span class="note-header">Note:</span>
                An inline note</div>""",
                """
                <dl class="notes">
                <dt>Warning:</dt>
                <dd id="p.q.r-warning" class="note-tag-warning">a block warning</dd>
                </dl>""");
        checkOrder("m1/p/q/r/C.html", """
                <div class="inline-note note-tag-warning" id="i-warning1"><span class="note-header">Warning:</span>
                Warning in class field</div>""",
                """
                <dl class="notes">
                <dt>Note:</dt>
                <dd id="i-note" class="note-tag">Note in class field</dd>
                </dl>""");
    }

    @Test
    public void testMarkdown(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package p;
                /// First sentence. {@note [id=inline-note] abc {@linkplain C _emphasized_ link} def}
                ///
                /// @note [] xyz [**bold** link][C]
                ///
                public class C {
                    /// Constructor.
                    C() {}
                }
                """);
        tb.writeFile(src.resolve("p/doc-files/markdown.md"), """
                Markdown file.
                {@note Note containing a [link to class C][C]}
                {@note Second note with a {@linkplain C link to class C}}
                """);

        javadoc("-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "p");
        checkExit(Exit.OK);

        checkOrder("p/C.html", """
                    <div class="block"><p>First sentence.</p>
                    <div class="inline-note note-tag" id="inline-note"><span class="note-header">Note:</span>
                    abc <a href="C.html" title="class in p"><em>emphasized</em> link</a> def</div>""",
                """
                    <dl class="notes">
                    <dt>Note:</dt>
                    <dd id="p.C-note" class="note-tag">xyz <a href="C.html" title="class in p"><strong>bold</strong> link</a></dd>
                    </dl>""");

        checkOrder("p/doc-files/markdown.html", """
                <p>Markdown file.</p>""",
                """
                    <div class="inline-note note-tag" id="unknown-element-note3"><span class="note-header">Note:</span>
                    Note containing a <a href="../C.html" title="class in p">link to class C</a></div>""",
                """
                    <div class="inline-note note-tag" id="unknown-element-note4"><span class="note-header">Note:</span>
                    Second note with a <a href="../C.html" title="class in p">link to class C</a></div>""");
    }

    @Test
    public void testGroupedBlockNotes(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                    package p;
                    /**
                     * @note First note
                     * @note Second note
                     * @note [header="Important:"] First important note
                     * @note [header="Important:"] Second important note
                     * @note [header="Warning:" id="first-warning" kind="warning"] First warning
                     * @note [header="Warning:" id="second-warning" kind="warning"] Second warning
                     */
                    public class C {
                    }
                    """);

        javadoc("-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "p");
        checkExit(Exit.OK);

        checkOrder("p/C.html",
                """
                        <dt>Note:</dt>
                        <dd id="p.C-note" class="note-tag">First note</dd>
                        <dd id="p.C-note1" class="note-tag">Second note</dd>""",
                """
                        <dt>Important:</dt>
                        <dd id="p.C-note2" class="note-tag">First important note</dd>
                        <dd id="p.C-note3" class="note-tag">Second important note</dd>""",
                """
                        <dt>Warning:</dt>
                        <dd id="first-warning" class="note-tag-warning">First warning</dd>
                        <dd id="second-warning" class="note-tag-warning">Second warning</dd>
                        </dl>""");
    }

    @Test
    public void testUnterminatedAttributes(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                    package p;
                    /**
                    * First sentence. {@note [header=Warning }
                    * @note
                    *  [ id=important-note
                    *    kind=important
                    */
                    public class C {
                    }
                    """);

        javadoc("-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "p");
        checkExit(Exit.ERROR);

        checkOutput(Output.OUT, true, """
                    C.java:3: error: unterminated attributes
                    * First sentence. {@note [header=Warning }
                                                             ^
                    """,
                """
                    C.java:6: error: unterminated attributes
                    *    kind=important
                                      ^
                    """);


        checkOrder("p/C.html", """
                <details class="invalid-tag">
                <summary>invalid @note</summary>
                <pre>{@note [header=Warning }</pre>
                </details>""");
    }

    @Test
    public void testValuelessAttribute(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                    package p;
                    /**
                    * First sentence. {@note [id] body }
                    *
                    * @note [ id=important-note kind ] body
                    */
                    public class C {
                    }
                    """);

        javadoc("-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "p");
        checkExit(Exit.OK);

        checkOutput(Output.OUT, true, """
                    C.java:3: warning: attribute lacks value
                    * First sentence. {@note [id] body }
                                              ^
                    """,
                """
                    C.java:5: warning: attribute lacks value
                    * @note [ id=important-note kind ] body
                                                ^
                    """);

        checkOrder("p/C.html", """
                    <div class="inline-note note-tag" id="p.C-note1"><span class="note-header">Note:</span>
                    body </div>
                    </div>
                    <dl class="notes">
                    <dt>Note:</dt>
                    <dd id="important-note" class="note-tag">body</dd>
                    </dl>""");
    }

    @Test
    public void testDuplicateAttribute(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                    package p;
                    /**
                    * First sentence. {@note [id=foo id=bar] body }
                    *
                    * @note [kind=important kind=other] body
                    */
                    public class C {
                    }
                    """);

        javadoc("-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "p");
        checkExit(Exit.OK);

        checkOutput(Output.OUT, true, """
                    C.java:3: warning: repeated attribute: id=bar
                    * First sentence. {@note [id=foo id=bar] body }
                                                     ^
                    """,
                """
                    C.java:5: warning: repeated attribute: kind=other
                    * @note [kind=important kind=other] body
                                            ^
                    """);

        checkOrder("p/C.html", """
                    <div class="inline-note note-tag" id="foo"><span class="note-header">Note:</span>
                    body </div>
                    </div>
                    <dl class="notes">
                    <dt>Note:</dt>
                    <dd id="p.C-note" class="note-tag-important">body</dd>
                    </dl>""");
    }

    @Test
    public void testUniqueIds(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                    package p;
                    /**
                     * @note [id=block-note] A
                     * @note [id=block-note] B
                     * @note [id=block-note] C
                     */
                    public class C {
                        /**
                         * {@note [id=inline-note] 1} {@note 2}.
                         * {@note [id=inline-note] 3}
                         */
                        public void m() {}
                    }
                    """);

        javadoc("-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "p");
        checkExit(Exit.OK);

        checkOrder("p/C.html",
                // Block notes
                """
                    <dl class="notes">
                    <dt>Note:</dt>
                    <dd id="block-note" class="note-tag">A</dd>
                    <dd id="block-note1" class="note-tag">B</dd>
                    <dd id="block-note2" class="note-tag">C</dd>""",
                // The first two inline notes are duplicated in the method summary table.
                """
                    <div class="inline-note note-tag" id="inline-note"><span class="note-header">Note:</span>
                    1</div>""",
                """
                    <div class="inline-note note-tag" id="m()-note1"><span class="note-header">Note:</span>
                    2</div>""",
                """
                    <div class="inline-note note-tag" id="inline-note1"><span class="note-header">Note:</span>
                    1</div>""",
                """
                    <div class="inline-note note-tag" id="m()-note2"><span class="note-header">Note:</span>
                    2</div>""",
                """
                    <div class="inline-note note-tag" id="inline-note2"><span class="note-header">Note:</span>
                    3</div>"""
                );
    }

}
