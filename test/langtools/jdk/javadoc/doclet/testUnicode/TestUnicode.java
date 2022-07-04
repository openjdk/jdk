/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8203176 8271258
 * @summary  javadoc handles non-ASCII characters incorrectly
 * @library  /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @run main TestUnicode
 */

import java.nio.file.Files;
import java.nio.file.Path;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestUnicode extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestUnicode tester = new TestUnicode();
        tester.runTests(m -> new Object[] { Path.of(m.getName())});
    }

    ToolBox tb = new ToolBox();

    @Test
    public void testUnicode(Path base) throws Exception {
        char ellipsis = '\u2026';
        Path src = Files.createDirectories(base.resolve("src"));
        tb.writeJavaFiles(src,
                "/** Hel" + ellipsis + "lo {@code World(" + ellipsis + ")}. */\n"
                + "public class Code { }\n");

        javadoc("-d", base.resolve("out").toString(),
                "-encoding", "utf-8",
                src.resolve("Code.java").toString());
        checkExit(Exit.OK);

        checkOutput("Code.html", true,
                "<div class=\"block\">Hel" + ellipsis + "lo <code>World(" + ellipsis + ")</code>.</div>");
        checkOutput("Code.html", false,
                "\\u");
    }

    @Test
    public void testParam(Path base) throws Exception {
        String chineseElephant = "\u5927\u8c61"; // taken from JDK-8271258
        Path src = Files.createDirectories(base.resolve("src"));
        tb.writeJavaFiles(src,
                """
                    /**
                     * Comment. ##.
                     * @param <##> the ##
                     */
                    public class Code<##> {
                        /**
                         * Comment. ##.
                         * @param ## the ##
                         */
                         public void set##(int ##) { }
                    }""".replaceAll("##", chineseElephant));

        javadoc("-d", base.resolve("out").toString(),
                "-encoding", "utf-8",
                "--no-platform-links",
                src.resolve("Code.java").toString());
        checkExit(Exit.OK);

        checkOutput("Code.html", true,
                """
                    <h1 title="Class Code" class="title">Class Code&lt;##&gt;</h1>
                    """.replaceAll("##", chineseElephant),
                """
                    <div class="inheritance" title="Inheritance Tree">java.lang.Object
                    <div class="inheritance">Code&lt;##&gt;</div>
                    </div>
                    """.replaceAll("##", chineseElephant),
                """
                    <dl class="notes">
                    <dt>Type Parameters:</dt>
                    <dd><code>##</code> - the ##</dd>
                    </dl>
                    """.replaceAll("##", chineseElephant),
                """
                    <section class="detail" id="set##(int)">
                    <h3>set##</h3>
                    <div class="member-signature"><span class="modifiers">public</span>&nbsp;<span c\
                    lass="return-type">void</span>&nbsp;<span class="element-name">set##</span><wbr>\
                    <span class="parameters">(int&nbsp;##)</span></div>
                    <div class="block">Comment. ##.</div>
                    <dl class="notes">
                    <dt>Parameters:</dt>
                    <dd><code>##</code> - the ##</dd>
                    </dl>
                    </section>
                    """.replaceAll("##", chineseElephant)
                );

        // The following checks for the numeric forms of the Unicode characters being tested:
        // these numeric forms should not show up as literal character sequences.
        checkOutput("Code.html", false,
                Integer.toHexString(chineseElephant.charAt(0)),
                Integer.toHexString(chineseElephant.charAt(1))
                );
    }
}
