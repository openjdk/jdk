/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8141492 8071982 8141636 8147890 8166175 8168965 8176794 8175218 8147881
 *      8181622 8182263 8074407 8187521 8198522 8182765 8199278 8196201 8196202
 *      8184205 8214468 8222548 8223378 8234746 8241219
 * @summary Test the search feature of javadoc.
 * @library ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build javadoc.tester.*
 * @run main TestSearch
 */

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javadoc.tester.JavadocTester;

public class TestSearch extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestSearch tester = new TestSearch();
        tester.runTests();
    }

    @Test
    public void test1() {
        javadoc("-d", "out-1",
                "-sourcepath",
                "-use",
                testSrc("UnnamedPkgClass.java"));
        checkExit(Exit.OK);
        checkSearchOutput("UnnamedPkgClass.html", true, true);
        checkJqueryAndImageFiles(true);
        checkSearchJS();
        checkFiles(false,
                "tag-search-index.js");
        checkFiles(true,
                "package-search-index.js",
                "member-search-index.js",
                "type-search-index.js");
    }

    @Test
    public void test2() {
        javadoc("-d", "out-2",
                "-Xdoclint:none",
                "-sourcepath", testSrc,
                "-use",
                "pkg", "pkg1", "pkg2", "pkg3");
        checkExit(Exit.OK);
        checkInvalidUsageIndexTag();
        checkSearchOutput(true);
        checkSingleIndex(true, true);
        checkSingleIndexSearchTagDuplication();
        checkJqueryAndImageFiles(true);
        checkSearchJS();
        checkAllPkgsAllClasses();
        checkFiles(true,
                "member-search-index.js",
                "package-search-index.js",
                "tag-search-index.js",
                "type-search-index.js");
    }

    @Test
    public void test2a() {
        javadoc("-d", "out-2a",
                "-Xdoclint:all",
                "-sourcepath", testSrc,
                "-use",
                "pkg", "pkg1", "pkg2", "pkg3");
        checkExit(Exit.ERROR);
        checkDocLintErrors();
        checkSearchOutput(true);
        checkSingleIndex(true, true);
        checkSingleIndexSearchTagDuplication();
        checkJqueryAndImageFiles(true);
        checkSearchJS();
        checkFiles(true,
                "member-search-index.js",
                "package-search-index.js",
                "tag-search-index.js",
                "type-search-index.js");
    }

    @Test
    public void test3() {
        javadoc("-d", "out-3",
                "-noindex",
                "-Xdoclint:none",
                "-sourcepath", testSrc,
                "-use",
                "pkg", "pkg1", "pkg2", "pkg3");
        checkExit(Exit.OK);
        checkSearchOutput(false);
        checkJqueryAndImageFiles(false);
        checkFiles(false,
                "member-search-index.js",
                "package-search-index.js",
                "tag-search-index.js",
                "type-search-index.js",
                "index-all.html",
                "allpackages-index.html",
                "allclasses-index.html");
    }

    @Test
    public void test4() {
        javadoc("-d", "out-4",
                "-html5",
                "-Xdoclint:none",
                "-sourcepath", testSrc,
                "-use",
                "pkg", "pkg1", "pkg2", "pkg3");
        checkExit(Exit.OK);
        checkSearchOutput(true);
        checkSingleIndex(true, true);
        checkSingleIndexSearchTagDuplication();
        checkJqueryAndImageFiles(true);
        checkSearchJS();
        checkFiles(true,
                "member-search-index.js",
                "package-search-index.js",
                "tag-search-index.js",
                "type-search-index.js");
    }

    @Test
    public void test5() {
        javadoc("-d", "out-5",
                "-html5",
                "-noindex",
                "-Xdoclint:none",
                "-sourcepath", testSrc,
                "-use",
                "pkg", "pkg1", "pkg2", "pkg3");
        checkExit(Exit.OK);
        checkSearchOutput(false);
        checkJqueryAndImageFiles(false);
        checkFiles(false,
                "member-search-index.js",
                "package-search-index.js",
                "tag-search-index.js",
                "type-search-index.js",
                "index-all.html");
    }

    @Test
    public void test6() {
        javadoc("-d", "out-6",
                "-nocomment",
                "-Xdoclint:none",
                "-sourcepath", testSrc,
                "-use",
                "pkg", "pkg1", "pkg2", "pkg3");
        checkExit(Exit.OK);
        checkSearchOutput(true);
        checkIndexNoComment();
        checkJqueryAndImageFiles(true);
        checkSearchJS();
        checkFiles(true,
                "member-search-index.js",
                "package-search-index.js",
                "tag-search-index.js",
                "type-search-index.js");
    }

    @Test
    public void test7() {
        javadoc("-d", "out-7",
                "-nodeprecated",
                "-Xdoclint:none",
                "-sourcepath", testSrc,
                "-use",
                "pkg", "pkg1", "pkg2", "pkg3");

        checkExit(Exit.OK);
        checkSearchOutput(true);
        checkIndexNoDeprecated();
        checkJqueryAndImageFiles(true);
        checkSearchJS();
        checkFiles(true,
                "member-search-index.js",
                "package-search-index.js",
                "tag-search-index.js",
                "type-search-index.js");
    }

    @Test
    public void test8() {
        javadoc("-d", "out-8",
                "-splitindex",
                "-Xdoclint:none",
                "-sourcepath", testSrc,
                "-use",
                "pkg", "pkg1", "pkg2", "pkg3");
        checkExit(Exit.OK);
        checkInvalidUsageIndexTag();
        checkSearchOutput(true);
        checkSplitIndex();
        checkSplitIndexSearchTagDuplication();
        checkJqueryAndImageFiles(true);
        checkSearchJS();
        checkFiles(true,
                "member-search-index.js",
                "package-search-index.js",
                "tag-search-index.js",
                "type-search-index.js");
    }

    @Test
    public void test9() {
        javadoc("-d", "out-9",
                "-sourcepath", testSrc,
                "-javafx",
                "--disable-javafx-strict-checks",
                "-package",
                "-use",
                "pkgfx", "pkg3");
        checkExit(Exit.OK);
        checkSearchOutput(true);
        checkJavaFXOutput();
        checkJqueryAndImageFiles(true);
        checkSearchJS();
        checkFiles(false,
                "tag-search-index.js");
        checkFiles(true,
                "member-search-index.js",
                "package-search-index.js",
                "type-search-index.js");
    }

    @Test
    public void testURLEncoding() {
        javadoc("-d", "out-encode-html5",
                "-Xdoclint:none",
                "-sourcepath", testSrc,
                "-use",
                "pkg", "pkg1", "pkg2", "pkg3");
        checkExit(Exit.OK);
        checkSearchJS();
        checkSearchIndex(true);
    }

    @Test
    public void testDefaultJapaneseLocale() {
        Locale prev = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("ja-JP"));
        try {
            javadoc("-d", "out-jp-default",
                    "-Xdoclint:none",
                    "-sourcepath", testSrc,
                    "-use",
                    "pkg", "pkg1", "pkg2", "pkg3");
            checkExit(Exit.OK);
            checkOutput(Output.OUT, true,
                    "\u30d1\u30c3\u30b1\u30fc\u30b8pkg\u306e\u30bd\u30fc\u30b9\u30fb\u30d5\u30a1" +
                            "\u30a4\u30eb\u3092\u8aad\u307f\u8fbc\u3093\u3067\u3044\u307e\u3059...\n",
                    "\u30d1\u30c3\u30b1\u30fc\u30b8pkg1\u306e\u30bd\u30fc\u30b9\u30fb\u30d5\u30a1" +
                            "\u30a4\u30eb\u3092\u8aad\u307f\u8fbc\u3093\u3067\u3044\u307e\u3059...\n");
            checkSearchJS();
            checkSearchIndex(true);
        } finally {
            Locale.setDefault(prev);
        }
    }

    @Test
    public void testJapaneseLocaleOption() {
        javadoc("-locale", "ja_JP",
                "-d", "out-jp-option",
                "-Xdoclint:none",
                "-sourcepath", testSrc,
                "-use",
                "pkg", "pkg1", "pkg2", "pkg3");
        checkExit(Exit.OK);
        checkOutput(Output.OUT, true,
                """
                    Loading source files for package pkg...
                    """,
                """
                    Loading source files for package pkg1...
                    """);
        checkOutput("index.html", true,
                "<span>\u30d1\u30c3\u30b1\u30fc\u30b8</span>");
        checkSearchJS();
        checkSearchIndex(true);
    }

    @Test
    public void testDefaultChineseLocale() {
        Locale prev = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("zh-CN"));
        try {
            javadoc("-d", "out-cn-default",
                    "-Xdoclint:none",
                    "-sourcepath", testSrc,
                    "-use",
                    "pkg", "pkg1", "pkg2", "pkg3");
            checkExit(Exit.OK);
            checkOutput(Output.OUT, true,
                    "\u6b63\u5728\u52a0\u8f7d\u7a0b\u5e8f\u5305pkg\u7684\u6e90\u6587\u4ef6...\n",
                    "\u6b63\u5728\u52a0\u8f7d\u7a0b\u5e8f\u5305pkg1\u7684\u6e90\u6587\u4ef6...\n",
                    "\u6b63\u5728\u52a0\u8f7d\u7a0b\u5e8f\u5305pkg2\u7684\u6e90\u6587\u4ef6...\n",
                    "\u6b63\u5728\u52a0\u8f7d\u7a0b\u5e8f\u5305pkg3\u7684\u6e90\u6587\u4ef6...\n");
            checkSearchJS();
            checkSearchIndex(true);
        } finally {
            Locale.setDefault(prev);
        }
    }

    @Test
    public void testChineseLocaleOption() {
        javadoc("-locale", "zh_CN",
                "-d", "out-cn-option",
                "-Xdoclint:none",
                "-sourcepath", testSrc,
                "-use",
                "pkg", "pkg1", "pkg2", "pkg3");
        checkExit(Exit.OK);
        checkOutput(Output.OUT, true,
                """
                    Loading source files for package pkg...
                    """,
                """
                    Loading source files for package pkg1...
                    """,
                """
                    Loading source files for package pkg2...
                    """,
                """
                    Loading source files for package pkg3...
                    """);
        checkOutput("index.html", true,
                "<span>\u7a0b\u5e8f\u5305</span>");
        checkSearchJS();
        checkSearchIndex(true);
    }

    void checkDocLintErrors() {
        checkOutput(Output.OUT, true,
                """
                    A sample method. Testing search tag for {@index "unclosed quote}.""",
                "Another test class. Testing empty {@index }.",
                "Constant field. Testing no text in index tag {@index}.",
                "A test field. Testing only white-spaces in index tag text {@index       }.");
    }

    void checkSearchOutput(boolean expectedOutput) {
        checkSearchOutput("index.html", expectedOutput, true);
    }

    void checkSearchIndex(boolean expectedOutput) {
        checkOutput("member-search-index.js", expectedOutput,
                """
                    {"p":"pkg","c":"AnotherClass","l":"AnotherClass()","u":"%3Cinit%3E()"}""",
                """
                    {"p":"pkg1","c":"RegClass","l":"RegClass()","u":"%3Cinit%3E()"}""",
                """
                    {"p":"pkg2","c":"TestError","l":"TestError()","u":"%3Cinit%3E()"}""",
                """
                    {"p":"pkg","c":"AnotherClass","l":"method(byte[], int, String)","u":"method(byte[],int,java.lang.String)"}""");
        checkOutput("member-search-index.js", !expectedOutput,
                """
                    {"p":"pkg","c":"AnotherClass","l":"method(RegClass)","u":"method-pkg1.RegClass-"}""",
                """
                    {"p":"pkg2","c":"TestClass","l":"TestClass()","u":"TestClass--"}""",
                """
                    {"p":"pkg","c":"TestError","l":"TestError()","u":"TestError--"}""",
                """
                    {"p":"pkg","c":"AnotherClass","l":"method(byte[], int, String)","u":"method-byte:A-int-java.lang.String-"}""");
    }

    void checkSearchOutput(boolean expectedOutput, boolean moduleDirectoriesVar) {
        checkSearchOutput("index.html", expectedOutput, moduleDirectoriesVar);
    }

    void checkSearchOutput(String fileName, boolean expectedOutput, boolean moduleDirectoriesVar) {
        // Test for search related markup
        checkOutput(fileName, expectedOutput,
                """
                    <link rel="stylesheet" type="text/css" href="script-dir/jquery-ui.min.css" title="Style">
                    """,
                """
                    <script type="text/javascript" src="script-dir/jquery-3.5.1.min.js"></script>
                    """,
                """
                    <script type="text/javascript" src="script-dir/jquery-ui.min.js"></script>""",
                """
                    var pathtoroot = "./";
                    loadScripts(document, 'script');""",
                "<div class=\"nav-list-search\">",
                """
                    <label for="search">SEARCH:</label>
                    <input type="text" id="search" value="search" disabled="disabled">
                    <input type="reset" id="reset" value="reset" disabled="disabled">
                    """);
        checkOutput(fileName, true,
                "<div class=\"flex-box\">");
    }

    void checkSingleIndex(boolean expectedOutput, boolean html5) {
        String html_span_see_span = html5 ? "html%3Cspan%3Esee%3C/span%3E" : "html-span-see-/span-";

        // Test for search tags markup in index file.
        checkOutput("index-all.html", expectedOutput,
                """
                    <dt><span class="search-tag-link"><a href="pkg/package-summary.html#phrasewithsp\
                    aces">phrase with spaces</a></span> - Search tag in package pkg</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="pkg/package-summary.html#pkg">pkg</a></span> - Search tag in package pkg</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="pkg/package-summary.html#pkg2.5">pkg2\
                    .5</a></span> - Search tag in package pkg</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="pkg/package-summary.html#r">r</a></span> - Search tag in package pkg</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="pkg1/RegClass.html#searchphrase">sear\
                    ch phrase</a></span> - Search tag in class pkg1.RegClass</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="pkg1/RegClass.html#SearchWordWithDesc\
                    ription">SearchWordWithDescription</a></span> - Search tag in pkg1.RegClass.CONS\
                    TANT_FIELD_1</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="pkg2/TestAnnotationType.html#searchph\
                    rasewithdescdeprecated">search phrase with desc deprecated</a></span> - Search t\
                    ag in annotation type pkg2.TestAnnotationType</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="pkg2/TestClass.html#SearchTagDeprecat\
                    edClass">SearchTagDeprecatedClass</a></span> - Search tag in class pkg2.TestClas\
                    s</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="pkg2/TestEnum.html#searchphrasedeprec\
                    ated">search phrase deprecated</a></span> - Search tag in pkg2.TestEnum.ONE</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="pkg2/TestEnum.html#searchphrasedeprec\
                    ated">search phrase deprecated</a></span> - Search tag in pkg2.TestEnum.ONE</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="pkg2/TestError.html#SearchTagDeprecat\
                    edMethod">SearchTagDeprecatedMethod</a></span> - Search tag in pkg2.TestError.Te\
                    stError()</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="pkg2/TestError.html#SearchTagDeprecat\
                    edMethod">SearchTagDeprecatedMethod</a></span> - Search tag in pkg2.TestError.Te\
                    stError()</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="pkg/package-summary.html#SingleWord">\
                    SingleWord</a></span> - Search tag in package pkg</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="pkg/AnotherClass.ModalExclusionType.h\
                    tml#nested%7B@indexnested_tag_test%7D">nested {@index nested_tag_test}</a></span\
                    > - Search tag in pkg.AnotherClass.ModalExclusionType.NO_EXCLUDE</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="pkg/AnotherClass.ModalExclusionType.html#""" + html_span_see_span + """
                    ">html &lt;span&gt; see &lt;/span&gt;</a></span> - Search tag in pkg.AnotherClas\
                    s.ModalExclusionType.APPLICATION_EXCLUDE</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="pkg/AnotherClass.html#quoted">quoted<\
                    /a></span> - Search tag in pkg.AnotherClass.CONSTANT1</dt>""",
                """
                    <dt><span class="member-name-link"><a href="pkg2/TestEnum.html#ONE">ONE</a></spa\
                    n> - pkg2.<a href="pkg2/TestEnum.html" title="enum in pkg2">TestEnum</a></dt>""",
                """
                    <dt><span class="member-name-link"><a href="pkg2/TestEnum.html#THREE">THREE</a><\
                    /span> - pkg2.<a href="pkg2/TestEnum.html" title="enum in pkg2">TestEnum</a></dt\
                    >""",
                """
                    <dt><span class="member-name-link"><a href="pkg2/TestEnum.html#TWO">TWO</a></spa\
                    n> - pkg2.<a href="pkg2/TestEnum.html" title="enum in pkg2">TestEnum</a></dt>""");
        checkOutput("index-all.html", true,
                """
                    <div class="deprecation-comment">class_test1 passes. Search tag <span id="Search\
                    TagDeprecatedClass" class="search-tag-result">SearchTagDeprecatedClass</span></d\
                    iv>""",
                """
                    <div class="deprecation-comment">error_test3 passes. Search tag for
                     method <span id="SearchTagDeprecatedMethod" class="search-tag-result">SearchTagDeprecatedMethod</span></div>""");
    }

    void checkSplitIndex() {
        // Test for search tags markup in split index file.
        checkOutput("index-files/index-13.html", true,
                """
                    <dt><span class="search-tag-link"><a href="../pkg1/RegClass.html#searchphrase">s\
                    earch phrase</a></span> - Search tag in class pkg1.RegClass</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="../pkg1/RegClass.html#SearchWordWithD\
                    escription">SearchWordWithDescription</a></span> - Search tag in pkg1.RegClass.C\
                    ONSTANT_FIELD_1</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="../pkg2/TestAnnotationType.html#searc\
                    hphrasewithdescdeprecated">search phrase with desc deprecated</a></span> - Searc\
                    h tag in annotation type pkg2.TestAnnotationType</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="../pkg2/TestClass.html#SearchTagDepre\
                    catedClass">SearchTagDeprecatedClass</a></span> - Search tag in class pkg2.TestC\
                    lass</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="../pkg2/TestEnum.html#searchphrasedep\
                    recated">search phrase deprecated</a></span> - Search tag in pkg2.TestEnum.ONE</\
                    dt>""",
                """
                    <dt><span class="search-tag-link"><a href="../pkg2/TestEnum.html#searchphrasedep\
                    recated">search phrase deprecated</a></span> - Search tag in pkg2.TestEnum.ONE</\
                    dt>""",
                """
                    <dt><span class="search-tag-link"><a href="../pkg2/TestError.html#SearchTagDepre\
                    catedMethod">SearchTagDeprecatedMethod</a></span> - Search tag in pkg2.TestError\
                    .TestError()</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="../pkg2/TestError.html#SearchTagDepre\
                    catedMethod">SearchTagDeprecatedMethod</a></span> - Search tag in pkg2.TestError\
                    .TestError()</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="../pkg/package-summary.html#SingleWor\
                    d">SingleWord</a></span> - Search tag in package pkg</dt>""",
                """
                    <br><a href="../allclasses-index.html">All&nbsp;Classes</a><span class="vertical\
                    -separator">|</span><a href="../allpackages-index.html">All&nbsp;Packages</a>""");
        checkOutput("index-files/index-10.html", true,
                """
                    <dt><span class="search-tag-link"><a href="../pkg/package-summary.html#phrasewit\
                    hspaces">phrase with spaces</a></span> - Search tag in package pkg</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="../pkg/package-summary.html#pkg">pkg<\
                    /a></span> - Search tag in package pkg</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="../pkg/package-summary.html#pkg2.5">p\
                    kg2.5</a></span> - Search tag in package pkg</dt>""");
        checkOutput("index-files/index-12.html", true,
                """
                    <dt><span class="search-tag-link"><a href="../pkg/package-summary.html#r">r</a></span> - Search tag in package pkg</dt>""");
        checkOutput("index-files/index-8.html", true,
                """
                    <dt><span class="search-tag-link"><a href="../pkg/AnotherClass.ModalExclusionTyp\
                    e.html#nested%7B@indexnested_tag_test%7D">nested {@index nested_tag_test}</a></s\
                    pan> - Search tag in pkg.AnotherClass.ModalExclusionType.NO_EXCLUDE</dt>""");
        checkOutput("index-files/index-5.html", true,
                """
                    <dt><span class="search-tag-link"><a href="../pkg/AnotherClass.ModalExclusionTyp\
                    e.html#html%3Cspan%3Esee%3C/span%3E">html &lt;span&gt; see &lt;/span&gt;</a></sp\
                    an> - Search tag in pkg.AnotherClass.ModalExclusionType.APPLICATION_EXCLUDE</dt>""");
        checkOutput("index-files/index-11.html", true,
                """
                    <dt><span class="search-tag-link"><a href="../pkg/AnotherClass.html#quoted">quot\
                    ed</a></span> - Search tag in pkg.AnotherClass.CONSTANT1</dt>""");
        checkOutput("index-files/index-9.html", true,
                """
                    <dt><span class="member-name-link"><a href="../pkg2/TestEnum.html#ONE">ONE</a></\
                    span> - pkg2.<a href="../pkg2/TestEnum.html" title="enum in pkg2">TestEnum</a></\
                    dt>""");
        checkOutput("index-files/index-14.html", true,
                """
                    <dt><span class="member-name-link"><a href="../pkg2/TestEnum.html#THREE">THREE</\
                    a></span> - pkg2.<a href="../pkg2/TestEnum.html" title="enum in pkg2">TestEnum</\
                    a></dt>""",
                """
                    <dt><span class="member-name-link"><a href="../pkg2/TestEnum.html#TWO">TWO</a></\
                    span> - pkg2.<a href="../pkg2/TestEnum.html" title="enum in pkg2">TestEnum</a></\
                    dt>""");
    }

    void checkIndexNoComment() {
        // Test for search tags markup in index file when javadoc is executed with -nocomment.
        checkOutput("index-all.html", false,
                """
                    <dt><span class="search-tag-link"><a href="pkg/package-summary.html#phrasewithsp\
                    aces">phrase with spaces</a></span> - Search tag in package pkg</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="pkg/package-summary.html#pkg">pkg</a></span> - Search tag in package pkg</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="pkg/package-summary.html#pkg2.5">pkg2\
                    .5</a></span> - Search tag in package pkg</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="pkg/package-summary.html#r">r</a></span> - Search tag in package pkg</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="pkg1/RegClass.html#searchphrase">sear\
                    ch phrase</a></span> - Search tag in class pkg1.RegClass</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="pkg1/RegClass.html#SearchWordWithDesc\
                    ription">SearchWordWithDescription</a></span> - Search tag in pkg1.RegClass.CONS\
                    TANT_FIELD_1</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="pkg2/TestAnnotationType.html#searchph\
                    rasewithdescdeprecated">search phrase with desc deprecated</a></span> - Search t\
                    ag in annotation type pkg2.TestAnnotationType</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="pkg2/TestClass.html#SearchTagDeprecat\
                    edClass">SearchTagDeprecatedClass</a></span> - Search tag in class pkg2.TestClas\
                    s</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="pkg/package-summary.html#SingleWord">\
                    SingleWord</a></span> - Search tag in package pkg</dt>""",
                """
                    <div class="deprecation-comment">class_test1 passes. Search tag <span id="Search\
                    TagDeprecatedClass">SearchTagDeprecatedClass</span></div>""",
                """
                    <div class="deprecation-comment">error_test3 passes. Search tag for
                     method <span id="SearchTagDeprecatedMethod">SearchTagDeprecatedMethod</span></div>""");
        checkOutput("index-all.html", true,
                """
                    <dt><span class="search-tag-link"><a href="pkg2/TestEnum.html#searchphrasedeprec\
                    ated">search phrase deprecated</a></span> - Search tag in pkg2.TestEnum.ONE</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="pkg2/TestError.html#SearchTagDeprecat\
                    edMethod">SearchTagDeprecatedMethod</a></span> - Search tag in pkg2.TestError.Te\
                    stError()</dt>""");
    }

    void checkIndexNoDeprecated() {
        // Test for search tags markup in index file when javadoc is executed using -nodeprecated.
        checkOutput("index-all.html", true,
                """
                    <dt><span class="search-tag-link"><a href="pkg/package-summary.html#phrasewithsp\
                    aces">phrase with spaces</a></span> - Search tag in package pkg</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="pkg1/RegClass.html#searchphrase">sear\
                    ch phrase</a></span> - Search tag in class pkg1.RegClass</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="pkg1/RegClass.html#SearchWordWithDesc\
                    ription">SearchWordWithDescription</a></span> - Search tag in pkg1.RegClass.CONS\
                    TANT_FIELD_1</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="pkg/package-summary.html#SingleWord">\
                    SingleWord</a></span> - Search tag in package pkg</dt>""");
        checkOutput("index-all.html", false,
                """
                    <dt><span class="search-tag-link"><a href="pkg2/TestAnnotationType.html#searchph\
                    rasewithdescdeprecated">search phrase with desc deprecated</a></span> - Search t\
                    ag in annotation type pkg2.TestAnnotationType</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="pkg2/TestClass.html#SearchTagDeprecat\
                    edClass">SearchTagDeprecatedClass</a></span> - Search tag in class pkg2.TestClas\
                    s</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="pkg2/TestEnum.html#searchphrasedeprec\
                    ated">search phrase deprecated</a></span> - Search tag in pkg2.TestEnum.ONE</dt>""",
                """
                    <dt><span class="search-tag-link"><a href="pkg2/TestError.html#SearchTagDeprecat\
                    edMethod">SearchTagDeprecatedMethod</a></span> - Search tag in pkg2.TestError.Te\
                    stError()</dt>""",
                """
                    <div class="deprecation-comment">class_test1 passes. Search tag <span id="Search\
                    TagDeprecatedClass">SearchTagDeprecatedClass</span></div>""",
                """
                    <div class="deprecation-comment">error_test3 passes. Search tag for
                     method <span id="SearchTagDeprecatedMethod">SearchTagDeprecatedMethod</span></div>""");
    }

    void checkJavaFXOutput() {
        checkOutput("index-all.html", false, "test treat as private");
    }

    void checkInvalidUsageIndexTag() {
        checkOutput(Output.OUT, true,
                "AnotherClass.java:29: warning - invalid usage of tag {@index",
                "AnotherClass.java:39: warning - invalid usage of tag {@index",
                "AnotherClass.java:34: warning - invalid usage of tag {@index",
                "AnotherClass.java:68: warning - invalid usage of tag {@index");
    }

    void checkJqueryAndImageFiles(boolean expectedOutput) {
        checkFiles(expectedOutput,
                "search.js",
                "jquery-ui.overrides.css",
                "script-dir/jquery-3.5.1.min.js",
                "script-dir/jquery-ui.min.js",
                "script-dir/jquery-ui.min.css",
                "script-dir/jquery-ui.structure.min.css",
                "script-dir/images/ui-bg_glass_65_dadada_1x400.png",
                "script-dir/images/ui-icons_454545_256x240.png",
                "script-dir/images/ui-bg_glass_95_fef1ec_1x400.png",
                "script-dir/images/ui-bg_glass_75_dadada_1x400.png",
                "script-dir/images/ui-bg_highlight-soft_75_cccccc_1x100.png",
                "script-dir/images/ui-icons_888888_256x240.png",
                "script-dir/images/ui-icons_2e83ff_256x240.png",
                "script-dir/images/ui-icons_cd0a0a_256x240.png",
                "script-dir/images/ui-bg_glass_55_fbf9ee_1x400.png",
                "script-dir/images/ui-icons_222222_256x240.png",
                "script-dir/images/ui-bg_glass_75_e6e6e6_1x400.png",
                "resources/x.png",
                "resources/glass.png");
    }

    void checkSearchJS() {
        checkOutput("search.js", true,
                "function concatResults(a1, a2) {",
                """
                    $("#search").on('click keydown paste', function() {
                            if ($(this).val() == watermark) {
                                $(this).val('').removeClass('watermark');
                            }
                        });""",
                """
                    function getURLPrefix(ui) {
                        var urlPrefix="";
                        var slash = "/";
                        if (ui.item.category === catModules) {
                            return ui.item.l + slash;
                        } else if (ui.item.category === catPackages && ui.item.m) {
                            return ui.item.m + slash;
                        } else if ((ui.item.category === catTypes && ui.item.p) || ui.item.category === catMembers) {
                            $.each(packageSearchIndex, function(index, item) {
                                if (item.m && ui.item.p == item.l) {
                                    urlPrefix = item.m + slash;
                                }
                            });
                            return urlPrefix;
                        } else {
                            return urlPrefix;
                        }
                        return urlPrefix;
                    }""",
                "url += ui.item.l;");

        checkCssClasses("search.js", "stylesheet.css");
    }

    void checkCssClasses(String jsFile, String cssFile) {
        // Check that all CSS class names mentioned in the JavaScript file
        // are also defined as class selectors somewhere in the stylesheet file.
        String js = readOutputFile(jsFile);
        Set<String> cssClasses = new TreeSet<>();
        addMatches(js, Pattern.compile("class=\\\\*\"([^\\\\\"]+)\\\\*\""), cssClasses);
        addMatches(js, Pattern.compile("attr\\(\"class\", \"([^\"]+)\"\\)"), cssClasses);
        // verify that the regex did find use of CSS class names
        checking("Checking CSS classes found");
        if (cssClasses.isEmpty()) {
            failed("no CSS classes found");
        } else {
            passed(cssClasses.size() + " found: " + cssClasses);
        }
        checkOutput(cssFile, true, cssClasses.toArray(new String[0]));
    }

    void addMatches(String js, Pattern p, Set<String> cssClasses) {
        Matcher m = p.matcher(js);
        while (m.find()) {
            cssClasses.add("." + m.group(1));
        }
    }

    void checkSingleIndexSearchTagDuplication() {
        // Test for search tags duplication in index file.
        checkOutput("index-all.html", true,
                """
                    <dt><span class="search-tag-link"><a href="pkg2/TestError.html#SearchTagDeprecat\
                    edMethod">SearchTagDeprecatedMethod</a></span> - Search tag in pkg2.TestError.Te\
                    stError()</dt>
                    <dd>with description</dd>""");
        checkOutput("index-all.html", false,
                """
                    <dt><span class="search-tag-link"><a href="pkg2/TestError.html#SearchTagDeprecat\
                    edMethod">SearchTagDeprecatedMethod</a></span> - Search tag in pkg2.TestError.Te\
                    stError()</dt>
                    <dd>with description</dd>
                    <dt><span class="search-tag-link"><a href="pkg2/TestError.html#SearchTagDeprecat\
                    edMethod">SearchTagDeprecatedMethod</a></span> - Search tag in pkg2.TestError.Te\
                    stError()</dt>
                    <dd>with description</dd>""");
    }

    void checkSplitIndexSearchTagDuplication() {
        // Test for search tags duplication in index file.
        checkOutput("index-files/index-13.html", true,
                """
                    <dt><span class="search-tag-link"><a href="../pkg2/TestError.html#SearchTagDepre\
                    catedMethod">SearchTagDeprecatedMethod</a></span> - Search tag in pkg2.TestError\
                    .TestError()</dt>
                    <dd>with description</dd>""");
        checkOutput("index-files/index-13.html", false,
                """
                    <dt><span class="search-tag-link"><a href="../pkg2/TestError.html#SearchTagDepre\
                    catedMethod">SearchTagDeprecatedMethod</a></span> - Search tag in pkg2.TestError\
                    .TestError()</dt>
                    <dd>with description</dd>
                    <dt><span class="search-tag-link"><a href="../pkg2/TestError.html#SearchTagDepre\
                    catedMethod">SearchTagDeprecatedMethod</a></span> - Search tag in pkg2.TestError\
                    .TestError()</dt>
                    <dd>with description</dd>""");
    }

    void checkAllPkgsAllClasses() {
        checkOutput("allclasses-index.html", true,
                """
                    <div class="type-summary" id="all-classes-table">
                    <div class="table-tabs" role="tablist" aria-orientation="horizontal"><button rol\
                    e="tab" aria-selected="true" aria-controls="all-classes-table.tabpanel" tabindex\
                    ="0" onkeydown="switchTab(event)" id="t0" class="active-table-tab">All Classes</\
                    button><button role="tab" aria-selected="false" aria-controls="all-classes-table\
                    .tabpanel" tabindex="-1" onkeydown="switchTab(event)" id="t1" class="table-tab" \
                    onclick="show(1);">Interface Summary</button><button role="tab" aria-selected="f\
                    alse" aria-controls="all-classes-table.tabpanel" tabindex="-1" onkeydown="switch\
                    Tab(event)" id="t2" class="table-tab" onclick="show(2);">Class Summary</button><\
                    button role="tab" aria-selected="false" aria-controls="all-classes-table.tabpane\
                    l" tabindex="-1" onkeydown="switchTab(event)" id="t3" class="table-tab" onclick=\
                    "show(4);">Enum Summary</button><button role="tab" aria-selected="false" aria-co\
                    ntrols="all-classes-table.tabpanel" tabindex="-1" onkeydown="switchTab(event)" i\
                    d="t4" class="table-tab" onclick="show(8);">Exception Summary</button><button ro\
                    le="tab" aria-selected="false" aria-controls="all-classes-table.tabpanel" tabind\
                    ex="-1" onkeydown="switchTab(event)" id="t5" class="table-tab" onclick="show(16)\
                    ;">Error Summary</button><button role="tab" aria-selected="false" aria-controls=\
                    "all-classes-table.tabpanel" tabindex="-1" onkeydown="switchTab(event)" id="t6" \
                    class="table-tab" onclick="show(32);">Annotation Types Summary</button></div>
                    <div id="all-classes-table.tabpanel" role="tabpanel">
                    <table class="summary-table" aria-labelledby="t0">
                    <thead>
                    <tr>
                    <th class="col-first" scope="col">Class</th>
                    <th class="col-last" scope="col">Description</th>
                    </tr>""",
                """
                    var data = {"i0":32,"i1":2,"i2":4,"i3":2,"i4":2,"i5":1,"i6":2,"i7":32,"i8":2,"i9\
                    ":4,"i10":16,"i11":16,"i12":8,"i13":8,"i14":1,"i15":2};""");
        checkOutput("allpackages-index.html", true,
                """
                    <div class="packages-summary">
                    <table class="summary-table">
                    <caption><span>Package Summary</span></caption>
                    <thead>
                    <tr>
                    <th class="col-first" scope="col">Package</th>
                    <th class="col-last" scope="col">Description</th>
                    </tr>
                    """);
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
