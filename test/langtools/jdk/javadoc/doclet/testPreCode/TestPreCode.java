/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8352389
 * @summary Remove incidental whitespace in pre/code content
 * @library /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build javadoc.tester.* toolbox.ToolBox builder.ClassBuilder
 * @run main TestPreCode
 */


import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import builder.AbstractBuilder;
import builder.ClassBuilder;
import toolbox.ToolBox;

import javadoc.tester.JavadocTester;

public class TestPreCode extends JavadocTester {

    final ToolBox tb;

    public static void main(String... args) throws Exception {
        var tester = new TestPreCode();
        tester.runTests();
    }

    TestPreCode() {
        tb = new ToolBox();
    }

    @Test
    public void testWhitespace(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");

        new ClassBuilder(tb, "pkg.A")
                .setComments("""
                    Class A.
                    <pre> \t<code>
                      first line
                      second line
                    </code></pre>""")
                .setModifiers("public", "class")
                .addMembers(ClassBuilder.MethodBuilder.parse("public void m0() {}")
                                .setComments("""
                                Method m0.
                                <pre> {@code
                                  first line
                                  second line
                                }</pre>"""),
                        ClassBuilder.MethodBuilder.parse("public void m1() {}")
                                .setComments("""
                                Method m1.
                                <pre> <code> first line
                                  second line
                                </code></pre>"""),
                        ClassBuilder.MethodBuilder.parse("public void m2() {}")
                                .setComments("""
                                Method m2.
                                <pre> {@code\s
                                  first line
                                  second line
                                }</pre>"""),
                        ClassBuilder.MethodBuilder.parse("public void m3() {}")
                                .setComments("""
                                Method m3.
                                <pre>  .<code>
                                  second line
                                </code></pre>"""))
                .write(srcDir);

        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");

        checkExit(Exit.OK);

        checkOrder("pkg/A.html",
                """
                    Class A.
                    <pre><code>  first line
                      second line
                    </code></pre>""",
                """
                    Method m0.
                    <pre><code>  first line
                      second line
                    </code></pre>""",
                """
                    Method m1.
                    <pre> <code> first line
                      second line
                    </code></pre>""",
                """
                    Method m2.
                    <pre><code>  first line
                      second line
                    </code></pre>""",
                """
                    Method m3.
                    <pre>  .<code>
                      second line
                    </code></pre>""");
    }

    @Test
    public void testUnclosed(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");

        new ClassBuilder(tb, "pkg.A")
                .setComments("""
                    Class A.
                    <pre><code>
                      first line
                      second line
                    </code>""")
                .setModifiers("public", "class")
                .write(srcDir);

        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");

        checkExit(Exit.ERROR);

        // No whitespace normalization for unclosed <pre> element
        checkOrder("pkg/A.html",
                """
                    Class A.
                    <pre><code>
                      first line
                      second line
                    </code></div>""");
    }

}
