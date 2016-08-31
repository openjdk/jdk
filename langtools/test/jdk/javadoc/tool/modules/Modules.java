/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8159305
 * @summary Tests primarily the module graph computations.
 * @modules
 *      jdk.javadoc/jdk.javadoc.internal.api
 *      jdk.javadoc/jdk.javadoc.internal.tool
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @library /tools/lib
 * @build toolbox.ToolBox toolbox.TestRunner
 * @run main Modules
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import toolbox.*;

public class Modules extends ModuleTestBase {

    public static void main(String... args) throws Exception {
        new Modules().runTests();
    }

    @Test
    public void testBasicMoption(Path base) throws Exception {
        Files.createDirectory(base);
        Path src = base.resolve("src");
        ModuleBuilder mb = new ModuleBuilder(tb, "m1");
        mb.comment("The first module.")
                .exports("pub")
                .classes("package pub; /** Klass A */ public class A {}")
                .classes("package pro; /** Klass B */ public class B {}")
                .write(src);
        execTask("--module-source-path", src.toString(),
                 "--module", "m1");
        checkModulesSpecified("m1");
        checkPackagesIncluded("pub");
        checkTypesIncluded("pub.A");
    }

    @Test
    public void testMultipleModulesOption1(Path base) throws Exception {
        Path src = base.resolve("src");

        ModuleBuilder mb1 = new ModuleBuilder(tb, "m1");
        mb1.comment("The first module.")
                .exports("m1pub")
                .requires("m2")
                .classes("package m1pub; /** Klass A */ public class A {}")
                .classes("package m1pro; /** Klass B */ public class B {}")
                .write(src);

        ModuleBuilder mb2 = new ModuleBuilder(tb, "m2");
        mb2.comment("The second module.")
                .exports("m2pub")
                .classes("package m2pub; /** Klass A */ public class A {}")
                .classes("package m2pro; /** Klass B */ public class B {}")
                .write(src);
        execTask("--module-source-path", src.toString(),
            "--module", "m1,m2");
        checkModulesSpecified("m1", "m2");
        checkPackagesIncluded("m1pub", "m2pub");
        checkTypesIncluded("m1pub.A", "m2pub.A");

    }

    @Test
    public void testMultipleModulesAggregatedModuleOption(Path base) throws Exception {
        Path src = base.resolve("src");

        ModuleBuilder mb1 = new ModuleBuilder(tb, "m1");
        mb1.comment("The first module.")
                .exports("m1pub")
                .requires("m2")
                .classes("package m1pub; /** Klass A */ public class A {}")
                .classes("package m1pro; /** Klass B */ public class B {}")
                .write(src);

        ModuleBuilder mb2 = new ModuleBuilder(tb, "m2");
        mb2.comment("The second module.")
                .exports("m2pub")
                .classes("package m2pub; /** Klass A */ public class A {}")
                .classes("package m2pro; /** Klass B */ public class B {}")
                .write(src);
        execTask("--module-source-path", src.toString(),
            "--module", "m1",
            "--module", "m2");
        checkModulesSpecified("m1", "m2");
        checkPackagesIncluded("m1pub", "m2pub");
        checkTypesIncluded("m1pub.A", "m2pub.A");

    }

   /**
     * Tests diamond graph, inspired by javac diamond tests.
     *
     *
     * Module M : test module, with variable requires
     *
     * Module N :
     *     requires public O  --->   Module O:
     *                                 requires J   ---->   Module J:
     *                                 exports openO          exports openJ
     *
     *
     * Module L :
     *     requires public P  --->   Module P:
     *                                 exports openP
     *
     *
     */

    @Test
    public void testExpandRequiresNone(Path base) throws Exception {
        Path src = base.resolve("src");

        createAuxiliaryModules(src);

        new ModuleBuilder(tb, "M")
                .comment("The M module.")
                .requires("N", src)
                .requires("L", src)
                .requires("O", src)
                .exports("p")
                .classes("package p; public class Main { openO.O o; openN.N n; openL.L l; }")
                .write(src);

        execTask("--module-source-path", src.toString(),
                "--module", "M");

        checkModulesSpecified("M");
        checkModulesIncluded("M");
        checkPackagesIncluded("p");
        checkTypesIncluded("p.Main");
        checkPackagesNotIncluded(".*open.*");
    }

    @Test
    public void testExpandRequiresPublic(Path base) throws Exception {
        Path src = base.resolve("src");

        createAuxiliaryModules(src);

        new ModuleBuilder(tb, "M")
                .comment("The M module.")
                .requiresPublic("N", src)
                .requires("L", src)
                .exports("p")
                .classes("package p; public class Main { openO.O o; openN.N n; openL.L l; }")
                .write(src);

        execTask("--module-source-path", src.toString(),
                "--module", "M",
                "--expand-requires:public");

        checkModulesSpecified("M", "N", "O");
        checkModulesIncluded("M", "N", "O");
        checkPackagesIncluded("p", "openN", "openO");
        checkTypesIncluded("p.Main", "openN.N", "openO.O");
    }

    @Test
    public void testExpandRequiresAll(Path base) throws Exception {
        Path src = base.resolve("src");

        createAuxiliaryModules(src);

        new ModuleBuilder(tb, "M")
                .comment("The M module.")
                .requiresPublic("N", src)
                .requires("L", src)
                .requires("O", src)
                .exports("p")
                .classes("package p; public class Main { openO.O o; openN.N n; openL.L l; }")
                .write(src);

        execTask("--module-source-path", src.toString(),
                "--module", "M",
                "--expand-requires:all");

        checkModulesSpecified("M", "java.base", "N", "L", "O");
        checkModulesIncluded("M", "java.base", "N", "L", "O");
        checkModulesNotIncluded("P", "J", "Q");
        checkPackagesIncluded("p", "openN", "openL", "openO");
        checkPackagesNotIncluded(".*openP.*", ".*openJ.*");
        checkTypesIncluded("p.Main", "openN.N", "openL.L", "openO.O");
        checkTypesNotIncluded(".*openP.*", ".*openJ.*");
    }

    @Test
    public void testMissingModule(Path base) throws Exception {
        Path src = base.resolve("src");

        createAuxiliaryModules(src);

        new ModuleBuilder(tb, "M")
                .comment("The M module.")
                .requiresPublic("N", src)
                .requires("L", src)
                .requires("O", src)
                .exports("p")
                .classes("package p; public class Main { openO.O o; openN.N n; openL.L l; }")
                .write(src);

        execNegativeTask("--module-source-path", src.toString(),
                "--module", "MIA",
                "--expand-requires:all");

        assertErrorPresent("javadoc: error - module MIA not found.");
    }

    @Test
    public void testMissingModuleMultiModuleCmdline(Path base) throws Exception {
        Path src = base.resolve("src");

        createAuxiliaryModules(src);

        new ModuleBuilder(tb, "M")
                .comment("The M module.")
                .requiresPublic("N", src)
                .requires("L", src)
                .requires("O", src)
                .exports("p")
                .classes("package p; public class Main { openO.O o; openN.N n; openL.L l; }")
                .write(src);

        execNegativeTask("--module-source-path", src.toString(),
                "--module", "M,N,L,MIA,O,P",
                "--expand-requires:all");

        assertErrorPresent("javadoc: error - module MIA not found");
    }

    void createAuxiliaryModules(Path src) throws IOException {

        new ModuleBuilder(tb, "J")
                .comment("The J module.")
                .exports("openJ")
                .classes("package openJ;  /** Klass J open. */ public class J { }")
                .classes("package closedJ; /** Klass J closed. */ public class J  { }")
                .write(src);

        new ModuleBuilder(tb, "L")
                .comment("The L module.")
                .exports("openL")
                .requiresPublic("P")
                .classes("package openL; /** Klass L open */ public class L { }")
                .classes("package closedL;  /** Klass L closed */ public class L { }")
                .write(src);

        new ModuleBuilder(tb, "N")
                .comment("The N module.")
                .exports("openN")
                .requiresPublic("O")
                .classes("package openN; /** Klass N open */ public class N  { }")
                .classes("package closedN; /** Klass N closed */ public class N { }")
                .write(src);

        new ModuleBuilder(tb, "O")
                .comment("The O module.")
                .exports("openO")
                .requires("J")
                .classes("package openO; /** Klass O open. */ public class O { openJ.J j; }")
                .classes("package closedO;  /** Klass O closed. */ public class O { }")
                .write(src);

        new ModuleBuilder(tb, "P")
                .comment("The O module.")
                .exports("openP")
                .requires("J")
                .classes("package openP; /** Klass O open. */ public class O { openJ.J j; }")
                .classes("package closedP;  /** Klass O closed. */ public class O { }")
                .write(src);

        new ModuleBuilder(tb, "Q")
                .comment("The Q module.")
                .exports("openQ")
                .requires("J")
                .classes("package openQ; /** Klass Q open. */ public class Q { openJ.J j; }")
                .classes("package closedQ;  /** Klass Q closed. */ public class Q { }")
                .write(src);

    }
}
