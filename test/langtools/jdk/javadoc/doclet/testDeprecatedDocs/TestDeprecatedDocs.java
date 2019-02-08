/*
 * Copyright (c) 2003, 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @author   jamieh
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
                + "public class <span class=\"typeNameLabel\">DeprecatedClassByAnnotation</span>\n"
                + "extends java.lang.Object</pre>",
                "<pre>@Deprecated(forRemoval=true)\n"
                + "public&nbsp;int field</pre>\n"
                + "<div class=\"deprecationBlock\"><span class=\"deprecatedLabel\">Deprecated, for removal: This API element is subject to removal in a future version.</span></div>",
                "<pre>@Deprecated(forRemoval=true)\n"
                + "public&nbsp;DeprecatedClassByAnnotation()</pre>\n"
                + "<div class=\"deprecationBlock\"><span class=\"deprecatedLabel\">Deprecated, for removal: This API element is subject to removal in a future version.</span></div>",
                "<pre class=\"methodSignature\">@Deprecated\n"
                + "public&nbsp;void&nbsp;method()</pre>\n"
                + "<div class=\"deprecationBlock\"><span class=\"deprecatedLabel\">Deprecated.</span></div>");

        checkOutput("pkg/TestAnnotationType.html", true,
                "<hr>\n"
                + "<pre>@Deprecated(forRemoval=true)\n"
                + "@Documented\n"
                + "public @interface <span class=\"memberNameLabel\">TestAnnotationType</span></pre>\n"
                + "<div class=\"deprecationBlock\"><span class=\"deprecatedLabel\">Deprecated, for removal: This API element is subject to removal in a future version.</span>\n"
                + "<div class=\"deprecationComment\">annotation_test1 passes.</div>\n"
                + "</div>",
                "<pre>@Deprecated(forRemoval=true)\n"
                + "static final&nbsp;int&nbsp;field</pre>\n"
                + "<div class=\"deprecationBlock\"><span class=\"deprecatedLabel\">Deprecated, for removal: This "
                + "API element is subject to removal in a future version.</span>\n"
                + "<div class=\"deprecationComment\">annotation_test4 passes.</div>\n"
                + "</div>",
                "<pre>@Deprecated(forRemoval=true)\n"
                + "int&nbsp;required</pre>\n"
                + "<div class=\"deprecationBlock\"><span class=\"deprecatedLabel\">Deprecated, for removal: This API element is subject to removal in a future version.</span>\n"
                + "<div class=\"deprecationComment\">annotation_test3 passes.</div>\n"
                + "</div>",
                "<pre>java.lang.String&nbsp;optional</pre>\n"
                + "<div class=\"deprecationBlock\"><span class=\"deprecatedLabel\">Deprecated.</span>\n"
                + "<div class=\"deprecationComment\">annotation_test2 passes.</div>\n"
                + "</div>");

        checkOutput("pkg/TestClass.html", true,
                "<hr>\n"
                + "<pre>@Deprecated(forRemoval=true)\n"
                + "public class <span class=\"typeNameLabel\">TestClass</span>\n"
                + "extends java.lang.Object</pre>\n"
                + "<div class=\"deprecationBlock\"><span class=\"deprecatedLabel\">Deprecated, for removal: This API element is subject to removal in a future version.</span>\n"
                + "<div class=\"deprecationComment\">class_test1 passes.</div>\n"
                + "</div>",
                "<pre>@Deprecated(forRemoval=true)\n"
                + "public&nbsp;TestClass()</pre>\n"
                + "<div class=\"deprecationBlock\"><span class=\"deprecatedLabel\">Deprecated, for removal: This API element is subject to removal in a future version.</span>\n"
                + "<div class=\"deprecationComment\">class_test3 passes. This is the second sentence of deprecated description for a constructor.</div>\n"
                + "</div>",
                "<td class=\"colLast\">\n"
                + "<div class=\"block\"><span class=\"deprecatedLabel\">Deprecated.</span>\n"
                + "<div class=\"deprecationComment\">class_test2 passes.</div>\n"
                + "</div>\n"
                + "</td>",
                "<td class=\"colLast\">\n"
                + "<div class=\"block\"><span class=\"deprecatedLabel\">Deprecated, for removal: This API element is subject to removal in a future version.</span>\n"
                + "<div class=\"deprecationComment\">class_test3 passes.</div>\n"
                + "</div>\n"
                + "</td>",
                "<td class=\"colLast\">\n"
                + "<div class=\"block\"><span class=\"deprecatedLabel\">Deprecated.</span>\n"
                + "<div class=\"deprecationComment\">class_test4 passes.</div>\n"
                + "</div>\n"
                + "</td>");

        checkOutput("pkg/TestClass.html", false,
                "<div class=\"deprecationComment\">class_test2 passes. This is the second sentence of deprecated description for a field.</div>\n"
                + "</div>\n"
                + "</td>",
                "<div class=\"deprecationComment\">class_test3 passes. This is the second sentence of deprecated description for a constructor.</div>\n"
                + "</div>\n"
                + "</td>",
                "<div class=\"deprecationComment\">class_test4 passes. This is the second sentence of deprecated description for a method.</div>\n"
                + "</div>\n"
                + "</td>");

        checkOutput("pkg/TestEnum.html", true,
                "<hr>\n"
                + "<pre>@Deprecated(forRemoval=true)\n"
                + "public enum <span class=\"typeNameLabel\">TestEnum</span>\n"
                + "extends java.lang.Enum&lt;<a href=\"TestEnum.html\" title=\"enum in pkg\">TestEnum</a>&gt;</pre>\n"
                + "<div class=\"deprecationBlock\"><span class=\"deprecatedLabel\">Deprecated, for removal: This API element is subject to removal in a future version.</span>\n"
                + "<div class=\"deprecationComment\">enum_test1 passes.</div>\n"
                + "</div>",
                "<pre>@Deprecated(forRemoval=true)\n"
                + "public static final&nbsp;<a href=\"TestEnum.html\" title=\"enum in pkg\">TestEnum</a> FOR_REMOVAL</pre>\n"
                + "<div class=\"deprecationBlock\"><span class=\"deprecatedLabel\">Deprecated, for removal: This API element is subject to removal in a future version.</span>\n"
                + "<div class=\"deprecationComment\">enum_test3 passes.</div>\n"
                + "</div>");

        checkOutput("pkg/TestError.html", true,
                "<hr>\n"
                + "<pre>@Deprecated(forRemoval=true)\n"
                + "public class <span class=\"typeNameLabel\">TestError</span>\n"
                + "extends java.lang.Error</pre>\n"
                + "<div class=\"deprecationBlock\"><span class=\"deprecatedLabel\">Deprecated, for removal: This API element is subject to removal in a future version.</span>\n"
                + "<div class=\"deprecationComment\">error_test1 passes.</div>\n"
                + "</div>");

        checkOutput("pkg/TestException.html", true,
                "<hr>\n"
                + "<pre>@Deprecated(forRemoval=true)\n"
                + "public class <span class=\"typeNameLabel\">TestException</span>\n"
                + "extends java.lang.Exception</pre>\n"
                + "<div class=\"deprecationBlock\"><span class=\"deprecatedLabel\">Deprecated, for removal: This API element is subject to removal in a future version.</span>\n"
                + "<div class=\"deprecationComment\">exception_test1 passes.</div>\n"
                + "</div>");

        checkOutput("pkg/TestInterface.html", true,
                "<hr>\n"
                + "<pre>@Deprecated(forRemoval=true)\n"
                + "public class <span class=\"typeNameLabel\">TestInterface</span>\n"
                + "extends java.lang.Object</pre>\n"
                + "<div class=\"deprecationBlock\"><span class=\"deprecatedLabel\">Deprecated, for removal: This API element is subject to removal in a future version.</span>\n"
                + "<div class=\"deprecationComment\">interface_test1 passes.</div>\n"
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
                "<a id=\"forRemoval\">",
                "<div class=\"deprecatedSummary\">\n"
                + "<table>\n"
                + "<caption><span>For Removal</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Element</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>",
                "<div class=\"deprecatedSummary\">\n"
                + "<table>\n"
                + "<caption><span>Enums</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Enum</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>\n"
                + "<tbody>\n"
                + "<tr class=\"altColor\">\n"
                + "<th class=\"colDeprecatedItemName\" scope=\"row\"><a href=\"pkg/TestEnum.html\" title=\"enum in pkg\">pkg.TestEnum</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"deprecationComment\">enum_test1 passes.</div>\n"
                + "</td>\n"
                + "</tr>\n"
                + "</tbody>\n"
                + "</table>\n"
                + "</div>",
                "<div class=\"deprecatedSummary\">\n"
                + "<table>\n"
                + "<caption><span>Exceptions</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Exceptions</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>\n"
                + "<tbody>\n"
                + "<tr class=\"altColor\">\n"
                + "<th class=\"colDeprecatedItemName\" scope=\"row\"><a href=\"pkg/TestException.html\" title=\"class in pkg\">pkg.TestException</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"deprecationComment\">exception_test1 passes.</div>\n"
                + "</td>\n"
                + "</tr>\n"
                + "</tbody>\n"
                + "</table>\n"
                + "</div>",
                "<div class=\"deprecatedSummary\">\n"
                + "<table>\n"
                + "<caption><span>Fields</span><span class=\"tabEnd\">&nbsp;</span></caption>\n"
                + "<tr>\n"
                + "<th class=\"colFirst\" scope=\"col\">Field</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>\n"
                + "<tbody>\n"
                + "<tr class=\"altColor\">\n"
                + "<th class=\"colDeprecatedItemName\" scope=\"row\"><a href=\"pkg/DeprecatedClassByAnnotation.html#field\">pkg.DeprecatedClassByAnnotation.field</a></th>\n"
                + "<td class=\"colLast\"></td>\n"
                + "</tr>\n"
                + "<tr class=\"rowColor\">\n"
                + "<th class=\"colDeprecatedItemName\" scope=\"row\"><a href=\"pkg/TestAnnotationType.html#field\">pkg.TestAnnotationType.field</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"deprecationComment\">annotation_test4 passes.</div>\n"
                + "</td>\n"
                + "</tr>\n"
                + "<tr class=\"altColor\">\n"
                + "<th class=\"colDeprecatedItemName\" scope=\"row\"><a href=\"pkg/TestClass.html#field\">pkg.TestClass.field</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"deprecationComment\">class_test2 passes. This is the second sentence of deprecated description for a field.</div>\n"
                + "</td>\n"
                + "</tr>\n"
                + "<tr class=\"rowColor\">\n"
                + "<th class=\"colDeprecatedItemName\" scope=\"row\"><a href=\"pkg/TestError.html#field\">pkg.TestError.field</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"deprecationComment\">error_test2 passes.</div>\n"
                + "</td>\n"
                + "</tr>\n"
                + "<tr class=\"altColor\">\n"
                + "<th class=\"colDeprecatedItemName\" scope=\"row\"><a href=\"pkg/TestException.html#field\">pkg.TestException.field</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"deprecationComment\">exception_test2 passes.</div>\n"
                + "</td>\n"
                + "</tr>\n"
                + "<tr class=\"rowColor\">\n"
                + "<th class=\"colDeprecatedItemName\" scope=\"row\"><a href=\"pkg/TestInterface.html#field\">pkg.TestInterface.field</a></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"deprecationComment\">interface_test2 passes.</div>\n"
                + "</td>\n"
                + "</tr>\n"
                + "</tbody>\n"
                + "</table>\n"
                + "</div>");
    }
}
