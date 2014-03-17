/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @library  ../lib/
 * @build    JavadocTester TestDeprecatedDocs
 * @run main TestDeprecatedDocs
 */

public class TestDeprecatedDocs extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "4927552";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg"
    };

    private static final String TARGET_FILE  =
        BUG_ID + FS + "deprecated-list.html";

    private static final String TARGET_FILE2  =
        BUG_ID + FS + "pkg" + FS + "DeprecatedClassByAnnotation.html";

    //Input for string search tests.
    private static final String[][] TEST = {
        {TARGET_FILE, "annotation_test1 passes"},
        {TARGET_FILE, "annotation_test2 passes"},
        {TARGET_FILE, "annotation_test3 passes"},
        {TARGET_FILE, "class_test1 passes"},
        {TARGET_FILE, "class_test2 passes"},
        {TARGET_FILE, "class_test3 passes"},
        {TARGET_FILE, "class_test4 passes"},
        {TARGET_FILE, "enum_test1 passes"},
        {TARGET_FILE, "enum_test2 passes"},
        {TARGET_FILE, "error_test1 passes"},
        {TARGET_FILE, "error_test2 passes"},
        {TARGET_FILE, "error_test3 passes"},
        {TARGET_FILE, "error_test4 passes"},
        {TARGET_FILE, "exception_test1 passes"},
        {TARGET_FILE, "exception_test2 passes"},
        {TARGET_FILE, "exception_test3 passes"},
        {TARGET_FILE, "exception_test4 passes"},
        {TARGET_FILE, "interface_test1 passes"},
        {TARGET_FILE, "interface_test2 passes"},
        {TARGET_FILE, "interface_test3 passes"},
        {TARGET_FILE, "interface_test4 passes"},
        {TARGET_FILE, "pkg.DeprecatedClassByAnnotation"},
        {TARGET_FILE, "pkg.DeprecatedClassByAnnotation()"},
        {TARGET_FILE, "pkg.DeprecatedClassByAnnotation.method()"},
        {TARGET_FILE, "pkg.DeprecatedClassByAnnotation.field"},

        {TARGET_FILE2, "<pre>@Deprecated" + NL +
                 "public class <span class=\"typeNameLabel\">DeprecatedClassByAnnotation</span>" + NL +
                 "extends java.lang.Object</pre>"},

        {TARGET_FILE2, "<pre>@Deprecated" + NL +
                 "public&nbsp;int field</pre>" + NL +
                 "<div class=\"block\"><span class=\"deprecatedLabel\">Deprecated.</span>&nbsp;</div>"},

        {TARGET_FILE2, "<pre>@Deprecated" + NL +
                 "public&nbsp;DeprecatedClassByAnnotation()</pre>" + NL +
                 "<div class=\"block\"><span class=\"deprecatedLabel\">Deprecated.</span>&nbsp;</div>"},

        {TARGET_FILE2, "<pre>@Deprecated" + NL +
                 "public&nbsp;void&nbsp;method()</pre>" + NL +
                 "<div class=\"block\"><span class=\"deprecatedLabel\">Deprecated.</span>&nbsp;</div>"},
    };

    private static final String[][] NEGATED_TEST = NO_TEST;

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestDeprecatedDocs tester = new TestDeprecatedDocs();
        run(tester, ARGS, TEST, NEGATED_TEST);
        tester.printSummary();
    }

    /**
     * {@inheritDoc}
     */
    public String getBugId() {
        return BUG_ID;
    }

    /**
     * {@inheritDoc}
     */
    public String getBugName() {
        return getClass().getName();
    }
}
