/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4494033 7028815 7052425 8007338 8023608 8008164 8016549 8072461 8154261 8162363 8160196 8151743 8177417
 *           8175218 8176452 8181215 8182263 8183511 8169819 8183037 8185369 8182765 8196201 8184205 8223378 8241544
 *           8253117 8263528 8289334 8292594
 * @summary  Run tests on doclet stylesheet.
 * @library  /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @run main TestStylesheet
 */

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import javadoc.tester.HtmlChecker;
import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestStylesheet extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestStylesheet();
        tester.runTests();
    }

    @Test
    public void test(Path base) {
        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.ERROR);

        checkOutput(Output.OUT, true,
                "attribute not supported in HTML5: name");

        // TODO: most of this test seems a bit silly, since javadoc is simply
        // copying in the stylesheet from the source directory
        checkOutput("resource-files/stylesheet.css", true,
                """
                    body {
                        background-color:var(--body-background-color);
                        color:var(--body-text-color);
                        font-family:var(--body-font-family);
                        font-size:var(--body-font-size);
                        margin:0;
                        padding:0;
                        height:100%;
                        width:100%;
                    }""",
                """
                    ul {
                        list-style-type:disc;
                    }""",
                """
                    .caption {
                        position:relative;
                        text-align:left;
                        background-repeat:no-repeat;
                        color:var(--selected-text-color);
                        clear:none;
                        overflow:hidden;
                        padding: 10px 0 0 1px;
                        margin:0;
                    }""",
                """
                    .caption span {
                        font-weight:bold;
                        white-space:nowrap;
                        padding:5px 12px 7px 12px;
                        display:inline-block;
                        float:left;
                        background-color:var(--selected-background-color);
                        border: none;
                        height:16px;
                    }""",
                """
                    div.table-tabs > button {
                        border: none;
                        cursor: pointer;
                        padding: 5px 12px 7px 12px;
                        font-weight: bold;
                        margin-right: 8px;
                    }
                    div.table-tabs > .active-table-tab {
                        background: var(--selected-background-color);
                        color: var(--selected-text-color);
                    }
                    div.table-tabs > button.table-tab {
                        background: var(--navbar-background-color);
                        color: var(--navbar-text-color);
                    }""",
                // Test the formatting styles for proper content display in use and constant values pages.
                """
                    .col-first, .col-second, .col-constructor-name {
                        vertical-align:top;
                        overflow: auto;
                    }""",
                """
                    .summary-table > div, .details-table > div {
                        text-align:left;
                        padding: 8px 3px 3px 7px;
                        overflow: auto hidden;
                        scrollbar-width: thin;
                    }""",
                "@import url('fonts/dejavu.css');",
                """
                    .search-tag-result:target {
                        background-color:var(--search-tag-highlight-color);
                    }""",
                """
                    a[href]:hover, a[href]:active {
                        text-decoration:none;
                        color:var(--link-color-active);
                    }""",
                """
                    .col-first a:link, .col-first a:visited,
                    .col-second a:link, .col-second a:visited,
                    .col-first a:link, .col-first a:visited,
                    .col-second a:link, .col-second a:visited,
                    .col-constructor-name a:link, .col-constructor-name a:visited,
                    .col-summary-item-name a:link, .col-summary-item-name a:visited {
                        font-weight:bold;
                    }""",
                """
                    .deprecation-block, .preview-block, .restricted-block {
                        font-size:1em;
                        font-family:var(--block-font-family);
                        border-style:solid;
                        border-width:thin;
                        border-radius:10px;
                        padding:10px;
                        margin-bottom:10px;
                        margin-right:10px;
                        display:inline-block;
                    }""",
                """
                    input#reset-search, input.reset-filter {
                        background-color: transparent;
                        background-image:url('x.png');
                        background-repeat:no-repeat;
                        background-size:contain;
                        border:0;
                        border-radius:0;
                        width:12px;
                        height:12px;
                        font-size:0;
                        display:none;
                    }""",
                """
                    ::placeholder {
                        color:var(--search-input-placeholder-color);
                        opacity: 1;
                    }""");

        checkOutput("pkg/A.html", true,
                // Test whether a link to the stylesheet file is inserted properly
                // in the class documentation.
                """
                    <link rel="stylesheet" type="text/css" href="../resource-files/stylesheet.css" title="Style">""",
                """
                    <div class="block">Test comment for a class which has an <a name="named_anchor">anchor_with_name</a> and
                     an <a id="named_anchor1">anchor_with_id</a>.</div>""");

        checkOutput("pkg/package-summary.html", true,
                """
                    <div class="col-last even-row-color class-summary class-summary-tab2">
                    <div class="block">Test comment for a class which has an <a name="named_anchor">anchor_with_name</a> and
                     an <a id="named_anchor1">anchor_with_id</a>.</div>
                    </div>""");

        checkOutput("index.html", true,
                """
                    <link rel="stylesheet" type="text/css" href="resource-files/stylesheet.css" title="Style">""");

        checkOutput("resource-files/stylesheet.css", false,
                """
                    * {
                        margin:0;
                        padding:0;
                    }""",
                """
                    a:active {
                        text-decoration:none;
                        color:#4A6782;
                    }""",
                """
                    a[name]:hover {
                        text-decoration:none;
                        color:#353833;
                    }""",
                """
                    td.col-first a:link, td.col-first a:visited,
                    td.col-second a:link, td.col-second a:visited,
                    th.col-first a:link, th.col-first a:visited,
                    th.col-second a:link, th.col-second a:visited,
                    th.col-constructor-name a:link, th.col-constructor-name a:visited,
                    td.col-last a:link, td.col-last a:visited,
                    .constant-values-container td a:link, .constant-values-container td a:visited {
                        font-weight:bold;
                    }""");
    }

    ToolBox tb = new ToolBox();

    @Test
    public void testStyles(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "module mA { exports p; }",
                """
                    package p; public class C {
                    public C() { }
                    public C(int i) { }
                    public int f1;
                    public int f2;
                    public int m1() { }
                    public int m2(int i) { }
                    }
                    """,
                """
                    package p; public @interface Anno {
                    public int value();
                    }
                    """
        );

        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "--module", "mA");
        checkExit(Exit.OK);
        checkStyles(addExtraCSSClassNamesTo(readStylesheet()));
    }

    Set<String> readStylesheet() {
        // scan for class selectors, skipping '{' ... '}'
        Set<String> styles = new TreeSet<>();
        String stylesheet = readFile("resource-files/stylesheet.css");
        for (int i = 0; i < stylesheet.length(); i++) {
            char ch = stylesheet.charAt(i);
            switch (ch) {
                case '.':
                    i++;
                    int start = i;
                    while (i < stylesheet.length()) {
                        ch = stylesheet.charAt(i);
                        if (!(Character.isLetterOrDigit(ch) || ch == '-')) {
                            break;
                        }
                        i++;
                    }
                    styles.add(stylesheet.substring(start, i));
                    break;

                case '{':
                    i++;
                    while (i < stylesheet.length()) {
                        ch = stylesheet.charAt(i);
                        if (ch == '}') {
                            break;
                        }
                        i++;
                    }
                    break;

                case '@':
                    i++;
                    while (i < stylesheet.length()) {
                        ch = stylesheet.charAt(i);
                        if (ch == '{') {
                            break;
                        }
                        i++;
                    }
                    break;
            }
        }
        out.println("found styles: " + styles);
        return styles;
    }

    Set<String> addExtraCSSClassNamesTo(Set<String> styles) throws Exception {
        // The following names are used in the generated HTML,
        // but have no corresponding definitions in the stylesheet file.
        // They are mostly optional, in the "use if you want to" category.
        // They are included here so that we do not get errors when these
        // names are used in the generated HTML.
        List<String> extra = List.of(
                // entries for <body> elements
                "all-classes-index-page",
                "all-packages-index-page",
                "constants-summary-page",
                "deprecated-list-page",
                "help-page",
                "index-redirect-page",
                "package-declaration-page",
                "package-tree-page",
                "single-index-page",
                "tree-page",
                // the following names are matched by [class$='...'] in the stylesheet
                "constructor-details",
                "constructor-summary",
                "field-details",
                "field-summary",
                "member-details",
                "method-details",
                "method-summary",
                // the following provide the ability to optionally override components of the
                // memberSignature structure
                "name",
                "modifiers",
                "packages",
                "return-type",
                // and others...
                "hierarchy",        // for the hierarchy on a tree page
                "index"             // on the index page
        );
        Set<String> all = new TreeSet<>(styles);
        for (String e : extra) {
            if (styles.contains(e)) {
                throw new Exception("extra CSS class name found in style sheet: " + e);
            }
            all.add(e);
        }
        return all;
    }

    /**
     * Checks that all the CSS names found in {@code class} attributes in HTML files in the
     * output directory are present in a given set of styles.
     *
     * @param styles the styles
     */
    void checkStyles(Set<String> styles) {
        checking("Check CSS class names");
        CSSClassChecker c = new CSSClassChecker(out, this::readFile, styles);
        try {
            c.checkDirectory(outputDir);
            c.report();
            int errors = c.getErrorCount();
            if (errors == 0) {
                passed("No CSS class name errors found");
            } else {
                failed(errors + " errors found when checking CSS class names");
            }
        } catch (IOException e) {
            failed("exception thrown when reading files: " + e);
        }

    }

    class CSSClassChecker extends HtmlChecker {
        Set<String> styles;
        int errors;

        protected CSSClassChecker(PrintStream out,
                                  Function<Path, String> fileReader,
                                  Set<String> styles) {
            super(out, fileReader);
            this.styles = styles;
        }

        protected int getErrorCount() {
            return errors;
        }

        @Override
        protected void report() {
            if (getErrorCount() == 0) {
                out.println("All CSS class names found");
            } else {
                out.println(getErrorCount() + " CSS class names not found");
            }

        }

        @Override
        public void startElement(String name, Map<String,String> attrs, boolean selfClosing) {
            String style = attrs.get("class");
            if (style != null && !styles.contains(style)) {
                error(currFile, getLineNumber(), "CSS class name not found: " + style);
            }
        }
    }
}
