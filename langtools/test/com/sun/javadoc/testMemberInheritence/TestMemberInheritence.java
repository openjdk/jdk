/*
 * Copyright (c) 2002, 2005, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4638588 4635809 6256068 6270645
 * @summary Test to make sure that members are inherited properly in the Javadoc.
 *          Verify that inheritence labels are correct.
 * @author jamieh
 * @library ../lib/
 * @build JavadocTester
 * @build TestMemberInheritence
 * @run main TestMemberInheritence
 */

public class TestMemberInheritence extends JavadocTester {

    private static final String BUG_ID = "4638588-4635809-6256068-6270645";

    private static final String[][] TEST = {
        //Public field should be inherited
        {BUG_ID + FS + "pkg" + FS + "SubClass.html",
         "<a href=\"../pkg/BaseClass.html#pubField\">"},

        //Public method should be inherited
        {BUG_ID + FS + "pkg" + FS + "SubClass.html",
         "<a href=\"../pkg/BaseClass.html#pubMethod()\">"},

        //Public inner class should be inherited.
        {BUG_ID + FS + "pkg" + FS + "SubClass.html",
         "<a href=\"../pkg/BaseClass.pubInnerClass.html\" title=\"class in pkg\">"},

        //Protected field should be inherited
        {BUG_ID + FS + "pkg" + FS + "SubClass.html",
         "<a href=\"../pkg/BaseClass.html#proField\">"},

        //Protected method should be inherited
        {BUG_ID + FS + "pkg" + FS + "SubClass.html",
         "<a href=\"../pkg/BaseClass.html#proMethod()\">"},

        //Protected inner class should be inherited.
        {BUG_ID + FS + "pkg" + FS + "SubClass.html",
         "<a href=\"../pkg/BaseClass.proInnerClass.html\" title=\"class in pkg\">"},

        // New labels as of 1.5.0
        {BUG_ID + FS + "pkg" + FS + "SubClass.html",
         "Nested classes/interfaces inherited from class&nbsp;pkg." +
                 "<a href=\"../pkg/BaseClass.html\" title=\"class in pkg\">BaseClass</a>"},
        {BUG_ID + FS + "pkg" + FS + "SubClass.html",
         "Nested classes/interfaces inherited from interface&nbsp;pkg." +
                 "<a href=\"../pkg/BaseInterface.html\" title=\"interface in pkg\">BaseInterface</a>"},

         // Test overriding/implementing methods with generic parameters.
                 {BUG_ID + FS + "pkg" + FS + "BaseClass.html",
         "<dl>" + NL + "<dt><strong>Specified by:</strong></dt>" + NL +
                          "<dd><code><a href=\"../pkg/BaseInterface.html#getAnnotation(java.lang.Class)\">" +
                          "getAnnotation</a></code>&nbsp;in interface&nbsp;<code>" +
                          "<a href=\"../pkg/BaseInterface.html\" title=\"interface in pkg\">" +
                          "BaseInterface</a></code></dd>" + NL + "</dl>"},

         // Test diamond inheritence member summary (6256068)
                 {BUG_ID + FS + "diamond" + FS + "Z.html",
                 "<code><a href=\"../diamond/A.html#aMethod()\">aMethod</a></code>"},

         // Test that doc is inherited from closed parent (6270645)
                 {BUG_ID + FS + "inheritDist" + FS + "C.html",
                 "<div class=\"block\">m1-B</div>"},

    };

    private static final String[][] NEGATED_TEST = {
        {BUG_ID + FS + "pkg" + FS + "SubClass.html",
        "<a href=\"../pkg/BaseClass.html#staticMethod()\">staticMethod</a></code>"},
    };
    private static final String[] ARGS =
        new String[] {
            "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg", "diamond", "inheritDist"};

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestMemberInheritence tester = new TestMemberInheritence();
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
