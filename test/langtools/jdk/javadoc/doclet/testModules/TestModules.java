/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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
 *      8168766 8168688 8162674 8160196 8175799 8174974 8176778 8177562 8175218
 *      8175823 8166306 8178043 8181622 8183511 8169819 8074407 8183037 8191464
 *      8164407 8192007 8182765 8196200 8196201 8196202 8196202 8205593 8202462
 *      8184205 8219060 8223378 8234746 8239804
 * @summary Test modules support in javadoc.
 * @library ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build javadoc.tester.*
 * @run main TestModules
 */
import javadoc.tester.JavadocTester;

public class TestModules extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestModules tester = new TestModules();
        tester.runTests();
    }

    /**
     * Test generated module pages for HTML 5.
     */
    @Test
    public void testHtml5() {
        javadoc("-d", "out-html5",
                "-use",
                "-Xdoclint:none",
                "-overview", testSrc("overview.html"),
                "--module-source-path", testSrc,
                "--module", "moduleA,moduleB",
                "testpkgmdlA", "testpkgmdlB");
        checkExit(Exit.OK);
        checkHtml5Description(true);
        checkHtml5NoDescription(false);
        checkHtml5OverviewSummaryModules();
        checkModuleLink();
        checkModuleFilesAndLinks(true);
        checkModulesInSearch(true);
        checkAllPkgsAllClasses(true);
    }

    /**
     * Test generated module pages for HTML 5 with -nocomment option.
     */
    @Test
    public void testHtml5NoComment() {
        javadoc("-d", "out-html5-nocomment",
                "-nocomment",
                "-use",
                "-Xdoclint:none",
                "-overview", testSrc("overview.html"),
                "--module-source-path", testSrc,
                "--module", "moduleA,moduleB",
                "testpkgmdlA", "testpkgmdlB");
        checkExit(Exit.OK);
        checkHtml5Description(false);
        checkHtml5NoDescription(true);
        checkModuleLink();
        checkModuleFilesAndLinks(true);
    }

    /**
     * Test generated pages, in an unnamed module, for HTML 5.
     */
    @Test
    public void testHtml5UnnamedModule() {
        javadoc("-d", "out-html5-nomodule",
                "-use",
                "-overview", testSrc("overview.html"),
                "-sourcepath", testSrc,
                "testpkgnomodule", "testpkgnomodule1");
        checkExit(Exit.OK);
        checkHtml5OverviewSummaryPackages();
        checkModuleFilesAndLinks(false);
        checkModulesInSearch(false);
    }

    /**
     * Test generated module pages with javadoc tags.
     */
    @Test
    public void testJDTagsInModules() {
        javadoc("-d", "out-mdltags",
                "-author",
                "-version",
                "-Xdoclint:none",
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
    public void testModuleSummary() {
        javadoc("-d", "out-moduleSummary",
                "-use",
                "-Xdoclint:none",
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
    public void testAggregatorModuleSummary() {
        javadoc("-d", "out-aggregatorModuleSummary",
                "-use",
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
    public void testModuleFilesAndLinks() {
        javadoc("-d", "out-modulelinks",
                "-Xdoclint:none",
                "--module-source-path", testSrc,
                "--module", "moduleA,moduleB",
                "testpkgmdlA", "testpkgmdlB");
        checkExit(Exit.OK);
        checkModuleFilesAndLinks(true);
    }

    /**
     * Test generated module pages for a deprecated module.
     */
    @Test
    public void testModuleDeprecation() {
        javadoc("-d", "out-moduledepr",
                "-Xdoclint:none",
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
    public void testModuleAnnotation() {
        javadoc("-d", "out-moduleanno",
                "-Xdoclint:none",
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
    public void testApiMode() {
        javadoc("-d", "out-api",
                "-use",
                "--show-module-contents=api",
                "-author",
                "-version",
                "-Xdoclint:none",
                "-tag", "regular:a:Regular Tag:",
                "-tag", "moduletag:s:Module Tag:",
                "--module-source-path", testSrc,
                "--module", "moduleA,moduleB,moduleC,moduletags",
                "testpkgmdlA", "moduleA/concealedpkgmdlA", "testpkgmdlB", "testpkg2mdlB", "testpkgmdlC", "testpkgmdltags");
        checkExit(Exit.OK);
        checkModuleModeCommon();
        checkModuleModeApi(true);
        checkModuleModeAll(false);
    }

    /**
     * Test module summary pages in "all" mode.
     */
    @Test
    public void testAllMode() {
        javadoc("-d", "out-all",
                "-use",
                "--show-module-contents=all",
                "-author",
                "-version",
                "-Xdoclint:none",
                "-tag", "regular:a:Regular Tag:",
                "-tag", "moduletag:s:Module Tag:",
                "--module-source-path", testSrc,
                "--module", "moduleA,moduleB,moduleC,moduletags",
                "testpkgmdlA", "moduleA/concealedpkgmdlA", "testpkgmdlB", "testpkg2mdlB", "testpkgmdlC", "testpkgmdltags");
        checkExit(Exit.OK);
        checkModuleModeCommon();
        checkModuleModeApi(false);
        checkModuleModeAll(true);
    }

    /**
     * Test generated module summary page of a module with no exported package.
     */
    @Test
    public void testModuleSummaryNoExportedPkgAll() {
        javadoc("-d", "out-ModuleSummaryNoExportedPkgAll",
                "-use",
                "--show-module-contents=all",
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
    public void testModuleSummaryNoExportedPkgApi() {
        javadoc("-d", "out-ModuleSummaryNoExportedPkgApi",
                "-use",
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
    public void testSingleModuleSinglePkg() {
        javadoc("-d", "out-singlemod",
                "--module-source-path", testSrc,
                "--module", "moduleC",
                "testpkgmdlC");
        checkExit(Exit.OK);
    }

    /**
     * Test generated module pages for javadoc run for a single module having multiple packages.
     */
    @Test
    public void testSingleModuleMultiplePkg() {
        javadoc("-d", "out-singlemodmultiplepkg",
                "--show-module-contents=all",
                "-Xdoclint:none",
                "--module-source-path", testSrc,
                "--module", "moduleB",
                "testpkg2mdlB", "testpkgmdlB");
        checkExit(Exit.OK);
    }

    /**
     * Test -group option for modules. The overview-summary.html page should group the modules accordingly.
     */
    @Test
    public void testGroupOption() {
        javadoc("-d", "out-group",
                "--show-module-contents=all",
                "-Xdoclint:none",
                "-tag", "regular:a:Regular Tag:",
                "-tag", "moduletag:s:Module Tag:",
                "--module-source-path", testSrc,
                "-group", "Module Group A", "moduleA*",
                "-group", "Module Group B & C", "moduleB*:moduleC*",
                "-group", "Java SE Modules", "java*",
                "--module", "moduleA,moduleB,moduleC,moduletags",
                "moduleA/concealedpkgmdlA", "testpkgmdlA", "testpkg2mdlB", "testpkgmdlB", "testpkgmdlC",
                "testpkgmdltags");
        checkExit(Exit.OK);
        checkGroupOption();
    }

    /**
     * Test -group option for modules and the ordering of module groups.
     * The overview-summary.html page should group the modules accordingly and display the group tabs in
     * the order it was provided on the command-line.
     */
    @Test
    public void testGroupOptionOrdering() {
        javadoc("-d", "out-groupOrder",
                "--show-module-contents=all",
                "-Xdoclint:none",
                "-tag", "regular:a:Regular Tag:",
                "-tag", "moduletag:s:Module Tag:",
                "--module-source-path", testSrc,
                "-group", "B Group", "moduleB*",
                "-group", "C Group", "moduleC*",
                "-group", "A Group", "moduleA*",
                "-group", "Java SE Modules", "java*",
                "--module", "moduleA,moduleB,moduleC,moduletags",
                "moduleA/concealedpkgmdlA", "testpkgmdlA", "testpkg2mdlB", "testpkgmdlB", "testpkgmdlC",
                "testpkgmdltags");
        checkExit(Exit.OK);
        checkGroupOptionOrdering();
    }

    /**
     * Test -group option for unnamed modules. The overview-summary.html page should group the packages accordingly.
     */
    @Test
    public void testUnnamedModuleGroupOption() {
        javadoc("-d", "out-groupnomodule",
                "-use",
                "-Xdoclint:none",
                "-overview", testSrc("overview.html"),
                "-sourcepath", testSrc,
                "-group", "Package Group 0", "testpkgnomodule",
                "-group", "Package Group 1", "testpkgnomodule1",
                "testpkgnomodule", "testpkgnomodule1");
        checkExit(Exit.OK);
        checkUnnamedModuleGroupOption();
    }

    /**
     * Test -group option for unnamed modules and the ordering of package groups.
     * The overview-summary.html page should group the packages accordingly and display the group tabs in
     * the order it was provided on the command-line.
     */
    @Test
    public void testGroupOptionPackageOrdering() {
        javadoc("-d", "out-groupPkgOrder",
                "-use",
                "-Xdoclint:none",
                "-overview", testSrc("overview.html"),
                "-sourcepath", testSrc,
                "-group", "Z Group", "testpkgnomodule",
                "-group", "A Group", "testpkgnomodule1",
                "testpkgnomodule", "testpkgnomodule1");
        checkExit(Exit.OK);
        checkGroupOptionPackageOrdering();
    }

    /**
     * Test -group option for a single module.
     */
    @Test
    public void testGroupOptionSingleModule() {
        javadoc("-d", "out-groupsinglemodule",
                "-use",
                "-Xdoclint:none",
                "--module-source-path", testSrc,
                "-group", "Module Group B", "moduleB*",
                "--module", "moduleB",
                "testpkg2mdlB", "testpkgmdlB");
        checkExit(Exit.OK);
        checkGroupOptionSingleModule();
    }

    /**
     * Test -group option for a single module.
     */
    @Test
    public void testModuleName() {
        javadoc("-d", "out-modulename",
                "-use",
                "-Xdoclint:none",
                "--module-source-path", testSrc,
                "--module", "moduleB,test.moduleFullName",
                "testpkg2mdlB", "testpkgmdlB", "testpkgmdlfullname");
        checkExit(Exit.OK);
        checkModuleName(true);
    }

    /**
     * Test -linkoffline option.
     */
    @Test
    public void testLinkOffline() {
        String url = "https://docs.oracle.com/javase/9/docs/api/";
        javadoc("-d", "out-linkoffline",
                "-use",
                "--show-module-contents=all",
                "-Xdoclint:none",
                "--module-source-path", testSrc,
                "--module", "moduleA,moduleB",
                "-linkoffline", url, testSrc + "/jdk",
                "testpkgmdlA", "testpkgmdlB", "testpkg3mdlB");
        checkExit(Exit.OK);
        checkLinkOffline();
    }

    /**
     * Test -linksource option.
     */
    @Test
    public void testLinkSource() {
        javadoc("-d", "out-linksource",
                "-use",
                "-linksource",
                "-Xdoclint:none",
                "--module-source-path", testSrc,
                "--module", "moduleA,moduleB");
        checkExit(Exit.OK);
        checkLinks();
        checkLinkSource(false);
    }

    /**
     * Test -linksource option combined with -private.
     */
    @Test
    public void testLinkSourcePrivate() {
        javadoc("-d", "out-linksource-private",
                "-use",
                "-private",
                "-linksource",
                "-Xdoclint:none",
                "--module-source-path", testSrc,
                "--module", "moduleA,moduleB");
        checkExit(Exit.OK);
        checkLinks();
        checkLinkSource(true);
    }

    void checkDescription(boolean found) {
        checkOutput("moduleA/module-summary.html", found,
                "<!-- ============ MODULE DESCRIPTION =========== -->\n"
                + "<a name=\"module.description\">\n"
                + "<!--   -->\n"
                + "</a>\n"
                + "<div class=\"block\">This is a test description for the moduleA module with a Search "
                + "phrase <span id=\"searchphrase\" class=\"searchTagResult\">search phrase</span>.</div>");
        checkOutput("moduleB/module-summary.html", found,
                "<!-- ============ MODULE DESCRIPTION =========== -->\n"
                + "<a name=\"module.description\">\n"
                + "<!--   -->\n"
                + "</a>\n"
                + "<div class=\"block\">This is a test description for the moduleB module. Search "
                + "word <span id=\"search_word\" class=\"searchTagResult\">search_word</span> with no description.</div>");
        checkOutput("index.html", found,
                "</script>\n"
                + "<div class=\"contentContainer\">\n"
                + "<div class=\"block\">The overview summary page header.</div>\n"
                + "</div>\n"
                + "<div class=\"contentContainer\">\n"
                + "<div class=\"overviewSummary\">\n"
                + "<table summary=\"Module Summary table, listing modules, and an explanation\">\n"
                + "<caption><span>Modules</span><span class=\"tabEnd\">&nbsp;</span></caption>");
        checkOutput("index.html", false,
                "</table>\n"
                + "</div>\n"
                + "<div class=\"contentContainer\">\n"
                + "<div class=\"block\">The overview summary page header.</div>\n"
                + "</div>\n"
                + "<div class=\"contentContainer\">\n"
                + "<div class=\"overviewSummary\">\n"
                + "<table summary=\"Module Summary table, listing modules, and an explanation\">\n"
                + "<caption><span>Modules</span><span class=\"tabEnd\">&nbsp;</span></caption>");
    }

    void checkNoDescription(boolean found) {
        checkOutput("moduleA/module-summary.html", found,
                "<div class=\"contentContainer\">\n"
                + "<ul class=\"blockList\">\n"
                + "<li class=\"blockList\">\n"
                + "<ul class=\"blockList\">\n"
                + "<li class=\"blockList\">\n"
                + "<!-- ============ PACKAGES SUMMARY =========== -->");
        checkOutput("moduleB/module-summary.html", found,
                "<div class=\"contentContainer\">\n"
                + "<ul class=\"blockList\">\n"
                + "<li class=\"blockList\">\n"
                + "<ul class=\"blockList\">\n"
                + "<li class=\"blockList\">\n"
                + "<!-- ============ PACKAGES SUMMARY =========== -->");
    }

    void checkHtml5Description(boolean found) {
        checkOutput("moduleA/module-summary.html", found,
                "<section class=\"moduleDescription\" id=\"module.description\">\n"
                + "<div class=\"deprecationBlock\"><span class=\"deprecatedLabel\">Deprecated, for removal:"
                + " This API element is subject to removal in a future version.</span>\n"
                + "<div class=\"deprecationComment\">This module is deprecated.</div>\n"
                + "</div>\n"
                + "<!-- ============ MODULE DESCRIPTION =========== -->\n"
                + "<div class=\"block\">This is a test description for the moduleA module with a Search "
                + "phrase <span id=\"searchphrase\" class=\"searchTagResult\">search phrase</span>.</div>");
        checkOutput("moduleB/module-summary.html", found,
                "<section class=\"moduleDescription\" id=\"module.description\">\n"
                + "<!-- ============ MODULE DESCRIPTION =========== -->\n"
                + "<div class=\"block\">This is a test description for the moduleB module. Search "
                + "word <span id=\"search_word\" class=\"searchTagResult\">search_word</span> with no description.</div>");
        checkOutput("index.html", found,
                "</nav>\n"
                + "</header>\n"
                + "<div class=\"flexContent\">\n"
                + "<main role=\"main\">\n"
                + "<div class=\"contentContainer\">\n"
                + "<div class=\"block\">The overview summary page header.</div>\n"
                + "</div>\n"
                + "<div class=\"contentContainer\">\n"
                + "<div class=\"overviewSummary\">\n<table>\n"
                + "<caption><span>Modules</span><span class=\"tabEnd\">&nbsp;</span></caption>");
        checkOutput("index.html", false,
                "</table>\n"
                + "</div>\n"
                + "</main>\n"
                + "<main role=\"main\">\n"
                + "<div class=\"contentContainer\">\n"
                + "<div class=\"block\">The overview summary page header.</div>\n"
                + "</div>\n"
                + "<div class=\"contentContainer\">\n"
                + "<div class=\"overviewSummary\">\n<table>\n"
                + "<caption><span>Modules</span><span class=\"tabEnd\">&nbsp;</span></caption>");
    }

    void checkHtml5NoDescription(boolean found) {
        checkOutput("moduleA/module-summary.html", found,
                "<div class=\"contentContainer\">\n"
                + "<section class=\"summary\">\n"
                + "<ul class=\"blockList\">\n"
                + "<li class=\"blockList\">\n"
                + "<section class=\"packagesSummary\" id=\"packages.summary\">\n"
                + "<!-- ============ PACKAGES SUMMARY =========== -->");
        checkOutput("moduleB/module-summary.html", found,
                "<div class=\"contentContainer\">\n"
                + "<section class=\"summary\">\n"
                + "<ul class=\"blockList\">\n"
                + "<li class=\"blockList\">\n"
                + "<section class=\"packagesSummary\" id=\"packages.summary\">\n"
                + "<!-- ============ PACKAGES SUMMARY =========== -->");
    }

    void checkModuleLink() {
        checkOutput("index.html", true,
                "<li>Module</li>");
        checkOutput("moduleA/module-summary.html", true,
                "<li class=\"navBarCell1Rev\">Module</li>");
        checkOutput("moduleB/module-summary.html", true,
                "<li class=\"navBarCell1Rev\">Module</li>");
        checkOutput("moduleA/testpkgmdlA/class-use/TestClassInModuleA.html", true,
                "<li><a href=\"../../module-summary.html\">Module</a></li>");
        checkOutput("moduleB/testpkgmdlB/package-summary.html", true,
                "<li><a href=\"../module-summary.html\">Module</a></li>");
        checkOutput("moduleB/testpkgmdlB/TestClassInModuleB.html", true,
                "<li><a href=\"../module-summary.html\">Module</a></li>");
        checkOutput("moduleB/testpkgmdlB/class-use/TestClassInModuleB.html", true,
                "<li><a href=\"../../module-summary.html\">Module</a></li>");
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
        checkOutput("moduletags/module-summary.html", true,
                "Type Link: <a href=\"testpkgmdltags/TestClassInModuleTags.html\" title=\"class in "
                + "testpkgmdltags\"><code>TestClassInModuleTags</code></a>.",
                "Member Link: <a href=\"testpkgmdltags/TestClassInModuleTags.html#"
                + "testMethod(java.lang.String)\"><code>testMethod(String)</code></a>.",
                "Package Link: <a href=\"testpkgmdltags/package-summary.html\"><code>testpkgmdltags</code></a>.",
                "<dt>Since:</dt>\n"
                + "<dd>JDK 9</dd>",
                "<dt>See Also:</dt>\n"
                + "<dd>\"Test see tag\", \n"
                + "<a href=\"testpkgmdltags/TestClassInModuleTags.html\" title=\"class in testpkgmdltags\"><code>"
                + "TestClassInModuleTags</code></a></dd>",
                "<dt>Regular Tag:</dt>\n"
                + "<dd>Just a regular simple tag.</dd>",
                "<dt>Module Tag:</dt>\n"
                + "<dd>Just a simple module tag.</dd>",
                "<dt>Version:</dt>\n"
                + "<dd>1.0</dd>",
                "<dt>Author:</dt>\n"
                + "<dd>Alice</dd>");
        checkOutput("moduletags/testpkgmdltags/TestClassInModuleTags.html", false,
                "<dt>Module Tag:</dt>\n"
                + "<dd>Just a simple module tag.</dd>");
    }

    void checkOverviewSummaryModules() {
        checkOutput("index.html", true,
                "<div class=\"overviewSummary\">\n"
                + "<table summary=\"Module Summary table, listing modules, and an explanation\">\n"
                + "<caption><span>Modules</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<thead>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Module</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>\n"
                + "</thead>");
        checkOutput("overview-summary.html", false,
                "<div class=\"overviewSummary\">\n"
                + "<table summary=\"Package Summary table, listing packages, and an explanation\">\n"
                + "<caption><span>Packages</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<thead>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Package</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>\n"
                + "</thead>");
    }

    void checkOverviewSummaryPackages() {
        checkOutput("index.html", false,
                "<div class=\"overviewSummary\">\n"
                + "<table summary=\"Module Summary table, listing modules, and an explanation\">\n"
                + "<caption><span>Modules</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<thead>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Module</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>\n"
                + "</thead>",
                "</table>\n"
                + "</div>\n"
                + "<div class=\"contentContainer\">\n"
                + "<div class=\"block\">The overview summary page header.</div>\n"
                + "</div>\n"
                + "<div class=\"contentContainer\">\n"
                + "<div class=\"overviewSummary\">\n"
                + "<table summary=\"Package Summary table, listing packages, and an explanation\">\n"
                + "<caption><span>Packages</span><span class=\"tabEnd\">&nbsp;</span></caption>");
        checkOutput("index.html", true,
                "<div class=\"overviewSummary\">\n"
                + "<table summary=\"Package Summary table, listing packages, and an explanation\">\n"
                + "<caption><span>Packages</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<thead>n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Package</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>\n"
                + "</thead>\n",
                "</script>\n"
                + "<div class=\"contentContainer\">\n"
                + "<div class=\"block\">The overview summary page header.</div>\n"
                + "</div>\n"
                + "<div class=\"contentContainer\">\n"
                + "<div class=\"overviewSummary\">\n"
                + "<table summary=\"Package Summary table, listing packages, and an explanation\">\n"
                + "<caption><span>Packages</span><span class=\"tabEnd\">&nbsp;</span></caption>");
    }

    void checkHtml5OverviewSummaryModules() {
        checkOutput("index.html", true,
                "<div class=\"overviewSummary\">\n"
                + "<table>\n"
                + "<caption><span>Modules</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<thead>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Module</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>\n"
                + "</thead>");
        checkOutput("overview-summary.html", false,
                "<div class=\"overviewSummary\">\n"
                + "<table>\n"
                + "<caption><span>Packages</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<thead>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Package</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>\n"
                + "</thead>");
    }

    void checkHtml5OverviewSummaryPackages() {
        checkOutput("index.html", false,
                "<div class=\"overviewSummary\">\n"
                + "<table>\n"
                + "<caption><span>Modules</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<thead>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Module</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>\n"
                + "</thead>",
                "</table>\n"
                + "</div>\n"
                + "</main>\n"
                + "<main role=\"main\">\n"
                + "<div class=\"contentContainer\">\n"
                + "<div class=\"block\">The overview summary page header.</div>\n"
                + "</div>\n"
                + "<div class=\"contentContainer\"><a id=\"Packages\">\n"
                + "<!--   -->\n"
                + "</a>\n"
                + "<div class=\"overviewSummary\">\n"
                + "<table>\n"
                + "<caption><span>Packages</span><span class=\"tabEnd\">&nbsp;</span></caption>");
        checkOutput("index.html", true,
                "<div class=\"overviewSummary\">\n<table>\n"
                + "<caption><span>Packages</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<thead>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Package</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>\n"
                + "</thead>",
                "</nav>\n"
                + "</header>\n"
                + "<div class=\"flexContent\">\n"
                + "<main role=\"main\">\n"
                + "<div class=\"contentContainer\">\n"
                + "<div class=\"block\">The overview summary page header.</div>\n"
                + "</div>\n"
                + "<div class=\"contentContainer\">\n"
                + "<div class=\"overviewSummary\">\n<table>\n"
                + "<caption><span>Packages</span><span class=\"tabEnd\">&nbsp;</span></caption>");
    }

    void checkModuleSummary() {
        checkOutput("moduleA/module-summary.html", true,
                "<ul class=\"subNavList\">\n"
                + "<li>Module:&nbsp;</li>\n"
                + "<li><a href=\"#module.description\">Description</a>&nbsp;|&nbsp;</li>\n"
                + "<li><a href=\"#modules.summary\">Modules</a>&nbsp;|&nbsp;</li>\n"
                + "<li><a href=\"#packages.summary\">Packages</a>&nbsp;|&nbsp;</li>\n"
                + "<li>Services</li>\n"
                + "</ul>",
                "<section class=\"modulesSummary\" id=\"modules.summary\">\n"
                + "<!-- ============ MODULES SUMMARY =========== -->\n"
                + "<h2>Modules</h2>",
                "<tr class=\"altColor\" id=\"i0\">\n"
                + "<th class=\"colFirst\" scope=\"row\"><a href=\"testpkgmdlA/package-summary.html\">testpkgmdlA</a></th>\n"
                + "<td class=\"colLast\">&nbsp;</td>\n"
                + "</tr>",
                "<section class=\"packagesSummary\" id=\"packages.summary\">\n"
                + "<!-- ============ PACKAGES SUMMARY =========== -->\n"
                + "<h2>Packages</h2>",
                "<tr class=\"altColor\">\n"
                + "<td class=\"colFirst\">transitive</td>\n"
                + "<th class=\"colSecond\" scope=\"row\"><a href=\"../moduleB/module-summary.html\">moduleB</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"block\">This is a test description for the moduleB module.</div>\n"
                + "</td>\n"
                + "</tr>");
        checkOutput("moduleB/module-summary.html", true,
                "<li><a href=\"#module.description\">Description</a>&nbsp;|&nbsp;</li>\n"
                + "<li>Modules&nbsp;|&nbsp;</li>\n"
                + "<li><a href=\"#packages.summary\">Packages</a>&nbsp;|&nbsp;</li>\n"
                + "<li><a href=\"#services.summary\">Services</a></li>",
                "<!-- ============ PACKAGES SUMMARY =========== -->\n"
                + "<h2>Packages</h2>",
                "<tr class=\"altColor\" id=\"i0\">\n"
                + "<th class=\"colFirst\" scope=\"row\"><a href=\"testpkgmdlB/package-summary.html\">testpkgmdlB</a></th>\n"
                + "<td class=\"colLast\">&nbsp;</td>\n"
                + "</tr>",
                "<!-- ============ PACKAGES SUMMARY =========== -->\n"
                + "<h2>Packages</h2>",
                "<!-- ============ SERVICES SUMMARY =========== -->\n"
                + "<h2>Services</h2>",
                "<tr class=\"altColor\">\n"
                + "<th class=\"colFirst\" scope=\"row\"><a href=\"testpkgmdlB/TestClassInModuleB.html\" title=\"class in testpkgmdlB\">TestClassInModuleB</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"block\">With a test description for uses.</div>\n</td>\n"
                + "</tr>",
                "<caption><span>Opens</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<thead>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Package</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>\n"
                + "</thead>",
                "<caption><span>Uses</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<thead>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Type</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>\n"
                + "</thead>",
                "<caption><span>Provides</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<thead>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Type</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>\n"
                + "</thead>");
    }

    void checkAggregatorModuleSummary() {
        checkOutput("moduleT/module-summary.html", true,
                "<div class=\"header\">\n"
                + "<h1 title=\"Module\" class=\"title\">Module&nbsp;moduleT</h1>\n"
                + "</div>",
                "<div class=\"block\">This is a test description for the moduleT module. "
                + "Search phrase <span id=\"searchphrase\" class=\"searchTagResult\">search phrase</span>. "
                + "Make sure there are no exported packages.</div>",
                "<tbody>\n"
                + "<tr class=\"altColor\">\n"
                + "<td class=\"colFirst\">transitive</td>\n"
                + "<th class=\"colSecond\" scope=\"row\"><a href=\"../moduleA/module-summary.html\">moduleA</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"block\">This is a test description for the moduleA module with a Search "
                + "phrase search phrase.</div>\n"
                + "</td>\n"
                + "</tr>\n"
                + "<tr class=\"rowColor\">\n"
                + "<td class=\"colFirst\">transitive</td>\n"
                + "<th class=\"colSecond\" scope=\"row\"><a href=\"../moduleB/module-summary.html\">moduleB</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"block\">This is a test description for the moduleB module.</div>\n"
                + "</td>\n"
                + "</tr>\n"
                + "</tbody>");
    }

    void checkNegatedModuleSummary() {
        checkOutput("moduleA/module-summary.html", false,
                "<!-- ============ SERVICES SUMMARY =========== -->\n"
                + "<h2>Services</h2>");
    }

    void checkModuleFilesAndLinks(boolean found) {
        checkFileAndOutput("moduleA/testpkgmdlA/package-summary.html", found,
                "<li><a href=\"../module-summary.html\">Module</a></li>",
                "<div class=\"subTitle\"><span class=\"moduleLabelInPackage\">Module</span>&nbsp;"
                + "<a href=\"../module-summary.html\">moduleA</a></div>");
        checkFileAndOutput("moduleA/testpkgmdlA/TestClassInModuleA.html", found,
                "<li><a href=\"../module-summary.html\">Module</a></li>",
                "<div class=\"subTitle\"><span class=\"moduleLabelInType\">Module</span>&nbsp;"
                + "<a href=\"../module-summary.html\">moduleA</a></div>");
        checkFileAndOutput("moduleB/testpkgmdlB/AnnotationType.html", found,
                "<div class=\"subTitle\"><span class=\"moduleLabelInType\">Module</span>&nbsp;"
                + "<a href=\"../module-summary.html\">moduleB</a></div>",
                "<div class=\"subTitle\"><span class=\"packageLabelInType\">"
                + "Package</span>&nbsp;<a href=\"package-summary.html\">testpkgmdlB</a></div>");
        checkFiles(found,
                "moduleA/module-summary.html");
    }

    void checkModulesInSearch(boolean found) {
        checkOutput("index-all.html", found,
                "<dl class=\"index\">\n"
                + "<dt><a href=\"moduleA/module-summary.html\">moduleA</a> - module moduleA</dt>\n"
                + "<dd>\n"
                + "<div class=\"block\">This is a test description for the moduleA module with a Search "
                + "phrase search phrase.</div>\n"
                + "</dd>\n"
                + "<dt><a href=\"moduleB/module-summary.html\">moduleB</a> - module moduleB</dt>\n"
                + "<dd>\n"
                + "<div class=\"block\">This is a test description for the moduleB module.</div>\n"
                + "</dd>\n"
                + "</dl>",
                "<dl class=\"index\">\n"
                + "<dt><span class=\"searchTagLink\"><a href=\"moduleB/module-summary.html#search_word\">"
                + "search_word</a></span> - Search tag in module moduleB</dt>\n"
                + "<dd>&nbsp;</dd>\n"
                + "<dt><span class=\"searchTagLink\"><a href=\"moduleA/module-summary.html#searchphrase\">"
                + "search phrase</a></span> - Search tag in module moduleA</dt>\n"
                + "<dd>with description</dd>\n"
                + "</dl>");
        checkOutput("index-all.html", false,
                "<dt><span class=\"searchTagLink\"><a href=\"moduleA/module-summary.html#searchphrase\">"
                + "search phrase</a></span> - Search tag in module moduleA</dt>\n"
                + "<dd>with description</dd>\n"
                + "<dt><span class=\"searchTagLink\"><a href=\"moduleA/module-summary.html#searchphrase\">"
                + "search phrase</a></span> - Search tag in module moduleA</dt>\n"
                + "<dd>with description</dd>");
    }

    void checkModuleModeCommon() {
        checkOutput("index.html", true,
                "<th class=\"colFirst\" scope=\"row\"><a href=\"moduleA/module-summary.html\">moduleA</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"block\">This is a test description for the moduleA module with a Search "
                + "phrase search phrase.</div>\n"
                + "</td>",
                "<th class=\"colFirst\" scope=\"row\"><a href=\"moduleB/module-summary.html\">moduleB</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"block\">This is a test description for the moduleB module.</div>\n"
                + "</td>",
                "<th class=\"colFirst\" scope=\"row\"><a href=\"moduletags/module-summary.html\">moduletags</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"block\">This is a test description for the moduletags module.<br>\n"
                + " Type Link: <a href=\"moduletags/testpkgmdltags/TestClassInModuleTags.html\" title=\"class in testpkgmdltags\"><code>TestClassInModuleTags</code></a>.<br>\n"
                + " Member Link: <a href=\"moduletags/testpkgmdltags/TestClassInModuleTags.html#testMethod(java.lang.String)\"><code>testMethod(String)</code></a>.<br>\n"
                + " Package Link: <a href=\"moduletags/testpkgmdltags/package-summary.html\"><code>testpkgmdltags</code></a>.<br></div>\n"
                + "</td>");
        checkOutput("moduleA/module-summary.html", true,
                "<li><a href=\"#module.description\">Description</a>&nbsp;|&nbsp;</li>\n"
                + "<li><a href=\"#modules.summary\">Modules</a>&nbsp;|&nbsp;</li>\n"
                + "<li><a href=\"#packages.summary\">Packages</a>&nbsp;|&nbsp;</li>\n"
                + "<li>Services</li>",
                "<th class=\"colFirst\" scope=\"row\"><a href=\"../moduleB/module-summary.html\">moduleB</a></th>\n"
                + "<td class=\"colLast\"><a href=\"../moduleB/testpkgmdlB/package-summary.html\">testpkgmdlB</a></td>\n");
        checkOutput("moduleB/module-summary.html", true,
                "<th class=\"colFirst\" scope=\"row\"><a href=\"testpkgmdlB/TestClassInModuleB.html\" title=\"class in testpkgmdlB\">TestClassInModuleB</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"block\">With a test description for uses.</div>\n</td>\n");
        checkOutput("moduletags/module-summary.html", true,
                "<li><a href=\"#module.description\">Description</a>&nbsp;|&nbsp;</li>\n"
                + "<li><a href=\"#modules.summary\">Modules</a>&nbsp;|&nbsp;</li>\n"
                + "<li><a href=\"#packages.summary\">Packages</a>&nbsp;|&nbsp;</li>\n"
                + "<li>Services</li>",
                "<div class=\"requiresSummary\">\n<table>\n"
                + "<caption><span>Indirect Requires</span><span class=\"tabEnd\">&nbsp;</span></caption>",
                "<td class=\"colFirst\">transitive</td>\n"
                + "<th class=\"colSecond\" scope=\"row\"><a href=\"../moduleB/module-summary.html\">moduleB</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"block\">This is a test description for the moduleB module.</div>\n"
                + "</td>",
                "<div class=\"packagesSummary\">\n<table>\n"
                + "<caption><span>Indirect Exports</span><span class=\"tabEnd\">&nbsp;</span></caption>",
                "<td class=\"colFirst\">transitive static</td>\n"
                + "<th class=\"colSecond\" scope=\"row\"><a href=\"../moduleA/module-summary.html\">moduleA</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"block\">This is a test description for the moduleA module with a Search "
                + "phrase search phrase.</div>\n"
                + "</td>",
                "<div class=\"requiresSummary\">\n<table>\n"
                + "<caption><span>Requires</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<thead>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Modifier</th>\n"
                + "<th class=\"colSecond\" scope=\"col\">Module</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>",
                "<div class=\"requiresSummary\">\n<table>\n"
                + "<caption><span>Indirect Requires</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<thead>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Modifier</th>\n"
                + "<th class=\"colSecond\" scope=\"col\">Module</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>",
                "<div class=\"packagesSummary\">\n<table>\n"
                + "<caption><span>Indirect Opens</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<thead>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">From</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Packages</th>\n"
                + "</tr>\n",
                "<th class=\"colFirst\" scope=\"row\"><a href=\"../moduleB/module-summary.html\">moduleB</a></th>\n"
                + "<td class=\"colLast\"><a href=\"../moduleB/testpkgmdlB/package-summary.html\">testpkgmdlB</a></td>\n");
    }

    void checkModuleModeApi(boolean found) {
        checkOutput("moduleA/module-summary.html", found,
                "<th class=\"colFirst\" scope=\"row\"><a href=\"testpkgmdlA/package-summary.html\">testpkgmdlA</a></th>\n"
                + "<td class=\"colLast\">&nbsp;</td>");
        checkOutput("moduleB/module-summary.html", found,
                "<li><a href=\"#module.description\">Description</a>&nbsp;|&nbsp;</li>\n"
                + "<li>Modules&nbsp;|&nbsp;</li>\n"
                + "<li><a href=\"#packages.summary\">Packages</a>&nbsp;|&nbsp;</li>\n"
                + "<li><a href=\"#services.summary\">Services</a></li>",
                "<th class=\"colFirst\" scope=\"row\"><a href=\"testpkgmdlB/package-summary.html\">testpkgmdlB</a></th>\n"
                + "<td class=\"colLast\">&nbsp;</td>",
                "<div class=\"packagesSummary\">\n<table>\n"
                + "<caption><span>Opens</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<thead>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Package</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>\n"
                + "</thead>\n"
                + "<tbody>\n"
                + "<tr class=\"altColor\" id=\"i0\">\n"
                + "<th class=\"colFirst\" scope=\"row\"><a href=\"testpkgmdlB/package-summary.html\">testpkgmdlB</a></th>\n"
                + "<td class=\"colLast\">&nbsp;</td>\n"
                + "</tr>\n"
                + "</tbody>\n"
                + "</table>");
        checkOutput("moduletags/module-summary.html", true,
                "<th class=\"colFirst\" scope=\"row\"><a href=\"testpkgmdltags/package-summary.html\">testpkgmdltags</a></th>\n"
                + "<td class=\"colLast\">&nbsp;</td>");
    }

    void checkModuleModeAll(boolean found) {
        checkOutput("moduleA/module-summary.html", found,
                "<td class=\"colFirst\"> </td>\n"
                + "<th class=\"colSecond\" scope=\"row\">java.base</th>\n"
                + "<td class=\"colLast\">&nbsp;</td>",
                "<td class=\"colFirst\"> </td>\n"
                + "<th class=\"colSecond\" scope=\"row\"><a href=\"../moduleC/module-summary.html\">moduleC</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"block\">This is a test description for the moduleC module.</div>\n"
                + "</td>",
                "<th class=\"colFirst\" scope=\"row\"><a href=\"../moduleC/module-summary.html\">moduleC</a></th>\n"
                + "<td class=\"colLast\"><a href=\"../moduleC/testpkgmdlC/package-summary.html\">testpkgmdlC</a></td>",
                "<th class=\"colFirst\" scope=\"row\"><a href=\"testpkgmdlA/package-summary.html\">testpkgmdlA</a></th>\n"
                + "<td class=\"colSecond\">All Modules</td>\n"
                + "<td class=\"colLast\">&nbsp;</td>",
                "<div role=\"tablist\" aria-orientation=\"horizontal\"><button role=\"tab\" aria-selected=\"true\""
                + " aria-controls=\"packagesSummary_tabpanel\" tabindex=\"0\" onkeydown=\"switchTab(event)\" id=\"t0\""
                + " class=\"activeTableTab\">All Packages</button><button role=\"tab\" aria-selected=\"false\""
                + " aria-controls=\"packagesSummary_tabpanel\" tabindex=\"-1\" onkeydown=\"switchTab(event)\" id=\"t1\""
                + " class=\"tableTab\" onclick=\"show(1);\">Exports</button><button role=\"tab\" aria-selected=\"false\""
                + " aria-controls=\"packagesSummary_tabpanel\" tabindex=\"-1\" onkeydown=\"switchTab(event)\" id=\"t3\""
                + " class=\"tableTab\" onclick=\"show(4);\">Concealed</button></div>",
                "<th class=\"colFirst\" scope=\"row\"><a href=\"concealedpkgmdlA/package-summary.html\">concealedpkgmdlA</a></th>\n"
                + "<td class=\"colSecond\">None</td>\n"
                + "<td class=\"colLast\">&nbsp;</td>");
        checkOutput("moduleB/module-summary.html", found,
                "<li><a href=\"#module.description\">Description</a>&nbsp;|&nbsp;</li>\n"
                + "<li><a href=\"#modules.summary\">Modules</a>&nbsp;|&nbsp;</li>\n"
                + "<li><a href=\"#packages.summary\">Packages</a>&nbsp;|&nbsp;</li>\n"
                + "<li><a href=\"#services.summary\">Services</a></li>",
                "<th class=\"colFirst\" scope=\"row\"><a href=\"testpkgmdlB/package-summary.html\">testpkgmdlB</a></th>\n"
                + "<td class=\"colSecond\">None</td>\n"
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
                "<div role=\"tablist\" aria-orientation=\"horizontal\"><button role=\"tab\" aria-selected=\"true\""
                + " aria-controls=\"packagesSummary_tabpanel\" tabindex=\"0\" onkeydown=\"switchTab(event)\" id=\"t0\""
                + " class=\"activeTableTab\">All Packages</button><button role=\"tab\" aria-selected=\"false\""
                + " aria-controls=\"packagesSummary_tabpanel\" tabindex=\"-1\" onkeydown=\"switchTab(event)\" id=\"t1\""
                + " class=\"tableTab\" onclick=\"show(1);\">Exports</button><button role=\"tab\" aria-selected=\"false\""
                + " aria-controls=\"packagesSummary_tabpanel\" tabindex=\"-1\" onkeydown=\"switchTab(event)\" id=\"t2\""
                + " class=\"tableTab\" onclick=\"show(2);\">Opens</button></div>");
        checkOutput("moduleC/module-summary.html", found,
                "<caption><span>Exports</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<thead>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Package</th>\n"
                + "<th class=\"colSecond\" scope=\"col\">Exported To Modules</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>\n"
                + "</thead>");
        checkOutput("moduletags/module-summary.html", true,
                "<th class=\"colFirst\" scope=\"row\"><a href=\"testpkgmdltags/package-summary.html\">testpkgmdltags</a></th>\n"
                + "<td class=\"colLast\">&nbsp;</td>");
    }

    void checkModuleDeprecation(boolean found) {
        checkOutput("moduleA/module-summary.html", found,
                "<div class=\"deprecationBlock\"><span class=\"deprecatedLabel\">Deprecated, for removal:"
                + " This API element is subject to removal in a future version.</span>\n"
                + "<div class=\"deprecationComment\">This module is deprecated.</div>\n"
                + "</div>");
        checkOutput("deprecated-list.html", found,
                "<ul>\n"
                + "<li><a href=\"#forRemoval\">For Removal</a></li>\n"
                + "<li><a href=\"#module\">Modules</a></li>\n"
                + "</ul>",
                "<tr class=\"altColor\">\n"
                + "<th class=\"colDeprecatedItemName\" scope=\"row\"><a href=\"moduleA/module-summary.html\">moduleA</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"deprecationComment\">This module is deprecated.</div>\n"
                + "</td>\n"
                + "</tr>");
        checkOutput("moduleB/module-summary.html", !found,
                "<div class=\"deprecationBlock\"><span class=\"deprecatedLabel\">Deprecated.</span>\n"
                + "<div class=\"deprecationComment\">This module is deprecated using just the javadoc tag.</div>\n");
        checkOutput("moduletags/module-summary.html", found,
                "<p>@Deprecated\n"
                + "</p>",
                "<div class=\"deprecationBlock\"><span class=\"deprecatedLabel\">Deprecated.</span></div>");
    }

    void checkModuleAnnotation() {
        checkOutput("moduleB/module-summary.html", true,
                "<p><a href=\"testpkgmdlB/AnnotationType.html\" title=\"annotation in testpkgmdlB\">@AnnotationType</a>(<a href=\"testpkgmdlB/AnnotationType.html#optional()\">optional</a>=\"Module Annotation\",\n"
                + "                <a href=\"testpkgmdlB/AnnotationType.html#required()\">required</a>=2016)\n"
                + "</p>");
        checkOutput("moduleB/module-summary.html", false,
                "@AnnotationTypeUndocumented");
    }

    void checkModuleSummaryNoExported(boolean found) {
        checkOutput("moduleNoExport/module-summary.html", found,
                "<!-- ============ PACKAGES SUMMARY =========== -->\n"
                + "<h2>Packages</h2>",
                "<caption><span>Concealed</span><span class=\"tabEnd\">&nbsp;</span></caption>");
    }

    void checkGroupOption() {
        checkOutput("index.html", true,
                "<div class=\"contentContainer\">\n"
                + "<div class=\"overviewSummary\">\n"
                + "<div role=\"tablist\" aria-orientation=\"horizontal\"><button role=\"tab\""
                + " aria-selected=\"true\" aria-controls=\"overviewSummary_tabpanel\" tabindex=\"0\""
                + " onkeydown=\"switchTab(event)\" id=\"t0\" class=\"activeTableTab\">All Modules</button>"
                + "<button role=\"tab\" aria-selected=\"false\" aria-controls=\"overviewSummary_tabpanel\""
                + " tabindex=\"-1\" onkeydown=\"switchTab(event)\" id=\"t1\" class=\"tableTab\""
                + " onclick=\"show(1);\">Module Group A</button><button role=\"tab\" aria-selected=\"false\""
                + " aria-controls=\"overviewSummary_tabpanel\" tabindex=\"-1\" onkeydown=\"switchTab(event)\""
                + " id=\"t2\" class=\"tableTab\" onclick=\"show(2);\">Module Group B &amp; C</button><button"
                + " role=\"tab\" aria-selected=\"false\" aria-controls=\"overviewSummary_tabpanel\" tabindex=\"-1\""
                + " onkeydown=\"switchTab(event)\" id=\"t4\" class=\"tableTab\" onclick=\"show(4);\">"
                + "Other Modules</button></div>\n"
                + "<div id=\"overviewSummary_tabpanel\" role=\"tabpanel\">\n"
                + "<table aria-labelledby=\"t0\">",
                "var data = {\"i0\":1,\"i1\":2,\"i2\":2,\"i3\":4};\n"
                + "var tabs = {65535:[\"t0\",\"All Modules\"],1:[\"t1\",\"Module Group A\"],2:[\"t2\",\"Module Group B & C\"],4:[\"t4\",\"Other Modules\"]};\n"
                + "var altColor = \"altColor\";\n"
                + "var rowColor = \"rowColor\";\n"
                + "var tableTab = \"tableTab\";\n"
                + "var activeTableTab = \"activeTableTab\";");
        checkOutput("index.html", false,
                "<div class=\"overviewSummary\">\n<table>\n"
                + "<caption><span>Modules</span><span class=\"tabEnd\">&nbsp;</span></caption>",
                "Java SE Modules");
    }

    void checkGroupOptionOrdering() {
        checkOutput("index.html", true,
                "<div role=\"tablist\" aria-orientation=\"horizontal\"><button role=\"tab\""
                + " aria-selected=\"true\" aria-controls=\"overviewSummary_tabpanel\" tabindex=\"0\""
                + " onkeydown=\"switchTab(event)\" id=\"t0\" class=\"activeTableTab\">All Modules</button>"
                + "<button role=\"tab\" aria-selected=\"false\" aria-controls=\"overviewSummary_tabpanel\""
                + " tabindex=\"-1\" onkeydown=\"switchTab(event)\" id=\"t1\" class=\"tableTab\""
                + " onclick=\"show(1);\">B Group</button><button role=\"tab\" aria-selected=\"false\""
                + " aria-controls=\"overviewSummary_tabpanel\" tabindex=\"-1\" onkeydown=\"switchTab(event)\""
                + " id=\"t2\" class=\"tableTab\" onclick=\"show(2);\">C Group</button><button role=\"tab\""
                + " aria-selected=\"false\" aria-controls=\"overviewSummary_tabpanel\" tabindex=\"-1\""
                + " onkeydown=\"switchTab(event)\" id=\"t4\" class=\"tableTab\" onclick=\"show(4);\">A Group"
                + "</button><button role=\"tab\" aria-selected=\"false\" aria-controls=\"overviewSummary_tabpanel\""
                + " tabindex=\"-1\" onkeydown=\"switchTab(event)\" id=\"t8\" class=\"tableTab\" onclick=\"show(8);\">"
                + "Other Modules</button></div>",
                "var tabs = {65535:[\"t0\",\"All Modules\"],1:[\"t1\",\"B Group\"],2:[\"t2\",\"C Group\"],"
                + "4:[\"t4\",\"A Group\"],8:[\"t8\",\"Other Modules\"]};");
        checkOutput("index.html", false,
                "<div role=\"tablist\" aria-orientation=\"horizontal\"><button role=\"tab\""
                + " aria-selected=\"true\" aria-controls=\"overviewSummary_tabpanel\" tabindex=\"0\""
                + " onkeydown=\"switchTab(event)\" id=\"t0\" class=\"activeTableTab\">All Modules</button>"
                + "<button role=\"tab\" aria-selected=\"false\" aria-controls=\"overviewSummary_tabpanel\""
                + " tabindex=\"-1\" onkeydown=\"switchTab(event)\" id=\"t1\" class=\"tableTab\""
                + " onclick=\"show(1);\">A Group</button><button role=\"tab\" aria-selected=\"false\""
                + " aria-controls=\"overviewSummary_tabpanel\" tabindex=\"-1\" onkeydown=\"switchTab(event)\""
                + " id=\"t2\" class=\"tableTab\" onclick=\"show(2);\">B Group</button><button role=\"tab\""
                + " aria-selected=\"false\" aria-controls=\"overviewSummary_tabpanel\" tabindex=\"-1\""
                + " onkeydown=\"switchTab(event)\" id=\"t4\" class=\"tableTab\" onclick=\"show(4);\">C Group"
                + "</button><button role=\"tab\" aria-selected=\"false\" aria-controls=\"overviewSummary_tabpanel\""
                + " tabindex=\"-1\" onkeydown=\"switchTab(event)\" id=\"t8\" class=\"tableTab\""
                + " onclick=\"show(8);\">Other Modules</button></div>",
                "Java SE Modules");
    }

    void checkUnnamedModuleGroupOption() {
        checkOutput("index.html", true,
                "<div class=\"contentContainer\">\n"
                + "<div class=\"block\">The overview summary page header.</div>\n"
                + "</div>\n"
                + "<div class=\"contentContainer\">\n"
                + "<div class=\"overviewSummary\">\n"
                + "<div role=\"tablist\" aria-orientation=\"horizontal\"><button role=\"tab\""
                + " aria-selected=\"true\" aria-controls=\"overviewSummary_tabpanel\" tabindex=\"0\""
                + " onkeydown=\"switchTab(event)\" id=\"t0\" class=\"activeTableTab\">All Packages</button>"
                + "<button role=\"tab\" aria-selected=\"false\" aria-controls=\"overviewSummary_tabpanel\""
                + " tabindex=\"-1\" onkeydown=\"switchTab(event)\" id=\"t1\" class=\"tableTab\""
                + " onclick=\"show(1);\">Package Group 0</button><button role=\"tab\" aria-selected=\"false\""
                + " aria-controls=\"overviewSummary_tabpanel\" tabindex=\"-1\" onkeydown=\"switchTab(event)\""
                + " id=\"t2\" class=\"tableTab\" onclick=\"show(2);\">Package Group 1</button></div>\n"
                + "<div id=\"overviewSummary_tabpanel\" role=\"tabpanel\">\n"
                + "<table aria-labelledby=\"t0\">",
                "var data = {\"i0\":1,\"i1\":2};\n"
                + "var tabs = {65535:[\"t0\",\"All Packages\"],1:[\"t1\",\"Package Group 0\"],2:[\"t2\",\"Package Group 1\"]};\n"
                + "var altColor = \"altColor\";\n"
                + "var rowColor = \"rowColor\";\n"
                + "var tableTab = \"tableTab\";\n"
                + "var activeTableTab = \"activeTableTab\";");
    }

    void checkGroupOptionPackageOrdering() {
        checkOutput("index.html", true,
                "<div role=\"tablist\" aria-orientation=\"horizontal\"><button role=\"tab\""
                + " aria-selected=\"true\" aria-controls=\"overviewSummary_tabpanel\" tabindex=\"0\""
                + " onkeydown=\"switchTab(event)\" id=\"t0\" class=\"activeTableTab\">All Packages</button>"
                + "<button role=\"tab\" aria-selected=\"false\" aria-controls=\"overviewSummary_tabpanel\""
                + " tabindex=\"-1\" onkeydown=\"switchTab(event)\" id=\"t1\" class=\"tableTab\""
                + " onclick=\"show(1);\">Z Group</button><button role=\"tab\" aria-selected=\"false\""
                + " aria-controls=\"overviewSummary_tabpanel\" tabindex=\"-1\" onkeydown=\"switchTab(event)\""
                + " id=\"t2\" class=\"tableTab\" onclick=\"show(2);\">A Group</button></div>",
                "var tabs = {65535:[\"t0\",\"All Packages\"],1:[\"t1\",\"Z Group\"],2:[\"t2\",\"A Group\"]};");
    }

    void checkGroupOptionSingleModule() {
        checkOutput("index.html", true,
                "window.location.replace('moduleB/module-summary.html')");
    }

    void checkModuleName(boolean found) {
        checkOutput("test.moduleFullName/module-summary.html", found,
                "<div class=\"header\">\n"
                + "<h1 title=\"Module\" class=\"title\">Module&nbsp;test.moduleFullName</h1>\n"
                + "</div>");
        checkOutput("index-all.html", found,
                "<h2 class=\"title\" id=\"I:T\">T</h2>\n"
                + "<dl class=\"index\">\n"
                + "<dt><a href=\"test.moduleFullName/module-summary.html\">test.moduleFullName</a> - module test.moduleFullName</dt>\n"
                + "<dd>\n"
                + "<div class=\"block\">This is a test description for the test.moduleFullName.</div>\n"
                + "</dd>");
        checkOutput("test.moduleFullName/module-summary.html", !found,
                "<div class=\"header\">\n"
                + "<h1 title=\"Module\" class=\"title\">Module&nbsp;moduleFullName</h1>\n"
                + "</div>");
        checkOutput("index-all.html", !found,
                "<dl class=\"index\">\n"
                + "<dt><a href=\"test.moduleFullName/module-summary.html\">moduleFullName</a> - module moduleFullName</dt>\n"
                + "<dd>\n"
                + "<div class=\"block\">This is a test description for the test.moduleFullName.</div>\n"
                + "</dd>\n"
                + "</dl>");
    }

    void checkLinkOffline() {
        checkOutput("moduleB/testpkg3mdlB/package-summary.html", true,
                "<a href=\"https://docs.oracle.com/javase/9/docs/api/java.base/java/lang/String.html\" "
                + "title=\"class or interface in java.lang\" class=\"externalLink\"><code>Link to String Class</code></a>");
        checkOutput("moduleB/testpkg3mdlB/package-summary.html", true,
                "<a href=\"https://docs.oracle.com/javase/9/docs/api/java.base/java/lang/package-summary.html\" "
                + "class=\"externalLink\"><code>Link to java.lang package</code></a>");
        checkOutput("moduleB/testpkg3mdlB/package-summary.html", true,
                "<a href=\"https://docs.oracle.com/javase/9/docs/api/java.base/module-summary.html\" "
                + "class=\"externalLink\"><code>Link to java.base module</code></a>");
    }

    void checkLinkSource(boolean includePrivate) {
        checkOutput("moduleA/module-summary.html", !includePrivate,
                "<table>\n<caption><span>Exports</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<thead>\n<tr>\n<th class=\"colFirst\" scope=\"col\">Package</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n</tr>\n</thead>\n"
                + "<tbody>\n<tr class=\"altColor\" id=\"i0\">\n"
                + "<th class=\"colFirst\" scope=\"row\"><a href=\"testpkgmdlA/package-summary.html\">testpkgmdlA</a></th>\n"
                + "<td class=\"colLast\">&nbsp;</td>\n</tr>\n</tbody>\n</table>");
        checkOutput("moduleA/testpkgmdlA/TestClassInModuleA.html", true,
                "<section class=\"description\">\n<hr>\n"
                + "<pre>public class <a href=\"../../src-html/moduleA/testpkgmdlA/TestClassInModuleA.html#line.25\">"
                + "TestClassInModuleA</a>\nextends java.lang.Object</pre>\n</section>");
        checkOutput("src-html/moduleA/testpkgmdlA/TestClassInModuleA.html", true,
                "<span class=\"sourceLineNo\">019</span><span id=\"line.19\"> * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA</span>\n"
                + "<span class=\"sourceLineNo\">020</span><span id=\"line.20\"> * or visit www.oracle.com if you need additional information or have any</span>\n"
                + "<span class=\"sourceLineNo\">021</span><span id=\"line.21\"> * questions.</span>\n"
                + "<span class=\"sourceLineNo\">022</span><span id=\"line.22\"> */</span>\n"
                + "<span class=\"sourceLineNo\">023</span><span id=\"line.23\">package testpkgmdlA;</span>\n"
                + "<span class=\"sourceLineNo\">024</span><span id=\"line.24\"></span>\n"
                + "<span class=\"sourceLineNo\">025</span><span id=\"line.25\">public class TestClassInModuleA {</span>\n"
                + "<span class=\"sourceLineNo\">026</span><span id=\"line.26\">}</span>");
        if (includePrivate) {
            checkOutput("src-html/moduleA/concealedpkgmdlA/ConcealedClassInModuleA.html", true,
                    "<span class=\"sourceLineNo\">024</span><span id=\"line.24\">package concealedpkgmdlA;</span>\n"
                    + "<span class=\"sourceLineNo\">025</span><span id=\"line.25\"></span>\n"
                    + "<span class=\"sourceLineNo\">026</span><span id=\"line.26\">public class ConcealedClassInModuleA {</span>\n"
                    + "<span class=\"sourceLineNo\">027</span><span id=\"line.27\">    public void testMethodConcealedClass() { }</span>\n"
                    + "<span class=\"sourceLineNo\">028</span><span id=\"line.28\">}</span>");
        }
    }

    void checkAllPkgsAllClasses(boolean found) {
        checkOutput("allclasses-index.html", true,
                "<div role=\"tablist\" aria-orientation=\"horizontal\"><button role=\"tab\""
                + " aria-selected=\"true\" aria-controls=\"typeSummary_tabpanel\" tabindex=\"0\""
                + " onkeydown=\"switchTab(event)\" id=\"t0\" class=\"activeTableTab\">All Classes</button>"
                + "<button role=\"tab\" aria-selected=\"false\" aria-controls=\"typeSummary_tabpanel\""
                + " tabindex=\"-1\" onkeydown=\"switchTab(event)\" id=\"t2\" class=\"tableTab\""
                + " onclick=\"show(2);\">Class Summary</button><button role=\"tab\" aria-selected=\"false\""
                + " aria-controls=\"typeSummary_tabpanel\" tabindex=\"-1\" onkeydown=\"switchTab(event)\""
                + " id=\"t6\" class=\"tableTab\" onclick=\"show(32);\">Annotation Types Summary</button></div>\n",
                "<thead>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Class</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>\n"
                + "</thead>\n");
        checkOutput("allpackages-index.html", true,
                "<caption><span>Package Summary</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<thead>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Package</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>\n"
                + "</thead>");
        checkOutput("allclasses-index.html", found,
                "<table aria-labelledby=\"t0\">\n");
        checkOutput("allpackages-index.html", found,
                "<div class=\"packagesSummary\">\n<table>\n");
        checkOutput("allclasses-index.html", !found,
                "<table summary=\"Class Summary table, listing classes, and an explanation\" aria-labelledby=\"t0\">");
        checkOutput("allpackages-index.html", !found,
                "<div class=\"packagesSummary\">\n"
                + "<table summary=\"Package Summary table, listing packages, and an explanation\">");
        checkOutput("type-search-index.js", true,
                "{\"l\":\"All Classes\",\"u\":\"allclasses-index.html\"}");
        checkOutput("package-search-index.js", true,
                "{\"l\":\"All Packages\",\"u\":\"allpackages-index.html\"}");
        checkOutput("index-all.html", true,
                "<br><a href=\"allclasses-index.html\">All&nbsp;Classes</a>"
                + "<span class=\"verticalSeparator\">|</span>"
                + "<a href=\"allpackages-index.html\">All&nbsp;Packages</a>");
    }
}
