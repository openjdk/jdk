/*
 * Copyright (c) 2003, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4927552 8026567 8071982 8162674 8175200 8175218
 * @summary  <DESC>
 * @author   jamieh
 * @library  ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    JavadocTester
 * @run main TestDeprecatedDocs
 */

public class TestDeprecatedDocs extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestDeprecatedDocs tester = new TestDeprecatedDocs();
        tester.runTests();
    }

    @Test
    void test() {
        javadoc("-d", "out",
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.OK);

        checkOutput("deprecated-list.html", true,
                "annotation_test1 passes",
                "annotation_test2 passes",
                "annotation_test3 passes",
                "annotation_test4 passes.",
                "class_test1 passes",
                "class_test2 passes",
                "class_test3 passes",
                "class_test4 passes",
                "enum_test1 passes",
                "enum_test2 passes",
                "error_test1 passes",
                "error_test2 passes",
                "error_test3 passes",
                "error_test4 passes",
                "exception_test1 passes",
                "exception_test2 passes",
                "exception_test3 passes",
                "exception_test4 passes",
                "interface_test1 passes",
                "interface_test2 passes",
                "interface_test3 passes",
                "interface_test4 passes",
                "pkg.DeprecatedClassByAnnotation",
                "pkg.DeprecatedClassByAnnotation()",
                "pkg.DeprecatedClassByAnnotation.method()",
                "pkg.DeprecatedClassByAnnotation.field"
        );

        checkOutput("pkg/DeprecatedClassByAnnotation.html", true,
                "<pre>@Deprecated\n"
                + "public class <span class=\"typeNameLabel\">DeprecatedClassByAnnotation</span>\n"
                + "extends java.lang.Object</pre>",
                "<pre>@Deprecated(forRemoval=true)\n"
                + "public&nbsp;int field</pre>\n"
                + "<div class=\"block\"><span class=\"deprecatedLabel\">Deprecated, for removal: This API element is subject to removal in a future version.</span>&nbsp;</div>",
                "<pre>@Deprecated(forRemoval=true)\n"
                + "public&nbsp;DeprecatedClassByAnnotation&#8203;()</pre>\n"
                + "<div class=\"block\"><span class=\"deprecatedLabel\">Deprecated, for removal: This API element is subject to removal in a future version.</span>&nbsp;</div>",
                "<pre>@Deprecated\n"
                + "public&nbsp;void&nbsp;method&#8203;()</pre>\n"
                + "<div class=\"block\"><span class=\"deprecatedLabel\">Deprecated.</span>&nbsp;</div>");

        checkOutput("pkg/TestAnnotationType.html", true,
                "<div class=\"block\"><span class=\"deprecatedLabel\">Deprecated, for removal: This API element is subject to removal in a future version.</span>&nbsp;\n"
                + "<div class=\"block\"><span class=\"deprecationComment\">annotation_test1 passes.</span></div>\n"
                + "</div>\n"
                + "<br>\n"
                + "<pre>@Deprecated(forRemoval=true)\n"
                + "@Documented\n"
                + "public @interface <span class=\"memberNameLabel\">TestAnnotationType</span></pre>",
                "<pre>@Deprecated(forRemoval=true)\n"
                + "static final&nbsp;int&nbsp;field</pre>\n"
                + "<div class=\"block\"><span class=\"deprecatedLabel\">Deprecated, for removal: This "
                + "API element is subject to removal in a future version.</span>&nbsp;<span class=\"deprecationComment\">annotation_test4 passes.</span></div>",
                "<pre>@Deprecated(forRemoval=true)\n"
                + "int&nbsp;required</pre>\n"
                + "<div class=\"block\"><span class=\"deprecatedLabel\">Deprecated, for removal: This API element is subject to removal in a future version.</span>&nbsp;"
                + "<span class=\"deprecationComment\">annotation_test3 passes.</span></div>",
                "<pre>java.lang.String&nbsp;optional</pre>\n"
                + "<div class=\"block\"><span class=\"deprecatedLabel\">Deprecated.</span>&nbsp;<span class=\"deprecationComment\">annotation_test2 passes.</span></div>");

        checkOutput("pkg/TestClass.html", true,
                "<div class=\"block\"><span class=\"deprecatedLabel\">Deprecated, for removal: This API element is subject to removal in a future version.</span>&nbsp;\n"
                + "<div class=\"block\"><span class=\"deprecationComment\">class_test1 passes.</span></div>\n"
                + "</div>\n"
                + "<br>\n"
                + "<pre>@Deprecated(forRemoval=true)\n"
                + "public class <span class=\"typeNameLabel\">TestClass</span>\n"
                + "extends java.lang.Object</pre>",
                "<pre>@Deprecated(forRemoval=true)\n"
                + "public&nbsp;TestClass&#8203;()</pre>\n"
                + "<div class=\"block\"><span class=\"deprecatedLabel\">Deprecated, for removal: This API element is subject to removal in a future version.</span>&nbsp;"
                + "<span class=\"deprecationComment\">class_test3 passes.</span></div>");

        checkOutput("pkg/TestEnum.html", true,
                "<div class=\"block\"><span class=\"deprecatedLabel\">Deprecated, for removal: This API element is subject to removal in a future version.</span>&nbsp;\n"
                + "<div class=\"block\"><span class=\"deprecationComment\">enum_test1 passes.</span></div>\n"
                + "</div>\n"
                + "<br>\n"
                + "<pre>@Deprecated(forRemoval=true)\n"
                + "public enum <span class=\"typeNameLabel\">TestEnum</span>\n"
                + "extends java.lang.Enum&lt;<a href=\"../pkg/TestEnum.html\" title=\"enum in pkg\">TestEnum</a>&gt;</pre>",
                "<pre>@Deprecated(forRemoval=true)\n"
                + "public static final&nbsp;<a href=\"../pkg/TestEnum.html\" title=\"enum in pkg\">TestEnum</a> FOR_REMOVAL</pre>\n"
                + "<div class=\"block\"><span class=\"deprecatedLabel\">Deprecated, for removal: This API element is subject to removal in a future version.</span>&nbsp;"
                + "<span class=\"deprecationComment\">enum_test3 passes.</span></div>");

        checkOutput("pkg/TestError.html", true,
                "<div class=\"block\"><span class=\"deprecatedLabel\">Deprecated, for removal: This API element is subject to removal in a future version.</span>&nbsp;\n"
                + "<div class=\"block\"><span class=\"deprecationComment\">error_test1 passes.</span></div>\n"
                + "</div>\n"
                + "<br>\n"
                + "<pre>@Deprecated(forRemoval=true)\n"
                + "public class <span class=\"typeNameLabel\">TestError</span>\n"
                + "extends java.lang.Error</pre>");

        checkOutput("pkg/TestException.html", true,
                "<div class=\"block\"><span class=\"deprecatedLabel\">Deprecated, for removal: This API element is subject to removal in a future version.</span>&nbsp;\n"
                + "<div class=\"block\"><span class=\"deprecationComment\">exception_test1 passes.</span></div>\n"
                + "</div>\n"
                + "<br>\n"
                + "<pre>@Deprecated(forRemoval=true)\n"
                + "public class <span class=\"typeNameLabel\">TestException</span>\n"
                + "extends java.lang.Exception</pre>");

        checkOutput("pkg/TestInterface.html", true,
                "<div class=\"block\"><span class=\"deprecatedLabel\">Deprecated, for removal: This API element is subject to removal in a future version.</span>&nbsp;\n"
                + "<div class=\"block\"><span class=\"deprecationComment\">interface_test1 passes.</span></div>\n"
                + "</div>\n"
                + "<br>\n"
                + "<pre>@Deprecated(forRemoval=true)\n"
                + "public class <span class=\"typeNameLabel\">TestInterface</span>\n"
                + "extends java.lang.Object</pre>");

        checkOutput("deprecated-list.html", true,
                "<ul>\n"
                + "<li><a href=\"#forRemoval\">Deprecated For Removal</a></li>\n"
                + "<li><a href=\"#class\">Deprecated Classes</a></li>\n"
                + "<li><a href=\"#enum\">Deprecated Enums</a></li>\n"
                + "<li><a href=\"#exception\">Deprecated Exceptions</a></li>\n"
                + "<li><a href=\"#error\">Deprecated Errors</a></li>\n"
                + "<li><a href=\"#annotation.type\">Deprecated Annotation Types</a></li>\n"
                + "<li><a href=\"#field\">Deprecated Fields</a></li>\n"
                + "<li><a href=\"#method\">Deprecated Methods</a></li>\n"
                + "<li><a href=\"#constructor\">Deprecated Constructors</a></li>\n"
                + "<li><a href=\"#enum.constant\">Deprecated Enum Constants</a></li>\n"
                + "<li><a href=\"#annotation.type.member\">Deprecated Annotation Type Elements</a></li>\n"
                + "</ul>",
                "<a name=\"forRemoval\">",
                "<table class=\"deprecatedSummary\" summary=\"Deprecated For Removal table, listing deprecated for removal, and an explanation\">\n"
                + "<caption><span>Deprecated For Removal</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Element</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>",
                "<table class=\"deprecatedSummary\" summary=\"Deprecated Enums table, listing deprecated enums, and an explanation\">\n"
                + "<caption><span>Deprecated Enums</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Enum</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>\n"
                + "<tbody>\n"
                + "<tr class=\"altColor\">\n"
                + "<th class=\"colFirst\" scope=\"row\"><a href=\"pkg/TestEnum.html\" title=\"enum in pkg\">pkg.TestEnum</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"block\"><span class=\"deprecationComment\">enum_test1 passes.</span></div>\n"
                + "</td>\n"
                + "</tr>\n"
                + "</tbody>\n"
                + "</table>",
                "<table class=\"deprecatedSummary\" summary=\"Deprecated Exceptions table, listing deprecated exceptions, and an explanation\">\n"
                + "<caption><span>Deprecated Exceptions</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Exceptions</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>\n"
                + "<tbody>\n"
                + "<tr class=\"altColor\">\n"
                + "<th class=\"colFirst\" scope=\"row\"><a href=\"pkg/TestException.html\" title=\"class in pkg\">pkg.TestException</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"block\"><span class=\"deprecationComment\">exception_test1 passes.</span></div>\n"
                + "</td>\n"
                + "</tr>\n"
                + "</tbody>\n"
                + "</table>");
    }
}
