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
 * @bug 8178339 8182765 8184205
 * @summary Tests indirect exports and opens in the module summary page
 * @modules jdk.javadoc/jdk.javadoc.internal.api
 *          jdk.javadoc/jdk.javadoc.internal.tool
 *          jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @library ../lib /tools/lib
 * @build toolbox.ToolBox toolbox.ModuleBuilder JavadocTester
 * @run main TestIndirectExportsOpens
 */

import java.nio.file.Path;
import java.nio.file.Paths;

import toolbox.*;

public class TestIndirectExportsOpens extends JavadocTester {

    public final ToolBox tb;
    public static void main(String... args) throws Exception {
        TestIndirectExportsOpens tester = new TestIndirectExportsOpens();
        tester.runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    public TestIndirectExportsOpens() {
        tb = new ToolBox();
    }

    @Test
    public void checkNoIndirects(Path base) throws Exception {

        ModuleBuilder mb0 = new ModuleBuilder(tb, "m")
                .classes("package pm; public class A {}");
        Path p0 = mb0.write(base);

        ModuleBuilder mb1 = new ModuleBuilder(tb, "a")
                .requiresTransitive("m", p0)
                .classes("package pa; public class NoOp {}")
                .exports("pa");
        mb1.write(base);

        javadoc("-d", base.resolve("out-api").toString(),
                "-quiet",
                "--module-source-path", base.toString(),
                "--expand-requires", "transitive",
                "--module", "a");
        checkExit(Exit.OK);
        verifyIndirectExports(false);
        verifyIndirectOpens(false);

        javadoc("-d", base.resolve("out-api-html4").toString(),
                "-html4",
                "-quiet",
                "--module-source-path", base.toString(),
                "--expand-requires", "transitive",
                "--module", "a");
        checkExit(Exit.OK);
        verifyIndirectExports_html4(false);
        verifyIndirectOpens_html4(false);
    }

    @Test
    public void checkExportsOpens(Path base) throws Exception {

        ModuleBuilder mb0 = new ModuleBuilder(tb, "m")
                .classes("package pm; public class A {}")
                .exports("pm")
                .opens("pm");

        Path p0 = mb0.write(base);

        ModuleBuilder mb1 = new ModuleBuilder(tb, "a")
                .requiresTransitive("m", p0)
                .classes("package pa ; public class NoOp {}")
                .exports("pa");
        mb1.write(base);

        javadoc("-d", base.resolve("out-api").toString(),
                "-quiet",
                "--module-source-path", base.toString(),
                "--expand-requires", "transitive",
                "--module", "a");
        checkExit(Exit.OK);
        verifyIndirectExports(true);
        verifyIndirectOpens(true);

        javadoc("-d", base.resolve("out-api-html4").toString(),
                "-html4",
                "-quiet",
                "--module-source-path", base.toString(),
                "--expand-requires", "transitive",
                "--module", "a");
        checkExit(Exit.OK);
        verifyIndirectExports_html4(true);
        verifyIndirectOpens_html4(true);
    }

    @Test
    public void checkExportsToOpensTo(Path base) throws Exception {

        ModuleBuilder mb0 = new ModuleBuilder(tb, "m")
                .classes("package pm; public class A {}")
                .exportsTo("pm", "x")
                .opensTo("pm", "x");

        Path p0 = mb0.write(base);

        ModuleBuilder mb1 = new ModuleBuilder(tb, "a")
                .requiresTransitive("m", p0)
                .classes("package pa ; public class NoOp {}")
                .exports("pa");
        mb1.write(base);

        javadoc("-d", base.resolve("out-api").toString(),
                "-quiet",
                "--module-source-path", base.toString(),
                "--expand-requires", "transitive",
                "--module", "a");

        checkExit(Exit.OK);
        verifyIndirectExports(false);
        verifyIndirectOpens(false);

        javadoc("-d", base.resolve("out-api-html4").toString(),
                "-html4",
                "-quiet",
                "--module-source-path", base.toString(),
                "--expand-requires", "transitive",
                "--module", "a");

        checkExit(Exit.OK);
        verifyIndirectExports_html4(false);
        verifyIndirectOpens_html4(false);
    }

    @Test
    public void checkExportsToOpensToDetailMode(Path base) throws Exception {

        ModuleBuilder mb0 = new ModuleBuilder(tb, "m")
                .classes("package exportsto; public class A {}")
                .classes("package opensto; public class A {}")
                .exportsTo("exportsto", "x")
                .opensTo("opensto", "x");

        Path p0 = mb0.write(base);

        ModuleBuilder mb1 = new ModuleBuilder(tb, "a")
                .requiresTransitive("m", p0)
                .classes("package pa ; public class NoOp {}")
                .exports("pa");
        mb1.write(base);

        javadoc("-d", base.resolve("out-detail").toString(),
                "-quiet",
                "--module-source-path", base.toString(),
                "--expand-requires", "transitive",
                "--show-module-contents", "all",
                "--module", "a");

        checkExit(Exit.OK);

        // In details mode all kinds of packages from java.base,
        // could be listed in the indirects section, so just
        // check for minimal expected strings.
        checkOutput("a/module-summary.html", true,
                "Indirect Exports",
                "<th class=\"colFirst\" scope=\"row\"><a href=\"../m/module-summary.html\">m</a></th>\n"
                + "<td class=\"colLast\"><a href=\"../m/exportsto/package-summary.html\">exportsto</a></td>\n"
                + "</tr>\n");

        checkOutput("a/module-summary.html", true,
                "Indirect Opens",
                "<th class=\"colFirst\" scope=\"row\"><a href=\"../m/module-summary.html\">m</a></th>\n"
                + "<td class=\"colLast\">opensto</td>\n"
                + "</tr>\n");
    }

    void verifyIndirectExports(boolean present) {
        verifyIndirects(present, false);
    }

    void verifyIndirectOpens(boolean present) {
        verifyIndirects(present, true);
    }

    void verifyIndirects(boolean present, boolean opens) {

        String typeString = opens ? "Indirect Opens" : "Indirect Exports";

        // Avoid false positives, just check for primary string absence.
        if (!present) {
            checkOutput("a/module-summary.html", false, typeString);
            return;
        }

        checkOutput("a/module-summary.html", present,
                "<div class=\"packagesSummary\">\n"
                + "<table>\n"
                + "<caption><span>" + typeString + "</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">From</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Packages</th>\n"
                + "</tr>\n"
                + "<tbody>\n"
                + "<tr class=\"altColor\">\n"
                + "<th class=\"colFirst\" scope=\"row\"><a href=\"../m/module-summary.html\">m</a></th>\n"
                + "<td class=\"colLast\"><a href=\"../m/pm/package-summary.html\">pm</a></td>\n"
                + "</tr>\n"
                + "</tbody>\n"
                + "</table>\n"
                + "</div>");
    }

    void verifyIndirectExports_html4(boolean present) {
        verifyIndirects_html4(present, false);
    }

    void verifyIndirectOpens_html4(boolean present) {
        verifyIndirects_html4(present, true);
    }

    void verifyIndirects_html4(boolean present, boolean opens) {

        String typeString = opens ? "Indirect Opens" : "Indirect Exports";

        // Avoid false positives, just check for primary string absence.
        if (!present) {
            checkOutput("a/module-summary.html", false, typeString);
            return;
        }

        checkOutput("a/module-summary.html", present,
                "<div class=\"packagesSummary\">\n"
                + "<table summary=\"" + typeString + " table, listing modules, and packages\">\n"
                + "<caption><span>" + typeString + "</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">From</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Packages</th>\n"
                + "</tr>\n"
                + "<tbody>\n"
                + "<tr class=\"altColor\">\n"
                + "<th class=\"colFirst\" scope=\"row\"><a href=\"../m/module-summary.html\">m</a></th>\n"
                + "<td class=\"colLast\"><a href=\"../m/pm/package-summary.html\">pm</a></td>\n"
                + "</tr>\n"
                + "</tbody>\n"
                + "</table>\n"
                + "</div>");
    }
}
