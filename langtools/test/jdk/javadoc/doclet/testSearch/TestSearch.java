/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8141492 8071982 8141636 8147890 8166175 8168965 8176794 8175218
 * @summary Test the search feature of javadoc.
 * @author bpatel
 * @library ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build JavadocTester
 * @run main TestSearch
 */
public class TestSearch extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestSearch tester = new TestSearch();
        tester.runTests();
    }

    @Test
    void test1() {
        javadoc("-d", "out-1", "-sourcepath", "-use", testSrc("UnnamedPkgClass.java"));
        checkExit(Exit.OK);
        checkSearchOutput("UnnamedPkgClass.html", true);
        checkJqueryAndImageFiles(true);
        checkSearchJS();
        checkFiles(false,
                "package-search-index.zip",
                "tag-search-index.zip",
                "package-search-index.js",
                "tag-search-index.js");
        checkFiles(true,
                "member-search-index.zip",
                "type-search-index.zip",
                "member-search-index.js",
                "type-search-index.js");
    }

    @Test
    void test2() {
        javadoc("-d", "out-2", "-Xdoclint:none", "-sourcepath", testSrc,
                "-use", "pkg", "pkg1", "pkg2", "pkg3");
        checkExit(Exit.OK);
        checkInvalidUsageIndexTag();
        checkSearchOutput(true);
        checkSingleIndex(true);
        checkJqueryAndImageFiles(true);
        checkSearchJS();
        checkFiles(true,
                "member-search-index.zip",
                "package-search-index.zip",
                "tag-search-index.zip",
                "type-search-index.zip",
                "member-search-index.js",
                "package-search-index.js",
                "tag-search-index.js",
                "type-search-index.js");
    }

    @Test
    void test2a() {
        javadoc("-d", "out-2a", "-Xdoclint:all", "-sourcepath", testSrc,
                "-use", "pkg", "pkg1", "pkg2", "pkg3");
        checkExit(Exit.ERROR);
        checkDocLintErrors();
        checkSearchOutput(true);
        checkSingleIndex(true);
        checkJqueryAndImageFiles(true);
        checkSearchJS();
        checkFiles(true,
                "member-search-index.zip",
                "package-search-index.zip",
                "tag-search-index.zip",
                "type-search-index.zip",
                "member-search-index.js",
                "package-search-index.js",
                "tag-search-index.js",
                "type-search-index.js");
    }

    @Test
    void test3() {
        javadoc("-d", "out-3", "-noindex", "-Xdoclint:none",
                "-sourcepath", testSrc,
                "-use", "pkg", "pkg1", "pkg2", "pkg3");
        checkExit(Exit.OK);
        checkSearchOutput(false);
        checkJqueryAndImageFiles(false);
        checkFiles(false,
                "member-search-index.zip",
                "package-search-index.zip",
                "tag-search-index.zip",
                "type-search-index.zip",
                "member-search-index.js",
                "package-search-index.js",
                "tag-search-index.js",
                "type-search-index.js",
                "index-all.html");
    }

    @Test
    void test4() {
        javadoc("-d", "out-4", "-html5", "-Xdoclint:none",
                "-sourcepath", testSrc,
                "-use", "pkg", "pkg1", "pkg2", "pkg3");
        checkExit(Exit.OK);
        checkSearchOutput(true);
        checkSingleIndex(true);
        checkJqueryAndImageFiles(true);
        checkSearchJS();
        checkFiles(true,
                "member-search-index.zip",
                "package-search-index.zip",
                "tag-search-index.zip",
                "type-search-index.zip",
                "member-search-index.js",
                "package-search-index.js",
                "tag-search-index.js",
                "type-search-index.js");
    }

    @Test
    void test5() {
        javadoc("-d", "out-5", "-noindex", "-html5", "-Xdoclint:none",
                "-sourcepath", testSrc,
                "-use", "pkg", "pkg1", "pkg2", "pkg3");
        checkExit(Exit.OK);
        checkSearchOutput(false);
        checkJqueryAndImageFiles(false);
        checkFiles(false,
                "member-search-index.zip",
                "package-search-index.zip",
                "tag-search-index.zip",
                "type-search-index.zip",
                "member-search-index.js",
                "package-search-index.js",
                "tag-search-index.js",
                "type-search-index.js",
                "index-all.html");
    }

    @Test
    void test6() {
        javadoc("-d", "out-6", "-nocomment", "-Xdoclint:none",
                "-sourcepath", testSrc,
                "-use", "pkg", "pkg1", "pkg2", "pkg3");
        checkExit(Exit.OK);
        checkSearchOutput(true);
        checkIndexNoComment();
        checkJqueryAndImageFiles(true);
        checkSearchJS();
        checkFiles(true,
                "member-search-index.zip",
                "package-search-index.zip",
                "tag-search-index.zip",
                "type-search-index.zip",
                "member-search-index.js",
                "package-search-index.js",
                "tag-search-index.js",
                "type-search-index.js");
    }

    @Test
    void test7() {
        javadoc("-d", "out-7", "-nodeprecated", "-Xdoclint:none",
                "-sourcepath", testSrc,
                "-use", "pkg", "pkg1", "pkg2", "pkg3");
        checkExit(Exit.OK);
        checkSearchOutput(true);
        checkIndexNoDeprecated();
        checkJqueryAndImageFiles(true);
        checkSearchJS();
        checkFiles(true,
                "member-search-index.zip",
                "package-search-index.zip",
                "tag-search-index.zip",
                "type-search-index.zip",
                "member-search-index.js",
                "package-search-index.js",
                "tag-search-index.js",
                "type-search-index.js");
    }

    @Test
    void test8() {
        javadoc("-d", "out-8", "-splitindex", "-Xdoclint:none", "-sourcepath", testSrc,
                "-use", "pkg", "pkg1", "pkg2", "pkg3");
        checkExit(Exit.OK);
        checkInvalidUsageIndexTag();
        checkSearchOutput(true);
        checkSplitIndex();
        checkJqueryAndImageFiles(true);
        checkSearchJS();
        checkFiles(true,
                "member-search-index.zip",
                "package-search-index.zip",
                "tag-search-index.zip",
                "type-search-index.zip",
                "member-search-index.js",
                "package-search-index.js",
                "tag-search-index.js",
                "type-search-index.js");
    }

    @Test
    void test9() {
        javadoc("-d", "out-9", "-sourcepath", testSrc, "-javafx", "-package",
                "-use", "pkgfx", "pkg3");
        checkExit(Exit.OK);
        checkSearchOutput(true);
        checkJavaFXOutput();
        checkJqueryAndImageFiles(true);
        checkSearchJS();
        checkFiles(false,
                "tag-search-index.zip",
                "tag-search-index.js");
        checkFiles(true,
                "member-search-index.zip",
                "package-search-index.zip",
                "type-search-index.zip",
                "member-search-index.js",
                "package-search-index.js",
                "type-search-index.js");
    }

    void checkDocLintErrors() {
        checkOutput(Output.OUT, true,
                "A sample method. Testing search tag for {@index \"unclosed quote}.",
                "Another test class. Testing empty {@index }.",
                "Constant field. Testing no text in index tag {@index}.",
                "A test field. Testing only white-spaces in index tag text {@index       }.");
    }

    void checkSearchOutput(boolean expectedOutput) {
        checkSearchOutput("overview-summary.html", expectedOutput);
    }

    void checkSearchOutput(String fileName, boolean expectedOutput) {
        // Test for search related markup
        checkOutput(fileName, expectedOutput,
                "<link rel=\"stylesheet\" type=\"text/css\" href=\"jquery/jquery-ui.css\" title=\"Style\">\n",
                "<script type=\"text/javascript\" src=\"jquery/jszip/dist/jszip.min.js\"></script>\n",
                "<script type=\"text/javascript\" src=\"jquery/jszip-utils/dist/jszip-utils.min.js\"></script>\n",
                "<!--[if IE]>\n",
                "<script type=\"text/javascript\" src=\"jquery/jszip-utils/dist/jszip-utils-ie.min.js\"></script>\n",
                "<![endif]-->\n",
                "<script type=\"text/javascript\" src=\"jquery/jquery-1.10.2.js\"></script>\n",
                "<script type=\"text/javascript\" src=\"jquery/jquery-ui.js\"></script>",
                "var pathtoroot = \"./\";loadScripts(document, 'script');",
                "<ul class=\"navListSearch\">\n",
                "<li><span>SEARCH:&nbsp;</span>\n",
                "<input type=\"text\" id=\"search\" value=\" \" disabled=\"disabled\">\n",
                "<input type=\"reset\" id=\"reset\" value=\" \" disabled=\"disabled\">\n");
        checkOutput(fileName, true,
                "<div class=\"fixedNav\">");
    }

    void checkSingleIndex(boolean expectedOutput) {
        // Test for search tags markup in index file.
        checkOutput("index-all.html", expectedOutput,
                "<dt><span class=\"searchTagLink\"><a href=\"pkg/package-summary.html#phrasewithspaces\">"
                + "phrase with spaces</a></span> - Search tag in pkg</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"pkg/package-summary.html#pkg\">"
                + "pkg</a></span> - Search tag in pkg</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"pkg/package-summary.html#pkg2.5\">"
                + "pkg2.5</a></span> - Search tag in pkg</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"pkg/package-summary.html#r\">"
                + "r</a></span> - Search tag in pkg</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"pkg1/RegClass.html#searchphrase\">"
                + "search phrase</a></span> - Search tag in pkg1.RegClass</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"pkg1/RegClass.html#SearchWordWithDescription\">"
                + "SearchWordWithDescription</a></span> - Search tag in pkg1.RegClass.CONSTANT_FIELD_1</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"pkg2/TestAnnotationType.html#searchphrasewithdescdeprecated\">"
                + "search phrase with desc deprecated</a></span> - Search tag in pkg2.TestAnnotationType</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"pkg2/TestClass.html#SearchTagDeprecatedClass\">"
                + "SearchTagDeprecatedClass</a></span> - Search tag in pkg2.TestClass</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"pkg2/TestEnum.html#searchphrasedeprecated\">"
                + "search phrase deprecated</a></span> - Search tag in pkg2.TestEnum.ONE</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"pkg2/TestEnum.html#searchphrasedeprecated\">"
                + "search phrase deprecated</a></span> - Search tag in pkg2.TestEnum.ONE</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"pkg2/TestError.html#SearchTagDeprecatedMethod\">"
                + "SearchTagDeprecatedMethod</a></span> - Search tag in pkg2.TestError</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"pkg2/TestError.html#SearchTagDeprecatedMethod\">"
                + "SearchTagDeprecatedMethod</a></span> - Search tag in pkg2.TestError</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"pkg/package-summary.html#SingleWord\">"
                + "SingleWord</a></span> - Search tag in pkg</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"pkg/AnotherClass.ModalExclusionType.html"
                + "#nested%7B@indexnested_tag_test%7D\">nested {@index nested_tag_test}</a></span> - "
                + "Search tag in pkg.AnotherClass.ModalExclusionType.NO_EXCLUDE</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"pkg/AnotherClass.ModalExclusionType.html"
                + "#html-span-see-/span-\">html &lt;span&gt; see &lt;/span&gt;</a></span> - Search "
                + "tag in pkg.AnotherClass.ModalExclusionType.APPLICATION_EXCLUDE</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"pkg/AnotherClass.html#quoted\">quoted</a>"
                + "</span> - Search tag in pkg.AnotherClass.CONSTANT1</dt>",
                "<dt><span class=\"memberNameLink\"><a href=\"pkg2/TestEnum.html#ONE\">ONE</a></span> - "
                + "pkg2.<a href=\"pkg2/TestEnum.html\" title=\"enum in pkg2\">TestEnum</a></dt>",
                "<dt><span class=\"memberNameLink\"><a href=\"pkg2/TestEnum.html#THREE\">THREE</a></span> - "
                + "pkg2.<a href=\"pkg2/TestEnum.html\" title=\"enum in pkg2\">TestEnum</a></dt>",
                "<dt><span class=\"memberNameLink\"><a href=\"pkg2/TestEnum.html#TWO\">TWO</a></span> - "
                + "pkg2.<a href=\"pkg2/TestEnum.html\" title=\"enum in pkg2\">TestEnum</a></dt>");
        checkOutput("index-all.html", true,
                "<div class=\"block\"><span class=\"deprecationComment\">class_test1 passes. Search tag"
                + " <a id=\"SearchTagDeprecatedClass\" class=\"searchTagResult\">SearchTagDeprecatedClass</a></span></div>",
                "<div class=\"block\"><span class=\"deprecationComment\">error_test3 passes. Search tag for\n"
                + " method <a id=\"SearchTagDeprecatedMethod\" class=\"searchTagResult\">SearchTagDeprecatedMethod</a></span></div>");
    }

    void checkSplitIndex() {
        // Test for search tags markup in split index file.
        checkOutput("index-files/index-13.html", true,
                "<dt><span class=\"searchTagLink\"><a href=\"../pkg1/RegClass.html#searchphrase\">"
                + "search phrase</a></span> - Search tag in pkg1.RegClass</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"../pkg1/RegClass.html#SearchWordWithDescription\">"
                + "SearchWordWithDescription</a></span> - Search tag in pkg1.RegClass.CONSTANT_FIELD_1</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"../pkg2/TestAnnotationType.html#searchphrasewithdescdeprecated\">"
                + "search phrase with desc deprecated</a></span> - Search tag in pkg2.TestAnnotationType</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"../pkg2/TestClass.html#SearchTagDeprecatedClass\">"
                + "SearchTagDeprecatedClass</a></span> - Search tag in pkg2.TestClass</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"../pkg2/TestEnum.html#searchphrasedeprecated\">"
                + "search phrase deprecated</a></span> - Search tag in pkg2.TestEnum.ONE</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"../pkg2/TestEnum.html#searchphrasedeprecated\">"
                + "search phrase deprecated</a></span> - Search tag in pkg2.TestEnum.ONE</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"../pkg2/TestError.html#SearchTagDeprecatedMethod\">"
                + "SearchTagDeprecatedMethod</a></span> - Search tag in pkg2.TestError</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"../pkg2/TestError.html#SearchTagDeprecatedMethod\">"
                + "SearchTagDeprecatedMethod</a></span> - Search tag in pkg2.TestError</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"../pkg/package-summary.html#SingleWord\">"
                + "SingleWord</a></span> - Search tag in pkg</dt>");
        checkOutput("index-files/index-10.html", true,
                "<dt><span class=\"searchTagLink\"><a href=\"../pkg/package-summary.html#phrasewithspaces\">"
                + "phrase with spaces</a></span> - Search tag in pkg</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"../pkg/package-summary.html#pkg\">"
                + "pkg</a></span> - Search tag in pkg</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"../pkg/package-summary.html#pkg2.5\">"
                + "pkg2.5</a></span> - Search tag in pkg</dt>");
        checkOutput("index-files/index-12.html", true,
                "<dt><span class=\"searchTagLink\"><a href=\"../pkg/package-summary.html#r\">"
                + "r</a></span> - Search tag in pkg</dt>");
        checkOutput("index-files/index-8.html", true,
                "<dt><span class=\"searchTagLink\"><a href=\"../pkg/AnotherClass.ModalExclusionType.html"
                + "#nested%7B@indexnested_tag_test%7D\">nested {@index nested_tag_test}</a></span> - "
                + "Search tag in pkg.AnotherClass.ModalExclusionType.NO_EXCLUDE</dt>");
        checkOutput("index-files/index-5.html", true,
                "<dt><span class=\"searchTagLink\"><a href=\"../pkg/AnotherClass.ModalExclusionType.html"
                + "#html-span-see-/span-\">html &lt;span&gt; see &lt;/span&gt;</a></span> - Search "
                + "tag in pkg.AnotherClass.ModalExclusionType.APPLICATION_EXCLUDE</dt>");
        checkOutput("index-files/index-11.html", true,
                "<dt><span class=\"searchTagLink\"><a href=\"../pkg/AnotherClass.html#quoted\">quoted</a>"
                + "</span> - Search tag in pkg.AnotherClass.CONSTANT1</dt>");
        checkOutput("index-files/index-9.html", true,
                "<dt><span class=\"memberNameLink\"><a href=\"../pkg2/TestEnum.html#ONE\">ONE</a>"
                + "</span> - pkg2.<a href=\"../pkg2/TestEnum.html\" title=\"enum in pkg2\">TestEnum</a></dt>");
        checkOutput("index-files/index-14.html", true,
                "<dt><span class=\"memberNameLink\"><a href=\"../pkg2/TestEnum.html#THREE\">THREE</a></span> - "
                + "pkg2.<a href=\"../pkg2/TestEnum.html\" title=\"enum in pkg2\">TestEnum</a></dt>",
                "<dt><span class=\"memberNameLink\"><a href=\"../pkg2/TestEnum.html#TWO\">TWO</a></span> - "
                + "pkg2.<a href=\"../pkg2/TestEnum.html\" title=\"enum in pkg2\">TestEnum</a></dt>");
    }

    void checkIndexNoComment() {
        // Test for search tags markup in index file when javadoc is executed with -nocomment.
        checkOutput("index-all.html", false,
                "<dt><span class=\"searchTagLink\"><a href=\"pkg/package-summary.html#phrasewithspaces\">"
                + "phrase with spaces</a></span> - Search tag in pkg</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"pkg/package-summary.html#pkg\">"
                + "pkg</a></span> - Search tag in pkg</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"pkg/package-summary.html#pkg2.5\">"
                + "pkg2.5</a></span> - Search tag in pkg</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"pkg/package-summary.html#r\">"
                + "r</a></span> - Search tag in pkg</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"pkg1/RegClass.html#searchphrase\">"
                + "search phrase</a></span> - Search tag in pkg1.RegClass</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"pkg1/RegClass.html#SearchWordWithDescription\">"
                + "SearchWordWithDescription</a></span> - Search tag in pkg1.RegClass.CONSTANT_FIELD_1</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"pkg2/TestAnnotationType.html#searchphrasewithdescdeprecated\">"
                + "search phrase with desc deprecated</a></span> - Search tag in pkg2.TestAnnotationType</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"pkg2/TestClass.html#SearchTagDeprecatedClass\">"
                + "SearchTagDeprecatedClass</a></span> - Search tag in pkg2.TestClass</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"pkg/package-summary.html#SingleWord\">"
                + "SingleWord</a></span> - Search tag in pkg</dt>",
                "<div class=\"block\"><span class=\"deprecationComment\">class_test1 passes. Search tag"
                + " <a id=\"SearchTagDeprecatedClass\">SearchTagDeprecatedClass</a></span></div>",
                "<div class=\"block\"><span class=\"deprecationComment\">error_test3 passes. Search tag for\n"
                + " method <a id=\"SearchTagDeprecatedMethod\">SearchTagDeprecatedMethod</a></span></div>");
        checkOutput("index-all.html", true,
                "<dt><span class=\"searchTagLink\"><a href=\"pkg2/TestEnum.html#searchphrasedeprecated\">"
                + "search phrase deprecated</a></span> - Search tag in pkg2.TestEnum.ONE</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"pkg2/TestError.html#SearchTagDeprecatedMethod\">"
                + "SearchTagDeprecatedMethod</a></span> - Search tag in pkg2.TestError</dt>");
    }

    void checkIndexNoDeprecated() {
        // Test for search tags markup in index file when javadoc is executed using -nodeprecated.
        checkOutput("index-all.html", true,
                "<dt><span class=\"searchTagLink\"><a href=\"pkg/package-summary.html#phrasewithspaces\">"
                + "phrase with spaces</a></span> - Search tag in pkg</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"pkg1/RegClass.html#searchphrase\">"
                + "search phrase</a></span> - Search tag in pkg1.RegClass</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"pkg1/RegClass.html#SearchWordWithDescription\">"
                + "SearchWordWithDescription</a></span> - Search tag in pkg1.RegClass.CONSTANT_FIELD_1</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"pkg/package-summary.html#SingleWord\">"
                + "SingleWord</a></span> - Search tag in pkg</dt>");
        checkOutput("index-all.html", false,
                "<dt><span class=\"searchTagLink\"><a href=\"pkg2/TestAnnotationType.html#searchphrasewithdescdeprecated\">"
                + "search phrase with desc deprecated</a></span> - Search tag in pkg2.TestAnnotationType</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"pkg2/TestClass.html#SearchTagDeprecatedClass\">"
                + "SearchTagDeprecatedClass</a></span> - Search tag in pkg2.TestClass</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"pkg2/TestEnum.html#searchphrasedeprecated\">"
                + "search phrase deprecated</a></span> - Search tag in pkg2.TestEnum.ONE</dt>",
                "<dt><span class=\"searchTagLink\"><a href=\"pkg2/TestError.html#SearchTagDeprecatedMethod\">"
                + "SearchTagDeprecatedMethod</a></span> - Search tag in pkg2.TestError</dt>",
                "<div class=\"block\"><span class=\"deprecationComment\">class_test1 passes. Search tag"
                + " <a id=\"SearchTagDeprecatedClass\">SearchTagDeprecatedClass</a></span></div>",
                "<div class=\"block\"><span class=\"deprecationComment\">error_test3 passes. Search tag for\n"
                + " method <a id=\"SearchTagDeprecatedMethod\">SearchTagDeprecatedMethod</a></span></div>");
    }

    void checkJavaFXOutput() {
        checkOutput("index-all.html", false, "test treat as private");
    }

    void checkInvalidUsageIndexTag() {
        checkOutput(Output.OUT, true,
                "AnotherClass.java:29: warning - invalid usage of tag {@index",
                "AnotherClass.java:41: warning - invalid usage of tag {@index",
                "AnotherClass.java:36: warning - invalid usage of tag {@index",
                "AnotherClass.java:70: warning - invalid usage of tag {@index");
    }

    void checkJqueryAndImageFiles(boolean expectedOutput) {
        checkFiles(expectedOutput,
                "search.js",
                "jquery/jquery-1.10.2.js",
                "jquery/jquery-ui.js",
                "jquery/jquery-ui.css",
                "jquery/jquery-ui.min.js",
                "jquery/jquery-ui.min.css",
                "jquery/jquery-ui.structure.min.css",
                "jquery/jquery-ui.structure.css",
                "jquery/external/jquery/jquery.js",
                "jquery/jszip/dist/jszip.js",
                "jquery/jszip/dist/jszip.min.js",
                "jquery/jszip-utils/dist/jszip-utils.js",
                "jquery/jszip-utils/dist/jszip-utils.min.js",
                "jquery/jszip-utils/dist/jszip-utils-ie.js",
                "jquery/jszip-utils/dist/jszip-utils-ie.min.js",
                "jquery/images/ui-bg_flat_0_aaaaaa_40x100.png",
                "jquery/images/ui-icons_454545_256x240.png",
                "jquery/images/ui-bg_glass_95_fef1ec_1x400.png",
                "jquery/images/ui-bg_glass_75_dadada_1x400.png",
                "jquery/images/ui-bg_highlight-soft_75_cccccc_1x100.png",
                "jquery/images/ui-icons_888888_256x240.png",
                "jquery/images/ui-icons_2e83ff_256x240.png",
                "jquery/images/ui-bg_glass_65_ffffff_1x400.png",
                "jquery/images/ui-icons_cd0a0a_256x240.png",
                "jquery/images/ui-bg_glass_55_fbf9ee_1x400.png",
                "jquery/images/ui-icons_222222_256x240.png",
                "jquery/images/ui-bg_glass_75_e6e6e6_1x400.png",
                "jquery/images/ui-bg_flat_75_ffffff_40x100.png",
                "resources/x.png",
                "resources/glass.png");
    }

    void checkSearchJS() {
        checkOutput("search.js", true,
                "camelCaseRegexp = ($.ui.autocomplete.escapeRegex(request.term)).split(/(?=[A-Z])/).join(\"([a-z0-9_$]*?)\");",
                "var camelCaseMatcher = new RegExp(\"^\" + camelCaseRegexp);",
                "camelCaseMatcher.test(item.l)",
                "var secondaryresult = new Array();",
                "function nestedName(e) {",
                "function sortAndConcatResults(a1, a2) {",
                "if (exactMatcher.test(item.l)) {\n"
                + "                        presult.unshift(item);");
    }
}
