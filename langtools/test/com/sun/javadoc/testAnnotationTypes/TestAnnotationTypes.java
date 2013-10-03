/*
 * Copyright (c) 2004, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4973609 8015249
 * @summary  Make sure that annotation types with 0 members does not have
 *           extra HR tags.
 * @author   jamieh
 * @library  ../lib/
 * @build    JavadocTester TestAnnotationTypes
 * @run main TestAnnotationTypes
 */

public class TestAnnotationTypes extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "4973609-8015249";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "-source", "1.5", "pkg"
    };

    //Input for string search tests.
    private static final String[][] TEST = {
        {BUG_ID + FS + "pkg" + FS + "AnnotationTypeField.html",
            "<li>Summary:&nbsp;</li>" + NL + "<li><a href=\"#annotation_type_" +
            "field_summary\">Field</a>&nbsp;|&nbsp;</li>"},
        {BUG_ID + FS + "pkg" + FS + "AnnotationTypeField.html",
            "<li>Detail:&nbsp;</li>" + NL + "<li><a href=\"#annotation_type_" +
            "field_detail\">Field</a>&nbsp;|&nbsp;</li>"},
        {BUG_ID + FS + "pkg" + FS + "AnnotationTypeField.html",
            "<!-- =========== ANNOTATION TYPE FIELD SUMMARY =========== -->"},
        {BUG_ID + FS + "pkg" + FS + "AnnotationTypeField.html",
            "<h3>Field Summary</h3>"},
        {BUG_ID + FS + "pkg" + FS + "AnnotationTypeField.html",
            "<td class=\"colLast\"><code><span class=\"strong\"><a href=\"../" +
            "pkg/AnnotationTypeField.html#DEFAULT_NAME\">DEFAULT_NAME</a></span>" +
            "</code>&nbsp;</td>"},
        {BUG_ID + FS + "pkg" + FS + "AnnotationTypeField.html",
            "<!-- ============ ANNOTATION TYPE FIELD DETAIL =========== -->"},
        {BUG_ID + FS + "pkg" + FS + "AnnotationTypeField.html",
            "<h4>DEFAULT_NAME</h4>" + NL + "<pre>public static final&nbsp;java." +
            "lang.String&nbsp;DEFAULT_NAME</pre>"},
        {BUG_ID + FS + "pkg" + FS + "AnnotationType.html",
            "<li>Summary:&nbsp;</li>" + NL + "<li>Field&nbsp;|&nbsp;</li>"},
        {BUG_ID + FS + "pkg" + FS + "AnnotationType.html",
            "<li>Detail:&nbsp;</li>" + NL + "<li>Field&nbsp;|&nbsp;</li>"},
    };
    private static final String[][] NEGATED_TEST = {
        {BUG_ID + FS + "pkg" + FS + "AnnotationType.html",
            "<HR>" + NL + NL + "<P>" + NL + NL + "<P>" +
            "<!-- ========= END OF CLASS DATA ========= -->" + "<HR>"}
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestAnnotationTypes tester = new TestAnnotationTypes();
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
