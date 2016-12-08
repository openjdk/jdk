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
 * @bug 8154119 8154262 8156077 8157987 8154261 8154817 8135291 8155995 8162363 8168766 8168688
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

    /**
     * Test generated module pages for HTML 4.
     */
    @Test
    void testHtml4() {
        javadoc("-d", "out", "-use",
                "--module-source-path", testSrc,
                "--add-modules", "module1,module2",
                "testpkgmdl1", "testpkgmdl2");
        checkExit(Exit.OK);
        checkDescription(true);
        checkNoDescription(false);
        checkOverviewSummaryModules();
        checkModuleLink();
        checkModuleClickThroughLinks();
        checkModuleClickThrough(true);
        checkModuleFilesAndLinks(true);
        checkModulesInSearch(true);
        checkOverviewFrame(true);
    }

    /**
     * Test generated module pages for HTML 5.
     */
    @Test
    void testHtml5() {
        javadoc("-d", "out-html5", "-html5", "-use",
                "--module-source-path", testSrc,
                "--add-modules", "module1,module2",
                "testpkgmdl1", "testpkgmdl2");
        checkExit(Exit.OK);
        checkHtml5Description(true);
        checkHtml5NoDescription(false);
        checkHtml5OverviewSummaryModules();
        checkModuleLink();
        checkModuleClickThroughLinks();
        checkModuleClickThrough(true);
        checkModuleFilesAndLinks(true);
        checkModulesInSearch(true);
        checkOverviewFrame(true);
    }

    /**
     * Test generated module pages for HTML 4 with -nocomment option.
     */
    @Test
    void testHtml4NoComment() {
        javadoc("-d", "out-nocomment", "-nocomment", "-use",
                "--module-source-path", testSrc,
                "--add-modules", "module1,module2",
                "testpkgmdl1", "testpkgmdl2");
        checkExit(Exit.OK);
        checkDescription(false);
        checkNoDescription(true);
        checkModuleLink();
        checkModuleFilesAndLinks(true);
        checkOverviewFrame(true);
    }

    /**
     * Test generated module pages for HTML 5 with -nocomment option.
     */
    @Test
    void testHtml5NoComment() {
        javadoc("-d", "out-html5-nocomment", "-nocomment", "-html5", "-use",
                "--module-source-path", testSrc,
                "--add-modules", "module1,module2",
                "testpkgmdl1", "testpkgmdl2");
        checkExit(Exit.OK);
        checkHtml5Description(false);
        checkHtml5NoDescription(true);
        checkModuleLink();
        checkModuleFilesAndLinks(true);
        checkOverviewFrame(true);
    }

    /**
     * Test generated pages, in an unnamed module, for HTML 4.
     */
    @Test
    void testHtml4UnnamedModule() {
        javadoc("-d", "out-nomodule", "-use",
                "-sourcepath", testSrc,
                "testpkgnomodule", "testpkgnomodule1");
        checkExit(Exit.OK);
        checkOverviewSummaryPackages();
        checkModuleClickThrough(false);
        checkModuleFilesAndLinks(false);
        checkModulesInSearch(false);
        checkOverviewFrame(false);
    }

    /**
     * Test generated pages, in an unnamed module, for HTML 5.
     */
    @Test
    void testHtml5UnnamedModule() {
        javadoc("-d", "out-html5-nomodule", "-html5", "-use",
                "-sourcepath", testSrc,
                "testpkgnomodule", "testpkgnomodule1");
        checkExit(Exit.OK);
        checkHtml5OverviewSummaryPackages();
        checkModuleFilesAndLinks(false);
        checkModulesInSearch(false);
        checkOverviewFrame(false);
    }

    /**
     * Test generated module pages with javadoc tags.
     */
    @Test
    void testJDTagsInModules() {
        javadoc("-d", "out-mdltags", "-author", "-version",
                "-tag", "regular:a:Regular Tag:",
                "-tag", "moduletag:s:Module Tag:",
                "--module-source-path", testSrc,
                "--add-modules", "moduletags,module2",
                "testpkgmdltags", "testpkgmdl2");
        checkExit(Exit.OK);
        checkModuleTags();
    }

    /**
     * Test generated module summary page.
     */
    @Test
    void testModuleSummary() {
        javadoc("-d", "out-moduleSummary", "-use",
                "--module-source-path", testSrc,
                "--add-modules", "module1,module2",
                "testpkgmdl1", "testpkgmdl2", "module2/testpkg2mdl2");
        checkExit(Exit.OK);
        checkModuleSummary();
        checkNegatedModuleSummary();
    }

    /**
     * Test generated module pages and pages with link to modules.
     */
    @Test
    void testModuleFilesAndLinks() {
        javadoc("-d", "out-modulelinks",
                "--module-source-path", testSrc,
                "--add-modules", "module1",
                "testpkgmdl1");
        checkExit(Exit.OK);
        checkModuleFilesAndLinks(true);
        checkNegatedOverviewFrame();
    }

    /**
     * Test generated module pages for a deprecated module.
     */
    @Test
    void testModuleDeprecation() {
        javadoc("-d", "out-moduledepr",
                "-tag", "regular:a:Regular Tag:",
                "-tag", "moduletag:s:Module Tag:",
                "--module-source-path", testSrc,
                "--module", "module1,module2,moduletags",
                "testpkgmdl1", "testpkgmdl2", "testpkgmdltags");
        checkExit(Exit.OK);
        checkModuleDeprecation(true);
    }

    /**
     * Test annotations on modules.
     */
    @Test
    void testModuleAnnotation() {
        javadoc("-d", "out-moduleanno",
                "--module-source-path", testSrc,
                "--module", "module1,module2",
                "testpkgmdl1", "testpkgmdl2");
        checkExit(Exit.OK);
        checkModuleAnnotation();
    }

    void checkDescription(boolean found) {
        checkOutput("module1-summary.html", found,
                "<!-- ============ MODULE DESCRIPTION =========== -->\n"
                + "<a name=\"module.description\">\n"
                + "<!--   -->\n"
                + "</a>\n"
                + "<div class=\"block\">This is a test description for the module1 module. Search "
                + "phrase <a id=\"searchphrase\">search phrase</a>.</div>");
        checkOutput("module2-summary.html", found,
                "<!-- ============ MODULE DESCRIPTION =========== -->\n"
                + "<a name=\"module.description\">\n"
                + "<!--   -->\n"
                + "</a>\n"
                + "<div class=\"block\">This is a test description for the module2 module. Search "
                + "word <a id=\"search_word\">search_word</a> with no description.</div>");
    }

    void checkNoDescription(boolean found) {
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

    void checkHtml5Description(boolean found) {
        checkOutput("module1-summary.html", found,
                "<section role=\"region\">\n"
                + "<div class=\"deprecatedContent\"><span class=\"deprecatedLabel\">Deprecated.</span>\n"
                + "<div class=\"block\"><span class=\"deprecationComment\">This module is deprecated.</span></div>\n"
                + "</div>\n"
                + "<!-- ============ MODULE DESCRIPTION =========== -->\n"
                + "<a id=\"module.description\">\n"
                + "<!--   -->\n"
                + "</a>\n"
                + "<div class=\"block\">This is a test description for the module1 module. Search "
                + "phrase <a id=\"searchphrase\">search phrase</a>.</div>");
        checkOutput("module2-summary.html", found,
                "<section role=\"region\">\n"
                + "<!-- ============ MODULE DESCRIPTION =========== -->\n"
                + "<a id=\"module.description\">\n"
                + "<!--   -->\n"
                + "</a>\n"
                + "<div class=\"block\">This is a test description for the module2 module. Search "
                + "word <a id=\"search_word\">search_word</a> with no description.</div>");
    }

    void checkHtml5NoDescription(boolean found) {
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

    void checkModuleLink() {
        checkOutput("overview-summary.html", true,
                "<li>Module</li>");
        checkOutput("module1-summary.html", true,
                "<li class=\"navBarCell1Rev\">Module</li>");
        checkOutput("module2-summary.html", true,
                "<li class=\"navBarCell1Rev\">Module</li>");
        checkOutput("testpkgmdl1/class-use/TestClassInModule1.html", true,
                "<li><a href=\"../../module1-summary.html\">Module</a></li>");
        checkOutput("testpkgmdl2/package-summary.html", true,
                "<li><a href=\"../module2-summary.html\">Module</a></li>");
        checkOutput("testpkgmdl2/TestClassInModule2.html", true,
                "<li><a href=\"../module2-summary.html\">Module</a></li>");
        checkOutput("testpkgmdl2/class-use/TestClassInModule2.html", true,
                "<li><a href=\"../../module2-summary.html\">Module</a></li>");
    }

    void checkNoModuleLink() {
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

    void checkModuleTags() {
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

    void checkOverviewSummaryModules() {
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

    void checkOverviewSummaryPackages() {
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

    void checkHtml5OverviewSummaryModules() {
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

    void checkHtml5OverviewSummaryPackages() {
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

    void checkModuleSummary() {
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
                + "<th class=\"colFirst\" scope=\"row\"><a href=\"testpkgmdl1/package-summary.html\">testpkgmdl1</a></th>\n"
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
                + "<th class=\"colFirst\" scope=\"row\"><a href=\"module2-summary.html\">module2</a></th>\n"
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
                + "<th class=\"colFirst\" scope=\"row\"><a href=\"testpkg2mdl2/package-summary.html\">"
                + "testpkg2mdl2</a></th>\n"
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
                + "<th class=\"colFirst\" scope=\"row\"><a href=\"java.base-summary.html\">java.base</a></th>\n"
                + "<td class=\"colLast\">&nbsp;</td>\n"
                + "</tr>");
        checkOutput("module2-summary.html", true,
                "<!-- ============ SERVICES SUMMARY =========== -->\n"
                + "<a name=\"services.summary\">\n"
                + "<!--   -->\n"
                + "</a>");
        checkOutput("module2-summary.html", true,
                "<tr class=\"altColor\">\n"
                + "<th class=\"colFirst\" scope=\"row\"><a href=\"testpkgmdl2/TestClassInModule2.html\" "
                + "title=\"class in testpkgmdl2\">TestClassInModule2</a></th>\n"
                + "<td class=\"colLast\">&nbsp;</td>\n"
                + "</tr>");
        checkOutput("module2-summary.html", true,
                "<tr class=\"altColor\">\n"
                + "<th class=\"colFirst\" scope=\"row\"><a href=\"testpkg2mdl2/TestInterfaceInModule2.html\" "
                + "title=\"interface in testpkg2mdl2\">TestInterfaceInModule2</a><br>"
                + "(<span class=\"implementationLabel\">Implementation:</span>&nbsp;"
                + "<a href=\"testpkgmdl2/TestClassInModule2.html\" title=\"class in testpkgmdl2\">"
                + "TestClassInModule2</a>)</th>\n"
                + "<td class=\"colLast\">&nbsp;</td>\n"
                + "</tr");
        checkOutput("module2-summary.html", true,
                "<caption><span>Exported Packages</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Package</th>\n"
                + "<th class=\"colSecond\" scope=\"col\">Module</th>\n"
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

    void checkNegatedModuleSummary() {
        checkOutput("module1-summary.html", false,
                "<!-- ============ SERVICES SUMMARY =========== -->\n"
                + "<a name=\"services.summary\">\n"
                + "<!--   -->\n"
                + "</a>");
    }

    void checkModuleClickThroughLinks() {
        checkOutput("module-overview-frame.html", true,
                "<li><a href=\"module1-frame.html\" target=\"packageListFrame\" "
                + "onclick=\"updateModuleFrame('module1-type-frame.html','module1-summary.html');"
                + "\">module1</a></li>");
        checkOutput("module-overview-frame.html", true,
                "<li><a href=\"module2-frame.html\" target=\"packageListFrame\" "
                + "onclick=\"updateModuleFrame('module2-type-frame.html','module2-summary.html');"
                + "\">module2</a></li>");
        checkOutput("script.js", true,
                "function updateModuleFrame(pFrame, cFrame)\n"
                + "{\n"
                + "    top.packageFrame.location = pFrame;\n"
                + "    top.classFrame.location = cFrame;\n"
                + "}");
    }

    void checkModuleClickThrough(boolean found) {
        checkFiles(found,
                "module1-type-frame.html",
                "module2-type-frame.html");
    }

    void checkModuleFilesAndLinks(boolean found) {
        checkOutput("testpkgmdl1/package-summary.html", found,
                "<li><a href=\"../module1-summary.html\">Module</a></li>");
        checkOutput("testpkgmdl1/package-summary.html", found,
                "<div class=\"subTitle\"><span class=\"moduleLabelInClass\">Module</span>&nbsp;"
                + "<a href=\"../module1-summary.html\">module1</a></div>");
        checkOutput("testpkgmdl1/TestClassInModule1.html", found,
                "<li><a href=\"../module1-summary.html\">Module</a></li>");
        checkOutput("testpkgmdl1/TestClassInModule1.html", found,
                "<div class=\"subTitle\"><span class=\"moduleLabelInClass\">Module</span>&nbsp;"
                + "<a href=\"../module1-summary.html\">module1</a></div>");
        checkFiles(found,
                "module1-frame.html",
                "module1-summary.html",
                "module-overview-frame.html");
    }

    void checkModulesInSearch(boolean found) {
        checkOutput("index-all.html", found,
                "<dl>\n"
                + "<dt><a href=\"module1-summary.html\">module1</a> - module module1</dt>\n"
                + "<dd>\n"
                + "<div class=\"block\">This is a test description for the module1 module.</div>\n"
                + "</dd>\n"
                + "<dt><a href=\"module2-summary.html\">module2</a> - module module2</dt>\n"
                + "<dd>\n"
                + "<div class=\"block\">This is a test description for the module2 module.</div>\n"
                + "</dd>\n"
                + "</dl>");
        checkOutput("index-all.html", found,
                "<dl>\n"
                + "<dt><span class=\"searchTagLink\"><a href=\"module1-summary.html#searchphrase\">"
                + "search phrase</a></span> - Search tag in module1</dt>\n"
                + "<dd>with description</dd>\n"
                + "<dt><span class=\"searchTagLink\"><a href=\"module2-summary.html#search_word\">"
                + "search_word</a></span> - Search tag in module2</dt>\n"
                + "<dd>&nbsp;</dd>\n"
                + "</dl>");
}

    void checkModuleDeprecation(boolean found) {
        checkOutput("module1-summary.html", found,
                "<div class=\"deprecatedContent\"><span class=\"deprecatedLabel\">Deprecated.</span>\n"
                + "<div class=\"block\"><span class=\"deprecationComment\">This module is deprecated.</span></div>\n"
                + "</div>");
        checkOutput("deprecated-list.html", found,
                "<ul>\n"
                + "<li><a href=\"#module\">Deprecated Modules</a></li>\n"
                + "</ul>",
                "<tr class=\"altColor\">\n"
                + "<th class=\"colFirst\" scope=\"row\"><a href=\"module1-summary.html\">module1</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"block\"><span class=\"deprecationComment\">This module is deprecated.</span></div>\n"
                + "</td>\n"
                + "</tr>");
        checkOutput("module2-summary.html", !found,
                "<div class=\"deprecatedContent\"><span class=\"deprecatedLabel\">Deprecated.</span>\n"
                + "<div class=\"block\"><span class=\"deprecationComment\">This module is deprecated using just the javadoc tag.</span></div>");
        checkOutput("moduletags-summary.html", found,
                "<p>@Deprecated\n"
                + "</p>",
                "<div class=\"deprecatedContent\"><span class=\"deprecatedLabel\">Deprecated.</span></div>");
    }

    void checkModuleAnnotation() {
        checkOutput("module2-summary.html", true,
                "<p><a href=\"testpkgmdl2/AnnotationType.html\" title=\"annotation in testpkgmdl2\">@AnnotationType</a>(<a href=\"testpkgmdl2/AnnotationType.html#optional--\">optional</a>=\"Module Annotation\",\n"
                + "                <a href=\"testpkgmdl2/AnnotationType.html#required--\">required</a>=2016)\n"
                + "</p>");
        checkOutput("module2-summary.html", false,
                "@AnnotationTypeUndocumented");
}

    void checkOverviewFrame(boolean found) {
        checkOutput("index.html", !found,
                "<iframe src=\"overview-frame.html\" name=\"packageListFrame\" title=\"All Packages\"></iframe>");
        checkOutput("index.html", found,
                "<iframe src=\"module-overview-frame.html\" name=\"packageListFrame\" title=\"All Modules\"></iframe>");
}

    void checkNegatedOverviewFrame() {
        checkOutput("index.html", false,
                "<iframe src=\"overview-frame.html\" name=\"packageListFrame\" title=\"All Packages\"></iframe>");
        checkOutput("index.html", false,
                "<iframe src=\"module-overview-frame.html\" name=\"packageListFrame\" title=\"All Modules\"></iframe>");
    }
}
