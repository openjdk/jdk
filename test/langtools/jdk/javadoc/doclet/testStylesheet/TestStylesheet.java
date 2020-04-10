/*
 * Copyright (c) 2005, 2020, Oracle and/or its affiliates. All rights reserved.
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
        TestStylesheet tester = new TestStylesheet();
        tester.runTests(m -> new Object[] { Path.of(m.getName())});
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
        checkOutput("stylesheet.css", true,
                "body {\n"
                + "    background-color:#ffffff;\n"
                + "    color:#353833;\n"
                + "    font-family:'DejaVu Sans', Arial, Helvetica, sans-serif;\n"
                + "    font-size:14px;\n"
                + "    margin:0;\n"
                + "    padding:0;\n"
                + "    height:100%;\n"
                + "    width:100%;\n"
                + "}",
                "iframe {\n"
                + "    margin:0;\n"
                + "    padding:0;\n"
                + "    height:100%;\n"
                + "    width:100%;\n"
                + "    overflow-y:scroll;\n"
                + "    border:none;\n"
                + "}",
                "ul {\n"
                + "    list-style-type:disc;\n"
                + "}",
                ".overview-summary caption, .member-summary caption, .type-summary caption,\n"
                + ".use-summary caption, .constants-summary caption, .deprecated-summary caption,\n"
                + ".requires-summary caption, .packages-summary caption, .provides-summary caption,\n"
                + ".uses-summary caption, .system-properties-summary caption {\n"
                + "    position:relative;\n"
                + "    text-align:left;\n"
                + "    background-repeat:no-repeat;\n"
                + "    color:#253441;\n"
                + "    font-weight:bold;\n"
                + "    clear:none;\n"
                + "    overflow:hidden;\n"
                + "    padding:0px;\n"
                + "    padding-top:10px;\n"
                + "    padding-left:1px;\n"
                + "    margin:0px;\n"
                + "    white-space:pre;\n"
                + "}",
                ".overview-summary caption span, .member-summary caption span, .type-summary caption span,\n"
                + ".use-summary caption span, .constants-summary caption span, .deprecated-summary caption span,\n"
                + ".requires-summary caption span, .packages-summary caption span, .provides-summary caption span,\n"
                + ".uses-summary caption span, .system-properties-summary caption span {\n"
                + "    white-space:nowrap;\n"
                + "    padding-top:5px;\n"
                + "    padding-left:12px;\n"
                + "    padding-right:12px;\n"
                + "    padding-bottom:7px;\n"
                + "    display:inline-block;\n"
                + "    float:left;\n"
                + "    background-color:#F8981D;\n"
                + "    border: none;\n"
                + "    height:16px;\n"
                + "}",
                ".overview-summary [role=tablist] button, .member-summary [role=tablist] button,\n"
                + ".type-summary [role=tablist] button, .packages-summary [role=tablist] button {\n"
                + "   border: none;\n"
                + "   cursor: pointer;\n"
                + "   padding: 5px 12px 7px 12px;\n"
                + "   font-weight: bold;\n"
                + "   margin-right: 3px;\n"
                + "}",
                ".overview-summary [role=tablist] .active-table-tab, .member-summary [role=tablist] .active-table-tab,\n"
                + ".type-summary [role=tablist] .active-table-tab, .packages-summary [role=tablist] .active-table-tab {\n"
                + "   background: #F8981D;\n"
                + "   color: #253441;\n"
                + "}",
                ".overview-summary [role=tablist] .table-tab, .member-summary [role=tablist] .table-tab,\n"
                + ".type-summary [role=tablist] .table-tab, .packages-summary [role=tablist] .table-tab {\n"
                + "   background: #4D7A97;\n"
                + "   color: #FFFFFF;\n"
                + "}",
                // Test the formatting styles for proper content display in use and constant values pages.
                ".overview-summary td.col-first, .overview-summary th.col-first,\n"
                + ".requires-summary td.col-first, .requires-summary th.col-first,\n"
                + ".packages-summary td.col-first, .packages-summary td.col-second, .packages-summary th.col-first, .packages-summary th,\n"
                + ".uses-summary td.col-first, .uses-summary th.col-first,\n"
                + ".provides-summary td.col-first, .provides-summary th.col-first,\n"
                + ".member-summary td.col-first, .member-summary th.col-first,\n"
                + ".member-summary td.col-second, .member-summary th.col-second, .member-summary th.col-constructor-name,\n"
                + ".type-summary td.col-first, .type-summary th.col-first {\n"
                + "    vertical-align:top;\n"
                + "}",
                ".overview-summary td, .member-summary td, .type-summary td,\n"
                + ".use-summary td, .constants-summary td, .deprecated-summary td,\n"
                + ".requires-summary td, .packages-summary td, .provides-summary td,\n"
                + ".uses-summary td, .system-properties-summary td {\n"
                + "    text-align:left;\n"
                + "    padding:0px 0px 12px 10px;\n"
                + "}",
                "@import url('resources/fonts/dejavu.css');",
                ".search-tag-result:target {\n"
                + "    background-color:yellow;\n"
                + "}",
                "a[href]:hover, a[href]:focus {\n"
                + "    text-decoration:none;\n"
                + "    color:#bb7a2a;\n"
                + "}",
                "td.col-first a:link, td.col-first a:visited,\n"
                + "td.col-second a:link, td.col-second a:visited,\n"
                + "th.col-first a:link, th.col-first a:visited,\n"
                + "th.col-second a:link, th.col-second a:visited,\n"
                + "th.col-constructor-name a:link, th.col-constructor-name a:visited,\n"
                + "th.col-deprecated-item-name a:link, th.col-deprecated-item-name a:visited,\n"
                + ".constant-values-container td a:link, .constant-values-container td a:visited,\n"
                + ".all-classes-container td a:link, .all-classes-container td a:visited,\n"
                + ".all-packages-container td a:link, .all-packages-container td a:visited {\n"
                + "    font-weight:bold;\n"
                + "}",
                ".deprecation-block {\n"
                + "    font-size:14px;\n"
                + "    font-family:'DejaVu Serif', Georgia, \"Times New Roman\", Times, serif;\n"
                + "    border-style:solid;\n"
                + "    border-width:thin;\n"
                + "    border-radius:10px;\n"
                + "    padding:10px;\n"
                + "    margin-bottom:10px;\n"
                + "    margin-right:10px;\n"
                + "    display:inline-block;\n"
                + "}",
                "#reset {\n"
                + "    background-color: rgb(255,255,255);\n"
                + "    background-image:url('resources/x.png');\n"
                + "    background-position:center;\n"
                + "    background-repeat:no-repeat;\n"
                + "    background-size:12px;\n"
                + "    border:0 none;\n"
                + "    width:16px;\n"
                + "    height:16px;\n"
                + "    position:relative;\n"
                + "    left:-4px;\n"
                + "    top:-4px;\n"
                + "    font-size:0px;\n"
                + "}",
                ".watermark {\n"
                + "    color:#545454;\n"
                + "}");

        checkOutput("pkg/A.html", true,
                // Test whether a link to the stylesheet file is inserted properly
                // in the class documentation.
                "<link rel=\"stylesheet\" type=\"text/css\" "
                + "href=\"../stylesheet.css\" title=\"Style\">",
                "<div class=\"block\">Test comment for a class which has an <a name=\"named_anchor\">"
                + "anchor_with_name</a> and\n"
                + " an <a id=\"named_anchor1\">anchor_with_id</a>.</div>");

        checkOutput("pkg/package-summary.html", true,
                "<td class=\"col-last\">\n"
                + "<div class=\"block\">Test comment for a class which has an <a name=\"named_anchor\">"
                + "anchor_with_name</a> and\n"
                + " an <a id=\"named_anchor1\">anchor_with_id</a>.</div>\n"
                + "</td>");

        checkOutput("index.html", true,
                "<link rel=\"stylesheet\" type=\"text/css\" href=\"stylesheet.css\" title=\"Style\">");

        checkOutput("stylesheet.css", false,
                "* {\n"
                + "    margin:0;\n"
                + "    padding:0;\n"
                + "}",
                "a:active {\n"
                + "    text-decoration:none;\n"
                + "    color:#4A6782;\n"
                + "}",
                "a[name]:hover {\n"
                + "    text-decoration:none;\n"
                + "    color:#353833;\n"
                + "}",
                "td.col-first a:link, td.col-first a:visited,\n"
                + "td.col-second a:link, td.col-second a:visited,\n"
                + "th.col-first a:link, th.col-first a:visited,\n"
                + "th.col-second a:link, th.col-second a:visited,\n"
                + "th.col-constructor-name a:link, th.col-constructor-name a:visited,\n"
                + "td.col-last a:link, td.col-last a:visited,\n"
                + ".constant-values-container td a:link, .constant-values-container td a:visited {\n"
                + "    font-weight:bold;\n"
                + "}");
    }

    ToolBox tb = new ToolBox();

    @Test
    public void testStyles(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "module mA { exports p; }",
                "package p; public class C {\n"
                + "public C() { }\n"
                + "public C(int i) { }\n"
                + "public int f1;\n"
                + "public int f2;\n"
                + "public int m1() { }\n"
                + "public int m2(int i) { }\n"
                + "}\n",
                "package p; public @interface Anno {\n"
                + "public int value();\n"
                + "}\n"
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
        String stylesheet = readFile("stylesheet.css");
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
                "member-name",
                "modifiers",
                "packages",
                "return-type",
                // and others...
                "help-section",     // part of the help page
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
            c.checkDirectory(outputDir.toPath());
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
