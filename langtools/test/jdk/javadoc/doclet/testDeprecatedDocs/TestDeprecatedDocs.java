/*
 * Copyright (c) 2003, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4927552 8026567
 * @summary  <DESC>
 * @author   jamieh
 * @library  ../lib
 * @modules jdk.javadoc
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
                "<pre>@Deprecated\n"
                + "public&nbsp;int field</pre>\n"
                + "<div class=\"block\"><span class=\"deprecatedLabel\">Deprecated.</span>&nbsp;</div>",
                "<pre>@Deprecated\n"
                + "public&nbsp;DeprecatedClassByAnnotation()</pre>\n"
                + "<div class=\"block\"><span class=\"deprecatedLabel\">Deprecated.</span>&nbsp;</div>",
                "<pre>@Deprecated\n"
                + "public&nbsp;void&nbsp;method()</pre>\n"
                + "<div class=\"block\"><span class=\"deprecatedLabel\">Deprecated.</span>&nbsp;</div>");
    }
}
