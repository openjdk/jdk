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
 * @bug      8397227
 * @summary  Test search index contents and encoding
 * @library  /tools/lib ../../lib
 * @modules  jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @run main TestSearchIndex
 */

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestSearchIndex extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestSearchIndex();
        tester.runTests();
    }

    ToolBox tb = new ToolBox();

    @Test
    public void testSearchIndex(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                    /**
                     * Traditional doc comment.
                     *
                     * <ul>
                     * <li> {@index "Entities ℤ♥树😀<&>" &Zopf;&hearts;&#x6811;&#x1F600;&lt;&amp;&gt;}
                     * <li> {@index Literal {@literal <&>&lt;&amp;&gt;}}
                     * <li> {@index "Unicode ℤ♥树😀" ℤ♥树😀}
                     * <li> {@index Quotes "'"'}
                     * </ul>
                     * @spec http://example.com/entities Entities: &Zopf;&hearts;&#x6811;&#x1F600;&lt;&amp;&gt;
                     * @spec http://example.com/literal Literal: {@literal <&>&lt;&amp;&gt;}
                     * @spec http://example.com/unicode Unicode: ℤ♥树😀
                     * @spec http://example.com/quotes Quotes: "'"'
                     */
                    public class C {
                        ///
                        /// Markdown doc comment.
                        ///
                        /// - {@index "Entities ℤ♥树😀<&>" &Zopf;&hearts;&#x6811;&#x1F600;&lt;&amp;&gt;}
                        /// - {@index Literal {@literal <&>&lt;&amp;&gt;}}
                        /// - {@index "Unicode ℤ♥树😀"  ℤ♥树😀}
                        /// - {@index Quotes "'"'}
                        ///
                        /// @spec http://example.com/entities Entities: &Zopf;&hearts;&#x6811;&#x1F600;&lt;&amp;&gt;
                        /// @spec http://example.com/literal Literal: {@literal <&>&lt;&amp;&gt;}
                        /// @spec http://example.com/unicode Unicode: ℤ♥树😀
                        /// @spec http://example.com/quotes Quotes: "'"'
                        public void method() {}
                    }
                    """);

        javadoc("-Xdoclint:none",
                "-docencoding", "UTF8",
                "-d", base.resolve("out-utf8").toString(),
                "-sourcepath", src.toString(),
                src.resolve("C.java").toString());
        checkExit(Exit.OK);
        checkJavaScriptEncoding(true);
        checkHtmlEncoding(true);

        javadoc("-Xdoclint:none",
                "-docencoding", "ISO8859_1",
                "-d", base.resolve("out-iso8859-1").toString(),
                "-sourcepath", src.toString(),
                src.resolve("C.java").toString());
        checkExit(Exit.OK);
        checkJavaScriptEncoding(false);
        checkHtmlEncoding(false);

        javadoc("-Xdoclint:none",
                "-docencoding", "ASCII",
                "-d", base.resolve("out-ascii").toString(),
                "-sourcepath", src.toString(),
                src.resolve("C.java").toString());
        checkExit(Exit.OK);
        checkJavaScriptEncoding(false);
        checkHtmlEncoding(false);
    }

    void checkJavaScriptEncoding(boolean isUnicode) {
        String unicode = isUnicode ? "ℤ♥树😀" : "\\u2124\\u2665\\u6811\\ud83d\\ude00";
        String entities = unicode + "<&>";
        String literal = "<&>&lt;&amp;&gt;";
        String quotes = "\\\"'\\\"'";

        checkJavaScriptIndexEntry("Entities " + entities, entities);
        checkJavaScriptIndexEntry("Literal", literal);
        checkJavaScriptIndexEntry("Unicode " + unicode, unicode);
        checkJavaScriptIndexEntry("Quotes", quotes);

        checkJavaScriptIndexEntry("Entities: " + entities, null);
        checkJavaScriptIndexEntry("Literal: " + literal, null);
        checkJavaScriptIndexEntry("Unicode: " + unicode, null);
        checkJavaScriptIndexEntry("Quotes: " + quotes, null);
    }

    void checkJavaScriptIndexEntry(String label, String description) {
        for (String holder : List.of("class C", "C.method()")) {
            String expected = "\"l\":\"" + label + "\",\"h\":\"" + holder + "\"";
            if (description != null) {
                expected += ",\"d\":\"" + description + "\"";
            }
            checkOutput("tag-search-index.js", true, expected);
        }
    }

    void checkHtmlEncoding(boolean isUnicode) {
        String unicode = isUnicode ? "ℤ♥树😀" : "&#x2124;&#x2665;&#x6811;&#x1F600;";
        String entities = unicode + "&lt;&amp;&gt;";
        String literal = "&lt;&amp;&gt;&amp;lt;&amp;amp;&amp;gt;";
        String quotes = "\"'\"'";

        checkOutput("index-all.html", true,
                ">Entities " + entities + "</a>",
                ">Unicode " + unicode + "</a>",
                "<dd>" + entities + "</dd>",
                "<dd>" + literal + "</dd>",
                "<dd>" + unicode + "</dd>",
                "<dd>" + quotes + "</dd>",
                ">Entities: " + entities + "</a>",
                ">Literal: " + literal + "</a>",
                ">Unicode: " + unicode + "</a>",
                ">Quotes: " + quotes + "</a>");

        checkOutput("search-tags.html", true,
                "Entities " + entities + "</div>",
                "Unicode " + unicode + "</div>",
                entities + "</div>",
                literal + "</div>",
                unicode + "</div>",
                quotes + "</div>");

        checkOutput("external-specs.html", true,
                "<a href=\"http://example.com/entities\">Entities: " + entities + "</a>",
                "<a href=\"http://example.com/literal\">Literal: " + literal + "</a>",
                "<a href=\"http://example.com/unicode\">Unicode: " + unicode + "</a>",
                "<a href=\"http://example.com/quotes\">Quotes: " + quotes + "</a>");
    }

}
