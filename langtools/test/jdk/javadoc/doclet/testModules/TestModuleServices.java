/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8178067
 * @summary tests the module's services, such as provides and uses
 * @modules jdk.javadoc/jdk.javadoc.internal.api
 *          jdk.javadoc/jdk.javadoc.internal.tool
 *          jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @library ../lib /tools/lib
 * @build toolbox.ToolBox toolbox.ModuleBuilder JavadocTester
 * @run main TestModuleServices
 */

import java.nio.file.Path;
import java.nio.file.Paths;

import toolbox.*;

public class TestModuleServices extends JavadocTester {

    public final ToolBox tb;
    public static void main(String... args) throws Exception {
        TestModuleServices tester = new TestModuleServices();
        tester.runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    public TestModuleServices() {
        tb = new ToolBox();
    }

    @Test
    public void checkUsesNoApiTagModuleModeDefault(Path base) throws Exception {
        ModuleBuilder mb = new ModuleBuilder(tb, "m")
                .comment("module m.\n@provides p1.A abc") // bogus tag
                .uses("p1.A")
                .uses("p1.B")
                .exports("p1")
                .classes("package p1; public class A {}")
                .classes("package p1; public class B {}");
                mb.write(base);

        javadoc("-d", base.toString() + "/out",
                "-quiet",
                "--module-source-path", base.toString(),
                "--module", "m");
        checkExit(Exit.OK);

        checkOutput("m-summary.html", false,
                "<h3>Services</h3>");
    }

    @Test
    public void checkUsesNoApiTagModuleModeAll(Path base) throws Exception {
        ModuleBuilder mb = new ModuleBuilder(tb, "m")
                .uses("p1.A")
                .uses("p1.B")
                .exports("p1")
                .classes("package p1; public class A {}")
                .classes("package p1; public class B {}");
        mb.write(base);

        javadoc("-d", base.toString() + "/out",
                "-quiet",
                "--show-module-contents", "all",
                "--module-source-path", base.toString(),
                "--module", "m");
        checkExit(Exit.OK);

        checkOutput("m-summary.html", true,
                "<h3>Services</h3>");

        checkOutput("m-summary.html", true,
                "<table class=\"usesSummary\" summary=\"Uses table, listing types, and an explanation\">\n" +
                "<caption><span>Uses</span><span class=\"tabEnd\">&nbsp;</span></caption>\n" +
                "<tr>\n" +
                "<th class=\"colFirst\" scope=\"col\">Type</th>\n" +
                "<th class=\"colLast\" scope=\"col\">Description</th>\n" +
                "</tr>\n" +
                "<tbody>\n" +
                "<tr class=\"altColor\">\n" +
                "<th class=\"colFirst\" scope=\"row\"><a href=\"p1/A.html\" title=\"class in p1\">A</a></th>\n" +
                "<td class=\"colLast\">&nbsp;</td>\n" +
                "</tr>\n" +
                "<tr class=\"rowColor\">\n" +
                "<th class=\"colFirst\" scope=\"row\"><a href=\"p1/B.html\" title=\"class in p1\">B</a></th>\n" +
                "<td class=\"colLast\">&nbsp;</td>\n" +
                "</tr>\n" +
                "</tbody>\n" +
                "</table>\n");
    }

    @Test
    public void checkUsesWithApiTagModuleModeDefault(Path base) throws Exception {
        ModuleBuilder mb = new ModuleBuilder(tb, "m")
                .comment("module m.\n@uses p1.A")
                .uses("p1.A")
                .uses("p1.B")
                .exports("p1")
                .classes("package p1; public class A {}")
                .classes("package p1; public class B {}");
        mb.write(base);

        javadoc("-d", base.toString() + "/out",
                "-quiet",
                "--module-source-path", base.toString(),
                "--module", "m");
        checkExit(Exit.OK);

        checkOutput("m-summary.html", true,
                "<h3>Services</h3>");

        checkOutput("m-summary.html", true,
                "<table class=\"usesSummary\" summary=\"Uses table, listing types, and an explanation\">\n" +
                "<caption><span>Uses</span><span class=\"tabEnd\">&nbsp;</span></caption>\n" +
                "<tr>\n" +
                "<th class=\"colFirst\" scope=\"col\">Type</th>\n" +
                "<th class=\"colLast\" scope=\"col\">Description</th>\n" +
                "</tr>\n" +
                "<tbody>\n" +
                "<tr class=\"altColor\">\n" +
                "<th class=\"colFirst\" scope=\"row\"><a href=\"p1/A.html\" title=\"class in p1\">A</a></th>\n" +
                "<td class=\"colLast\">&nbsp;</td>\n" +
                "</tr>\n" +
                "</tbody>\n" +
                "</table>\n");
    }

    @Test
    public void checkProvidesNoApiTagModuleModeDefault(Path base) throws Exception {
        ModuleBuilder mb = new ModuleBuilder(tb, "m")
                .comment("module m.\n@uses p1.A")
                .provides("p1.A", "p1.B")
                .exports("p1")
                .classes("package p1; public interface A {}")
                .classes("package p1; public class B implements A {}")
                .provides("p2.A", "p2.B")
                .exports("p2")
                .classes("package p2; public interface A {}")
                .classes("package p2; public class B implements A {}");
        mb.write(base);

        javadoc("-d", base.toString() + "/out",
                "-quiet",
                "--module-source-path", base.toString(),
                "--module", "m");

        checkExit(Exit.OK);

        checkOutput("m-summary.html", false,
                "<h3>Services</h3>");
    }

    @Test
    public void checkProvidesNoApiTagModuleModeAll(Path base) throws Exception {
        ModuleBuilder mb = new ModuleBuilder(tb, "m")
                .comment("module m.\n@uses p1.A") // bogus uses tag
                .provides("p1.A", "p1.B")
                .exports("p1")
                .classes("package p1; public interface A {}")
                .classes("package p1; public class B implements A {}")
                .provides("p2.A", "p2.B")
                .exports("p2")
                .classes("package p2; public interface A {}")
                .classes("package p2; public class B implements A {}");
        mb.write(base);

        javadoc("-d", base.toString() + "/out",
                "-quiet",
                "--show-module-contents", "all",
                "--module-source-path", base.toString(),
                "--module", "m");

        checkExit(Exit.OK);

        checkOutput("m-summary.html", true,
                "<h3>Services</h3>");

        checkOutput("m-summary.html", true,
                "<table class=\"providesSummary\" summary=\"Provides table, listing types, and an explanation\">\n" +
                "<caption><span>Provides</span><span class=\"tabEnd\">&nbsp;</span></caption>\n" +
                "<tr>\n" +
                "<th class=\"colFirst\" scope=\"col\">Type</th>\n" +
                "<th class=\"colLast\" scope=\"col\">Description</th>\n" +
                "</tr>\n" +
                "<tbody>\n" +
                "<tr class=\"altColor\">\n" +
                "<th class=\"colFirst\" scope=\"row\"><a href=\"p1/A.html\" title=\"interface in p1\">A</a></th>\n" +
                "<td class=\"colLast\">&nbsp;<br>(<span class=\"implementationLabel\">Implementation(s):</span>&nbsp;<a href=\"p1/B.html\" title=\"class in p1\">B</a>)</td>\n" +
                "</tr>\n" +
                "<tr class=\"rowColor\">\n" +
                "<th class=\"colFirst\" scope=\"row\"><a href=\"p2/A.html\" title=\"interface in p2\">A</a></th>\n" +
                "<td class=\"colLast\">&nbsp;<br>(<span class=\"implementationLabel\">Implementation(s):</span>&nbsp;<a href=\"p2/B.html\" title=\"class in p2\">B</a>)</td>\n" +
                "</tr>\n" +
                "</tbody>\n");
    }

    @Test
    public void checkProvidesWithApiTagModuleModeDefault(Path base) throws Exception {
        ModuleBuilder mb = new ModuleBuilder(tb, "m")
                .comment("module m.\n@provides p1.A abc")
                .provides("p1.A", "p1.B")
                .exports("p1")
                .classes("package p1; public interface A {}")
                .classes("package p1; public class B implements A {}")
                .provides("p2.A", "p2.B")
                .exports("p2")
                .classes("package p2; public interface A {}")
                .classes("package p2; public class B implements A {}");
        mb.write(base);

        javadoc("-d", base.toString() + "/out",
                "-quiet",
                "--module-source-path", base.toString(),
                "--module", "m");

        checkExit(Exit.OK);

        checkOutput("m-summary.html", true,
                "<h3>Services</h3>");

        checkOutput("m-summary.html", true,
                "<table class=\"providesSummary\" summary=\"Provides table, listing types, and an explanation\">\n" +
                "<caption><span>Provides</span><span class=\"tabEnd\">&nbsp;</span></caption>\n" +
                "<tr>\n" +
                "<th class=\"colFirst\" scope=\"col\">Type</th>\n" +
                "<th class=\"colLast\" scope=\"col\">Description</th>\n" +
                "</tr>\n" +
                "<tbody>\n" +
                "<tr class=\"altColor\">\n" +
                "<th class=\"colFirst\" scope=\"row\"><a href=\"p1/A.html\" title=\"interface in p1\">A</a></th>\n" +
                "<td class=\"colLast\">abc&nbsp;</td>\n" +
                "</tr>\n" +
                "</tbody>\n" +
                "</table>\n");
    }

    @Test
    public void checkUsesProvidesWithApiTagsModeDefault(Path base) throws Exception {
        ModuleBuilder mb = new ModuleBuilder(tb, "m")
                .comment("module m.\n@provides p1.A abc\n@uses p2.B def")
                .provides("p1.A", "p1.B")
                .exports("p1")
                .classes("package p1; public interface A {}")
                .classes("package p1; public class B implements A {}")
                .provides("p2.A", "p2.B")
                .uses("p2.B")
                .exports("p2")
                .classes("package p2; public interface A {}")
                .classes("package p2; public class B implements A {}");
        mb.write(base);

        javadoc("-d", base.toString() + "/out",
                "-quiet",
                "--module-source-path", base.toString(),
                "--module", "m");

        checkExit(Exit.OK);

        checkOutput("m-summary.html", true,
                "<h3>Services</h3>");

        checkOutput("m-summary.html", true,
                "<table class=\"providesSummary\" summary=\"Provides table, listing types, and an explanation\">\n" +
                "<caption><span>Provides</span><span class=\"tabEnd\">&nbsp;</span></caption>\n" +
                "<tr>\n" +
                "<th class=\"colFirst\" scope=\"col\">Type</th>\n" +
                "<th class=\"colLast\" scope=\"col\">Description</th>\n" +
                "</tr>\n" +
                "<tbody>\n" +
                "<tr class=\"altColor\">\n" +
                "<th class=\"colFirst\" scope=\"row\"><a href=\"p1/A.html\" title=\"interface in p1\">A</a></th>\n" +
                "<td class=\"colLast\">abc&nbsp;</td>\n" +
                "</tr>\n" +
                "</tbody>\n" +
                "</table>",
                "<table class=\"usesSummary\" summary=\"Uses table, listing types, and an explanation\">\n" +
                "<caption><span>Uses</span><span class=\"tabEnd\">&nbsp;</span></caption>\n" +
                "<tr>\n" +
                "<th class=\"colFirst\" scope=\"col\">Type</th>\n" +
                "<th class=\"colLast\" scope=\"col\">Description</th>\n" +
                "</tr>\n" +
                "<tbody>\n" +
                "<tr class=\"altColor\">\n" +
                "<th class=\"colFirst\" scope=\"row\"><a href=\"p2/B.html\" title=\"class in p2\">B</a></th>\n" +
                "<td class=\"colLast\">def&nbsp;</td>\n" +
                "</tr>\n" +
                "</tbody>\n" +
                "</table>\n");
    }

}
