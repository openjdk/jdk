/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8195795
 * @summary test the use of module directories in output,
 *          and the --no-module-directories option
 * @modules jdk.javadoc/jdk.javadoc.internal.api
 *          jdk.javadoc/jdk.javadoc.internal.tool
 *          jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @library ../lib /tools/lib
 * @build toolbox.ToolBox toolbox.ModuleBuilder JavadocTester
 * @run main TestModuleDirs
 */

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import toolbox.ModuleBuilder;
import toolbox.ToolBox;

public class TestModuleDirs extends JavadocTester {

    public final ToolBox tb;
    public static void main(String... args) throws Exception {
        TestModuleDirs tester = new TestModuleDirs();
        tester.runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    public TestModuleDirs() {
        tb = new ToolBox();
    }

    @Test
    public void testNoModules(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, "package p; public class C { }");

        javadoc("-d", base.resolve("api").toString(),
                "-sourcepath", src.toString(),
                "-quiet",
                "p");

        checkExit(Exit.OK);
        checkFiles(true, "p/package-summary.html");
    }

    @Test
    public void testNoModuleDirs(Path base) throws IOException {
        Path src = base.resolve("src");
        new ModuleBuilder(tb, "m")
                .classes("package p; public class A {}")
                .exports("p")
                .write(src);

        javadoc("-d", base.resolve("api").toString(),
                "-quiet",
                "--module-source-path", src.toString(),
                "--no-module-directories",
                "--module", "m");

        checkExit(Exit.OK);
        checkFiles(true,
                "m-summary.html",
                "p/package-summary.html");
        checkFiles(false,
                "m/module-summary.html",
                "m/p/package-summary.html");
    }

    @Test
    public void testModuleDirs(Path base) throws IOException {
        Path src = base.resolve("src");
        new ModuleBuilder(tb, "m")
                .classes("package p; public class A {}")
                .exports("p")
                .write(src);

        javadoc("-d", base.resolve("api").toString(),
                "-quiet",
                "--module-source-path", src.toString(),
                "--module", "m");

        checkExit(Exit.OK);
        checkFiles(false,
                "m-summary.html",
                "p/package-summary.html");
        checkFiles(true,
                "m/module-summary.html",
                "m/p/package-summary.html");
    }
}

