/*
 * Copyright (c) 2002, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4695326 4750173 4920381 8078320 8071982 8239804 8393742
 * @summary Test the declaration of simple tags using -tag. Verify that
 * "-tag name" is a shortcut for "-tag name:a:Name:".  Also verity that
 * you can escape the ":" character with a back slash so that it is not
 * considered a separator when parsing the simple tag argument.
 * @library /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build toolbox.ToolBox javadoc.tester.*
 * @run main TestSimpleTag
 */

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

import java.io.IOException;
import java.nio.file.Path;

public class TestSimpleTag extends JavadocTester {

    private final ToolBox tb = new ToolBox();

    public static void main(String... args) throws Exception {
        var tester = new TestSimpleTag();
        tester.runTests();
    }

    @Test
    public void test() {
        javadoc("-d", "out",
                "-sourcepath", testSrc,
                "-tag", "param",
                "-tag", "todo",
                "-tag", "ejb\\:bean:a:EJB Beans:",
                "-tag", "regular:a:Regular Tag:",
                "-tag", "tag-with-hyphens:a:Tag-With-Hyphens:",
                testSrc("C.java"));
        checkExit(Exit.OK);

        checkOutput("C.html", true,
                "<dl class=\"notes\">",
                "<dt>Todo:</dt>",
                "<dt>EJB Beans:</dt>",
                "<dt>Regular Tag:</dt>",
                "<dt>Tag-With-Hyphens:</dt>",
                """
                    <dt>Parameters:</dt>
                    <dd><code>arg</code> - this is an int argument.</dd>""");
    }


    // Multiple custom tags should be encoded as individual <dd> elements,
    // while preserving comma-separated list format for other simple tags.
    @Test
    public void testMultipleTags(Path base) throws IOException {
        Path src = base.resolve("src");

        tb.writeJavaFiles(src, """
                package test;
                /**
                 * Test class with multiple tags.
                 *
                 * @warning
                 * First warning.
                 *
                 * @version 1.0
                 * @author Duke
                 * @author Leo
                 * @author Lisa
                 * @version 1.0.1
                 *
                 * @note This is a note.
                 *
                 * @note Second note with multiple paragraphs.
                 * <p>Second paragraph of second note.</p>
                 * <p>Third paragraph of second note.</p>
                 *
                 * @note Third note.
                 *
                 * @warning Another warning.
                 * <ul>
                 * <li>First list item.
                 * <li>Second list item.
                 * </ul>
                 */
                public class MultiTag {
                }
                """);

        javadoc("-d", base.resolve("out").toString(),
                "-author", "-version",
                "-tag", "warning:a:Warning:",
                "-tag", "note:a:Note:",
                "-tag", "author",
                "-tag", "version",
                "-sourcepath", src.toString(),
                "test");

        checkExit(Exit.OK);

        checkOutput("test/MultiTag.html", true, """
                <dl class="notes">
                <dt>Warning:</dt>
                <dd>First warning.</dd>
                <dd>Another warning.
                <ul>
                <li>First list item.
                <li>Second list item.
                </ul></dd>
                """,
            """
                <dt>Note:</dt>
                <dd>This is a note.</dd>
                <dd>Second note with multiple paragraphs.
                <p>Second paragraph of second note.</p>
                <p>Third paragraph of second note.</p></dd>
                <dd>Third note.</dd>
                """,
            """
                <dt>Author:</dt>
                <dd>Duke, Leo, Lisa</dd>
                <dt>Version:</dt>
                <dd>1.0, 1.0.1</dd>
                </dl>
                """);
    }
}
