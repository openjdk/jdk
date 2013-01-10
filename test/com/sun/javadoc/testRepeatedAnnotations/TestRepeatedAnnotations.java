/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8005092
 * @summary  Test repeated annotations output.
 * @author   bpatel
 * @library  ../lib/
 * @build    JavadocTester TestRepeatedAnnotations
 * @run main TestRepeatedAnnotations
 */

public class TestRepeatedAnnotations extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "8005092";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg", "pkg1"
    };

    //Input for string search tests.
    private static final String[][] TEST = {
        {BUG_ID + FS + "pkg" + FS + "C.html",
            "<a href=\"../pkg/ContaineeSynthDoc.html\" " +
            "title=\"annotation in pkg\">@ContaineeSynthDoc</a> " +
            "<a href=\"../pkg/ContaineeSynthDoc.html\" " +
            "title=\"annotation in pkg\">@ContaineeSynthDoc</a>"},
        {BUG_ID + FS + "pkg" + FS + "C.html",
            "<a href=\"../pkg/ContaineeRegDoc.html\" " +
            "title=\"annotation in pkg\">@ContaineeRegDoc</a> " +
            "<a href=\"../pkg/ContaineeRegDoc.html\" " +
            "title=\"annotation in pkg\">@ContaineeRegDoc</a>"},
        {BUG_ID + FS + "pkg" + FS + "C.html",
            "<a href=\"../pkg/RegContainerDoc.html\" " +
            "title=\"annotation in pkg\">@RegContainerDoc</a>" +
            "(<a href=\"../pkg/RegContainerDoc.html#value()\">value</a>={" +
            "<a href=\"../pkg/RegContaineeNotDoc.html\" " +
            "title=\"annotation in pkg\">@RegContaineeNotDoc</a>," +
            "<a href=\"../pkg/RegContaineeNotDoc.html\" " +
            "title=\"annotation in pkg\">@RegContaineeNotDoc</a>})"},
        {BUG_ID + FS + "pkg" + FS + "C.html",
            "<a href=\"../pkg/ContaineeSynthDoc.html\" " +
            "title=\"annotation in pkg\">@ContaineeSynthDoc</a> " +
            "<a href=\"../pkg/ContaineeSynthDoc.html\" " +
            "title=\"annotation in pkg\">@ContaineeSynthDoc</a> " +
            "<a href=\"../pkg/ContaineeSynthDoc.html\" " +
            "title=\"annotation in pkg\">@ContaineeSynthDoc</a>"},
        {BUG_ID + FS + "pkg" + FS + "C.html",
            "<a href=\"../pkg/ContainerSynthDoc.html\" " +
            "title=\"annotation in pkg\">@ContainerSynthDoc</a>(" +
            "<a href=\"../pkg/ContainerSynthDoc.html#value()\">value</a>=" +
            "<a href=\"../pkg/ContaineeSynthDoc.html\" " +
            "title=\"annotation in pkg\">@ContaineeSynthDoc</a>)"},
        {BUG_ID + FS + "pkg" + FS + "C.html",
            "<a href=\"../pkg/ContaineeSynthDoc.html\" " +
            "title=\"annotation in pkg\">@ContaineeSynthDoc</a> " +
            "<a href=\"../pkg/ContaineeSynthDoc.html\" " +
            "title=\"annotation in pkg\">@ContaineeSynthDoc</a>"},

        {BUG_ID + FS + "pkg" + FS + "D.html",
            "<a href=\"../pkg/RegDoc.html\" title=\"annotation in pkg\">@RegDoc</a>" +
            "(<a href=\"../pkg/RegDoc.html#x()\">x</a>=1)"},
        {BUG_ID + FS + "pkg" + FS + "D.html",
            "<a href=\"../pkg/RegArryDoc.html\" title=\"annotation in pkg\">@RegArryDoc</a>" +
            "(<a href=\"../pkg/RegArryDoc.html#y()\">y</a>=1)"},
        {BUG_ID + FS + "pkg" + FS + "D.html",
            "<a href=\"../pkg/RegArryDoc.html\" title=\"annotation in pkg\">@RegArryDoc</a>" +
            "(<a href=\"../pkg/RegArryDoc.html#y()\">y</a>={1,2})"},
        {BUG_ID + FS + "pkg" + FS + "D.html",
            "<a href=\"../pkg/NonSynthDocContainer.html\" " +
            "title=\"annotation in pkg\">@NonSynthDocContainer</a>" +
            "(<a href=\"../pkg/NonSynthDocContainer.html#value()\">value</a>=" +
            "<a href=\"../pkg/RegArryDoc.html\" title=\"annotation in pkg\">@RegArryDoc</a>)"},

        {BUG_ID + FS + "pkg1" + FS + "C.html",
            "<a href=\"../pkg1/RegContainerValDoc.html\" " +
            "title=\"annotation in pkg1\">@RegContainerValDoc</a>" +
            "(<a href=\"../pkg1/RegContainerValDoc.html#value()\">value</a>={" +
            "<a href=\"../pkg1/RegContaineeNotDoc.html\" " +
            "title=\"annotation in pkg1\">@RegContaineeNotDoc</a>," +
            "<a href=\"../pkg1/RegContaineeNotDoc.html\" " +
            "title=\"annotation in pkg1\">@RegContaineeNotDoc</a>}," +
            "<a href=\"../pkg1/RegContainerValDoc.html#y()\">y</a>=3)"},
        {BUG_ID + FS + "pkg1" + FS + "C.html",
            "<a href=\"../pkg1/ContainerValDoc.html\" " +
            "title=\"annotation in pkg1\">@ContainerValDoc</a>" +
            "(<a href=\"../pkg1/ContainerValDoc.html#value()\">value</a>={" +
            "<a href=\"../pkg1/ContaineeNotDoc.html\" " +
            "title=\"annotation in pkg1\">@ContaineeNotDoc</a>," +
            "<a href=\"../pkg1/ContaineeNotDoc.html\" " +
            "title=\"annotation in pkg1\">@ContaineeNotDoc</a>}," +
            "<a href=\"../pkg1/ContainerValDoc.html#x()\">x</a>=1)"}
    };

    private static final String[][] NEGATED_TEST = {
        {BUG_ID + FS + "pkg" + FS + "C.html",
            "<a href=\"../pkg/RegContaineeDoc.html\" " +
            "title=\"annotation in pkg\">@RegContaineeDoc</a> " +
            "<a href=\"../pkg/RegContaineeDoc.html\" " +
            "title=\"annotation in pkg\">@RegContaineeDoc</a>"},
        {BUG_ID + FS + "pkg" + FS + "C.html",
            "<a href=\"../pkg/RegContainerNotDoc.html\" " +
            "title=\"annotation in pkg\">@RegContainerNotDoc</a>" +
            "(<a href=\"../pkg/RegContainerNotDoc.html#value()\">value</a>={" +
            "<a href=\"../pkg/RegContaineeNotDoc.html\" " +
            "title=\"annotation in pkg\">@RegContaineeNotDoc</a>," +
            "<a href=\"../pkg/RegContaineeNotDoc.html\" " +
            "title=\"annotation in pkg\">@RegContaineeNotDoc</a>})"},

        {BUG_ID + FS + "pkg1" + FS + "C.html",
            "<a href=\"../pkg1/ContaineeSynthDoc.html\" " +
            "title=\"annotation in pkg1\">@ContaineeSynthDoc</a> " +
            "<a href=\"../pkg1/ContaineeSynthDoc.html\" " +
            "title=\"annotation in pkg1\">@ContaineeSynthDoc</a>"},
        {BUG_ID + FS + "pkg1" + FS + "C.html",
            "<a href=\"../pkg1/RegContainerValNotDoc.html\" " +
            "title=\"annotation in pkg1\">@RegContainerValNotDoc</a>" +
            "(<a href=\"../pkg1/RegContainerValNotDoc.html#value()\">value</a>={" +
            "<a href=\"../pkg1/RegContaineeDoc.html\" " +
            "title=\"annotation in pkg1\">@RegContaineeDoc</a>," +
            "<a href=\"../pkg1/RegContaineeDoc.html\" " +
            "title=\"annotation in pkg1\">@RegContaineeDoc</a>}," +
            "<a href=\"../pkg1/RegContainerValNotDoc.html#y()\">y</a>=4)"},
        {BUG_ID + FS + "pkg1" + FS + "C.html",
            "<a href=\"../pkg1/ContainerValNotDoc.html\" " +
            "title=\"annotation in pkg1\">@ContainerValNotDoc</a>" +
            "(<a href=\"../pkg1/ContainerValNotDoc.html#value()\">value</a>={" +
            "<a href=\"../pkg1/ContaineeNotDoc.html\" " +
            "title=\"annotation in pkg1\">@ContaineeNotDoc</a>," +
            "<a href=\"../pkg1/ContaineeNotDoc.html\" " +
            "title=\"annotation in pkg1\">@ContaineeNotDoc</a>}," +
            "<a href=\"../pkg1/ContainerValNotDoc.html#x()\">x</a>=2)"},
        {BUG_ID + FS + "pkg1" + FS + "C.html",
            "<a href=\"../pkg1/ContainerSynthNotDoc.html\" " +
            "title=\"annotation in pkg1\">@ContainerSynthNotDoc</a>(" +
            "<a href=\"../pkg1/ContainerSynthNotDoc.html#value()\">value</a>=" +
            "<a href=\"../pkg1/ContaineeSynthDoc.html\" " +
            "title=\"annotation in pkg1\">@ContaineeSynthDoc</a>)"}
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestRepeatedAnnotations tester = new TestRepeatedAnnotations();
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
