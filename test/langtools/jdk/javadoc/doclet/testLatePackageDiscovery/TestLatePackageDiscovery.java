/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8304878
 * @library  /tools/lib ../../lib
 * @modules  jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @run main TestLatePackageDiscovery
 */

import java.io.IOException;
import java.nio.file.Path;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestLatePackageDiscovery extends JavadocTester {

    private final ToolBox tb = new ToolBox();

    public static void main(String... args) throws Exception {
        new TestLatePackageDiscovery().runTests();
    }

    @Test
    public void testLatePackageDiscovery(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src.resolve("test.bar"),
                "module test.bar { exports bar; }",
                "package bar;",
                "package bar; class Base { public void m() { } }",
                "package bar; public class Bar extends Base { }");

        tb.writeJavaFiles(src.resolve("test.foo"),
                "module test.foo { exports foo; requires test.bar; }",
                "package foo;",
                "package foo; public class Foo extends bar.Bar { }");

        javadoc("-d", base.resolve("out").toString(),
                "--module-source-path", src.toString(),
                "--module", "test.foo",
                "-nodeprecated");

        setAutomaticCheckNoStacktrace(true); // no exceptions!
        checkExit(Exit.OK);
    }
}
