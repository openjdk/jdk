/*
 * Copyright (c) 2003, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4927552 8026567 8071982 8162674 8175200 8175218 8183511 8186332
 *           8169819 8074407 8191030 8182765 8184205
 * @summary  test generated docs for deprecated items
 * @library  ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    javadoc.tester.*
 * @run main TestDeprecatedDocs
 */

import javadoc.tester.JavadocTester;

public class TestDeprecatedDocs extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestDeprecatedDocs tester = new TestDeprecatedDocs();
        tester.runTests();
    }

    @Test
    public void test() {
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
                + "public class <span class=\"type-name-label\">DeprecatedClassByAnnotation</span>\n"
                + "extends java.lang.Object</pre>",
                "<div class=\"member-signature\"><span class=\"annotations\">@Deprecated(forRemoval=true)\n"
                + "</span><span class=\"modifiers\">public</span>&nbsp;<span class=\"return-type\">int</span>"
                + "&nbsp;<span class=\"member-name\">field</span></div>\n"
                + "<div class=\"deprecation-block\"><span class=\"deprecated-label\">Deprecated, for removal: This API element is subject to removal in a future version.</span></div>",
                "<div class=\"member-signature\"><span class=\"annotations\">@Deprecated(forRemoval=true)\n"
                + "</span><span class=\"modifiers\">public</span>&nbsp;<span class=\"member-name\">DeprecatedClassByAnnotation</span>()</div>\n"
                + "<div class=\"deprecation-block\"><span class=\"deprecated-label\">Deprecated, for removal: This API element is subject to removal in a future version.</span></div>",
                "<div class=\"member-signature\"><span class=\"annotations\">@Deprecated\n"
                + "</span><span class=\"modifiers\">public</span>&nbsp;<span class=\"return-type\">"
                + "void</span>&nbsp;<span class=\"member-name\">method</span>()</div>\n"
                + "<div class=\"deprecation-block\"><span class=\"deprecated-label\">Deprecated.</span></div>");

        checkOutput("pkg/TestAnnotationType.html", true,
                "<hr>\n"
                + "<pre>@Deprecated(forRemoval=true)\n"
                + "@Documented\n"
                + "public @interface <span class=\"type-name-label\">TestAnnotationType</span></pre>\n"
                + "<div class=\"deprecation-block\"><span class=\"deprecated-label\">Deprecated, for removal: This API element is subject to removal in a future version.</span>\n"
                + "<div class=\"deprecation-comment\">annotation_test1 passes.</div>\n"
                + "</div>",
                "<div class=\"member-signature\"><span class=\"annotations\">@Deprecated(forRemoval=true)\n" +
                        "</span><span class=\"modifiers\">static final</span>&nbsp;<span class=\"return-type\">int</span>&nbsp;<span class=\"member-name\">field</span></div>\n"
                + "<div class=\"deprecation-block\"><span class=\"deprecated-label\">Deprecated, for removal: This "
                + "API element is subject to removal in a future version.</span>\n"
                + "<div class=\"deprecation-comment\">annotation_test4 passes.</div>\n"
                + "</div>",
                "<div class=\"member-signature\"><span class=\"annotations\">@Deprecated(forRemoval=true)\n"
                + "</span><span class=\"return-type\">int</span>&nbsp;<span class=\"member-name\">required</span></div>\n"
                + "<div class=\"deprecation-block\"><span class=\"deprecated-label\">Deprecated, for removal: This API element is subject to removal in a future version.</span>\n"
                + "<div class=\"deprecation-comment\">annotation_test3 passes.</div>\n"
                + "</div>",
                "<div class=\"member-signature\"><span class=\"return-type\">java.lang.String</span>"
                + "&nbsp;<span class=\"member-name\">optional</span></div>\n"
                + "<div class=\"deprecation-block\"><span class=\"deprecated-label\">Deprecated.</span>\n"
                + "<div class=\"deprecation-comment\">annotation_test2 passes.</div>\n"
                + "</div>");

        checkOutput("pkg/TestClass.html", true,
                "<hr>\n"
                + "<pre>@Deprecated(forRemoval=true)\n"
                + "public class <span class=\"type-name-label\">TestClass</span>\n"
                + "extends java.lang.Object</pre>\n"
                + "<div class=\"deprecation-block\"><span class=\"deprecated-label\">Deprecated, for removal: This API element is subject to removal in a future version.</span>\n"
                + "<div class=\"deprecation-comment\">class_test1 passes.</div>\n"
                + "</div>",
                "<div class=\"member-signature\"><span class=\"annotations\">@Deprecated(forRemoval=true)\n"
                + "</span><span class=\"modifiers\">public</span>&nbsp;<span class=\"member-name\">TestClass</span>()</div>\n"
                + "<div class=\"deprecation-block\"><span class=\"deprecated-label\">Deprecated, for removal: This API element is subject to removal in a future version.</span>\n"
                + "<div class=\"deprecation-comment\">class_test3 passes. This is the second sentence of deprecated description for a constructor.</div>\n"
                + "</div>",
                "<td class=\"col-last\">\n"
                + "<div class=\"block\"><span class=\"deprecated-label\">Deprecated.</span>\n"
                + "<div class=\"deprecation-comment\">class_test2 passes.</div>\n"
                + "</div>\n"
                + "</td>",
                "<td class=\"col-last\">\n"
                + "<div class=\"block\"><span class=\"deprecated-label\">Deprecated, for removal: This API element is subject to removal in a future version.</span>\n"
                + "<div class=\"deprecation-comment\">class_test3 passes.</div>\n"
                + "</div>\n"
                + "</td>",
                "<td class=\"col-last\">\n"
                + "<div class=\"block\"><span class=\"deprecated-label\">Deprecated.</span>\n"
                + "<div class=\"deprecation-comment\">class_test4 passes.</div>\n"
                + "</div>\n"
                + "</td>");

        checkOutput("pkg/TestClass.html", false,
                "<div class=\"deprecation-comment\">class_test2 passes. This is the second sentence of deprecated description for a field.</div>\n"
                + "</div>\n"
                + "</td>",
                "<div class=\"deprecation-comment\">class_test3 passes. This is the second sentence of deprecated description for a constructor.</div>\n"
                + "</div>\n"
                + "</td>",
                "<div class=\"deprecation-comment\">class_test4 passes. This is the second sentence of deprecated description for a method.</div>\n"
                + "</div>\n"
                + "</td>");

        checkOutput("pkg/TestEnum.html", true,
                "<hr>\n"
                + "<pre>@Deprecated(forRemoval=true)\n"
                + "public enum <span class=\"type-name-label\">TestEnum</span>\n"
                + "extends java.lang.Enum&lt;<a href=\"TestEnum.html\" title=\"enum in pkg\">TestEnum</a>&gt;</pre>\n"
                + "<div class=\"deprecation-block\"><span class=\"deprecated-label\">Deprecated, for removal: This API element is subject to removal in a future version.</span>\n"
                + "<div class=\"deprecation-comment\">enum_test1 passes.</div>\n"
                + "</div>",
                "<div class=\"member-signature\"><span class=\"annotations\">@Deprecated(forRemoval=true)\n"
                + "</span><span class=\"modifiers\">public static final</span>&nbsp;<span class=\"return-type\">"
                + "<a href=\"TestEnum.html\" title=\"enum in pkg\">TestEnum</a></span>&nbsp;<span class=\"member-name\">FOR_REMOVAL</span></div>\n"
                + "<div class=\"deprecation-block\"><span class=\"deprecated-label\">Deprecated, for removal: This API element is subject to removal in a future version.</span>\n"
                + "<div class=\"deprecation-comment\">enum_test3 passes.</div>\n"
                + "</div>");

        checkOutput("pkg/TestError.html", true,
                "<hr>\n"
                + "<pre>@Deprecated(forRemoval=true)\n"
                + "public class <span class=\"type-name-label\">TestError</span>\n"
                + "extends java.lang.Error</pre>\n"
                + "<div class=\"deprecation-block\"><span class=\"deprecated-label\">Deprecated, for removal: This API element is subject to removal in a future version.</span>\n"
                + "<div class=\"deprecation-comment\">error_test1 passes.</div>\n"
                + "</div>");

        checkOutput("pkg/TestException.html", true,
                "<hr>\n"
                + "<pre>@Deprecated(forRemoval=true)\n"
                + "public class <span class=\"type-name-label\">TestException</span>\n"
                + "extends java.lang.Exception</pre>\n"
                + "<div class=\"deprecation-block\"><span class=\"deprecated-label\">Deprecated, for removal: This API element is subject to removal in a future version.</span>\n"
                + "<div class=\"deprecation-comment\">exception_test1 passes.</div>\n"
                + "</div>");

        checkOutput("pkg/TestInterface.html", true,
                "<hr>\n"
                + "<pre>@Deprecated(forRemoval=true)\n"
                + "public class <span class=\"type-name-label\">TestInterface</span>\n"
                + "extends java.lang.Object</pre>\n"
                + "<div class=\"deprecation-block\"><span class=\"deprecated-label\">Deprecated, for removal: This API element is subject to removal in a future version.</span>\n"
                + "<div class=\"deprecation-comment\">interface_test1 passes.</div>\n"
                + "</div>");

        checkOutput("deprecated-list.html", true,
                "<ul>\n"
                + "<li><a href=\"#forRemoval\">For Removal</a></li>\n"
                + "<li><a href=\"#class\">Classes</a></li>\n"
                + "<li><a href=\"#enum\">Enums</a></li>\n"
                + "<li><a href=\"#exception\">Exceptions</a></li>\n"
                + "<li><a href=\"#error\">Errors</a></li>\n"
                + "<li><a href=\"#annotation.type\">Annotation Types</a></li>\n"
                + "<li><a href=\"#field\">Fields</a></li>\n"
                + "<li><a href=\"#method\">Methods</a></li>\n"
                + "<li><a href=\"#constructor\">Constructors</a></li>\n"
                + "<li><a href=\"#enum.constant\">Enum Constants</a></li>\n"
                + "<li><a href=\"#annotation.type.member\">Annotation Type Elements</a></li>\n"
                + "</ul>",
                "<div class=\"deprecated-summary\" id=\"forRemoval\">\n"
                + "<table class=\"summary-table\">\n"
                + "<caption><span>For Removal</span></caption>\n"
                + "<thead>\n"
                + "<tr>\n"
                + "<th class=\"col-first\" scope=\"col\">Element</th>\n"
                + "<th class=\"col-last\" scope=\"col\">Description</th>\n"
                + "</tr>\n"
                + "</thead>",
                "<div class=\"deprecated-summary\" id=\"enum\">\n"
                + "<table class=\"summary-table\">\n"
                + "<caption><span>Enums</span></caption>\n"
                + "<thead>\n"
                + "<tr>\n"
                + "<th class=\"col-first\" scope=\"col\">Enum</th>\n"
                + "<th class=\"col-last\" scope=\"col\">Description</th>\n"
                + "</tr>\n"
                + "</thead>\n"
                + "<tbody>\n"
                + "<tr class=\"alt-color\">\n"
                + "<th class=\"col-deprecated-item-name\" scope=\"row\"><a href=\"pkg/TestEnum.html\" title=\"enum in pkg\">pkg.TestEnum</a></th>\n"
                + "<td class=\"col-last\">\n"
                + "<div class=\"deprecation-comment\">enum_test1 passes.</div>\n"
                + "</td>\n"
                + "</tr>\n"
                + "</tbody>\n"
                + "</table>\n"
                + "</div>",
                "<div class=\"deprecated-summary\" id=\"exception\">\n"
                + "<table class=\"summary-table\">\n"
                + "<caption><span>Exceptions</span></caption>\n"
                + "<thead>\n"
                + "<tr>\n"
                + "<th class=\"col-first\" scope=\"col\">Exceptions</th>\n"
                + "<th class=\"col-last\" scope=\"col\">Description</th>\n"
                + "</tr>\n"
                + "</thead>\n"
                + "<tbody>\n"
                + "<tr class=\"alt-color\">\n"
                + "<th class=\"col-deprecated-item-name\" scope=\"row\"><a href=\"pkg/TestException.html\" title=\"class in pkg\">pkg.TestException</a></th>\n"
                + "<td class=\"col-last\">\n"
                + "<div class=\"deprecation-comment\">exception_test1 passes.</div>\n"
                + "</td>\n"
                + "</tr>\n"
                + "</tbody>\n"
                + "</table>\n"
                + "</div>",
                "<div class=\"deprecated-summary\" id=\"field\">\n"
                + "<table class=\"summary-table\">\n"
                + "<caption><span>Fields</span></caption>\n"
                + "<thead>\n"
                + "<tr>\n"
                + "<th class=\"col-first\" scope=\"col\">Field</th>\n"
                + "<th class=\"col-last\" scope=\"col\">Description</th>\n"
                + "</tr>\n"
                + "</thead>\n"
                + "<tbody>\n"
                + "<tr class=\"alt-color\">\n"
                + "<th class=\"col-deprecated-item-name\" scope=\"row\"><a href=\"pkg/DeprecatedClassByAnnotation.html#field\">pkg.DeprecatedClassByAnnotation.field</a></th>\n"
                + "<td class=\"col-last\"></td>\n"
                + "</tr>\n"
                + "<tr class=\"row-color\">\n"
                + "<th class=\"col-deprecated-item-name\" scope=\"row\"><a href=\"pkg/TestAnnotationType.html#field\">pkg.TestAnnotationType.field</a></th>\n"
                + "<td class=\"col-last\">\n"
                + "<div class=\"deprecation-comment\">annotation_test4 passes.</div>\n"
                + "</td>\n"
                + "</tr>\n"
                + "<tr class=\"alt-color\">\n"
                + "<th class=\"col-deprecated-item-name\" scope=\"row\"><a href=\"pkg/TestClass.html#field\">pkg.TestClass.field</a></th>\n"
                + "<td class=\"col-last\">\n"
                + "<div class=\"deprecation-comment\">class_test2 passes. This is the second sentence of deprecated description for a field.</div>\n"
                + "</td>\n"
                + "</tr>\n"
                + "<tr class=\"row-color\">\n"
                + "<th class=\"col-deprecated-item-name\" scope=\"row\"><a href=\"pkg/TestError.html#field\">pkg.TestError.field</a></th>\n"
                + "<td class=\"col-last\">\n"
                + "<div class=\"deprecation-comment\">error_test2 passes.</div>\n"
                + "</td>\n"
                + "</tr>\n"
                + "<tr class=\"alt-color\">\n"
                + "<th class=\"col-deprecated-item-name\" scope=\"row\"><a href=\"pkg/TestException.html#field\">pkg.TestException.field</a></th>\n"
                + "<td class=\"col-last\">\n"
                + "<div class=\"deprecation-comment\">exception_test2 passes.</div>\n"
                + "</td>\n"
                + "</tr>\n"
                + "<tr class=\"row-color\">\n"
                + "<th class=\"col-deprecated-item-name\" scope=\"row\"><a href=\"pkg/TestInterface.html#field\">pkg.TestInterface.field</a></th>\n"
                + "<td class=\"col-last\">\n"
                + "<div class=\"deprecation-comment\">interface_test2 passes.</div>\n"
                + "</td>\n"
                + "</tr>\n"
                + "</tbody>\n"
                + "</table>\n"
                + "</div>");
    }
}
