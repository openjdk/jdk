/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8154119 8154262 8156077 8157987 8154261 8154817 8135291 8155995 8162363
 *      8168766 8168688 8162674 8160196 8175799 8174974 8176778 8177562 8175218 8175823 8166306
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
                "-overview", testSrc("overview.html"),
                "--module-source-path", testSrc,
                "--module", "moduleA,moduleB",
                "testpkgmdlA", "testpkgmdlB");
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
                "-overview", testSrc("overview.html"),
                "--module-source-path", testSrc,
                "--module", "moduleA,moduleB",
                "testpkgmdlA", "testpkgmdlB");
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
                "-overview", testSrc("overview.html"),
                "--module-source-path", testSrc,
                "--module", "moduleA,moduleB",
                "testpkgmdlA", "testpkgmdlB");
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
                "-overview", testSrc("overview.html"),
                "--module-source-path", testSrc,
                "--module", "moduleA,moduleB",
                "testpkgmdlA", "testpkgmdlB");
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
                "-overview", testSrc("overview.html"),
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
                "-overview", testSrc("overview.html"),
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
                "--module", "moduletags,moduleB",
                "testpkgmdltags", "testpkgmdlB");
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
                "--module", "moduleA,moduleB",
                "testpkgmdlA", "testpkgmdlB", "moduleB/testpkg2mdlB");
        checkExit(Exit.OK);
        checkModuleSummary();
        checkNegatedModuleSummary();
    }

    /**
     * Test generated module summary page of an aggregating module.
     */
    @Test
    void testAggregatorModuleSummary() {
        javadoc("-d", "out-aggregatorModuleSummary", "-use",
                "--module-source-path", testSrc,
                "--expand-requires", "transitive",
                "--module", "moduleT");
        checkExit(Exit.OK);
        checkAggregatorModuleSummary();
    }

    /**
     * Test generated module pages and pages with link to modules.
     */
    @Test
    void testModuleFilesAndLinks() {
        javadoc("-d", "out-modulelinks",
                "--module-source-path", testSrc,
                "--module", "moduleA,moduleB",
                "testpkgmdlA", "testpkgmdlB");
        checkExit(Exit.OK);
        checkModuleFilesAndLinks(true);
        checkOverviewFrame(true);
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
                "--module", "moduleA,moduleB,moduletags",
                "testpkgmdlA", "testpkgmdlB", "testpkgmdltags");
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
                "--module", "moduleA,moduleB",
                "testpkgmdlA", "testpkgmdlB");
        checkExit(Exit.OK);
        checkModuleAnnotation();
    }

    /**
     * Test module summary pages in "api" mode.
     */
    @Test
    void testApiMode() {
        javadoc("-d", "out-api", "-use", "--show-module-contents=api", "-author", "-version",
                "-tag", "regular:a:Regular Tag:",
                "-tag", "moduletag:s:Module Tag:",
                "--module-source-path", testSrc,
                "--module", "moduleA,moduleB,moduleC,moduletags",
                "testpkgmdlA", "moduleA/concealedpkgmdlA", "testpkgmdlB", "testpkg2mdlB", "testpkgmdlC", "testpkgmdltags");
        checkExit(Exit.OK);
        checkModuleModeCommon();
        checkModuleModeApi(true);
        checkModuleModeAll(false);
        checkModuleFrameFiles(true);
        checkAllModulesLink(true);
    }

    /**
     * Test module summary pages in "all" mode.
     */
    @Test
    void testAllMode() {
        javadoc("-d", "out-all", "-use", "--show-module-contents=all", "-author", "-version",
                "-tag", "regular:a:Regular Tag:",
                "-tag", "moduletag:s:Module Tag:",
                "--module-source-path", testSrc,
                "--module", "moduleA,moduleB,moduleC,moduletags",
                "testpkgmdlA", "moduleA/concealedpkgmdlA", "testpkgmdlB", "testpkg2mdlB", "testpkgmdlC", "testpkgmdltags");
        checkExit(Exit.OK);
        checkModuleModeCommon();
        checkModuleModeApi(false);
        checkModuleModeAll(true);
        checkModuleFrameFiles(true);
        checkAllModulesLink(true);
    }

    /**
     * Test generated module summary page of a module with no exported package.
     */
    @Test
    void testModuleSummaryNoExportedPkgAll() {
        javadoc("-d", "out-ModuleSummaryNoExportedPkgAll", "-use", "--show-module-contents=all",
                "-sourcepath", testSrc + "/moduleNoExport",
                "--module", "moduleNoExport",
                "testpkgmdlNoExport");
        checkExit(Exit.OK);
        checkModuleSummaryNoExported(true);
    }

    /**
     * Test generated module summary page of a module with no exported package.
     */
    @Test
    void testModuleSummaryNoExportedPkgApi() {
        javadoc("-d", "out-ModuleSummaryNoExportedPkgApi", "-use",
                "-sourcepath", testSrc + "/moduleNoExport",
                "--module", "moduleNoExport",
                "testpkgmdlNoExport");
        checkExit(Exit.OK);
        checkModuleSummaryNoExported(false);
    }

    /**
     * Test generated module pages for javadoc run for a single module having a single package.
     */
    @Test
    void testSingleModuleSinglePkg() {
        javadoc("-d", "out-singlemod",
                "--module-source-path", testSrc,
                "--module", "moduleC",
                "testpkgmdlC");
        checkExit(Exit.OK);
        checkModuleFrameFiles(false);
    }

    /**
     * Test generated module pages for javadoc run for a single module having multiple packages.
     */
    @Test
    void testSingleModuleMultiplePkg() {
        javadoc("-d", "out-singlemodmultiplepkg", "--show-module-contents=all",
                "--module-source-path", testSrc,
                "--module", "moduleB",
                "testpkg2mdlB", "testpkgmdlB");
        checkExit(Exit.OK);
        checkAllModulesLink(false);
    }

    void checkDescription(boolean found) {
        checkOutput("moduleA-summary.html", found,
                "<!-- ============ MODULE DESCRIPTION =========== -->\n"
                + "<a name=\"module.description\">\n"
                + "<!--   -->\n"
                + "</a>\n"
                + "<div class=\"block\">This is a test description for the moduleA module. Search "
                + "phrase <a id=\"searchphrase\" class=\"searchTagResult\">search phrase</a>.</div>");
        checkOutput("moduleB-summary.html", found,
                "<!-- ============ MODULE DESCRIPTION =========== -->\n"
                + "<a name=\"module.description\">\n"
                + "<!--   -->\n"
                + "</a>\n"
                + "<div class=\"block\">This is a test description for the moduleB module. Search "
                + "word <a id=\"search_word\" class=\"searchTagResult\">search_word</a> with no description.</div>");
        checkOutput("overview-summary.html", found,
                "</script>\n"
                + "<div class=\"contentContainer\">\n"
                + "<div class=\"block\">The overview summary page header.</div>\n"
                + "</div>\n"
                + "<div class=\"contentContainer\">\n"
                + "<table class=\"overviewSummary\" summary=\"Module Summary table, listing modules, and an explanation\">\n"
                + "<caption><span>Modules</span><span class=\"tabEnd\">&nbsp;</span></caption>");
        checkOutput("overview-summary.html", false,
                "</table>\n"
                + "</div>\n"
                + "<div class=\"contentContainer\">\n"
                + "<div class=\"block\">The overview summary page header.</div>\n"
                + "</div>\n"
                + "<div class=\"contentContainer\">\n"
                + "<table class=\"overviewSummary\" summary=\"Module Summary table, listing modules, and an explanation\">\n"
                + "<caption><span>Modules</span><span class=\"tabEnd\">&nbsp;</span></caption>");
    }

    void checkNoDescription(boolean found) {
        checkOutput("moduleA-summary.html", found,
                "<div class=\"contentContainer\">\n"
                + "<ul class=\"blockList\">\n"
                + "<li class=\"blockList\">\n"
                + "<ul class=\"blockList\">\n"
                + "<li class=\"blockList\">\n"
                + "<!-- ============ PACKAGES SUMMARY =========== -->");
        checkOutput("moduleB-summary.html", found,
                "<div class=\"contentContainer\">\n"
                + "<ul class=\"blockList\">\n"
                + "<li class=\"blockList\">\n"
                + "<ul class=\"blockList\">\n"
                + "<li class=\"blockList\">\n"
                + "<!-- ============ PACKAGES SUMMARY =========== -->");
    }

    void checkHtml5Description(boolean found) {
        checkOutput("moduleA-summary.html", found,
                "<section role=\"region\">\n"
                + "<div class=\"deprecatedContent\"><span class=\"deprecatedLabel\">Deprecated, for removal:"
                + " This API element is subject to removal in a future version.</span>\n"
                + "<div class=\"block\"><span class=\"deprecationComment\">This module is deprecated.</span></div>\n"
                + "</div>\n"
                + "<!-- ============ MODULE DESCRIPTION =========== -->\n"
                + "<a id=\"module.description\">\n"
                + "<!--   -->\n"
                + "</a>\n"
                + "<div class=\"block\">This is a test description for the moduleA module. Search "
                + "phrase <a id=\"searchphrase\" class=\"searchTagResult\">search phrase</a>.</div>");
        checkOutput("moduleB-summary.html", found,
                "<section role=\"region\">\n"
                + "<!-- ============ MODULE DESCRIPTION =========== -->\n"
                + "<a id=\"module.description\">\n"
                + "<!--   -->\n"
                + "</a>\n"
                + "<div class=\"block\">This is a test description for the moduleB module. Search "
                + "word <a id=\"search_word\" class=\"searchTagResult\">search_word</a> with no description.</div>");
        checkOutput("overview-summary.html", found,
                "</nav>\n"
                + "</header>\n"
                + "<main role=\"main\">\n"
                + "<div class=\"contentContainer\">\n"
                + "<div class=\"block\">The overview summary page header.</div>\n"
                + "</div>\n"
                + "<div class=\"contentContainer\">\n"
                + "<table class=\"overviewSummary\">\n"
                + "<caption><span>Modules</span><span class=\"tabEnd\">&nbsp;</span></caption>");
        checkOutput("overview-summary.html", false,
                "</table>\n"
                + "</div>\n"
                + "</main>\n"
                + "<main role=\"main\">\n"
                + "<div class=\"contentContainer\">\n"
                + "<div class=\"block\">The overview summary page header.</div>\n"
                + "</div>\n"
                + "<div class=\"contentContainer\">\n"
                + "<table class=\"overviewSummary\">\n"
                + "<caption><span>Modules</span><span class=\"tabEnd\">&nbsp;</span></caption>");
    }

    void checkHtml5NoDescription(boolean found) {
        checkOutput("moduleA-summary.html", found,
                "<div class=\"contentContainer\">\n"
                + "<ul class=\"blockList\">\n"
                + "<li class=\"blockList\">\n"
                + "<ul class=\"blockList\">\n"
                + "<li class=\"blockList\">\n"
                + "<!-- ============ PACKAGES SUMMARY =========== -->");
        checkOutput("moduleB-summary.html", found,
                "<div class=\"contentContainer\">\n"
                + "<ul class=\"blockList\">\n"
                + "<li class=\"blockList\">\n"
                + "<ul class=\"blockList\">\n"
                + "<li class=\"blockList\">\n"
                + "<!-- ============ PACKAGES SUMMARY =========== -->");
    }

    void checkModuleLink() {
        checkOutput("overview-summary.html", true,
                "<li>Module</li>");
        checkOutput("moduleA-summary.html", true,
                "<li class=\"navBarCell1Rev\">Module</li>");
        checkOutput("moduleB-summary.html", true,
                "<li class=\"navBarCell1Rev\">Module</li>");
        checkOutput("testpkgmdlA/class-use/TestClassInModuleA.html", true,
                "<li><a href=\"../../moduleA-summary.html\">Module</a></li>");
        checkOutput("testpkgmdlB/package-summary.html", true,
                "<li><a href=\"../moduleB-summary.html\">Module</a></li>");
        checkOutput("testpkgmdlB/TestClassInModuleB.html", true,
                "<li><a href=\"../moduleB-summary.html\">Module</a></li>");
        checkOutput("testpkgmdlB/class-use/TestClassInModuleB.html", true,
                "<li><a href=\"../../moduleB-summary.html\">Module</a></li>");
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
                + "testpkgmdltags\"><code>TestClassInModuleTags</code></a>.",
                "Member Link: <a href=\"testpkgmdltags/TestClassInModuleTags.html#"
                + "testMethod-java.lang.String-\"><code>testMethod(String)</code></a>.",
                "Package Link: <a href=\"testpkgmdltags/package-summary.html\"><code>testpkgmdltags</code></a>.",
                "<dt><span class=\"simpleTagLabel\">Since:</span></dt>\n"
                + "<dd>JDK 9</dd>",
                "<dt><span class=\"seeLabel\">See Also:</span></dt>\n"
                + "<dd>\"Test see tag\", \n"
                + "<a href=\"testpkgmdltags/TestClassInModuleTags.html\" title=\"class in testpkgmdltags\"><code>"
                + "TestClassInModuleTags</code></a></dd>",
                "<dt><span class=\"simpleTagLabel\">Regular Tag:</span></dt>\n"
                + "<dd>Just a regular simple tag.</dd>",
                "<dt><span class=\"simpleTagLabel\">Module Tag:</span></dt>\n"
                + "<dd>Just a simple module tag.</dd>",
                "<dt><span class=\"simpleTagLabel\">Version:</span></dt>\n"
                + "<dd>1.0</dd>",
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
                + "</tr>",
                "</table>\n"
                + "</div>\n"
                + "<div class=\"contentContainer\">\n"
                + "<div class=\"block\">The overview summary page header.</div>\n"
                + "</div>\n"
                + "<div class=\"contentContainer\">\n"
                + "<table class=\"overviewSummary\" summary=\"Packages table, listing packages, and an explanation\">\n"
                + "<caption><span>Packages</span><span class=\"tabEnd\">&nbsp;</span></caption>");
        checkOutput("overview-summary.html", true,
                "<table class=\"overviewSummary\" summary=\"Packages table, listing packages, and an explanation\">\n"
                + "<caption><span>Packages</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Package</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>",
                "</script>\n"
                + "<div class=\"contentContainer\">\n"
                + "<div class=\"block\">The overview summary page header.</div>\n"
                + "</div>\n"
                + "<div class=\"contentContainer\">\n"
                + "<table class=\"overviewSummary\" summary=\"Packages table, listing packages, and an explanation\">\n"
                + "<caption><span>Packages</span><span class=\"tabEnd\">&nbsp;</span></caption>");
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
                + "</tr>",
                "</table>\n"
                + "</div>\n"
                + "</main>\n"
                + "<main role=\"main\">\n"
                + "<div class=\"contentContainer\">\n"
                + "<div class=\"block\">The overview summary page header.</div>\n"
                + "</div>\n"
                + "<div class=\"contentContainer\">\n"
                + "<table class=\"overviewSummary\">\n"
                + "<caption><span>Packages</span><span class=\"tabEnd\">&nbsp;</span></caption>");
        checkOutput("overview-summary.html", true,
                "<table class=\"overviewSummary\">\n"
                + "<caption><span>Packages</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Package</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>",
                "</script>\n"
                + "</nav>\n"
                + "</header>\n"
                + "<main role=\"main\">\n"
                + "<div class=\"contentContainer\">\n"
                + "<div class=\"block\">The overview summary page header.</div>\n"
                + "</div>\n"
                + "<div class=\"contentContainer\">\n"
                + "<table class=\"overviewSummary\">\n"
                + "<caption><span>Packages</span><span class=\"tabEnd\">&nbsp;</span></caption>");
    }

    void checkModuleSummary() {
        checkOutput("moduleA-summary.html", true,
                "<ul class=\"subNavList\">\n"
                + "<li>Module:&nbsp;</li>\n"
                + "<li><a href=\"#module.description\">Description</a>&nbsp;|&nbsp;<a "
                + "href=\"#modules.summary\">Modules</a>&nbsp;|&nbsp;<a href=\"#packages.summary\">"
                + "Packages</a>&nbsp;|&nbsp;Services</li>\n"
                + "</ul>",
                "<!-- ============ MODULES SUMMARY =========== -->\n"
                + "<a name=\"modules.summary\">\n"
                + "<!--   -->\n"
                + "</a>",
                "<tr class=\"altColor\" id=\"i0\">\n"
                + "<th class=\"colFirst\" scope=\"row\"><a href=\"testpkgmdlA/package-summary.html\">testpkgmdlA</a></th>\n"
                + "<td class=\"colLast\">&nbsp;</td>\n"
                + "</tr>",
                "<!-- ============ PACKAGES SUMMARY =========== -->\n"
                + "<a name=\"packages.summary\">\n"
                + "<!--   -->\n"
                + "</a>",
                "<tr class=\"altColor\">\n"
                + "<td class=\"colFirst\">transitive</td>\n"
                + "<th class=\"colSecond\" scope=\"row\"><a href=\"moduleB-summary.html\">moduleB</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"block\">This is a test description for the moduleB module.</div>\n"
                + "</td>\n"
                + "</tr>");
        checkOutput("moduleB-summary.html", true,
                "<li><a href=\"#module.description\">Description</a>&nbsp;|&nbsp;Modules&nbsp;|&nbsp;"
                + "<a href=\"#packages.summary\">Packages</a>&nbsp;|&nbsp;<a href=\"#services.summary\">"
                + "Services</a></li>",
                "<!-- ============ PACKAGES SUMMARY =========== -->\n"
                + "<a name=\"packages.summary\">\n"
                + "<!--   -->\n"
                + "</a>",
                "<tr class=\"altColor\" id=\"i0\">\n"
                + "<th class=\"colFirst\" scope=\"row\"><a href=\"testpkgmdlB/package-summary.html\">testpkgmdlB</a></th>\n"
                + "<td class=\"colLast\">&nbsp;</td>\n"
                + "</tr>",
                "<!-- ============ PACKAGES SUMMARY =========== -->\n"
                + "<a name=\"packages.summary\">\n"
                + "<!--   -->\n"
                + "</a>",
                "<!-- ============ SERVICES SUMMARY =========== -->\n"
                + "<a name=\"services.summary\">\n"
                + "<!--   -->\n"
                + "</a>",
                "<tr class=\"altColor\">\n"
                + "<th class=\"colFirst\" scope=\"row\"><a href=\"testpkgmdlB/TestClassInModuleB.html\" title=\"class in testpkgmdlB\">TestClassInModuleB</a></th>\n"
                + "<td class=\"colLast\">With a test description for uses.&nbsp;</td>\n"
                + "</tr>",
                "<caption><span>Opens</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Package</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>",
                "<caption><span>Uses</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Type</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>",
                "<caption><span>Provides</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Type</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>");
    }

    void checkAggregatorModuleSummary() {
        checkOutput("moduleT-summary.html", true,
                "<div class=\"header\">\n"
                + "<h1 title=\"Module\" class=\"title\">Module&nbsp;moduleT</h1>\n"
                + "</div>",
                "<div class=\"block\">This is a test description for the moduleT module. "
                + "Search phrase <a id=\"searchphrase\" class=\"searchTagResult\">search phrase</a>. "
                + "Make sure there are no exported packages.</div>",
                "<tbody>\n"
                + "<tr class=\"altColor\">\n"
                + "<td class=\"colFirst\">transitive</td>\n"
                + "<th class=\"colSecond\" scope=\"row\"><a href=\"moduleA-summary.html\">moduleA</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"block\">This is a test description for the moduleA module.</div>\n"
                + "</td>\n"
                + "</tr>\n"
                + "<tr class=\"rowColor\">\n"
                + "<td class=\"colFirst\">transitive</td>\n"
                + "<th class=\"colSecond\" scope=\"row\"><a href=\"moduleB-summary.html\">moduleB</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"block\">This is a test description for the moduleB module.</div>\n"
                + "</td>\n"
                + "</tr>\n"
                + "</tbody>");
    }

    void checkNegatedModuleSummary() {
        checkOutput("moduleA-summary.html", false,
                "<!-- ============ SERVICES SUMMARY =========== -->\n"
                + "<a name=\"services.summary\">\n"
                + "<!--   -->\n"
                + "</a>");
    }

    void checkModuleClickThroughLinks() {
        checkOutput("module-overview-frame.html", true,
                "<li><a href=\"moduleA-frame.html\" target=\"packageListFrame\" "
                + "onclick=\"updateModuleFrame('moduleA-type-frame.html','moduleA-summary.html');"
                + "\">moduleA</a></li>",
                "<li><a href=\"moduleB-frame.html\" target=\"packageListFrame\" "
                + "onclick=\"updateModuleFrame('moduleB-type-frame.html','moduleB-summary.html');"
                + "\">moduleB</a></li>");
        checkOutput("script.js", true,
                "function updateModuleFrame(pFrame, cFrame)\n"
                + "{\n"
                + "    top.packageFrame.location = pFrame;\n"
                + "    top.classFrame.location = cFrame;\n"
                + "}");
    }

    void checkModuleClickThrough(boolean found) {
        checkFiles(found,
                "moduleA-type-frame.html",
                "moduleB-type-frame.html");
    }

    void checkModuleFilesAndLinks(boolean found) {
        checkFileAndOutput("testpkgmdlA/package-summary.html", found,
                "<li><a href=\"../moduleA-summary.html\">Module</a></li>",
                "<div class=\"subTitle\"><span class=\"moduleLabelInPackage\">Module</span>&nbsp;"
                + "<a href=\"../moduleA-summary.html\">moduleA</a></div>");
        checkFileAndOutput("testpkgmdlA/TestClassInModuleA.html", found,
                "<li><a href=\"../moduleA-summary.html\">Module</a></li>",
                "<div class=\"subTitle\"><span class=\"moduleLabelInType\">Module</span>&nbsp;"
                + "<a href=\"../moduleA-summary.html\">moduleA</a></div>");
        checkFileAndOutput("testpkgmdlB/AnnotationType.html", found,
                "<div class=\"subTitle\"><span class=\"moduleLabelInType\">Module</span>&nbsp;"
                + "<a href=\"../moduleB-summary.html\">moduleB</a></div>",
                "<div class=\"subTitle\"><span class=\"packageLabelInType\">"
                + "Package</span>&nbsp;<a href=\"../testpkgmdlB/package-summary.html\">testpkgmdlB</a></div>");
        checkFiles(found,
                "moduleA-frame.html",
                "moduleA-summary.html",
                "module-overview-frame.html");
    }

    void checkModuleFrameFiles(boolean found) {
        checkFiles(found,
                "moduleC-frame.html",
                "moduleC-type-frame.html",
                "module-overview-frame.html");
        checkFiles(true,
                "moduleC-summary.html",
                "allclasses-frame.html",
                "allclasses-noframe.html");
    }

    void checkAllModulesLink(boolean found) {
        checkOutput("overview-frame.html", found,
                "<li><a href=\"module-overview-frame.html\" target=\"packageListFrame\">All&nbsp;Modules</a></li>");
    }

    void checkModulesInSearch(boolean found) {
        checkOutput("index-all.html", found,
                "<dl>\n"
                + "<dt><a href=\"moduleA-summary.html\">moduleA</a> - module moduleA</dt>\n"
                + "<dd>\n"
                + "<div class=\"block\">This is a test description for the moduleA module.</div>\n"
                + "</dd>\n"
                + "<dt><a href=\"moduleB-summary.html\">moduleB</a> - module moduleB</dt>\n"
                + "<dd>\n"
                + "<div class=\"block\">This is a test description for the moduleB module.</div>\n"
                + "</dd>\n"
                + "</dl>",
                "<dl>\n"
                + "<dt><span class=\"searchTagLink\"><a href=\"moduleA-summary.html#searchphrase\">"
                + "search phrase</a></span> - Search tag in moduleA</dt>\n"
                + "<dd>with description</dd>\n"
                + "<dt><span class=\"searchTagLink\"><a href=\"moduleB-summary.html#search_word\">"
                + "search_word</a></span> - Search tag in moduleB</dt>\n"
                + "<dd>&nbsp;</dd>\n"
                + "</dl>");
    }

    void checkModuleModeCommon() {
        checkOutput("overview-summary.html", true,
                "<th class=\"colFirst\" scope=\"row\"><a href=\"moduleA-summary.html\">moduleA</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"block\">This is a test description for the moduleA module.</div>\n"
                + "</td>",
                "<th class=\"colFirst\" scope=\"row\"><a href=\"moduleB-summary.html\">moduleB</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"block\">This is a test description for the moduleB module.</div>\n"
                + "</td>",
                "<th class=\"colFirst\" scope=\"row\"><a href=\"moduletags-summary.html\">moduletags</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"block\">This is a test description for the moduleA module.<br>\n"
                + " Type Link: <a href=\"testpkgmdltags/TestClassInModuleTags.html\" title=\"class in testpkgmdltags\"><code>TestClassInModuleTags</code></a>.<br>\n"
                + " Member Link: <a href=\"testpkgmdltags/TestClassInModuleTags.html#testMethod-java.lang.String-\"><code>testMethod(String)</code></a>.<br>\n"
                + " Package Link: <a href=\"testpkgmdltags/package-summary.html\"><code>testpkgmdltags</code></a>.<br></div>\n"
                + "</td>");
        checkOutput("moduleA-summary.html", true,
                "<li><a href=\"#module.description\">Description</a>&nbsp;|&nbsp;<a href=\"#modules.summary\">"
                + "Modules</a>&nbsp;|&nbsp;<a href=\"#packages.summary\">Packages</a>&nbsp;|&nbsp;Services</li>",
                "<th class=\"colFirst\" scope=\"row\"><a href=\"moduleB-summary.html\">moduleB</a></th>\n"
                + "<td class=\"colLast\"><a href=\"testpkgmdlB/package-summary.html\">testpkgmdlB</a></td>\n");
        checkOutput("moduleB-summary.html", true,
                "<th class=\"colFirst\" scope=\"row\"><a href=\"testpkgmdlB/TestClassInModuleB.html\" title=\"class in testpkgmdlB\">TestClassInModuleB</a></th>\n"
                + "<td class=\"colLast\">With a test description for uses.&nbsp;</td>");
        checkOutput("moduletags-summary.html", true,
                "<li><a href=\"#module.description\">Description</a>&nbsp;|&nbsp;<a href=\"#modules.summary\">Modules"
                + "</a>&nbsp;|&nbsp;<a href=\"#packages.summary\">Packages</a>&nbsp;|&nbsp;Services</li>",
                "<table class=\"requiresSummary\" summary=\"Indirect Requires table, listing modules, and an explanation\">\n"
                + "<caption><span>Indirect Requires</span><span class=\"tabEnd\">&nbsp;</span></caption>",
                "<td class=\"colFirst\">transitive</td>\n"
                + "<th class=\"colSecond\" scope=\"row\"><a href=\"moduleB-summary.html\">moduleB</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"block\">This is a test description for the moduleB module.</div>\n"
                + "</td>",
                "<table class=\"packagesSummary\" summary=\"Indirect Exports table, listing modules, and packages\">\n"
                + "<caption><span>Indirect Exports</span><span class=\"tabEnd\">&nbsp;</span></caption>",
                "<td class=\"colFirst\">transitive static</td>\n"
                + "<th class=\"colSecond\" scope=\"row\"><a href=\"moduleA-summary.html\">moduleA</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"block\">This is a test description for the moduleA module.</div>\n"
                + "</td>",
                "<table class=\"requiresSummary\" summary=\"Requires table, listing modules, and an explanation\">\n"
                + "<caption><span>Requires</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Modifier</th>\n"
                + "<th class=\"colSecond\" scope=\"col\">Module</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>",
                "<table class=\"requiresSummary\" summary=\"Indirect Requires table, listing modules, and an explanation\">\n"
                + "<caption><span>Indirect Requires</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Modifier</th>\n"
                + "<th class=\"colSecond\" scope=\"col\">Module</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>",
                "<table class=\"packagesSummary\" summary=\"Indirect Opens table, listing modules, and packages\">\n"
                + "<caption><span>Indirect Opens</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">From</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Packages</th>\n"
                + "</tr>\n",
                "<th class=\"colFirst\" scope=\"row\"><a href=\"moduleB-summary.html\">moduleB</a></th>\n"
                + "<td class=\"colLast\"><a href=\"testpkgmdlB/package-summary.html\">testpkgmdlB</a></td>\n");
    }

    void checkModuleModeApi(boolean found) {
        checkOutput("moduleA-summary.html", found,
                "<th class=\"colFirst\" scope=\"row\"><a href=\"testpkgmdlA/package-summary.html\">testpkgmdlA</a></th>\n"
                + "<td class=\"colLast\">&nbsp;</td>");
        checkOutput("moduleB-summary.html", found,
                "<li><a href=\"#module.description\">Description</a>&nbsp;|&nbsp;Modules&nbsp;|&nbsp;"
                + "<a href=\"#packages.summary\">Packages</a>&nbsp;|&nbsp;<a href=\"#services.summary\">Services</a></li>",
                "<th class=\"colFirst\" scope=\"row\"><a href=\"testpkgmdlB/package-summary.html\">testpkgmdlB</a></th>\n"
                + "<td class=\"colLast\">&nbsp;</td>",
                "<table class=\"packagesSummary\" summary=\"Packages table, listing packages, and an explanation\">\n"
                + "<caption><span>Opens</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Package</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>\n"
                + "<tbody>\n"
                + "<tr class=\"altColor\" id=\"i0\">\n"
                + "<th class=\"colFirst\" scope=\"row\"><a href=\"testpkgmdlB/package-summary.html\">testpkgmdlB</a></th>\n"
                + "<td class=\"colLast\">&nbsp;</td>\n"
                + "</tr>\n"
                + "</tbody>\n"
                + "</table>");
        checkOutput("moduletags-summary.html", found,
                "<th class=\"colFirst\" scope=\"row\"><a href=\"testpkgmdltags/package-summary.html\">testpkgmdltags</a></th>\n"
                + "<td class=\"colLast\">&nbsp;</td>");
    }

    void checkModuleModeAll(boolean found) {
        checkOutput("moduleA-summary.html", found,
                "<td class=\"colFirst\"> </td>\n"
                + "<th class=\"colSecond\" scope=\"row\">java.base</th>\n"
                + "<td class=\"colLast\">&nbsp;</td>",
                "<td class=\"colFirst\"> </td>\n"
                + "<th class=\"colSecond\" scope=\"row\"><a href=\"moduleC-summary.html\">moduleC</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"block\">This is a test description for the moduleC module.</div>\n"
                + "</td>",
                "<th class=\"colFirst\" scope=\"row\"><a href=\"moduleC-summary.html\">moduleC</a></th>\n"
                + "<td class=\"colLast\"><a href=\"testpkgmdlC/package-summary.html\">testpkgmdlC</a></td>",
                "<th class=\"colFirst\" scope=\"row\"><a href=\"testpkgmdlA/package-summary.html\">testpkgmdlA</a></th>\n"
                + "<td class=\"colSecond\">All Modules</td>\n"
                + "<td class=\"colLast\">&nbsp;</td>",
                "<caption><span id=\"t0\" class=\"activeTableTab\"><span>All Packages</span><span class=\"tabEnd\">&nbsp;</span></span>"
                + "<span id=\"t1\" class=\"tableTab\"><span><a href=\"javascript:showPkgs(1);\">Exports</a></span>"
                + "<span class=\"tabEnd\">&nbsp;</span></span><span id=\"t3\" class=\"tableTab\"><span><a href=\"javascript:showPkgs(4);\">"
                + "Concealed</a></span><span class=\"tabEnd\">&nbsp;</span></span></caption>",
                "<th class=\"colFirst\" scope=\"row\"><a href=\"concealedpkgmdlA/package-summary.html\">concealedpkgmdlA</a></th>\n"
                + "<td class=\"colSecond\">None</td>\n"
                + "<td class=\"colLast\">&nbsp;</td>");
        checkOutput("moduleB-summary.html", found,
                "<li><a href=\"#module.description\">Description</a>&nbsp;|&nbsp;<a href=\"#modules.summary\">"
                + "Modules</a>&nbsp;|&nbsp;<a href=\"#packages.summary\">Packages</a>&nbsp;|&nbsp;<a href=\"#services.summary\">Services</a></li>",
                "<th class=\"colFirst\" scope=\"row\"><a href=\"testpkgmdlB/package-summary.html\">testpkgmdlB</a></th>\n"
                + "<td class=\"colSecond\">All Modules</td>\n"
                + "<td class=\"colLast\">&nbsp;</td>",
                "<td class=\"colFirst\"> </td>\n"
                + "<th class=\"colSecond\" scope=\"row\">java.base</th>\n"
                + "<td class=\"colLast\">&nbsp;</td>",
                "<th class=\"colFirst\" scope=\"row\"><a href=\"testpkgmdlB/TestClass2InModuleB.html\" title=\"class in testpkgmdlB\">TestClass2InModuleB</a></th>\n"
                + "<td class=\"colLast\">&nbsp;</td>",
                "<th class=\"colFirst\" scope=\"row\"><a href=\"testpkg2mdlB/TestInterface2InModuleB.html\" title=\"interface in testpkg2mdlB\">TestInterface2InModuleB</a></th>\n"
                + "<td class=\"colLast\">&nbsp;<br>(<span class=\"implementationLabel\">Implementation(s):</span>&nbsp;<a href=\"testpkgmdlB/TestClass2InModuleB.html\" "
                + "title=\"class in testpkgmdlB\">TestClass2InModuleB</a>)</td>",
                "<th class=\"colFirst\" scope=\"row\"><a href=\"testpkg2mdlB/TestInterfaceInModuleB.html\" title=\"interface in testpkg2mdlB\">TestInterfaceInModuleB</a></th>\n"
                + "<td class=\"colLast\">&nbsp;<br>(<span class=\"implementationLabel\">Implementation(s):</span>&nbsp;<a href=\"testpkgmdlB/TestClassInModuleB.html\" "
                + "title=\"class in testpkgmdlB\">TestClassInModuleB</a>)</td>",
                "<caption><span id=\"t0\" class=\"activeTableTab\"><span>All Packages</span><span class=\"tabEnd\">&nbsp;</span></span><span id=\"t1\" class=\"tableTab\"><span>"
                + "<a href=\"javascript:showPkgs(1);\">Exports</a></span><span class=\"tabEnd\">&nbsp;</span></span><span id=\"t2\" class=\"tableTab\"><span>"
                + "<a href=\"javascript:showPkgs(2);\">Opens</a></span><span class=\"tabEnd\">&nbsp;</span></span></caption>");
        checkOutput("moduleC-summary.html", found,
                "<caption><span>Exports</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Package</th>\n"
                + "<th class=\"colSecond\" scope=\"col\">Module</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>");
        checkOutput("moduletags-summary.html", found,
                "<th class=\"colFirst\" scope=\"row\"><a href=\"testpkgmdltags/package-summary.html\">testpkgmdltags</a></th>\n"
                + "<td class=\"colSecond\">All Modules</td>\n"
                + "<td class=\"colLast\">&nbsp;</td>");
    }

    void checkModuleDeprecation(boolean found) {
        checkOutput("moduleA-summary.html", found,
                "<div class=\"deprecatedContent\"><span class=\"deprecatedLabel\">Deprecated, for removal:"
                + " This API element is subject to removal in a future version.</span>\n"
                + "<div class=\"block\"><span class=\"deprecationComment\">This module is deprecated.</span></div>\n"
                + "</div>");
        checkOutput("deprecated-list.html", found,
                "<ul>\n"
                + "<li><a href=\"#forRemoval\">Deprecated For Removal</a></li>\n"
                + "<li><a href=\"#module\">Deprecated Modules</a></li>\n"
                + "</ul>",
                "<tr class=\"altColor\">\n"
                + "<th class=\"colFirst\" scope=\"row\"><a href=\"moduleA-summary.html\">moduleA</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"block\"><span class=\"deprecationComment\">This module is deprecated.</span></div>\n"
                + "</td>\n"
                + "</tr>");
        checkOutput("moduleB-summary.html", !found,
                "<div class=\"deprecatedContent\"><span class=\"deprecatedLabel\">Deprecated.</span>\n"
                + "<div class=\"block\"><span class=\"deprecationComment\">This module is deprecated using just the javadoc tag.</span></div>");
        checkOutput("moduletags-summary.html", found,
                "<p>@Deprecated\n"
                + "</p>",
                "<div class=\"deprecatedContent\"><span class=\"deprecatedLabel\">Deprecated.</span></div>");
    }

    void checkModuleAnnotation() {
        checkOutput("moduleB-summary.html", true,
                "<p><a href=\"testpkgmdlB/AnnotationType.html\" title=\"annotation in testpkgmdlB\">@AnnotationType</a>(<a href=\"testpkgmdlB/AnnotationType.html#optional--\">optional</a>=\"Module Annotation\",\n"
                + "                <a href=\"testpkgmdlB/AnnotationType.html#required--\">required</a>=2016)\n"
                + "</p>");
        checkOutput("moduleB-summary.html", false,
                "@AnnotationTypeUndocumented");
    }

    void checkOverviewFrame(boolean found) {
        checkOutput("index.html", !found,
                "<iframe src=\"overview-frame.html\" name=\"packageListFrame\" title=\"All Packages\"></iframe>");
        checkOutput("index.html", found,
                "<iframe src=\"module-overview-frame.html\" name=\"packageListFrame\" title=\"All Modules\"></iframe>");
    }

    void checkModuleSummaryNoExported(boolean found) {
        checkOutput("moduleNoExport-summary.html", found,
                "<!-- ============ PACKAGES SUMMARY =========== -->\n"
                + "<a name=\"packages.summary\">\n"
                + "<!--   -->\n"
                + "</a>",
                "<caption><span>Concealed</span><span class=\"tabEnd\">&nbsp;</span></caption>");
    }
}
