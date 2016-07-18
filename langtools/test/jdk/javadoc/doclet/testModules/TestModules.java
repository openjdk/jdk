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

/*
 * @test
 * @bug 8154119 8154262 8156077 8157987 8154261
 * @summary Test modules support in javadoc.
 * @author bpatel
 * @library ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build JavadocTester
 * @run main TestModules
 */

public class TestModules extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestModules tester = new TestModules();
        tester.runTests();
    }

    @Test
    void test1() {
        javadoc("-d", "out", "-use",
                "-modulesourcepath", testSrc,
                "-addmods", "module1,module2",
                "testpkgmdl1", "testpkgmdl2");
        checkExit(Exit.OK);
        testDescription(true);
        testNoDescription(false);
        testOverviewSummaryModules();
        testModuleLink();
    }

    @Test
    void test2() {
        javadoc("-d", "out-html5", "-html5", "-use",
                "-modulesourcepath", testSrc,
                "-addmods", "module1,module2",
                "testpkgmdl1", "testpkgmdl2");
        checkExit(Exit.OK);
        testHtml5Description(true);
        testHtml5NoDescription(false);
        testHtml5OverviewSummaryModules();
        testModuleLink();
    }

    @Test
    void test3() {
        javadoc("-d", "out-nocomment", "-nocomment", "-use",
                "-modulesourcepath", testSrc,
                "-addmods", "module1,module2",
                "testpkgmdl1", "testpkgmdl2");
        checkExit(Exit.OK);
        testDescription(false);
        testNoDescription(true);
        testModuleLink();
    }

    @Test
    void test4() {
        javadoc("-d", "out-html5-nocomment", "-nocomment", "-html5", "-use",
                "-modulesourcepath", testSrc,
                "-addmods", "module1,module2",
                "testpkgmdl1", "testpkgmdl2");
        checkExit(Exit.OK);
        testHtml5Description(false);
        testHtml5NoDescription(true);
        testModuleLink();
    }

   @Test
    void test5() {
        javadoc("-d", "out-nomodule", "-use",
                "-sourcepath", testSrc,
                "testpkgnomodule", "testpkgnomodule1");
        checkExit(Exit.OK);
        testOverviewSummaryPackages();
    }

   @Test
    void test6() {
        javadoc("-d", "out-mdltags", "-author", "-version",
                "-tag", "regular:a:Regular Tag:",
                "-tag", "moduletag:s:Module Tag:",
                "-modulesourcepath", testSrc,
                "-addmods", "moduletags,module2",
                "testpkgmdltags", "testpkgmdl2");
        checkExit(Exit.OK);
        testModuleTags();
    }

    @Test
    void test7() {
        javadoc("-d", "out-moduleSummary", "-use",
                "-modulesourcepath", testSrc,
                "-addmods", "module1,module2",
                "testpkgmdl1", "testpkgmdl2", "testpkg2mdl2");
        checkExit(Exit.OK);
        testModuleSummary();
        testNegatedModuleSummary();
    }

   @Test
    void test8() {
        javadoc("-d", "out-html5-nomodule", "-html5", "-use",
                "-sourcepath", testSrc,
                "testpkgnomodule", "testpkgnomodule1");
        checkExit(Exit.OK);
        testHtml5OverviewSummaryPackages();
    }

    void testDescription(boolean found) {
        checkOutput("module1-summary.html", found,
                "<!-- ============ MODULE DESCRIPTION =========== -->\n"
                + "<a name=\"module.description\">\n"
                + "<!--   -->\n"
                + "</a>\n"
                + "<div class=\"block\">This is a test description for the module1 module.</div>");
        checkOutput("module2-summary.html", found,
                "<!-- ============ MODULE DESCRIPTION =========== -->\n"
                + "<a name=\"module.description\">\n"
                + "<!--   -->\n"
                + "</a>\n"
                + "<div class=\"block\">This is a test description for the module2 module.</div>");
    }

    void testNoDescription(boolean found) {
        checkOutput("module1-summary.html", found,
                "<div class=\"contentContainer\">\n"
                + "<ul class=\"blockList\">\n"
                + "<li class=\"blockList\">\n"
                + "<ul class=\"blockList\">\n"
                + "<li class=\"blockList\">\n"
                + "<!-- ============ MODULES SUMMARY =========== -->");
        checkOutput("module2-summary.html", found,
                "<div class=\"contentContainer\">\n"
                + "<ul class=\"blockList\">\n"
                + "<li class=\"blockList\">\n"
                + "<ul class=\"blockList\">\n"
                + "<li class=\"blockList\">\n"
                + "<!-- ============ MODULES SUMMARY =========== -->");
    }

    void testHtml5Description(boolean found) {
        checkOutput("module1-summary.html", found,
                "<section role=\"region\">\n"
                + "<!-- ============ MODULE DESCRIPTION =========== -->\n"
                + "<a id=\"module.description\">\n"
                + "<!--   -->\n"
                + "</a>\n"
                + "<div class=\"block\">This is a test description for the module1 module.</div>\n"
                + "</section>");
        checkOutput("module2-summary.html", found,
                "<section role=\"region\">\n"
                + "<!-- ============ MODULE DESCRIPTION =========== -->\n"
                + "<a id=\"module.description\">\n"
                + "<!--   -->\n"
                + "</a>\n"
                + "<div class=\"block\">This is a test description for the module2 module.</div>\n"
                + "</section>");
    }

    void testHtml5NoDescription(boolean found) {
        checkOutput("module1-summary.html", found,
                "<div class=\"contentContainer\">\n"
                + "<ul class=\"blockList\">\n"
                + "<li class=\"blockList\">\n"
                + "<ul class=\"blockList\">\n"
                + "<li class=\"blockList\">\n"
                + "<!-- ============ MODULES SUMMARY =========== -->");
        checkOutput("module2-summary.html", found,
                "<div class=\"contentContainer\">\n"
                + "<ul class=\"blockList\">\n"
                + "<li class=\"blockList\">\n"
                + "<ul class=\"blockList\">\n"
                + "<li class=\"blockList\">\n"
                + "<!-- ============ MODULES SUMMARY =========== -->");
    }

    void testModuleLink() {
        checkOutput("overview-summary.html", true,
                "<li>Module</li>");
        checkOutput("module1-summary.html", true,
                "<li class=\"navBarCell1Rev\">Module</li>");
        checkOutput("module2-summary.html", true,
                "<li class=\"navBarCell1Rev\">Module</li>");
        checkOutput("testpkgmdl1/package-summary.html", true,
                "<li><a href=\"../module1-summary.html\">Module</a></li>");
        checkOutput("testpkgmdl1/TestClassInModule1.html", true,
                "<li><a href=\"../module1-summary.html\">Module</a></li>");
        checkOutput("testpkgmdl1/class-use/TestClassInModule1.html", true,
                "<li><a href=\"../../module1-summary.html\">Module</a></li>");
        checkOutput("testpkgmdl2/package-summary.html", true,
                "<li><a href=\"../module2-summary.html\">Module</a></li>");
        checkOutput("testpkgmdl2/TestClassInModule2.html", true,
                "<li><a href=\"../module2-summary.html\">Module</a></li>");
        checkOutput("testpkgmdl2/class-use/TestClassInModule2.html", true,
                "<li><a href=\"../../module2-summary.html\">Module</a></li>");
    }

    void testNoModuleLink() {
        checkOutput("testpkgnomodule/package-summary.html", true,
                "<ul class=\"navList\" title=\"Navigation\">\n"
                + "<li><a href=\"../testpkgnomodule/package-summary.html\">Package</a></li>");
        checkOutput("testpkgnomodule/TestClassNoModule.html", true,
                "<ul class=\"navList\" title=\"Navigation\">\n"
                + "<li><a href=\"../testpkgnomodule/package-summary.html\">Package</a></li>");
        checkOutput("testpkgnomodule/class-use/TestClassNoModule.html", true,
                "<ul class=\"navList\" title=\"Navigation\">\n"
                + "<li><a href=\"../../testpkgnomodule/package-summary.html\">Package</a></li>");
    }

    void testModuleTags() {
        checkOutput("moduletags-summary.html", true,
                "Type Link: <a href=\"testpkgmdltags/TestClassInModuleTags.html\" title=\"class in "
                + "testpkgmdltags\"><code>TestClassInModuleTags</code></a>.");
        checkOutput("moduletags-summary.html", true,
                "Member Link: <a href=\"testpkgmdltags/TestClassInModuleTags.html#"
                + "testMethod-java.lang.String-\"><code>testMethod(String)</code></a>.");
        checkOutput("moduletags-summary.html", true,
                "Package Link: <a href=\"testpkgmdltags/package-summary.html\"><code>testpkgmdltags</code></a>.");
        checkOutput("moduletags-summary.html", true,
                "<dt><span class=\"simpleTagLabel\">Since:</span></dt>\n"
                + "<dd>JDK 9</dd>");
        checkOutput("moduletags-summary.html", true,
                "<dt><span class=\"seeLabel\">See Also:</span></dt>\n"
                + "<dd>\"Test see tag\", \n"
                + "<a href=\"testpkgmdltags/TestClassInModuleTags.html\" title=\"class in testpkgmdltags\"><code>"
                + "TestClassInModuleTags</code></a></dd>");
        checkOutput("moduletags-summary.html", true,
                "<dt><span class=\"simpleTagLabel\">Regular Tag:</span></dt>\n"
                + "<dd>Just a regular simple tag.</dd>");
        checkOutput("moduletags-summary.html", true,
                "<dt><span class=\"simpleTagLabel\">Module Tag:</span></dt>\n"
                + "<dd>Just a simple module tag.</dd>");
        checkOutput("moduletags-summary.html", true,
                "<dt><span class=\"simpleTagLabel\">Version:</span></dt>\n"
                + "<dd>1.0</dd>");
        checkOutput("moduletags-summary.html", true,
                "<dt><span class=\"simpleTagLabel\">Author:</span></dt>\n"
                + "<dd>Bhavesh Patel</dd>");
        checkOutput("testpkgmdltags/TestClassInModuleTags.html", false,
                "<dt><span class=\"simpleTagLabel\">Module Tag:</span></dt>\n"
                + "<dd>Just a simple module tag.</dd>");
    }

    void testOverviewSummaryModules() {
        checkOutput("overview-summary.html", true,
                "<table class=\"overviewSummary\" summary=\"Module Summary table, listing modules, and an explanation\">\n"
                + "<caption><span>Modules</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Module</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>");
        checkOutput("overview-summary.html", false,
                "<table class=\"overviewSummary\" summary=\"Packages table, listing packages, and an explanation\">\n"
                + "<caption><span>Packages</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Package</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>");
    }

    void testOverviewSummaryPackages() {
        checkOutput("overview-summary.html", false,
                "<table class=\"overviewSummary\" summary=\"Module Summary table, listing modules, and an explanation\">\n"
                + "<caption><span>Modules</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Module</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>");
        checkOutput("overview-summary.html", true,
                "<table class=\"overviewSummary\" summary=\"Packages table, listing packages, and an explanation\">\n"
                + "<caption><span>Packages</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Package</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>");
    }

    void testHtml5OverviewSummaryModules() {
        checkOutput("overview-summary.html", true,
                "<table class=\"overviewSummary\">\n"
                + "<caption><span>Modules</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Module</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>");
        checkOutput("overview-summary.html", false,
                "<table class=\"overviewSummary\">\n"
                + "<caption><span>Packages</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Package</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>");
    }

    void testHtml5OverviewSummaryPackages() {
        checkOutput("overview-summary.html", false,
                "<table class=\"overviewSummary\">\n"
                + "<caption><span>Modules</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Module</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>");
        checkOutput("overview-summary.html", true,
                "<table class=\"overviewSummary\">\n"
                + "<caption><span>Packages</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Package</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>");
    }

    void testModuleSummary() {
        checkOutput("module1-summary.html", true,
                "<ul class=\"subNavList\">\n"
                + "<li>Module:&nbsp;</li>\n"
                + "<li><a href=\"#module.description\">Description</a>&nbsp;|&nbsp;<a "
                + "href=\"#modules.summary\">Modules</a>&nbsp;|&nbsp;<a href=\"#packages.summary\">"
                + "Packages</a>&nbsp;|&nbsp;Services</li>\n"
                + "</ul>");
        checkOutput("module1-summary.html", true,
                "<!-- ============ MODULES SUMMARY =========== -->\n"
                + "<a name=\"modules.summary\">\n"
                + "<!--   -->\n"
                + "</a>");
        checkOutput("module1-summary.html", true,
                "<tr class=\"altColor\">\n"
                + "<td class=\"colFirst\"><a href=\"testpkgmdl1/package-summary.html\">testpkgmdl1</a></td>\n"
                + "<td class=\"colSecond\">All Modules</td>\n"
                + "<td class=\"colLast\">&nbsp;</td>\n"
                + "</tr>");
        checkOutput("module1-summary.html", true,
                "<!-- ============ PACKAGES SUMMARY =========== -->\n"
                + "<a name=\"packages.summary\">\n"
                + "<!--   -->\n"
                + "</a>");
        checkOutput("module1-summary.html", true,
                "<tr class=\"rowColor\">\n"
                + "<td class=\"colFirst\"><a href=\"module2-summary.html\">module2</a></td>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"block\">This is a test description for the module2 module.</div>\n"
                + "</td>\n"
                + "</tr>");
        checkOutput("module2-summary.html", true,
                "<li><a href=\"#module.description\">Description</a>&nbsp;|&nbsp;<a "
                + "href=\"#modules.summary\">Modules</a>&nbsp;|&nbsp;<a href=\"#packages.summary\">"
                + "Packages</a>&nbsp;|&nbsp;<a href=\"#services.summary\">Services</a></li>");
        checkOutput("module2-summary.html", true,
                "<!-- ============ MODULES SUMMARY =========== -->\n"
                + "<a name=\"modules.summary\">\n"
                + "<!--   -->\n"
                + "</a>");
        checkOutput("module2-summary.html", true,
                "<tr class=\"rowColor\">\n"
                + "<td class=\"colFirst\">testpkg2mdl2</td>\n"
                + "<td class=\"colSecond\">module1</td>\n"
                + "<td class=\"colLast\">&nbsp;</td>\n"
                + "</tr>");
        checkOutput("module2-summary.html", true,
                "<!-- ============ PACKAGES SUMMARY =========== -->\n"
                + "<a name=\"packages.summary\">\n"
                + "<!--   -->\n"
                + "</a>");
        checkOutput("module2-summary.html", true,
                "<tr class=\"altColor\">\n"
                + "<td class=\"colFirst\"><a href=\"java.base-summary.html\">java.base</a></td>\n"
                + "<td class=\"colLast\">&nbsp;</td>\n"
                + "</tr>");
        checkOutput("module2-summary.html", true,
                "<!-- ============ SERVICES SUMMARY =========== -->\n"
                + "<a name=\"services.summary\">\n"
                + "<!--   -->\n"
                + "</a>");
        checkOutput("module2-summary.html", true,
                "<tr class=\"altColor\">\n"
                + "<td class=\"colFirst\"><a href=\"testpkgmdl2/TestClassInModule2.html\" "
                + "title=\"class in testpkgmdl2\">TestClassInModule2</a></td>\n"
                + "<td class=\"colLast\">&nbsp;</td>\n"
                + "</tr>");
        checkOutput("module2-summary.html", true,
                "<tr class=\"altColor\">\n"
                + "<td class=\"colFirst\">testpkg2mdl2.TestInterfaceInModule2<br>(<span "
                + "class=\"implementationLabel\">Implementation:</span>&nbsp;<a "
                + "href=\"testpkgmdl2/TestClassInModule2.html\" title=\"class in testpkgmdl2\">"
                + "TestClassInModule2</a>)</td>\n"
                + "<td class=\"colLast\">&nbsp;</td>\n"
                + "</tr");
        checkOutput("module2-summary.html", true,
                "<caption><span>Exported Packages</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Package</th>\n"
                + "<th scope=\"col\">Module</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>");
        checkOutput("module2-summary.html", true,
                "<caption><span>Requires</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Module</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>");
        checkOutput("module2-summary.html", true,
                "<caption><span>Uses</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Type</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>");
        checkOutput("module2-summary.html", true,
                "<caption><span>Provides</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Type</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>");
    }

    void testNegatedModuleSummary() {
        checkOutput("module1-summary.html", false,
                "<!-- ============ SERVICES SUMMARY =========== -->\n"
                + "<a name=\"services.summary\">\n"
                + "<!--   -->\n"
                + "</a>");
    }
}
