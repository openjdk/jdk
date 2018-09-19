/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @bug 8210047
 * @summary some pages contains content outside of landmark region
 * @library /tools/lib ../lib
 * @modules
 *      jdk.javadoc/jdk.javadoc.internal.tool
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build JavadocTester
 * @run main TestHtmlLankmarkRegions
 */


import java.nio.file.Path;
import java.nio.file.Paths;

import builder.ClassBuilder;
import toolbox.ModuleBuilder;
import toolbox.ToolBox;

public class TestHtmlLankmarkRegions extends JavadocTester {

    final ToolBox tb;

    public static void main(String... args) throws Exception {
        TestHtmlLankmarkRegions tester = new TestHtmlLankmarkRegions();
        tester.runTests(m -> new Object[]{Paths.get(m.getName())});
    }

    TestHtmlLankmarkRegions() {
        tb = new ToolBox();
    }

    @Test
    void testModules(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        createModules(srcDir);

        Path outDir = base.resolve("out");
        javadoc("-d", outDir.toString(),
                "-doctitle", "Document Title",
                "-header", "Test Header",
                "--frames",
                "--module-source-path", srcDir.toString(),
                "--module", "m1,m2");

        checkExit(Exit.OK);

        checkOrder("module-overview-frame.html",
                "<header role=\"banner\">\n"
                + "<h1 title=\"Test Header\" class=\"bar\">Test Header</h1>\n"
                + "<nav role=\"navigation\" class=\"indexNav\">",
                "<main role=\"main\">\n"
                + "<div class=\"indexContainer\">\n"
                + "<h2 title=\"Modules\">Modules</h2>\n"
                + "<ul title=\"Modules\">",
                "<footer role=\"contentinfo\">");

        checkOrder("m1/module-frame.html",
                "<header role=\"banner\">\n"
                + "<h1 title=\"Test Header\" class=\"bar\">Test Header</h1>\n"
                + "<nav role=\"navigation\" class=\"indexNav\">",
                "<main role=\"main\">\n"
                + "<div class=\"indexContainer\">\n"
                + "<h2 title=\"m1\"><a href=\"module-summary.html\" target=\"classFrame\">m1</a>&nbsp;Packages</h2>",
                "<footer role=\"contentinfo\">");

        checkOrder("overview-summary.html",
                "<header role=\"banner\">\n"
                + "<nav role=\"navigation\">",
                "<main role=\"main\">\n"
                + "<div class=\"header\">\n"
                + "<h1 class=\"title\">Document Title</h1>",
                "<footer role=\"contentinfo\">\n"
                + "<nav role=\"navigation\">");
    }

    @Test
    void testModulesHtml4(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        createModules(srcDir);

        Path outDir = base.resolve("out2");
        javadoc("-d", outDir.toString(),
                "-doctitle", "Document Title",
                "-header", "Test Header",
                "--frames",
                "--module-source-path", srcDir.toString(),
                "--module", "m1,m2",
                "-html4");

        checkExit(Exit.OK);

        checkOrder("module-overview-frame.html",
                "<h1 title=\"Test Header\" class=\"bar\">Test Header</h1>\n"
                + "<div class=\"indexNav\">",
                "<div class=\"indexContainer\">\n"
                + "<h2 title=\"Modules\">Modules</h2>\n"
                + "<ul title=\"Modules\">");

        checkOrder("m1/module-frame.html",
                "<h1 title=\"Test Header\" class=\"bar\">Test Header</h1>\n"
                + "<div class=\"indexNav\">",
                "<div class=\"indexContainer\">\n"
                + "<h2 title=\"m1\"><a href=\"module-summary.html\" target=\"classFrame\">m1</a>&nbsp;Packages</h2>");

        checkOrder("overview-summary.html",
                "<div class=\"fixedNav\">",
                "<div class=\"header\">\n"
                + "<h1 class=\"title\">Document Title</h1>",
                "<div class=\"bottomNav\"><a name=\"navbar.bottom\">");
    }

    @Test
    void testPackages(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        createPackages(srcDir);

        Path outDir = base.resolve("out3");
        javadoc("-d", outDir.toString(),
                "-doctitle", "Document Title",
                "-header", "Test Header",
                "--frames",
                "-sourcepath", srcDir.toString(),
                "pkg1", "pkg2");

        checkExit(Exit.OK);

        checkOrder("overview-summary.html",
                "<header role=\"banner\">\n"
                + "<nav role=\"navigation\">",
                "<main role=\"main\">\n"
                + "<div class=\"header\">\n"
                + "<h1 class=\"title\">Document Title</h1>",
                "<footer role=\"contentinfo\">\n" +
                        "<nav role=\"navigation\">");

        checkOrder("overview-frame.html",
                "<header role=\"banner\">\n"
                + "<h1 title=\"Test Header\" class=\"bar\">Test Header</h1>\n"
                + "<nav role=\"navigation\" class=\"indexNav\">",
                "<main role=\"main\">\n"
                + "<div class=\"indexContainer\">\n"
                + "<h2 title=\"Packages\">Packages</h2>",
                "<footer role=\"contentinfo\">");
    }

    @Test
    void testPackagesHtml4(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        createPackages(srcDir);

        Path outDir = base.resolve("out4");
        javadoc("-d", outDir.toString(),
                "-doctitle", "Document Title",
                "-header", "Test Header",
                "--frames",
                "-sourcepath", srcDir.toString(),
                "pkg1", "pkg2",
                "-html4");

        checkExit(Exit.OK);

        checkOrder("overview-summary.html",
                "<div class=\"fixedNav\">",
                "<div class=\"header\">\n"
                + "<h1 class=\"title\">Document Title</h1>",
                "<div class=\"bottomNav\"><a name=\"navbar.bottom\">");

        checkOrder("overview-frame.html",
                "<h1 title=\"Test Header\" class=\"bar\">Test Header</h1>\n"
                + "<div class=\"indexNav\">",
                "<div class=\"indexContainer\">\n"
                + "<h2 title=\"Packages\">Packages</h2>"
        );
    }

    void createModules(Path srcDir) throws Exception {
        new ModuleBuilder(tb, "m1")
                .classes("package p1; public class a{}")
                .classes("package p2; public class b{}")
                .write(srcDir);
        new ModuleBuilder(tb, "m2")
                .classes("package p3; public class c{}")
                .classes("package p4; public class d{}")
                .write(srcDir);
    }

    void createPackages(Path srcDir) throws Exception {
        new ClassBuilder(tb, "pkg1.A")
                .setModifiers("public", "class")
                .write(srcDir);
        new ClassBuilder(tb, "pkg2.B")
                .setModifiers("public", "class")
                .write(srcDir);
    }
}
