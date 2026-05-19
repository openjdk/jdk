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
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javadoc.tester.JavadocTester;
import jdk.javadoc.doclet.Taglet;
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
                     <div class="inline-note note-tag-warning" id="inline-warning1"><span class="note-header">Warning:</span>
                     abc <a href="C.html" title="class in p"><em>emphasized</em></a> def</div>""",
                """
                     <dl class="notes">
                     <div id="example" class="note-tag-example">
                     <dt>Example:</dt>
                     <dd><p><strong>xyz</strong></p>""",
                """
                     <pre class="snippet" id="snippet-p.C1"><code class="language-java">   code ...
                     </code></pre>
                     </div>""");
    }

    // Make sure inline and block custom notes comply with -tag location flags
    @Test
    public void testLocationFlags(Path base) throws IOException {
        Path src = base.resolve("src");
        Path mdlSrc = src.resolve("m1");
        tb.writeJavaFiles(mdlSrc, """
                /**
                 * Module m1.
                 * {@warning [id=inline-MODULE-warning] inline warning}
                 * @warning [id=block-MODULE-warning] block warning
                 */
                module m1 {
                     exports p.q;
                     exports p.q.r;
                }
                """,
                """
                /**
                 * Package p.q.
                 * {@warning [id=inline-PACKAGE-warning] inline warning}
                 * @warning [id=block-PACKAGE-warning] block warning
                 */
                package p.q;
                """,
                """
                package p.q;
                /**
                 * An interface.
                 * {@warning [id=inline-TYPE-warning] inline warning}
                 * @warning [id=block-TYPE-warning] block warning
                 */
                public interface I {
                    /**
                     * A method.
                     * {@warning [id=inline-METHOD-warning] inline warning}
                     * @warning [id=block-METHOD-warning] block warning
                     */
                    void m();
                    /**
                     * An enum.
                     * {@warning [id=inline-TYPE-warning] inline warning}
                     * @warning [id=block-TYPE-warning] block warning
                     */
                    enum E {
                        /**
                         * An enum constant.
                         * {@warning [id=inline-FIELD-warning] inline warning}
                         * @warning [id=block-FIELD-warning] block warning
                         */
                        C
                    }
                }
                """,
                """
                package p.q.r;
                /**
                 * A class.
                 * {@warning [id=inline-TYPE-warning] inline warning}
                 * @warning [id=block-TYPE-warning] block warning
                 */
                public class C {
                    /**
                     * A field.
                     * {@warning [id=inline-FIELD-warning] inline warning}
                     * @warning [id=block-FIELD-warning] block warning
                     */
                    public static int i;
                    /**
                     * A constructor.
                     * {@warning [id=inline-CONSTRUCTOR-warning] inline warning}
                     * @warning [id=block-CONSTRUCTOR-warning] block warning
                     */
                    public C() { }
                    /**
                     * A record.
                     * {@warning [id=inline-TYPE-warning] inline warning}
                     * @warning [id=block-TYPE-warning] block warning
                     */
                    public record R(int x, int y) { }
                    /**
                     * An annotation interface.
                     * {@warning [id=inline-TYPE-warning] inline warning}
                     * @warning [id=block-TYPE-warning] block warning
                     */
                    public @interface A {
                        /**
                         * A method.
                         * {@warning [id=inline-METHOD-warning] inline warning}
                         * @warning [id=block-METHOD-warning] block warning
                         */
                        String a() default "";
                    }
                }
                """);
        tb.writeFile(src.resolve("overview.html"), """
                <html>
                <body>
                Overview file.
                {@warning [id=inline-OVERVIEW-warning] inline warning}
                @warning [id=block-OVERVIEW-warning] block warning
                </body>
                </html>
                """);
        tb.writeFile(mdlSrc.resolve("p/q/doc-files/test.html"), """
                <html>
                <body>
                HTML file.
                {@warning [id=inline-PACKAGE-warning] inline warning}
                @warning [id=block-PACKAGE-warning] block warning
                </body>
                </html>
                """);
        tb.writeFile(mdlSrc.resolve("p/q/r/package.html"), """
                <html>
                <body>
                Package HTML file.
                {@warning [id=inline-PACKAGE-warning] inline warning}
                @warning [id=block-PACKAGE-warning] block warning
                </body>
                </html>
                """);

        for (var location : Taglet.Location.values()) {
            testWithLocation(src, base, location);
        }
    }

    private void testWithLocation(Path src, Path base, Taglet.Location location) {
        var commandLineFlag = switch (location) {
            case OVERVIEW    -> 'o';
            case MODULE      -> 's';
            case PACKAGE     -> 'p';
            case TYPE        -> 't';
            case CONSTRUCTOR -> 'c';
            case METHOD      -> 'm';
            case FIELD       -> 'f';
        };

        javadoc("-d", base.resolve("out-" + location).toString(),
                "-overview", src.resolve("overview.html").toString(),
                "-tag", "warning:" + commandLineFlag + ":Warning:",
                "--module-source-path", src.toString(),
                "--module", "m1");
        checkExit(Exit.OK);

        // Test for warnings
        for (var loc2 : Taglet.Location.values()) {
            checkOutput(Output.OUT, loc2 != location,
                    "warning: Tag @warning cannot be used in "
                            + locationName(loc2)
                            + " documentation. It can only be used in the following types of documentation: "
                            + locationName(location));
        }

        // Generated files mapped to lists of contained taglet locations
        var files = Map.of("index.html", List.of(Taglet.Location.OVERVIEW),
                           "m1/module-summary.html", List.of(Taglet.Location.MODULE),
                           "m1/p/q/package-summary.html", List.of(Taglet.Location.PACKAGE),
                           "m1/p/q/doc-files/test.html", List.of(Taglet.Location.PACKAGE),
                           "m1/p/q/I.html", List.of(Taglet.Location.TYPE, Taglet.Location.METHOD),
                           "m1/p/q/I.E.html", List.of(Taglet.Location.TYPE, Taglet.Location.FIELD),
                           "m1/p/q/r/package-summary.html", List.of(Taglet.Location.PACKAGE),
                           "m1/p/q/r/C.html", List.of(Taglet.Location.TYPE, Taglet.Location.FIELD,
                                                      Taglet.Location.CONSTRUCTOR),
                           "m1/p/q/r/C.R.html", List.of(Taglet.Location.TYPE),
                           "m1/p/q/r/C.A.html", List.of(Taglet.Location.TYPE, Taglet.Location.METHOD));

        for (var entry : files.entrySet()) {
            for (var loc2: entry.getValue()) {
                checkOutput(entry.getKey(), loc2 == location,
                        """
                            <div class="inline-note note-tag-warning" id="inline-$LOCATION$-warning"\
                            ><span class="note-header">Warning:</span>
                            inline warning</div>""".replace("$LOCATION$", loc2.toString()),
                        """
                            <dl class="notes">
                            <div id="block-$LOCATION$-warning" class="note-tag-warning">
                            <dt>Warning:</dt>
                            <dd>block warning</dd>
                            </div>
                            </dl>""".replace("$LOCATION$", loc2.toString()));
            }
        }
    }

    private String locationName(Taglet.Location loc) {
        return loc == Taglet.Location.TYPE
                ? "class"
                : loc.name().toLowerCase(Locale.ROOT);
    }

    // Make sure inline and block custom tags comly with new 'B' and 'I' -tag option flags
    @Test
    public void testBlockAndInlineFlags(Path base) throws IOException {
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

        testBlockOrInlineFlag(src, base.resolve("out-all"), "", Exit.OK, null,
                """
                        <div class="block">First sentence.\s
                        <div class="inline-note note-tag-custom" id="inline-custom1"><span class="note\
                        -header">Custom Note:</span>
                        inline note</div>
                        </div>
                        <dl class="notes">
                        <div id="block-custom" class="note-tag-custom">
                        <dt>Custom Note:</dt>
                        <dd>block note</dd>
                        </div>
                        </dl>
                        </div>""");

        testBlockOrInlineFlag(src, base.resolve("out-inline"), "I", Exit.OK,
                "warning: Tag custom is used as a block tag. It can only be used as an inline tag.",
                """
                        <div class="block">First sentence.\s
                        <div class="inline-note note-tag-custom" id="inline-custom1"><span class="note\
                        -header">Custom Note:</span>
                        inline note</div>
                        </div>
                        </div>""");

        testBlockOrInlineFlag(src, base.resolve("out-block"), "B", Exit.OK,
                "warning: Tag custom is used as an inline tag. It can only be used as a block tag.",
                """
                        <div class="block">First sentence. </div>
                        <dl class="notes">
                        <div id="block-custom" class="note-tag-custom">
                        <dt>Custom Note:</dt>
                        <dd>block note</dd>
                        </div>
                        </dl>
                        </div>""");

        testBlockOrInlineFlag(src, base.resolve("out-error"), "BI", Exit.ERROR,
                "error: The B and I flags cannot be used together in the -tag option",
                """
                        <div class="block">First sentence. </div>
                        </div>""");
    }

    private void testBlockOrInlineFlag(Path src, Path out, String flag, Exit expectedExit,
                                   String expectedMessage, String... expectedOutput) {
        javadoc("-d", out.toString(),
                "-tag", "custom:A" + flag + ":Custom Note:",
                "--source-path", src.toString(),
                "p");

        checkExit(expectedExit);

        if (expectedMessage != null) {
            checkOutput(Output.OUT, true, expectedMessage);
        }

        checkOrder("p/C.html", expectedOutput);
    }

    // Make sure disabled tags generate no output
    @Test
    public void testDisabled(Path base) throws IOException {
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
        javadoc("-d", base.resolve("out").toString(),
                "-tag", "custom:x:Custom Note:",
                "--source-path", src.toString(),
                "p");
        checkExit(Exit.OK);

        checkOutput("p/C.html", false,
                "inline-note", "block-note", "inline note", "block note",
                "note-tag-custom", "Custom Note");
    }
}
