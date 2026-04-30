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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestNoteTag extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestNoteTag();
        tester.runTests();
    }

    ToolBox tb = new ToolBox();

    @Test
    public void testMultipleBlockNotes(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package p;
                /**
                 * @note First note
                 * @note Second note
                 * @note [header="Important:"] First important note
                 * @note [header="Important:"] Second important note
                 * @note [header="Warning:" id="first-warning" kind="warning"] First warning
                 * @note [header="Warning:" id="second-warning"] Second warning
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
                        <div class="block-note note-tag" id="note-p.C">
                        <dt>Note:</dt>
                        <dd>First note</dd>
                        <dd>Second note</dd>
                        </div>""",
                """
                        <div class="block-note note-tag" id="note-p.C1">
                        <dt>Important:</dt>
                        <dd>First important note</dd>
                        <dd>Second important note</dd>
                        </div>""",
                """
                        <div class="block-note note-tag-warning" id="first-warning">
                        <dt>Warning:</dt>
                        <dd>First warning</dd>
                        <dd id="second-warning">Second warning</dd>
                        </div>
                        </dl>""");
    }


}
