/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4749567 8071982
 * @summary  Test the output for -header, -footer, -nooverview, -nodeprecatedlist, -nonavbar, -notree, -stylesheetfile options.
 * @author   Bhavesh Patel
 * @library  ../lib
 * @modules jdk.javadoc
 * @build    JavadocTester
 * @run main TestOptions
 */

import java.io.File;

public class TestOptions extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestOptions tester = new TestOptions();
        tester.runTests();
    }

    @Test
    void testHeaderFooter() {
        javadoc("-d", "out-1",
                "-header", "Test header",
                "-footer", "Test footer",
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.OK);

        checkOutput("pkg/package-summary.html", true,
                "<div class=\"aboutLanguage\">Test header</div>",
                "<div class=\"aboutLanguage\">Test footer</div>");
    }

    @Test
    void testNoOverview() {
        javadoc("-d", "out-4",
                "-nooverview",
                "-sourcepath", testSrc,
                "pkg", "deprecated");

        checkExit(Exit.OK);

        checkFiles(false, "overview-summary.html");
    }

    @Test
    void testNoDeprecatedList() {
        javadoc("-d", "out-5",
                "-nodeprecatedlist",
                "-sourcepath", testSrc,
                "deprecated");
        checkExit(Exit.OK);

        checkFiles(false, "deprecated-list.html");
    }

    @Test
    void testNoNavbar() {
        javadoc("-d", "out-6",
                "-nonavbar",
                "-bottom", "Bottom text",
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.OK);

        checkOutput("pkg/Foo.html", false, "navbar");
        checkOutput("pkg/Foo.html", true, "Bottom text");
    }

    @Test
    void testNoTree() {
        javadoc("-d", "out-7",
                "-notree",
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.OK);

        checkFiles(false, "overview-tree.html");
        checkFiles(false, "pkg/package-tree.html");
        checkOutput("pkg/Foo.html", false, "<li><a href=\"package-tree.html\">Tree</a></li>");
    }

    @Test
    void testStylesheetFile() {
        javadoc("-d", "out-8",
                "-stylesheetfile", new File(testSrc, "custom-stylesheet.css").getAbsolutePath(),
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.OK);

        checkOutput("custom-stylesheet.css", true, "Custom javadoc style sheet");
        checkOutput("pkg/Foo.html", true, "<link rel=\"stylesheet\" type=\"text/css\" "
                + "href=\"../custom-stylesheet.css\" title=\"Style\">");
    }

    @Test
    void testLinkSource() {
        javadoc("-d", "out-9",
                "-linksource",
                "-javafx",
                "-sourcepath", testSrc,
                "-package",
                "linksource");
        checkExit(Exit.OK);

        checkOutput("linksource/AnnotationTypeField.html", true,
                "<pre>@Documented\npublic @interface <a href="
                + "\"../src-html/linksource/AnnotationTypeField.html#line.31\">"
                + "AnnotationTypeField</a></pre>",
                "<h4>DEFAULT_NAME</h4>\n<pre>static final&nbsp;java.lang.String&nbsp;"
                + "<a href=\"../src-html/linksource/AnnotationTypeField.html#line.32\">"
                + "DEFAULT_NAME</a></pre>",
                "<h4>name</h4>\n<pre>java.lang.String&nbsp;<a href="
                + "\"../src-html/linksource/AnnotationTypeField.html#line.34\">name</a></pre>");

        checkOutput("src-html/linksource/AnnotationTypeField.html", true,
                "<title>Source code</title>",
                "<span class=\"sourceLineNo\">031</span><a name=\"line.31\">"
                + "@Documented public @interface AnnotationTypeField {</a>");

        checkOutput("linksource/Properties.html", true,
                "<pre>public class <a href=\"../src-html/linksource/Properties.html#line.29\">"
                + "Properties</a>",
                "<pre>public&nbsp;java.lang.Object <a href="
                + "\"../src-html/linksource/Properties.html#line.31\">someProperty</a></pre>",
                "<pre>public&nbsp;java.lang.Object&nbsp;<a href="
                + "\"../src-html/linksource/Properties.html#line.31\">someProperty</a>()</pre>");

        checkOutput("src-html/linksource/Properties.html", true,
                "<title>Source code</title>",
                "<span class=\"sourceLineNo\">031</span><a name=\"line.31\">    "
                + "public Object someProperty() {</a>");

        checkOutput("linksource/SomeClass.html", true,
                "<pre>public class <a href=\"../src-html/linksource/SomeClass.html#line.29\">"
                + "SomeClass</a>\nextends java.lang.Object</pre>",
                "<pre>public&nbsp;int <a href=\"../src-html/linksource/SomeClass.html#line.31\">"
                + "field</a></pre>",
                "<pre>public&nbsp;<a href=\"../src-html/linksource/SomeClass.html#line.33\">"
                + "SomeClass</a>()</pre>",
                "<pre>public&nbsp;int&nbsp;<a href=\"../src-html/linksource/SomeClass.html#line.36\">"
                + "method</a>()</pre>");

        checkOutput("src-html/linksource/SomeClass.html", true,
                "<title>Source code</title>",
                "<span class=\"sourceLineNo\">029</span><a name=\"line.29\">"
                + "public class SomeClass {</a>",
                "<span class=\"sourceLineNo\">031</span><a name=\"line.31\">    "
                + "public int field;</a>",
                "<span class=\"sourceLineNo\">033</span><a name=\"line.33\">    "
                + "public SomeClass() {</a>",
                "<span class=\"sourceLineNo\">036</span><a name=\"line.36\">    "
                + "public int method() {</a>");

        checkOutput("linksource/SomeEnum.html", true,
                "<pre>public static final&nbsp;<a href=\"../linksource/SomeEnum.html\" "
                + "title=\"enum in linksource\">SomeEnum</a> <a href="
                + "\"../src-html/linksource/SomeEnum.html#line.29\">VALUE1</a></pre>",
                "<pre>public static final&nbsp;<a href=\"../linksource/SomeEnum.html\" "
                + "title=\"enum in linksource\">SomeEnum</a> <a href="
                + "\"../src-html/linksource/SomeEnum.html#line.30\">VALUE2</a></pre>");

        checkOutput("src-html/linksource/SomeEnum.html", true,
                "<span class=\"sourceLineNo\">029</span><a name=\"line.29\">    VALUE1,</a>",
                "<span class=\"sourceLineNo\">030</span><a name=\"line.30\">    VALUE2</a>");
    }

    @Test
    void testNoQualifier() {
        javadoc("-d", "out-10",
                "-noqualifier", "pkg",
                "-sourcepath", testSrc,
                "pkg", "deprecated");
        checkExit(Exit.OK);

        checkOutput("pkg/Foo.html", true,
                "<li>Foo</li>");
        checkOutput("deprecated/Foo.html", true,
                "<li>deprecated.Foo</li>");

        javadoc("-d", "out-10a",
                "-noqualifier", "all",
                "-sourcepath", testSrc,
                "pkg", "deprecated");
        checkExit(Exit.OK);

        checkOutput("pkg/Foo.html", true,
                "<li>Foo</li>");
        checkOutput("deprecated/Foo.html", true,
                "<li>Foo</li>");
    }
}
