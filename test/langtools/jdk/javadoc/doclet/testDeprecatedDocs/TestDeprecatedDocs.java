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
                """
                    <pre>@Deprecated
                    public class <span class="type-name-label">DeprecatedClassByAnnotation</span>
                    extends java.lang.Object</pre>""",
                """
                    <div class="member-signature"><span class="annotations">@Deprecated(forRemoval=true)
                    </span><span class="modifiers">public</span>&nbsp;<span class="return-type">int<\
                    /span>&nbsp;<span class="member-name">field</span></div>
                    <div class="deprecation-block"><span class="deprecated-label">Deprecated, for re\
                    moval: This API element is subject to removal in a future version.</span></div>""",
                """
                    <div class="member-signature"><span class="annotations">@Deprecated(forRemoval=true)
                    </span><span class="modifiers">public</span>&nbsp;<span class="member-name">DeprecatedClassByAnnotation</span>()</div>
                    <div class="deprecation-block"><span class="deprecated-label">Deprecated, for re\
                    moval: This API element is subject to removal in a future version.</span></div>""",
                """
                    <div class="member-signature"><span class="annotations">@Deprecated
                    </span><span class="modifiers">public</span>&nbsp;<span class="return-type">void\
                    </span>&nbsp;<span class="member-name">method</span>()</div>
                    <div class="deprecation-block"><span class="deprecated-label">Deprecated.</span></div>""");

        checkOutput("pkg/TestAnnotationType.html", true,
                """
                    <hr>
                    <pre>@Deprecated(forRemoval=true)
                    @Documented
                    public @interface <span class="type-name-label">TestAnnotationType</span></pre>
                    <div class="deprecation-block"><span class="deprecated-label">Deprecated, for re\
                    moval: This API element is subject to removal in a future version.</span>
                    <div class="deprecation-comment">annotation_test1 passes.</div>
                    </div>""",
                """
                    <div class="member-signature"><span class="annotations">@Deprecated(forRemoval=true)
                    </span><span class="modifiers">static final</span>&nbsp;<span class="return-type\
                    ">int</span>&nbsp;<span class="member-name">field</span></div>
                    <div class="deprecation-block"><span class="deprecated-label">Deprecated, for re\
                    moval: This API element is subject to removal in a future version.</span>
                    <div class="deprecation-comment">annotation_test4 passes.</div>
                    </div>""",
                """
                    <div class="member-signature"><span class="annotations">@Deprecated(forRemoval=true)
                    </span><span class="return-type">int</span>&nbsp;<span class="member-name">required</span></div>
                    <div class="deprecation-block"><span class="deprecated-label">Deprecated, for re\
                    moval: This API element is subject to removal in a future version.</span>
                    <div class="deprecation-comment">annotation_test3 passes.</div>
                    </div>""",
                """
                    <div class="member-signature"><span class="return-type">java.lang.String</span>&\
                    nbsp;<span class="member-name">optional</span></div>
                    <div class="deprecation-block"><span class="deprecated-label">Deprecated.</span>
                    <div class="deprecation-comment">annotation_test2 passes.</div>
                    </div>""");

        checkOutput("pkg/TestClass.html", true,
                """
                    <hr>
                    <pre>@Deprecated(forRemoval=true)
                    public class <span class="type-name-label">TestClass</span>
                    extends java.lang.Object</pre>
                    <div class="deprecation-block"><span class="deprecated-label">Deprecated, for re\
                    moval: This API element is subject to removal in a future version.</span>
                    <div class="deprecation-comment">class_test1 passes.</div>
                    </div>""",
                """
                    <div class="member-signature"><span class="annotations">@Deprecated(forRemoval=true)
                    </span><span class="modifiers">public</span>&nbsp;<span class="member-name">TestClass</span>()</div>
                    <div class="deprecation-block"><span class="deprecated-label">Deprecated, for re\
                    moval: This API element is subject to removal in a future version.</span>
                    <div class="deprecation-comment">class_test3 passes. This is the second sentence\
                     of deprecated description for a constructor.</div>
                    </div>""",
                """
                    <td class="col-last">
                    <div class="block"><span class="deprecated-label">Deprecated.</span>
                    <div class="deprecation-comment">class_test2 passes.</div>
                    </div>
                    </td>""",
                """
                    <td class="col-last">
                    <div class="block"><span class="deprecated-label">Deprecated, for removal: This \
                    API element is subject to removal in a future version.</span>
                    <div class="deprecation-comment">class_test3 passes.</div>
                    </div>
                    </td>""",
                """
                    <td class="col-last">
                    <div class="block"><span class="deprecated-label">Deprecated.</span>
                    <div class="deprecation-comment">class_test4 passes.</div>
                    </div>
                    </td>""");

        checkOutput("pkg/TestClass.html", false,
                """
                    <div class="deprecation-comment">class_test2 passes. This is the second sentence\
                     of deprecated description for a field.</div>
                    </div>
                    </td>""",
                """
                    <div class="deprecation-comment">class_test3 passes. This is the second sentence\
                     of deprecated description for a constructor.</div>
                    </div>
                    </td>""",
                """
                    <div class="deprecation-comment">class_test4 passes. This is the second sentence\
                     of deprecated description for a method.</div>
                    </div>
                    </td>""");

        checkOutput("pkg/TestEnum.html", true,
                """
                    <hr>
                    <pre>@Deprecated(forRemoval=true)
                    public enum <span class="type-name-label">TestEnum</span>
                    extends java.lang.Enum&lt;<a href="TestEnum.html" title="enum in pkg">TestEnum</a>&gt;</pre>
                    <div class="deprecation-block"><span class="deprecated-label">Deprecated, for re\
                    moval: This API element is subject to removal in a future version.</span>
                    <div class="deprecation-comment">enum_test1 passes.</div>
                    </div>""",
                """
                    <div class="member-signature"><span class="annotations">@Deprecated(forRemoval=true)
                    </span><span class="modifiers">public static final</span>&nbsp;<span class="retu\
                    rn-type"><a href="TestEnum.html" title="enum in pkg">TestEnum</a></span>&nbsp;<s\
                    pan class="member-name">FOR_REMOVAL</span></div>
                    <div class="deprecation-block"><span class="deprecated-label">Deprecated, for re\
                    moval: This API element is subject to removal in a future version.</span>
                    <div class="deprecation-comment">enum_test3 passes.</div>
                    </div>""");

        checkOutput("pkg/TestError.html", true,
                """
                    <hr>
                    <pre>@Deprecated(forRemoval=true)
                    public class <span class="type-name-label">TestError</span>
                    extends java.lang.Error</pre>
                    <div class="deprecation-block"><span class="deprecated-label">Deprecated, for re\
                    moval: This API element is subject to removal in a future version.</span>
                    <div class="deprecation-comment">error_test1 passes.</div>
                    </div>""");

        checkOutput("pkg/TestException.html", true,
                """
                    <hr>
                    <pre>@Deprecated(forRemoval=true)
                    public class <span class="type-name-label">TestException</span>
                    extends java.lang.Exception</pre>
                    <div class="deprecation-block"><span class="deprecated-label">Deprecated, for re\
                    moval: This API element is subject to removal in a future version.</span>
                    <div class="deprecation-comment">exception_test1 passes.</div>
                    </div>""");

        checkOutput("pkg/TestInterface.html", true,
                """
                    <hr>
                    <pre>@Deprecated(forRemoval=true)
                    public class <span class="type-name-label">TestInterface</span>
                    extends java.lang.Object</pre>
                    <div class="deprecation-block"><span class="deprecated-label">Deprecated, for re\
                    moval: This API element is subject to removal in a future version.</span>
                    <div class="deprecation-comment">interface_test1 passes.</div>
                    </div>""");

        checkOutput("deprecated-list.html", true,
                """
                    <ul>
                    <li><a href="#forRemoval">For Removal</a></li>
                    <li><a href="#class">Classes</a></li>
                    <li><a href="#enum">Enums</a></li>
                    <li><a href="#exception">Exceptions</a></li>
                    <li><a href="#error">Errors</a></li>
                    <li><a href="#annotation.type">Annotation Types</a></li>
                    <li><a href="#field">Fields</a></li>
                    <li><a href="#method">Methods</a></li>
                    <li><a href="#constructor">Constructors</a></li>
                    <li><a href="#enum.constant">Enum Constants</a></li>
                    <li><a href="#annotation.type.member">Annotation Type Elements</a></li>
                    </ul>""",
                """
                    <div class="deprecated-summary" id="forRemoval">
                    <table class="summary-table">
                    <caption><span>For Removal</span></caption>
                    <thead>
                    <tr>
                    <th class="col-first" scope="col">Element</th>
                    <th class="col-last" scope="col">Description</th>
                    </tr>
                    </thead>""",
                """
                    <div class="deprecated-summary" id="enum">
                    <table class="summary-table">
                    <caption><span>Enums</span></caption>
                    <thead>
                    <tr>
                    <th class="col-first" scope="col">Enum</th>
                    <th class="col-last" scope="col">Description</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr class="alt-color">
                    <th class="col-deprecated-item-name" scope="row"><a href="pkg/TestEnum.html" title="enum in pkg">pkg.TestEnum</a></th>
                    <td class="col-last">
                    <div class="deprecation-comment">enum_test1 passes.</div>
                    </td>
                    </tr>
                    </tbody>
                    </table>
                    </div>""",
                """
                    <div class="deprecated-summary" id="exception">
                    <table class="summary-table">
                    <caption><span>Exceptions</span></caption>
                    <thead>
                    <tr>
                    <th class="col-first" scope="col">Exceptions</th>
                    <th class="col-last" scope="col">Description</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr class="alt-color">
                    <th class="col-deprecated-item-name" scope="row"><a href="pkg/TestException.html\
                    " title="class in pkg">pkg.TestException</a></th>
                    <td class="col-last">
                    <div class="deprecation-comment">exception_test1 passes.</div>
                    </td>
                    </tr>
                    </tbody>
                    </table>
                    </div>""",
                """
                    <div class="deprecated-summary" id="field">
                    <table class="summary-table">
                    <caption><span>Fields</span></caption>
                    <thead>
                    <tr>
                    <th class="col-first" scope="col">Field</th>
                    <th class="col-last" scope="col">Description</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr class="alt-color">
                    <th class="col-deprecated-item-name" scope="row"><a href="pkg/DeprecatedClassByA\
                    nnotation.html#field">pkg.DeprecatedClassByAnnotation.field</a></th>
                    <td class="col-last"></td>
                    </tr>
                    <tr class="row-color">
                    <th class="col-deprecated-item-name" scope="row"><a href="pkg/TestAnnotationType\
                    .html#field">pkg.TestAnnotationType.field</a></th>
                    <td class="col-last">
                    <div class="deprecation-comment">annotation_test4 passes.</div>
                    </td>
                    </tr>
                    <tr class="alt-color">
                    <th class="col-deprecated-item-name" scope="row"><a href="pkg/TestClass.html#field">pkg.TestClass.field</a></th>
                    <td class="col-last">
                    <div class="deprecation-comment">class_test2 passes. This is the second sentence\
                     of deprecated description for a field.</div>
                    </td>
                    </tr>
                    <tr class="row-color">
                    <th class="col-deprecated-item-name" scope="row"><a href="pkg/TestError.html#field">pkg.TestError.field</a></th>
                    <td class="col-last">
                    <div class="deprecation-comment">error_test2 passes.</div>
                    </td>
                    </tr>
                    <tr class="alt-color">
                    <th class="col-deprecated-item-name" scope="row"><a href="pkg/TestException.html#field">pkg.TestException.field</a></th>
                    <td class="col-last">
                    <div class="deprecation-comment">exception_test2 passes.</div>
                    </td>
                    </tr>
                    <tr class="row-color">
                    <th class="col-deprecated-item-name" scope="row"><a href="pkg/TestInterface.html#field">pkg.TestInterface.field</a></th>
                    <td class="col-last">
                    <div class="deprecation-comment">interface_test2 passes.</div>
                    </td>
                    </tr>
                    </tbody>
                    </table>
                    </div>""");
    }
}
