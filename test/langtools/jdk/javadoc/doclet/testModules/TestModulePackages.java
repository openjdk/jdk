/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8178070 8196201 8184205
 * @summary Test packages table in module summary pages
 * @library /tools/lib ../../lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.javadoc/jdk.javadoc.internal.tool
 * @build toolbox.ModuleBuilder toolbox.ToolBox javadoc.tester.*
 * @run main TestModulePackages
 */

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import javadoc.tester.JavadocTester;
import toolbox.ModuleBuilder;
import toolbox.ToolBox;

public class TestModulePackages extends JavadocTester {
    enum TabKind { EXPORTS, OPENS, CONCEALED };
    enum ColKind { EXPORTED_TO, OPENED_TO };

    public static void main(String... args) throws Exception {
        TestModulePackages tester = new TestModulePackages();
        tester.runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    private final ToolBox tb;

    public TestModulePackages() {
        tb = new ToolBox();
    }

    // @Test: See: https://bugs.openjdk.java.net/browse/JDK-8193107
    public void empty(Path base) throws Exception {
        Path src = base.resolve("src");
        new ModuleBuilder(tb, "m")
                .comment("empty module")
                .write(src);

        javadoc("-d", base.resolve("out").toString(),
                "-quiet",
                "-noindex",
                "--module-source-path", src.toString(),
                "--module", "m");

        checkExit(Exit.OK);
        checkOutput("m/module-summary.html", false,
                "<h3>Packages</h3>\n"
                + "<table class=\"packagesSummary\" summary=\"Packages table, "
                + "listing packages, and an explanation\">");
    }

    @Test
    public void exportSingle(Path base) throws Exception {
        Path src = base.resolve("src");
        new ModuleBuilder(tb, "m")
                .comment("exports single package to all")
                .exports("p")
                .classes("package p; public class C { }")
                .write(src);

        javadoc("-d", base.resolve("out").toString(),
                "-quiet",
                "-noindex",
                "--module-source-path", src.toString(),
                "--module", "m");

        checkExit(Exit.OK);
        checkCaption("m", TabKind.EXPORTS);
        checkTableHead("m");
        checkPackageRow("m", "p", "i0", null, null, "&nbsp;");
    }

    @Test
    public void exportMultiple(Path base) throws Exception {
        Path src = base.resolve("src");
        new ModuleBuilder(tb, "m")
                .comment("exports multiple packages to all")
                .exports("p")
                .exports("q")
                .classes("package p; public class C { }")
                .classes("package q; public class D { }")
                .write(src);

        javadoc("-d", base.resolve("out").toString(),
                "-quiet",
                "-noindex",
                "--module-source-path", src.toString(),
                "--module", "m");

        checkExit(Exit.OK);
        checkCaption("m", TabKind.EXPORTS);
        checkTableHead("m");
        checkPackageRow("m", "p", "i0", null, null, "&nbsp;");
        checkPackageRow("m", "q", "i1", null, null, "&nbsp;");
    }

    @Test
    public void exportSomeQualified(Path base) throws Exception {
        Path src = base.resolve("src");
        new ModuleBuilder(tb, "m")
                .comment("exports multiple packages, some qualified")
                .exports("p")
                .exportsTo("q", "other")
                .classes("package p; public class C { }")
                .classes("package q; public class D { }")
                .write(src);

        new ModuleBuilder(tb, "other")
                .comment("dummy module for target of export")
                .write(src);

        javadoc("-d", base.resolve("out-api").toString(),
                "-quiet",
                "-noindex",
                "--module-source-path", src.toString(),
                "--module", "m,other");

        checkExit(Exit.OK);
        checkCaption("m", TabKind.EXPORTS);
        checkTableHead("m");
        checkPackageRow("m", "p", "i0", null, null, "&nbsp;");

        javadoc("-d", base.resolve("out-all").toString(),
                "-quiet",
                "-noindex",
                "--show-module-contents", "all",
                "--module-source-path", src.toString(),
                "--module", "m,other");

        checkExit(Exit.OK);
        checkCaption("m", TabKind.EXPORTS);
        checkTableHead("m", ColKind.EXPORTED_TO);
        checkPackageRow("m", "p", "i0", "All Modules", null, "&nbsp;");
        checkPackageRow("m", "q", "i1",
                "<a href=\"../other/module-summary.html\">other</a>", null, "&nbsp;");
    }

    @Test
    public void exportWithConcealed(Path base) throws Exception {
        Path src = base.resolve("src");
        new ModuleBuilder(tb, "m")
                .comment("exports package, has concealed package")
                .exports("p")
                .classes("package p; public class C { }")
                .classes("package q; public class D { }")
                .write(src);

        javadoc("-d", base.resolve("out-api").toString(),
                "-quiet",
                "-noindex",
                "--module-source-path", src.toString(),
                "--module", "m");

        checkExit(Exit.OK);
        checkCaption("m", TabKind.EXPORTS);
        checkTableHead("m");
        checkPackageRow("m", "p", "i0", null, null, "&nbsp;");

        javadoc("-d", base.resolve("out-all").toString(),
                "-quiet",
                "-noindex",
                "--show-module-contents", "all",
                "--show-packages", "all",
                "--module-source-path", src.toString(),
                "--module", "m");

        checkExit(Exit.OK);
        checkCaption("m", TabKind.EXPORTS, TabKind.CONCEALED);
        checkTableHead("m", ColKind.EXPORTED_TO);
        checkPackageRow("m", "p", "i0", "All Modules", null, "&nbsp;");
        checkPackageRow("m", "q", "i1", "None", null, "&nbsp;");
    }

    @Test
    public void exportOpenWithConcealed(Path base) throws Exception {
        Path src = base.resolve("src");
        new ModuleBuilder(tb, "m")
                .comment("exports and opens qual and unqual, with concealed")
                .exports("e.all")
                .exportsTo("e.other", "other")
                .opens("o.all")
                .opensTo("o.other", "other")
                .exports("eo")
                .opens("eo")
                .classes("package e.all; public class CEAll { }")
                .classes("package e.other; public class CEOther { }")
                .classes("package o.all; public class COAll { }")
                .classes("package o.other; public class COOther { }")
                .classes("package eo; public class CEO { }")
                .classes("package c; public class C { }")
                .write(src);

        new ModuleBuilder(tb, "other")
                .comment("dummy module for target of export and open")
                .write(src);

        javadoc("-d", base.resolve("out-api").toString(),
                "-quiet",
                "-noindex",
                "--module-source-path", src.toString(),
                "--module", "m,other");

        checkExit(Exit.OK);
        checkCaption("m", TabKind.EXPORTS, TabKind.OPENS);
        checkTableHead("m", ColKind.EXPORTED_TO, ColKind.OPENED_TO);
        checkPackageRow("m", "e.all", "i0", "All Modules", "None", "&nbsp;");
        checkPackageRow("m", "eo", "i1", "All Modules", "All Modules", "&nbsp;");

        javadoc("-d", base.resolve("out-all").toString(),
                "-quiet",
                "-noindex",
                "--show-module-contents", "all",
                "--show-packages", "all",
                "--module-source-path", src.toString(),
                "--module", "m,other");

        checkExit(Exit.OK);
        checkCaption("m", TabKind.EXPORTS, TabKind.OPENS, TabKind.CONCEALED);
        checkTableHead("m", ColKind.EXPORTED_TO, ColKind.OPENED_TO);
        checkPackageRow("m", "c", "i0", "None", "None", "&nbsp;");
        checkPackageRow("m", "e.all", "i1", "All Modules", "None", "&nbsp;");
        checkPackageRow("m", "e.other", "i2",
                "<a href=\"../other/module-summary.html\">other</a>", "None", "&nbsp;");
        checkPackageRow("m", "eo", "i3", "All Modules", "All Modules", "&nbsp;");
        checkPackageRow("m", "o.all", "i4", "None", "All Modules", "&nbsp;");
        checkPackageRow("m", "o.other", "i5", "None",
                "<a href=\"../other/module-summary.html\">other</a>", "&nbsp;");
    }

    @Test
    public void openModule(Path base) throws Exception {
        Path src = base.resolve("src");
        new ModuleBuilder(tb, true, "m")
                .comment("open module")
                .classes("/** implicitly open package */ package p;")
                .classes("package p; public class C { } ")
                .classes("/** implicitly open package */ package q;")
                .classes("package q; public class D { }")
                .write(src);

        javadoc("-d", base.resolve("out").toString(),
                "-quiet",
                "-noindex",
                "--show-packages", "all",  // required, to show open packages; see JDK-8193107
                "--module-source-path", src.toString(),
                "--module", "m");

        checkExit(Exit.OK);
        checkCaption("m", TabKind.OPENS);
        checkTableHead("m");
        checkPackageRow("m", "p", "i0", null, null,
                "\n<div class=\"block\">implicitly open package</div>\n");
        checkPackageRow("m", "q", "i1", null, null,
                "\n<div class=\"block\">implicitly open package</div>\n");
    }
    @Test
    public void openSingle(Path base) throws Exception {
        Path src = base.resolve("src");
        new ModuleBuilder(tb, "m")
                .comment("opens single package to all")
                .opens("p")
                .classes("package p; public class C { }")
                .write(src);

        javadoc("-d", base.resolve("out").toString(),
                "-quiet",
                "-noindex",
                "--show-packages", "all",  // required, to show open packages; see JDK-8193107
                "--module-source-path", src.toString(),
                "--module", "m");

        checkExit(Exit.OK);
        checkCaption("m", TabKind.OPENS);
        checkTableHead("m");
        checkPackageRow("m", "p", "i0", null, null, "&nbsp;");
    }

    @Test
    public void openMultiple(Path base) throws Exception {
        Path src = base.resolve("src");
        new ModuleBuilder(tb, "m")
                .comment("opens multiple packages to all")
                .opens("p")
                .opens("q")
                .classes("package p; public class C { }")
                .classes("package q; public class D { }")
                .write(src);

        javadoc("-d", base.resolve("out").toString(),
                "-quiet",
                "-noindex",
                "--show-packages", "all",  // required, to show open packages; see JDK-8193107
                "--module-source-path", src.toString(),
                "--module", "m");

        checkExit(Exit.OK);
        checkCaption("m", TabKind.OPENS);
        checkTableHead("m");
        checkPackageRow("m", "p", "i0", null, null, "&nbsp;");
        checkPackageRow("m", "q", "i1", null, null, "&nbsp;");
    }

    @Test
    public void openSomeQualified(Path base) throws Exception {
        Path src = base.resolve("src");
        new ModuleBuilder(tb, "m")
                .comment("opens multiple packages, some qualified")
                .opens("p")
                .opensTo("q", "other")
                .classes("package p; public class C { }")
                .classes("package q; public class D { }")
                .write(src);

        new ModuleBuilder(tb, "other")
                .comment("dummy module for target of export")
                .write(src);

        javadoc("-d", base.resolve("out-api").toString(),
                "-quiet",
                "-noindex",
                "--show-packages", "all",  // required, to show open packages; see JDK-8193107
                "--module-source-path", src.toString(),
                "--module", "m,other");

        checkExit(Exit.OK);
        checkCaption("m", TabKind.OPENS);
        checkTableHead("m");
        checkPackageRow("m", "p", "i0", null, null, "&nbsp;");

        javadoc("-d", base.resolve("out-all").toString(),
                "-quiet",
                "-noindex",
                "--show-packages", "all",  // required, to show open packages; see JDK-8193107
                "--show-module-contents", "all",
                "--module-source-path", src.toString(),
                "--module", "m,other");

        checkExit(Exit.OK);
        checkCaption("m", TabKind.OPENS);
        checkTableHead("m", ColKind.OPENED_TO);
        checkPackageRow("m", "p", "i0", null, "All Modules", "&nbsp;");
        checkPackageRow("m", "q", "i1", null,
                "<a href=\"../other/module-summary.html\">other</a>", "&nbsp;");
    }

    @Test
    public void openWithConcealed(Path base) throws Exception {
        Path src = base.resolve("src");
        new ModuleBuilder(tb, "m")
                .comment("opens package, has concealed package")
                .opens("p")
                .classes("package p; public class C { }")
                .classes("package q; public class D { }")
                .write(src);

        javadoc("-d", base.resolve("out-api").toString(),
                "-quiet",
                "-noindex",
                "--show-packages", "all",  // required, to show open packages; see JDK-8193107
                "--module-source-path", src.toString(),
                "--module", "m");

        checkExit(Exit.OK);
        checkCaption("m", TabKind.OPENS);
        checkTableHead("m");
        checkPackageRow("m", "p", "i0", null, null, "&nbsp;");

        javadoc("-d", base.resolve("out-all").toString(),
                "-quiet",
                "-noindex",
                "--show-module-contents", "all",
                "--show-packages", "all",
                "--module-source-path", src.toString(),
                "--module", "m");

        checkExit(Exit.OK);
        checkCaption("m", TabKind.OPENS, TabKind.CONCEALED);
        checkTableHead("m", ColKind.OPENED_TO);
        checkPackageRow("m", "p", "i0", null, "All Modules", "&nbsp;");
        checkPackageRow("m", "q", "i1", null, "None", "&nbsp;");
    }


    private void checkCaption(String moduleName, TabKind... kinds) {
        String expect;
        if (kinds.length > 1) {
            Set<TabKind> kindSet = Set.of(kinds);
            StringBuilder sb = new StringBuilder();
            sb.append("<div role=\"tablist\" aria-orientation=\"horizontal\">"
                        + "<button role=\"tab\" aria-selected=\"true\""
                        + " aria-controls=\"packagesSummary_tabpanel\" tabindex=\"0\""
                        + " onkeydown=\"switchTab(event)\""
                        + " id=\"t0\" class=\"activeTableTab\">All Packages</button>");
            if (kindSet.contains(TabKind.EXPORTS)) {
                sb.append("<button role=\"tab\" aria-selected=\"false\""
                        + " aria-controls=\"packagesSummary_tabpanel\" tabindex=\"-1\""
                        + " onkeydown=\"switchTab(event)\" id=\"t1\" class=\"tableTab\""
                        + " onclick=\"show(1);\">Exports</button>");
            }
            if (kindSet.contains(TabKind.OPENS)) {
                sb.append("<button role=\"tab\" aria-selected=\"false\""
                        + " aria-controls=\"packagesSummary_tabpanel\" tabindex=\"-1\""
                        + " onkeydown=\"switchTab(event)\" id=\"t2\" class=\"tableTab\""
                        + " onclick=\"show(2);\">Opens</button>");
            }
            if (kindSet.contains(TabKind.CONCEALED)) {
                sb.append("<button role=\"tab\" aria-selected=\"false\""
                        + " aria-controls=\"packagesSummary_tabpanel\" tabindex=\"-1\" "
                        + "onkeydown=\"switchTab(event)\" id=\"t3\" class=\"tableTab\" "
                        + "onclick=\"show(4);\">Concealed</button>");
            }
            sb.append("</div>");
            expect = sb.toString();
        } else {
            TabKind k = kinds[0];
            String name = k.toString().charAt(0) + k.toString().substring(1).toLowerCase();
            expect = "<caption>"
                        + "<span>" + name + "</span>"
                        + "<span class=\"tabEnd\">&nbsp;</span>"
                        + "</caption>";
        }

        checkOutput(moduleName + "/module-summary.html", true, expect);
    }


    private void checkTableHead(String moduleName, ColKind... kinds) {
        Set<ColKind> kindSet = Set.of(kinds);
        StringBuilder sb = new StringBuilder();
        sb.append("<tr>\n"
            + "<th class=\"colFirst\" scope=\"col\">Package</th>\n");
        if (kindSet.contains(ColKind.EXPORTED_TO)) {
            sb.append("<th class=\"colSecond\" scope=\"col\">Exported To Modules</th>\n");
        }
        if (kindSet.contains(ColKind.OPENED_TO)) {
            sb.append("<th class=\"colSecond\" scope=\"col\">Opened To Modules</th>\n");
        }
        sb.append("<th class=\"colLast\" scope=\"col\">Description</th>\n"
            + "</tr>");

        checkOutput(moduleName + "/module-summary.html", true, sb.toString());
    }

    private void checkPackageRow(String moduleName, String packageName,
            String id, String exportedTo, String openedTo, String desc) {
        StringBuilder sb = new StringBuilder();
        int idNum = Integer.parseInt(id.substring(1));
        String color = (idNum % 2 == 1 ? "rowColor" : "altColor");
        sb.append("<tr class=\"" + color + "\" id=\"" + id + "\">\n"
                + "<th class=\"colFirst\" scope=\"row\">"
                + "<a href=\"" + packageName.replace('.', '/') + "/package-summary.html\">"
                + packageName + "</a></th>\n");
        if (exportedTo != null) {
            sb.append("<td class=\"colSecond\">" + exportedTo + "</td>\n");
        }
        if (openedTo != null) {
            sb.append("<td class=\"colSecond\">" + openedTo + "</td>\n");
        }
        sb.append("<td class=\"colLast\">" + desc + "</td>");

        checkOutput(moduleName + "/module-summary.html", true, sb.toString());
    }

}

