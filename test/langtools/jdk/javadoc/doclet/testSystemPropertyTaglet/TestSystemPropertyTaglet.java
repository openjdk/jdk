/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5076751
 * @summary System properties documentation needed in javadocs
 * @library /tools/lib ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build JavadocTester toolbox.ToolBox builder.ClassBuilder
 * @run main TestSystemPropertyTaglet
 */


import java.nio.file.Path;
import java.nio.file.Paths;

import builder.ClassBuilder;
import builder.ClassBuilder.MethodBuilder;
import toolbox.ToolBox;

public class TestSystemPropertyTaglet extends JavadocTester {

    final ToolBox tb;

    public static void main(String... args) throws Exception {
        TestSystemPropertyTaglet tester = new TestSystemPropertyTaglet();
        tester.runTests(m -> new Object[]{Paths.get(m.getName())});
    }

    TestSystemPropertyTaglet() {
        tb = new ToolBox();
    }

    @Test
    void test(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");

        MethodBuilder method = MethodBuilder
                .parse("public void func(A a) {}")
                .setComments("test with {@systemProperty java.version}");

        new ClassBuilder(tb, "pkg.A")
                .setComments("test with {@systemProperty user.name}")
                .setModifiers("public", "class")
                .addMembers(method)
                .write(srcDir);

        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");

        checkExit(Exit.OK);

        checkOrder("pkg/A.html",
                "<h2 title=\"Class A\" class=\"title\">Class A</h2>",
                "test with <code><a id=\"user.name\" class=\"searchTagResult\">user.name</a></code>",
                "<h3>Method Detail</h3>",
                "test with <code><a id=\"java.version\" class=\"searchTagResult\">java.version</a></code>");

        checkOrder("index-all.html",
                "<h2 class=\"title\">J</h2>",
                "<dt><span class=\"searchTagLink\"><a href=\"pkg/A.html#java.version\">java.version</a>"
                + "</span> - Search tag in pkg.A</dt>\n<dd>System Property</dd>",
                "<h2 class=\"title\">U</h2>",
                "<dt><span class=\"searchTagLink\"><a href=\"pkg/A.html#user.name\">user.name</a></span>"
                + " - Search tag in pkg.A</dt>\n<dd>System Property</dd>");

        checkOutput("tag-search-index.js", true,
                "{\"l\":\"java.version\",\"h\":\"pkg.A\",\"d\":\"System Property\","
                + "\"u\":\"pkg/A.html#java.version\"}");

        checkOutput("tag-search-index.js", true,
                "{\"l\":\"user.name\",\"h\":\"pkg.A\",\"d\":\"System Property\","
                + "\"u\":\"pkg/A.html#user.name\"}");
    }

    @Test
    void testSystemProperytWithinATag(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");

        new ClassBuilder(tb, "pkg2.A")
                .setModifiers("public", "class")
                .addMembers(MethodBuilder.parse("public void func(){}")
                        .setComments("a within a : <a href='..'>{@systemProperty user.name}</a>"))
                .write(srcDir);

        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg2");

        checkExit(Exit.OK);

        checkOutput(Output.OUT, true,
                "warning: {@systemProperty} tag, which expands to <a>, within <a>");
    }
}
