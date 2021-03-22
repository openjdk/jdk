/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8263468
 * @summary  New page for "recent" new API
 * @library  /tools/lib ../../lib
 * @modules  jdk.javadoc/jdk.javadoc.internal.tool
 * @build    javadoc.tester.* toolbox.ToolBox builder.ClassBuilder
 * @run main TestNewApiList
 */

import builder.ClassBuilder;
import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

import java.nio.file.Path;
import java.nio.file.Paths;

public class TestNewApiList extends JavadocTester {

    final ToolBox tb;

    public static void main(String... args) throws Exception {
        TestNewApiList test = new TestNewApiList();
        test.runTests(m -> new Object[]{Paths.get(m.getName())});
    }

    TestNewApiList() {
        tb = new ToolBox();
    }

    @Test
    public void test(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");

        new ClassBuilder(tb, "pkg.A")
                .setComments("abc")
                .setModifiers("public", "class")
                .write(srcDir);

        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");

        checkExit(Exit.OK);
    }
}
