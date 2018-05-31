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
 * @bug 8196027 8196202
 * @summary test navigation links
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.javadoc/jdk.javadoc.internal.api
 *          jdk.javadoc/jdk.javadoc.internal.tool
 * @library ../lib /tools/lib
 * @build toolbox.ToolBox toolbox.ModuleBuilder JavadocTester
 * @run main TestModuleNavigation
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import toolbox.*;

public class TestModuleNavigation extends JavadocTester {

    public final ToolBox tb;
    public static void main(String... args) throws Exception {
        TestModuleNavigation  tester = new TestModuleNavigation ();
        tester.runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    public TestModuleNavigation () {
        tb = new ToolBox();
    }

    @Test
    public void checkNavbar(Path base) throws Exception {
        Path src = Files.createDirectories(base.resolve("src"));
        ModuleBuilder mb = new ModuleBuilder(tb, "m")
                .comment("module m.\n@uses p1.A")
                .uses("p1.A")
                .uses("p1.B")
                .exports("p1")
                .classes("package p1; public class A {}")
                .classes("package p1; public class B {}");
        mb.write(src);
        ModuleBuilder mb1 = new ModuleBuilder(tb, "m2")
                .comment("module m2.\n@uses m2p1.Am2")
                .uses("m2p1.Am2")
                .uses("m2p1.Bm2")
                .exports("m2p1")
                .classes("package m2p1; public class Am2 {}")
                .classes("package m2p1; public class Bm2 {}");
        mb1.write(src);

        javadoc("-d", base.resolve("out").toString(), "-use",
                "-quiet",
                "--frames",
                "--module-source-path", src.toString(),
                "--module", "m,m2");
        checkExit(Exit.OK);

        checkOutput("overview-summary.html", false,
                "Prev",
                "Next");

        checkOutput("m/module-summary.html", false,
                "Prev&nbsp;Module",
                "Next&nbsp;Module");

        checkOutput("m2/m2p1/package-summary.html", false,
                "Prev&nbsp;Package",
                "Next&nbsp;Package");

        checkOutput("m2/m2p1/Am2.html", false,
                "Prev&nbsp;Class",
                "Next&nbsp;Class");

        checkOutput("m2/m2p1/class-use/Am2.html", false,
                "Prev",
                "Next");

        checkOutput("m2/m2p1/package-tree.html", false,
                "Prev",
                "Next");

        checkOutput("deprecated-list.html", false,
                "Prev",
                "Next");

        checkOutput("index-all.html", false,
                "Prev",
                "Next");
    }
}
