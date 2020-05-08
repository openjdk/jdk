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
                """
                    <!-- ============ MODULE DESCRIPTION =========== -->
                    <a name="module.description">
                    <!--   -->
                    </a>
                    <div class="block">This is a test description for the moduleA module with a Sear\
                    ch phrase <span id="searchphrase" class="search-tag-result">search phrase</span>\
                    .</div>""");
        checkOutput("moduleB/module-summary.html", found,
                """
                    <!-- ============ MODULE DESCRIPTION =========== -->
                    <a name="module.description">
                    <!--   -->
                    </a>
                    <div class="block">This is a test description for the moduleB module. Search wor\
                    d <span id="search_word" class="search-tag-result">search_word</span> with no de\
                    scription.</div>""");
        checkOutput("index.html", found,
                """
                    </script>
                    <div class="block">The overview summary page header.</div>
                    </div>
                    <div class="overview-summary">
                    <table summary="Module Summary table, listing modules, and an explanation">
                    <caption><span>Modules</span><span class="tab-end">&nbsp;</span></caption>""");
        checkOutput("index.html", false,
                """
                    </table>
                    </div>
                    <div class="block">The overview summary page header.</div>
                    </div>
                    <div class="overview-summary">
                    <table summary="Module Summary table, listing modules, and an explanation">
                    <caption><span>Modules</span><span class="tab-end">&nbsp;</span></caption>""");
    }

    void checkNoDescription(boolean found) {
        checkOutput("moduleA/module-summary.html", found,
                """
                    <div class="header">
                    <p>@Deprecated(forRemoval=true)
                    </p>
                    <h1 title="Module" class="title">Module&nbsp;moduleA</h1>
                    </div><ul class="block-list">
                    <li>
                    <ul class="block-list">
                    <li>
                    <!-- ============ PACKAGES SUMMARY =========== -->""");
        checkOutput("moduleB/module-summary.html", found,
                """
                    <ul class="block-list">
                    <li>
                    <ul class="block-list">
                    <li>
                    <!-- ============ PACKAGES SUMMARY =========== -->""");
    }

    void checkHtml5Description(boolean found) {
        checkOutput("moduleA/module-summary.html", found,
                """
                    <section class="module-description" id="module.description">
                    <div class="deprecation-block"><span class="deprecated-label">Deprecated, for re\
                    moval: This API element is subject to removal in a future version.</span>
                    <div class="deprecation-comment">This module is deprecated.</div>
                    </div>
                    <!-- ============ MODULE DESCRIPTION =========== -->
                    <div class="block">This is a test description for the moduleA module with a Sear\
                    ch phrase <span id="searchphrase" class="search-tag-result">search phrase</span>\
                    .</div>""");
        checkOutput("moduleB/module-summary.html", found,
                """
                    <section class="module-description" id="module.description">
                    <!-- ============ MODULE DESCRIPTION =========== -->
                    <div class="block">This is a test description for the moduleB module. Search wor\
                    d <span id="search_word" class="search-tag-result">search_word</span> with no de\
                    scription.</div>""");
        checkOutput("index.html", found,
                """
                    </nav>
                    </header>
                    <div class="flex-content">
                    <main role="main">
                    <div class="block">The overview summary page header.</div>
                    <div class="overview-summary" id="all-modules-table">
                    <table class="summary-table">
                    <caption><span>Modules</span></caption>""");
        checkOutput("index.html", false,
                """
                    </table>
                    </div>
                    </main>
                    <main role="main">
                    <div class="block">The overview summary page header.</div>
                    </div>
                    <div class="overview-summary" id="all-modules-table">
                    <table class="summary-table">
                    <caption><span>Modules</span><</caption>""");
    }

    void checkHtml5NoDescription(boolean found) {
        checkOutput("moduleA/module-summary.html", found,
                """
                    <div class="header">
                    <p>@Deprecated(forRemoval=true)
                    </p>
                    <h1 title="Module" class="title">Module&nbsp;moduleA</h1>
                    </div>
                    <section class="summary">
                    <ul class="summary-list">
                    <li>
                    <section class="packages-summary" id="packages.summary">
                    <!-- ============ PACKAGES SUMMARY =========== -->""");
        checkOutput("moduleB/module-summary.html", found,
                """
                    <p><a href="testpkgmdlB/AnnotationType.html" title="annotation in testpkgmdlB">@\
                    AnnotationType</a>(<a href="testpkgmdlB/AnnotationType.html#optional()">optional\
                    </a>="Module Annotation",
                                    <a href="testpkgmdlB/AnnotationType.html#required()">required</a>=2016)
                    </p>
                    <h1 title="Module" class="title">Module&nbsp;moduleB</h1>
                    </div>
                    <section class="summary">
                    <ul class="summary-list">
                    <li>
                    <section class="packages-summary" id="packages.summary">
                    <!-- ============ PACKAGES SUMMARY =========== -->""");
    }

    void checkModuleLink() {
        checkOutput("index.html", true,
                "<li>Module</li>");
        checkOutput("moduleA/module-summary.html", true,
                """
                    <li class="nav-bar-cell1-rev">Module</li>""");
        checkOutput("moduleB/module-summary.html", true,
                """
                    <li class="nav-bar-cell1-rev">Module</li>""");
        checkOutput("moduleA/testpkgmdlA/class-use/TestClassInModuleA.html", true,
                """
                    <li><a href="../../module-summary.html">Module</a></li>""");
        checkOutput("moduleB/testpkgmdlB/package-summary.html", true,
                """
                    <li><a href="../module-summary.html">Module</a></li>""");
        checkOutput("moduleB/testpkgmdlB/TestClassInModuleB.html", true,
                """
                    <li><a href="../module-summary.html">Module</a></li>""");
        checkOutput("moduleB/testpkgmdlB/class-use/TestClassInModuleB.html", true,
                """
                    <li><a href="../../module-summary.html">Module</a></li>""");
    }

    void checkNoModuleLink() {
        checkOutput("testpkgnomodule/package-summary.html", true,
                """
                    <ul class="nav-list" title="Navigation">
                    <li><a href="../testpkgnomodule/package-summary.html">Package</a></li>""");
        checkOutput("testpkgnomodule/TestClassNoModule.html", true,
                """
                    <ul class="nav-list" title="Navigation">
                    <li><a href="../testpkgnomodule/package-summary.html">Package</a></li>""");
        checkOutput("testpkgnomodule/class-use/TestClassNoModule.html", true,
                """
                    <ul class="nav-list" title="Navigation">
                    <li><a href="../../testpkgnomodule/package-summary.html">Package</a></li>""");
    }

    void checkModuleTags() {
        checkOutput("moduletags/module-summary.html", true,
                """
                    Type Link: <a href="testpkgmdltags/TestClassInModuleTags.html" title="class in t\
                    estpkgmdltags"><code>TestClassInModuleTags</code></a>.""",
                """
                    Member Link: <a href="testpkgmdltags/TestClassInModuleTags.html#testMethod(java.\
                    lang.String)"><code>testMethod(String)</code></a>.""",
                """
                    Package Link: <a href="testpkgmdltags/package-summary.html"><code>testpkgmdltags</code></a>.""",
                "<dt>Since:</dt>\n"
                + "<dd>JDK 9</dd>",
                """
                    <dt>See Also:</dt>
                    <dd>"Test see tag",\s
                    <a href="testpkgmdltags/TestClassInModuleTags.html" title="class in testpkgmdlta\
                    gs"><code>TestClassInModuleTags</code></a></dd>""",
                """
                    <dt>Regular Tag:</dt>
                    <dd>Just a regular simple tag.</dd>""",
                """
                    <dt>Module Tag:</dt>
                    <dd>Just a simple module tag.</dd>""",
                "<dt>Version:</dt>\n"
                + "<dd>1.0</dd>",
                "<dt>Author:</dt>\n"
                + "<dd>Alice</dd>");
        checkOutput("moduletags/testpkgmdltags/TestClassInModuleTags.html", false,
                """
                    <dt>Module Tag:</dt>
                    <dd>Just a simple module tag.</dd>""");
    }

    void checkOverviewSummaryModules() {
        checkOutput("index.html", true,
                """
                    <div class="overview-summary">
                    <table summary="Module Summary table, listing modules, and an explanation">
                    <caption><span>Modules</span></caption>
                    <thead>
                    <tr>
                    <th class="col-first" scope="col">Module</th>
                    <th class="col-last" scope="col">Description</th>
                    </tr>
                    </thead>""");
        checkOutput("overview-summary.html", false,
                """
                    <div class="overview-summary">
                    <table summary="Package Summary table, listing packages, and an explanation">
                    <caption><span>Packages</span></caption>
                    <thead>
                    <tr>
                    <th class="col-first" scope="col">Package</th>
                    <th class="col-last" scope="col">Description</th>
                    </tr>
                    </thead>""");
    }

    void checkOverviewSummaryPackages() {
        checkOutput("index.html", false,
                """
                    <div class="overview-summary">
                    <table summary="Module Summary table, listing modules, and an explanation">
                    <caption><span>Modules</span></caption>
                    <thead>
                    <tr>
                    <th class="col-first" scope="col">Module</th>
                    <th class="col-last" scope="col">Description</th>
                    </tr>
                    </thead>""",
                """
                    </table>
                    </div>
                    <div class="block">The overview summary page header.</div>
                    </div>
                    <div class="overview-summary">
                    <table summary="Package Summary table, listing packages, and an explanation">
                    <caption><span>Packages</span></caption>""");
        checkOutput("index.html", true,
                """
                    <div class="overview-summary">
                    <table summary="Package Summary table, listing packages, and an explanation">
                    <caption><span>Packages</span></caption>
                    <thead>n<tr>
                    <th class="col-first" scope="col">Package</th>
                    <th class="col-last" scope="col">Description</th>
                    </tr>
                    </thead>
                    """,
                """
                    </script>
                    <div class="block">The overview summary page header.</div>
                    </div>
                    <div class="overview-summary">
                    <table summary="Package Summary table, listing packages, and an explanation">
                    <caption><span>Packages</span></caption>""");
    }

    void checkHtml5OverviewSummaryModules() {
        checkOutput("index.html", true,
                """
                    <div class="overview-summary" id="all-modules-table">
                    <table class="summary-table">
                    <caption><span>Modules</span></caption>
                    <thead>
                    <tr>
                    <th class="col-first" scope="col">Module</th>
                    <th class="col-last" scope="col">Description</th>
                    </tr>
                    </thead>""");
        checkOutput("overview-summary.html", false,
                """
                    <div class="overview-summary" id="all-modules-table">
                    <table class="summary-table">
                    <caption><span>Packages</span></caption>
                    <thead>
                    <tr>
                    <th class="col-first" scope="col">Package</th>
                    <th class="col-last" scope="col">Description</th>
                    </tr>
                    </thead>""");
    }

    void checkHtml5OverviewSummaryPackages() {
        checkOutput("index.html", false,
                """
                    <div class="overview-summary" id="all-modules-table">
                    <table class="summary-table">
                    <caption><span>Modules</span></caption>
                    <thead>
                    <tr>
                    <th class="col-first" scope="col">Module</th>
                    <th class="col-last" scope="col">Description</th>
                    </tr>
                    </thead>""",
                """
                    </table>
                    </div>
                    </main>
                    <main role="main">
                    <div class="block">The overview summary page header.</div>
                    </div>
                    <a id="Packages">
                    <!--   -->
                    </a>
                    <div class="overview-summary">
                    <table>
                    <caption><span>Packages</span></caption>""");
        checkOutput("index.html", true,
                """
                    <div class="overview-summary" id="all-packages-table">
                    <table class="summary-table">
                    <caption><span>Packages</span></caption>
                    <thead>
                    <tr>
                    <th class="col-first" scope="col">Package</th>
                    <th class="col-last" scope="col">Description</th>
                    </tr>
                    </thead>""",
                """
                    </nav>
                    </header>
                    <div class="flex-content">
                    <main role="main">
                    <div class="block">The overview summary page header.</div>
                    <div class="overview-summary" id="all-packages-table">
                    <table class="summary-table">
                    <caption><span>Packages</span></caption>""");
    }

    void checkModuleSummary() {
        checkOutput("moduleA/module-summary.html", true,
                """
                    <ul class="sub-nav-list">
                    <li>Module:&nbsp;</li>
                    <li><a href="#module.description">Description</a>&nbsp;|&nbsp;</li>
                    <li><a href="#modules.summary">Modules</a>&nbsp;|&nbsp;</li>
                    <li><a href="#packages.summary">Packages</a>&nbsp;|&nbsp;</li>
                    <li>Services</li>
                    </ul>""",
                """
                    <section class="modules-summary" id="modules.summary">
                    <!-- ============ MODULES SUMMARY =========== -->
                    <h2>Modules</h2>""",
                """
                    <tr class="alt-color" id="i0">
                    <th class="col-first" scope="row"><a href="testpkgmdlA/package-summary.html">testpkgmdlA</a></th>
                    <td class="col-last">&nbsp;</td>
                    </tr>""",
                """
                    <section class="packages-summary" id="packages.summary">
                    <!-- ============ PACKAGES SUMMARY =========== -->
                    <h2>Packages</h2>""",
                """
                    <tr class="alt-color">
                    <td class="col-first">transitive</td>
                    <th class="col-second" scope="row"><a href="../moduleB/module-summary.html">moduleB</a></th>
                    <td class="col-last">
                    <div class="block">This is a test description for the moduleB module.</div>
                    </td>
                    </tr>""");
        checkOutput("moduleB/module-summary.html", true,
                """
                    <li><a href="#module.description">Description</a>&nbsp;|&nbsp;</li>
                    <li>Modules&nbsp;|&nbsp;</li>
                    <li><a href="#packages.summary">Packages</a>&nbsp;|&nbsp;</li>
                    <li><a href="#services.summary">Services</a></li>""",
                """
                    <!-- ============ PACKAGES SUMMARY =========== -->
                    <h2>Packages</h2>""",
                """
                    <tr class="alt-color" id="i0">
                    <th class="col-first" scope="row"><a href="testpkgmdlB/package-summary.html">testpkgmdlB</a></th>
                    <td class="col-last">&nbsp;</td>
                    </tr>""",
                """
                    <!-- ============ PACKAGES SUMMARY =========== -->
                    <h2>Packages</h2>""",
                """
                    <!-- ============ SERVICES SUMMARY =========== -->
                    <h2>Services</h2>""",
                """
                    <tr class="alt-color">
                    <th class="col-first" scope="row"><a href="testpkgmdlB/TestClassInModuleB.html" \
                    title="class in testpkgmdlB">TestClassInModuleB</a></th>
                    <td class="col-last">
                    <div class="block">With a test description for uses.</div>
                    </td>
                    </tr>""",
                """
                    <caption><span>Opens</span></caption>
                    <thead>
                    <tr>
                    <th class="col-first" scope="col">Package</th>
                    <th class="col-last" scope="col">Description</th>
                    </tr>
                    </thead>""",
                """
                    <caption><span>Uses</span></caption>
                    <thead>
                    <tr>
                    <th class="col-first" scope="col">Type</th>
                    <th class="col-last" scope="col">Description</th>
                    </tr>
                    </thead>""",
                """
                    <caption><span>Provides</span></caption>
                    <thead>
                    <tr>
                    <th class="col-first" scope="col">Type</th>
                    <th class="col-last" scope="col">Description</th>
                    </tr>
                    </thead>""");
    }

    void checkAggregatorModuleSummary() {
        checkOutput("moduleT/module-summary.html", true,
                """
                    <div class="header">
                    <h1 title="Module" class="title">Module&nbsp;moduleT</h1>
                    </div>""",
                """
                    <div class="block">This is a test description for the moduleT module. Search phr\
                    ase <span id="searchphrase" class="search-tag-result">search phrase</span>. Make\
                     sure there are no exported packages.</div>""",
                """
                    <tbody>
                    <tr class="alt-color">
                    <td class="col-first">transitive</td>
                    <th class="col-second" scope="row"><a href="../moduleA/module-summary.html">moduleA</a></th>
                    <td class="col-last">
                    <div class="block">This is a test description for the moduleA module with a Search phrase search phrase.</div>
                    </td>
                    </tr>
                    <tr class="row-color">
                    <td class="col-first">transitive</td>
                    <th class="col-second" scope="row"><a href="../moduleB/module-summary.html">moduleB</a></th>
                    <td class="col-last">
                    <div class="block">This is a test description for the moduleB module.</div>
                    </td>
                    </tr>
                    </tbody>""");
    }

    void checkNegatedModuleSummary() {
        checkOutput("moduleA/module-summary.html", false,
                """
                    <!-- ============ SERVICES SUMMARY =========== -->
                    <h2>Services</h2>""");
    }

    void checkModuleFilesAndLinks(boolean found) {
        checkFileAndOutput("moduleA/testpkgmdlA/package-summary.html", found,
                """
                    <li><a href="../module-summary.html">Module</a></li>""",
                """
                    <div class="sub-title"><span class="module-label-in-package">Module</span>&nbsp;\
                    <a href="../module-summary.html">moduleA</a></div>""");
        checkFileAndOutput("moduleA/testpkgmdlA/TestClassInModuleA.html", found,
                """
                    <li><a href="../module-summary.html">Module</a></li>""",
                """
                    <div class="sub-title"><span class="module-label-in-type">Module</span>&nbsp;<a \
                    href="../module-summary.html">moduleA</a></div>""");
        checkFileAndOutput("moduleB/testpkgmdlB/AnnotationType.html", found,
                """
                    <div class="sub-title"><span class="module-label-in-type">Module</span>&nbsp;<a \
                    href="../module-summary.html">moduleB</a></div>""",
                """
                    <div class="sub-title"><span class="package-label-in-type">Package</span>&nbsp;<\
                    a href="package-summary.html">testpkgmdlB</a></div>""");
        checkFiles(found,
                "moduleA/module-summary.html");
    }

    void checkModulesInSearch(boolean found) {
        checkOutput("index-all.html", found,
                """
                    <dl class="index">
                    <dt><a href="moduleA/module-summary.html">moduleA</a> - module moduleA</dt>
                    <dd>
                    <div class="block">This is a test description for the moduleA module with a Search phrase search phrase.</div>
                    </dd>
                    <dt><a href="moduleB/module-summary.html">moduleB</a> - module moduleB</dt>
                    <dd>
                    <div class="block">This is a test description for the moduleB module.</div>
                    </dd>
                    </dl>""",
                """
                    <dl class="index">
                    <dt><span class="search-tag-link"><a href="moduleB/module-summary.html#search_wo\
                    rd">search_word</a></span> - Search tag in module moduleB</dt>
                    <dd>&nbsp;</dd>
                    <dt><span class="search-tag-link"><a href="moduleA/module-summary.html#searchphr\
                    ase">search phrase</a></span> - Search tag in module moduleA</dt>
                    <dd>with description</dd>
                    </dl>""");
        checkOutput("index-all.html", false,
                """
                    <dt><span class="search-tag-link"><a href="moduleA/module-summary.html#searchphr\
                    ase">search phrase</a></span> - Search tag in module moduleA</dt>
                    <dd>with description</dd>
                    <dt><span class="search-tag-link"><a href="moduleA/module-summary.html#searchphr\
                    ase">search phrase</a></span> - Search tag in module moduleA</dt>
                    <dd>with description</dd>""");
    }

    void checkModuleModeCommon() {
        checkOutput("index.html", true,
                """
                    <th class="col-first" scope="row"><a href="moduleA/module-summary.html">moduleA</a></th>
                    <td class="col-last">
                    <div class="block">This is a test description for the moduleA module with a Search phrase search phrase.</div>
                    </td>""",
                """
                    <th class="col-first" scope="row"><a href="moduleB/module-summary.html">moduleB</a></th>
                    <td class="col-last">
                    <div class="block">This is a test description for the moduleB module.</div>
                    </td>""",
                """
                    <th class="col-first" scope="row"><a href="moduletags/module-summary.html">moduletags</a></th>
                    <td class="col-last">
                    <div class="block">This is a test description for the moduletags module.<br>
                     Type Link: <a href="moduletags/testpkgmdltags/TestClassInModuleTags.html" title\
                    ="class in testpkgmdltags"><code>TestClassInModuleTags</code></a>.<br>
                     Member Link: <a href="moduletags/testpkgmdltags/TestClassInModuleTags.html#test\
                    Method(java.lang.String)"><code>testMethod(String)</code></a>.<br>
                     Package Link: <a href="moduletags/testpkgmdltags/package-summary.html"><code>testpkgmdltags</code></a>.<br></div>
                    </td>""");
        checkOutput("moduleA/module-summary.html", true,
                """
                    <li><a href="#module.description">Description</a>&nbsp;|&nbsp;</li>
                    <li><a href="#modules.summary">Modules</a>&nbsp;|&nbsp;</li>
                    <li><a href="#packages.summary">Packages</a>&nbsp;|&nbsp;</li>
                    <li>Services</li>""",
                """
                    <th class="col-first" scope="row"><a href="../moduleB/module-summary.html">moduleB</a></th>
                    <td class="col-last"><a href="../moduleB/testpkgmdlB/package-summary.html">testpkgmdlB</a></td>
                    """);
        checkOutput("moduleB/module-summary.html", true,
                """
                    <th class="col-first" scope="row"><a href="testpkgmdlB/TestClassInModuleB.html" \
                    title="class in testpkgmdlB">TestClassInModuleB</a></th>
                    <td class="col-last">
                    <div class="block">With a test description for uses.</div>
                    </td>
                    """);
        checkOutput("moduletags/module-summary.html", true,
                """
                    <li><a href="#module.description">Description</a>&nbsp;|&nbsp;</li>
                    <li><a href="#modules.summary">Modules</a>&nbsp;|&nbsp;</li>
                    <li><a href="#packages.summary">Packages</a>&nbsp;|&nbsp;</li>
                    <li>Services</li>""",
                """
                    <div class="requires-summary">
                    <table class="details-table">
                    <caption><span>Indirect Requires</span></caption>""",
                """
                    <td class="col-first">transitive</td>
                    <th class="col-second" scope="row"><a href="../moduleB/module-summary.html">moduleB</a></th>
                    <td class="col-last">
                    <div class="block">This is a test description for the moduleB module.</div>
                    </td>""",
                """
                    <div class="packages-summary">
                    <table class="details-table">
                    <caption><span>Indirect Exports</span></caption>""",
                """
                    <td class="col-first">transitive static</td>
                    <th class="col-second" scope="row"><a href="../moduleA/module-summary.html">moduleA</a></th>
                    <td class="col-last">
                    <div class="block">This is a test description for the moduleA module with a Search phrase search phrase.</div>
                    </td>""",
                """
                    <div class="requires-summary">
                    <table class="details-table">
                    <caption><span>Requires</span></caption>
                    <thead>
                    <tr>
                    <th class="col-first" scope="col">Modifier</th>
                    <th class="col-second" scope="col">Module</th>
                    <th class="col-last" scope="col">Description</th>""",
                """
                    <div class="requires-summary">
                    <table class="details-table">
                    <caption><span>Indirect Requires</span></caption>
                    <thead>
                    <tr>
                    <th class="col-first" scope="col">Modifier</th>
                    <th class="col-second" scope="col">Module</th>
                    <th class="col-last" scope="col">Description</th>""",
                """
                    <div class="packages-summary">
                    <table class="details-table">
                    <caption><span>Indirect Opens</span></caption>
                    <thead>
                    <tr>
                    <th class="col-first" scope="col">From</th>
                    <th class="col-last" scope="col">Packages</th>
                    </tr>
                    """,
                """
                    <th class="col-first" scope="row"><a href="../moduleB/module-summary.html">moduleB</a></th>
                    <td class="col-last"><a href="../moduleB/testpkgmdlB/package-summary.html">testpkgmdlB</a></td>
                    """);
    }

    void checkModuleModeApi(boolean found) {
        checkOutput("moduleA/module-summary.html", found,
                """
                    <th class="col-first" scope="row"><a href="testpkgmdlA/package-summary.html">testpkgmdlA</a></th>
                    <td class="col-last">&nbsp;</td>""");
        checkOutput("moduleB/module-summary.html", found,
                """
                    <li><a href="#module.description">Description</a>&nbsp;|&nbsp;</li>
                    <li>Modules&nbsp;|&nbsp;</li>
                    <li><a href="#packages.summary">Packages</a>&nbsp;|&nbsp;</li>
                    <li><a href="#services.summary">Services</a></li>""",
                """
                    <th class="col-first" scope="row"><a href="testpkgmdlB/package-summary.html">testpkgmdlB</a></th>
                    <td class="col-last">&nbsp;</td>""",
                """
                    <div class="packages-summary" id="package-summary-table">
                    <table class="summary-table">
                    <caption><span>Opens</span></caption>
                    <thead>
                    <tr>
                    <th class="col-first" scope="col">Package</th>
                    <th class="col-last" scope="col">Description</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr class="alt-color" id="i0">
                    <th class="col-first" scope="row"><a href="testpkgmdlB/package-summary.html">testpkgmdlB</a></th>
                    <td class="col-last">&nbsp;</td>
                    </tr>
                    </tbody>
                    </table>""");
        checkOutput("moduletags/module-summary.html", true,
                """
                    <th class="col-first" scope="row"><a href="testpkgmdltags/package-summary.html">testpkgmdltags</a></th>
                    <td class="col-last">&nbsp;</td>""");
    }

    void checkModuleModeAll(boolean found) {
        checkOutput("moduleA/module-summary.html", found,
                """
                    <td class="col-first"> </td>
                    <th class="col-second" scope="row">java.base</th>
                    <td class="col-last">&nbsp;</td>""",
                """
                    <td class="col-first"> </td>
                    <th class="col-second" scope="row"><a href="../moduleC/module-summary.html">moduleC</a></th>
                    <td class="col-last">
                    <div class="block">This is a test description for the moduleC module.</div>
                    </td>""",
                """
                    <th class="col-first" scope="row"><a href="../moduleC/module-summary.html">moduleC</a></th>
                    <td class="col-last"><a href="../moduleC/testpkgmdlC/package-summary.html">testpkgmdlC</a></td>""",
                """
                    <th class="col-first" scope="row"><a href="testpkgmdlA/package-summary.html">testpkgmdlA</a></th>
                    <td class="col-second">All Modules</td>
                    <td class="col-last">&nbsp;</td>""",
                """
                    <div class="table-tabs" role="tablist" aria-orientation="horizontal"><button rol\
                    e="tab" aria-selected="true" aria-controls="package-summary-table.tabpanel" tabi\
                    ndex="0" onkeydown="switchTab(event)" id="t0" class="active-table-tab">All Packa\
                    ges</button><button role="tab" aria-selected="false" aria-controls="package-summ\
                    ary-table.tabpanel" tabindex="-1" onkeydown="switchTab(event)" id="t1" class="ta\
                    ble-tab" onclick="show(1);">Exports</button><button role="tab" aria-selected="fa\
                    lse" aria-controls="package-summary-table.tabpanel" tabindex="-1" onkeydown="swi\
                    tchTab(event)" id="t3" class="table-tab" onclick="show(4);">Concealed</button></\
                    div>""",
                """
                    <th class="col-first" scope="row"><a href="concealedpkgmdlA/package-summary.html">concealedpkgmdlA</a></th>
                    <td class="col-second">None</td>
                    <td class="col-last">&nbsp;</td>""");
        checkOutput("moduleB/module-summary.html", found,
                """
                    <li><a href="#module.description">Description</a>&nbsp;|&nbsp;</li>
                    <li><a href="#modules.summary">Modules</a>&nbsp;|&nbsp;</li>
                    <li><a href="#packages.summary">Packages</a>&nbsp;|&nbsp;</li>
                    <li><a href="#services.summary">Services</a></li>""",
                """
                    <th class="col-first" scope="row"><a href="testpkgmdlB/package-summary.html">testpkgmdlB</a></th>
                    <td class="col-second">None</td>
                    <td class="col-second">All Modules</td>
                    <td class="col-last">&nbsp;</td>""",
                """
                    <td class="col-first"> </td>
                    <th class="col-second" scope="row">java.base</th>
                    <td class="col-last">&nbsp;</td>""",
                """
                    <th class="col-first" scope="row"><a href="testpkgmdlB/TestClass2InModuleB.html"\
                     title="class in testpkgmdlB">TestClass2InModuleB</a></th>
                    <td class="col-last">&nbsp;</td>""",
                """
                    <th class="col-first" scope="row"><a href="testpkg2mdlB/TestInterface2InModuleB.\
                    html" title="interface in testpkg2mdlB">TestInterface2InModuleB</a></th>
                    <td class="col-last">&nbsp;<br>(<span class="implementation-label">Implementatio\
                    n(s):</span>&nbsp;<a href="testpkgmdlB/TestClass2InModuleB.html" title="class in\
                     testpkgmdlB">TestClass2InModuleB</a>)</td>""",
                """
                    <th class="col-first" scope="row"><a href="testpkg2mdlB/TestInterfaceInModuleB.h\
                    tml" title="interface in testpkg2mdlB">TestInterfaceInModuleB</a></th>
                    <td class="col-last">&nbsp;<br>(<span class="implementation-label">Implementatio\
                    n(s):</span>&nbsp;<a href="testpkgmdlB/TestClassInModuleB.html" title="class in \
                    testpkgmdlB">TestClassInModuleB</a>)</td>""",
                """
                    <div class="table-tabs" role="tablist" aria-orientation="horizontal"><button rol\
                    e="tab" aria-selected="true" aria-controls="package-summary-table.tabpanel" tabi\
                    ndex="0" onkeydown="switchTab(event)" id="t0" class="active-table-tab">All Packa\
                    ges</button><button role="tab" aria-selected="false" aria-controls="package-summ\
                    ary-table.tabpanel" tabindex="-1" onkeydown="switchTab(event)" id="t1" class="ta\
                    ble-tab" onclick="show(1);">Exports</button><button role="tab" aria-selected="fa\
                    lse" aria-controls="package-summary-table.tabpanel" tabindex="-1" onkeydown="swi\
                    tchTab(event)" id="t2" class="table-tab" onclick="show(2);">Opens</button></div>""");
        checkOutput("moduleC/module-summary.html", found,
                """
                    <caption><span>Exports</span></caption>
                    <thead>
                    <tr>
                    <th class="col-first" scope="col">Package</th>
                    <th class="col-second" scope="col">Exported To Modules</th>
                    <th class="col-last" scope="col">Description</th>
                    </tr>
                    </thead>""");
        checkOutput("moduletags/module-summary.html", true,
                """
                    <th class="col-first" scope="row"><a href="testpkgmdltags/package-summary.html">testpkgmdltags</a></th>
                    <td class="col-last">&nbsp;</td>""");
    }

    void checkModuleDeprecation(boolean found) {
        checkOutput("moduleA/module-summary.html", found,
                """
                    <div class="deprecation-block"><span class="deprecated-label">Deprecated, for re\
                    moval: This API element is subject to removal in a future version.</span>
                    <div class="deprecation-comment">This module is deprecated.</div>
                    </div>""");
        checkOutput("deprecated-list.html", found,
                """
                    <ul>
                    <li><a href="#forRemoval">For Removal</a></li>
                    <li><a href="#module">Modules</a></li>
                    </ul>""",
                """
                    <tr class="alt-color">
                    <th class="col-deprecated-item-name" scope="row"><a href="moduleA/module-summary.html">moduleA</a></th>
                    <td class="col-last">
                    <div class="deprecation-comment">This module is deprecated.</div>
                    </td>
                    </tr>""");
        checkOutput("moduleB/module-summary.html", !found,
                """
                    <div class="deprecation-block"><span class="deprecated-label">Deprecated.</span>
                    <div class="deprecation-comment">This module is deprecated using just the javadoc tag.</div>
                    """);
        checkOutput("moduletags/module-summary.html", found,
                "<p>@Deprecated\n"
                + "</p>",
                """
                    <div class="deprecation-block"><span class="deprecated-label">Deprecated.</span></div>""");
    }

    void checkModuleAnnotation() {
        checkOutput("moduleB/module-summary.html", true,
                """
                    <p><a href="testpkgmdlB/AnnotationType.html" title="annotation in testpkgmdlB">@\
                    AnnotationType</a>(<a href="testpkgmdlB/AnnotationType.html#optional()">optional\
                    </a>="Module Annotation",
                                    <a href="testpkgmdlB/AnnotationType.html#required()">required</a>=2016)
                    </p>""");
        checkOutput("moduleB/module-summary.html", false,
                "@AnnotationTypeUndocumented");
    }

    void checkModuleSummaryNoExported(boolean found) {
        checkOutput("moduleNoExport/module-summary.html", found,
                """
                    <!-- ============ PACKAGES SUMMARY =========== -->
                    <h2>Packages</h2>""",
                "<caption><span>Concealed</span></caption>");
    }

    void checkGroupOption() {
        checkOutput("index.html", true,
                """
                    <div class="overview-summary" id="all-modules-table">
                    <div class="table-tabs" role="tablist" aria-orientation="horizontal"><button rol\
                    e="tab" aria-selected="true" aria-controls="all-modules-table.tabpanel" tabindex\
                    ="0" onkeydown="switchTab(event)" id="t0" class="active-table-tab">All Modules</\
                    button><button role="tab" aria-selected="false" aria-controls="all-modules-table\
                    .tabpanel" tabindex="-1" onkeydown="switchTab(event)" id="t1" class="table-tab" \
                    onclick="show(1);">Module Group A</button><button role="tab" aria-selected="fals\
                    e" aria-controls="all-modules-table.tabpanel" tabindex="-1" onkeydown="switchTab\
                    (event)" id="t2" class="table-tab" onclick="show(2);">Module Group B &amp; C</bu\
                    tton><button role="tab" aria-selected="false" aria-controls="all-modules-table.t\
                    abpanel" tabindex="-1" onkeydown="switchTab(event)" id="t4" class="table-tab" on\
                    click="show(4);">Other Modules</button></div>
                    <div id="all-modules-table.tabpanel" role="tabpanel">
                    <table class="summary-table" aria-labelledby="t0">""",
                """
                    var data = {"i0":1,"i1":2,"i2":2,"i3":4};
                    var tabs = {65535:["t0","All Modules"],1:["t1","Module Group A"],2:["t2","Module\
                     Group B & C"],4:["t4","Other Modules"]};
                    var altColor = "alt-color";
                    var rowColor = "row-color";
                    var tableTab = "table-tab";
                    var activeTableTab = "active-table-tab";""");
        checkOutput("index.html", false,
                """
                    <div class="overview-summary">
                    <table>
                    <caption><span>Modules</span></caption>""",
                "Java SE Modules");
    }

    void checkGroupOptionOrdering() {
        checkOutput("index.html", true,
                """
                    <div class="table-tabs" role="tablist" aria-orientation="horizontal"><button rol\
                    e="tab" aria-selected="true" aria-controls="all-modules-table.tabpanel" tabindex\
                    ="0" onkeydown="switchTab(event)" id="t0" class="active-table-tab">All Modules</\
                    button><button role="tab" aria-selected="false" aria-controls="all-modules-table\
                    .tabpanel" tabindex="-1" onkeydown="switchTab(event)" id="t1" class="table-tab" \
                    onclick="show(1);">B Group</button><button role="tab" aria-selected="false" aria\
                    -controls="all-modules-table.tabpanel" tabindex="-1" onkeydown="switchTab(event)\
                    " id="t2" class="table-tab" onclick="show(2);">C Group</button><button role="tab\
                    " aria-selected="false" aria-controls="all-modules-table.tabpanel" tabindex="-1"\
                     onkeydown="switchTab(event)" id="t4" class="table-tab" onclick="show(4);">A Gro\
                    up</button><button role="tab" aria-selected="false" aria-controls="all-modules-t\
                    able.tabpanel" tabindex="-1" onkeydown="switchTab(event)" id="t8" class="table-t\
                    ab" onclick="show(8);">Other Modules</button></div>""",
                """
                    var tabs = {65535:["t0","All Modules"],1:["t1","B Group"],2:["t2","C Group"],4:[\
                    "t4","A Group"],8:["t8","Other Modules"]};""");
        checkOutput("index.html", false,
                """
                    <div class="table-tabs" role="tablist" aria-orientation="horizontal"><button rol\
                    e="tab" aria-selected="true" aria-controls="all-modules-table.tabpanel" tabindex\
                    ="0" onkeydown="switchTab(event)" id="t0" class="active-table-tab">All Modules</\
                    button><button role="tab" aria-selected="false" aria-controls="all-modules-table\
                    .tabpanel" tabindex="-1" onkeydown="switchTab(event)" id="t1" class="table-tab" \
                    onclick="show(1);">A Group</button><button role="tab" aria-selected="false" aria\
                    -controls="all-modules-table.tabpanel" tabindex="-1" onkeydown="switchTab(event)\
                    " id="t2" class="table-tab" onclick="show(2);">B Group</button><button role="tab\
                    " aria-selected="false" aria-controls="all-modules-table.tabpanel" tabindex="-1"\
                     onkeydown="switchTab(event)" id="t4" class="table-tab" onclick="show(4);">C Gro\
                    up</button><button role="tab" aria-selected="false" aria-controls="all-modules-t\
                    able.tabpanel" tabindex="-1" onkeydown="switchTab(event)" id="t8" class="table-t\
                    ab" onclick="show(8);">Other Modules</button></div>""",
                "Java SE Modules");
    }

    void checkUnnamedModuleGroupOption() {
        checkOutput("index.html", true,
                """
                    <div class="block">The overview summary page header.</div>
                    <div class="overview-summary" id="all-packages-table">
                    <div class="table-tabs" role="tablist" aria-orientation="horizontal"><button rol\
                    e="tab" aria-selected="true" aria-controls="all-packages-table.tabpanel" tabinde\
                    x="0" onkeydown="switchTab(event)" id="t0" class="active-table-tab">All Packages\
                    </button><button role="tab" aria-selected="false" aria-controls="all-packages-ta\
                    ble.tabpanel" tabindex="-1" onkeydown="switchTab(event)" id="t1" class="table-ta\
                    b" onclick="show(1);">Package Group 0</button><button role="tab" aria-selected="\
                    false" aria-controls="all-packages-table.tabpanel" tabindex="-1" onkeydown="swit\
                    chTab(event)" id="t2" class="table-tab" onclick="show(2);">Package Group 1</butt\
                    on></div>
                    <div id="all-packages-table.tabpanel" role="tabpanel">
                    <table class="summary-table" aria-labelledby="t0">""",
                """
                    var data = {"i0":1,"i1":2};
                    var tabs = {65535:["t0","All Packages"],1:["t1","Package Group 0"],2:["t2","Package Group 1"]};
                    var altColor = "alt-color";
                    var rowColor = "row-color";
                    var tableTab = "table-tab";
                    var activeTableTab = "active-table-tab";""");
    }

    void checkGroupOptionPackageOrdering() {
        checkOutput("index.html", true,
                """
                    <div class="table-tabs" role="tablist" aria-orientation="horizontal"><button rol\
                    e="tab" aria-selected="true" aria-controls="all-packages-table.tabpanel" tabinde\
                    x="0" onkeydown="switchTab(event)" id="t0" class="active-table-tab">All Packages\
                    </button><button role="tab" aria-selected="false" aria-controls="all-packages-ta\
                    ble.tabpanel" tabindex="-1" onkeydown="switchTab(event)" id="t1" class="table-ta\
                    b" onclick="show(1);">Z Group</button><button role="tab" aria-selected="false" a\
                    ria-controls="all-packages-table.tabpanel" tabindex="-1" onkeydown="switchTab(ev\
                    ent)" id="t2" class="table-tab" onclick="show(2);">A Group</button></div>""",
                """
                    var tabs = {65535:["t0","All Packages"],1:["t1","Z Group"],2:["t2","A Group"]};""");
    }

    void checkGroupOptionSingleModule() {
        checkOutput("index.html", true,
                "window.location.replace('moduleB/module-summary.html')");
    }

    void checkModuleName(boolean found) {
        checkOutput("test.moduleFullName/module-summary.html", found,
                """
                    <div class="header">
                    <h1 title="Module" class="title">Module&nbsp;test.moduleFullName</h1>
                    </div>""");
        checkOutput("index-all.html", found,
                """
                    <h2 class="title" id="I:T">T</h2>
                    <dl class="index">
                    <dt><a href="test.moduleFullName/module-summary.html">test.moduleFullName</a> - module test.moduleFullName</dt>
                    <dd>
                    <div class="block">This is a test description for the test.moduleFullName.</div>
                    </dd>""");
        checkOutput("test.moduleFullName/module-summary.html", !found,
                """
                    <div class="header">
                    <h1 title="Module" class="title">Module&nbsp;moduleFullName</h1>
                    </div>""");
        checkOutput("index-all.html", !found,
                """
                    <dl class="index">
                    <dt><a href="test.moduleFullName/module-summary.html">moduleFullName</a> - module moduleFullName</dt>
                    <dd>
                    <div class="block">This is a test description for the test.moduleFullName.</div>
                    </dd>
                    </dl>""");
    }

    void checkLinkOffline() {
        checkOutput("moduleB/testpkg3mdlB/package-summary.html", true,
                """
                    <a href="https://docs.oracle.com/javase/9/docs/api/java.base/java/lang/String.ht\
                    ml" title="class or interface in java.lang" class="external-link"><code>Link to \
                    String Class</code></a>""");
        checkOutput("moduleB/testpkg3mdlB/package-summary.html", true,
                """
                    <a href="https://docs.oracle.com/javase/9/docs/api/java.base/java/lang/package-s\
                    ummary.html" class="external-link"><code>Link to java.lang package</code></a>""");
        checkOutput("moduleB/testpkg3mdlB/package-summary.html", true,
                """
                    <a href="https://docs.oracle.com/javase/9/docs/api/java.base/module-summary.html\
                    " class="external-link"><code>Link to java.base module</code></a>""");
    }

    void checkLinkSource(boolean includePrivate) {
        checkOutput("moduleA/module-summary.html", !includePrivate,
                """
                    <table class="summary-table">
                    <caption><span>Exports</span></caption>
                    <thead>
                    <tr>
                    <th class="col-first" scope="col">Package</th>
                    <th class="col-last" scope="col">Description</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr class="alt-color" id="i0">
                    <th class="col-first" scope="row"><a href="testpkgmdlA/package-summary.html">testpkgmdlA</a></th>
                    <td class="col-last">&nbsp;</td>
                    </tr>
                    </tbody>
                    </table>""");
        checkOutput("moduleA/testpkgmdlA/TestClassInModuleA.html", true,
                """
                    <section class="description">
                    <hr>
                    <pre>public class <a href="../../src-html/moduleA/testpkgmdlA/TestClassInModuleA.html#line.25">TestClassInModuleA</a>
                    extends java.lang.Object</pre>
                    </section>""");
        checkOutput("src-html/moduleA/testpkgmdlA/TestClassInModuleA.html", true,
                """
                    <span class="source-line-no">019</span><span id="line.19"> * Please contact Orac\
                    le, 500 Oracle Parkway, Redwood Shores, CA 94065 USA</span>
                    <span class="source-line-no">020</span><span id="line.20"> * or visit www.oracle\
                    .com if you need additional information or have any</span>
                    <span class="source-line-no">021</span><span id="line.21"> * questions.</span>
                    <span class="source-line-no">022</span><span id="line.22"> */</span>
                    <span class="source-line-no">023</span><span id="line.23">package testpkgmdlA;</span>
                    <span class="source-line-no">024</span><span id="line.24"></span>
                    <span class="source-line-no">025</span><span id="line.25">public class TestClassInModuleA {</span>
                    <span class="source-line-no">026</span><span id="line.26">}</span>""");
        if (includePrivate) {
            checkOutput("src-html/moduleA/concealedpkgmdlA/ConcealedClassInModuleA.html", true,
                    """
                        <span class="source-line-no">024</span><span id="line.24">package concealedpkgmdlA;</span>
                        <span class="source-line-no">025</span><span id="line.25"></span>
                        <span class="source-line-no">026</span><span id="line.26">public class ConcealedClassInModuleA {</span>
                        <span class="source-line-no">027</span><span id="line.27">    public void testMethodConcealedClass() { }</span>
                        <span class="source-line-no">028</span><span id="line.28">}</span>""");
        }
    }

    void checkAllPkgsAllClasses(boolean found) {
        checkOutput("allclasses-index.html", true,
                """
                    <div class="table-tabs" role="tablist" aria-orientation="horizontal"><button rol\
                    e="tab" aria-selected="true" aria-controls="all-classes-table.tabpanel" tabindex\
                    ="0" onkeydown="switchTab(event)" id="t0" class="active-table-tab">All Classes</\
                    button><button role="tab" aria-selected="false" aria-controls="all-classes-table\
                    .tabpanel" tabindex="-1" onkeydown="switchTab(event)" id="t2" class="table-tab" \
                    onclick="show(2);">Class Summary</button><button role="tab" aria-selected="false\
                    " aria-controls="all-classes-table.tabpanel" tabindex="-1" onkeydown="switchTab(\
                    event)" id="t6" class="table-tab" onclick="show(32);">Annotation Types Summary</\
                    button></div>
                    """,
                """
                    <thead>
                    <tr>
                    <th class="col-first" scope="col">Class</th>
                    <th class="col-last" scope="col">Description</th>
                    </tr>
                    </thead>
                    """);
        checkOutput("allpackages-index.html", true,
                """
                    <caption><span>Package Summary</span></caption>
                    <thead>
                    <tr>
                    <th class="col-first" scope="col">Package</th>
                    <th class="col-last" scope="col">Description</th>
                    </tr>
                    </thead>""");
        checkOutput("allclasses-index.html", found,
                """
                    <table class="summary-table" aria-labelledby="t0">
                    """);
        checkOutput("allpackages-index.html", found,
                """
                    <div class="packages-summary">
                    <table class="summary-table">
                    """);
        checkOutput("allclasses-index.html", !found,
                """
                    <table summary="Class Summary table, listing classes, and an explanation" aria-labelledby="t0">""");
        checkOutput("allpackages-index.html", !found,
                """
                    <div class="packages-summary">
                    <table summary="Package Summary table, listing packages, and an explanation">""");
        checkOutput("type-search-index.js", true,
                """
                    {"l":"All Classes","u":"allclasses-index.html"}""");
        checkOutput("package-search-index.js", true,
                """
                    {"l":"All Packages","u":"allpackages-index.html"}""");
        checkOutput("index-all.html", true,
                """
                    <br><a href="allclasses-index.html">All&nbsp;Classes</a><span class="vertical-se\
                    parator">|</span><a href="allpackages-index.html">All&nbsp;Packages</a>""");
    }
}
