/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8205593
 * @summary Javadoc -link makes broken links if module name matches package name
 * @library /tools/lib ../../lib
 * @modules
 *      jdk.javadoc/jdk.javadoc.internal.tool
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build javadoc.tester.*
 * @run main TestLinkOptionWithModule
 */

import java.nio.file.Path;
import java.nio.file.Paths;

import builder.ClassBuilder;
import builder.ClassBuilder.*;
import toolbox.ModuleBuilder;
import toolbox.ToolBox;


import javadoc.tester.JavadocTester;

public class TestLinkOptionWithModule extends JavadocTester {

    final ToolBox tb;
    private final Path src;

    public static void main(String... args) throws Exception {
        TestLinkOptionWithModule tester = new TestLinkOptionWithModule();
        tester.runTests(m -> new Object[]{Paths.get(m.getName())});
    }

    TestLinkOptionWithModule() throws Exception {
        tb = new ToolBox();
        src = Paths.get("src");
        initModulesAndPackages();
    }

    @Test
    public void testModuleLinkedToModule(Path base) throws Exception {
        Path out1 = base.resolve("out1a"), out2 = base.resolve("out1b");

        javadoc("-d", out1.toString(),
                "--module-source-path", src.toString(),
                "--module", "com.ex1");

        javadoc("-d", out2.toString(),
                "--module-source-path", src.toString(),
                "--module", "com.ex2",
                "-link", "../" + out1.getFileName());

        checkExit(Exit.OK);
        checkOutput("com.ex2/com/ex2/B.html", true,
                "<a href=\"../../../../out1a/com.ex1/com/ex1/A.html\" "
                + "title=\"class or interface in com.ex1\" class=\"externalLink\">A</a>");
    }

    @Test
    public void testPackageLinkedToPackage(Path base) throws Exception {
        Path out1 = base.resolve("out2a"), out2 = base.resolve("out2b");

        javadoc("-d", out1.toString(),
                "-sourcepath", src.toString(),
                "-subpackages", "com.ex1");

        javadoc("-d", out2.toString(),
                "-sourcepath", src.toString(),
                "-subpackages", "com.ex2",
                "-link", "../" + out1.getFileName());

        checkExit(Exit.OK);
        checkOutput("com/ex2/B.html", true,
                "<a href=\"../../../out2a/com/ex1/A.html\" title=\"class or interface in com.ex1\" "
                + "class=\"externalLink\">A</a>");
    }

    @Test
    public void testModuleLinkedToPackage(Path base) throws Exception {
        Path out1 = base.resolve("out3a"), out2 = base.resolve("out3b");

        javadoc("-d", out1.toString(),
                "-sourcepath", src.toString(),
                "-subpackages", "com.ex1");

        javadoc("-d", out2.toString(),
                "--module-source-path", src.toString(),
                "--module", "com.ex2",
                "-link", "../" + out1.getFileName());

        checkExit(Exit.ERROR);
        checkOutput(Output.OUT, true,
                "The code being documented uses modules but the packages defined "
                + "in ../out3a/ are in the unnamed module");
    }

    @Test
    public void testPackageLinkedToModule(Path base) throws Exception {
        Path out1 = base.resolve("out4a"), out2 = base.resolve("out4b");

        javadoc("-d", out1.toString(),
                "--module-source-path", src.toString(),
                "--module", "com.ex1");

        javadoc("-d", out2.toString(),
                "-sourcepath", src.toString(),
                "-subpackages", "com.ex2",
                "-link", "../" + out1.getFileName());

        checkExit(Exit.ERROR);
        checkOutput(Output.OUT, true,
                "The code being documented uses packages in the unnamed module, but the packages defined "
                + "in ../out4a/ are in named modules");
    }


    void initModulesAndPackages() throws Exception{
        new ModuleBuilder(tb, "com.ex1")
                .exports("com.ex1")
                .classes("package com.ex1; public class A{}")
                .write(src);

        new ModuleBuilder(tb, "com.ex2")
                .requires("com.ex1")
                .exports("com.ex2")
                .classes("package com.ex2; \n"
                        + "import com.ex1.A;\n"
                        + "public class B{\n"
                        + "public B(A obj){}\n"
                        + "}\n")
                .write(src);

        new ClassBuilder(tb, "com.ex1.A")
                .setModifiers("public","class")
                .write(src);

        new ClassBuilder(tb, "com.ex2.B")
                .addImports("com.ex1.A")
                .setModifiers("public","class")
                .addMembers(MethodBuilder.parse("public void foo(A a)"))
                .write(src);
    }

}
