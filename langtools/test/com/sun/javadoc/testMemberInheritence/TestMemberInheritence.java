/*
 * Copyright 2002-2005 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
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
         "<A HREF=\"../pkg/BaseClass.html#pubField\">"},

        //Public method should be inherited
        {BUG_ID + FS + "pkg" + FS + "SubClass.html",
         "<A HREF=\"../pkg/BaseClass.html#pubMethod()\">"},

        //Public inner class should be inherited.
        {BUG_ID + FS + "pkg" + FS + "SubClass.html",
         "<A HREF=\"../pkg/BaseClass.pubInnerClass.html\" title=\"class in pkg\">"},

        //Protected field should be inherited
        {BUG_ID + FS + "pkg" + FS + "SubClass.html",
         "<A HREF=\"../pkg/BaseClass.html#proField\">"},

        //Protected method should be inherited
        {BUG_ID + FS + "pkg" + FS + "SubClass.html",
         "<A HREF=\"../pkg/BaseClass.html#proMethod()\">"},

        //Protected inner class should be inherited.
        {BUG_ID + FS + "pkg" + FS + "SubClass.html",
         "<A HREF=\"../pkg/BaseClass.proInnerClass.html\" title=\"class in pkg\">"},

        // New labels as of 1.5.0
        {BUG_ID + FS + "pkg" + FS + "SubClass.html",
         "<STRONG>Nested classes/interfaces inherited from class pkg." +
         "<A HREF=\"../pkg/BaseClass.html\" title=\"class in pkg\">" +
         "BaseClass</A></STRONG>"},
        {BUG_ID + FS + "pkg" + FS + "SubClass.html",
         "<STRONG>Nested classes/interfaces inherited from interface pkg." +
         "<A HREF=\"../pkg/BaseInterface.html\" title=\"interface in pkg\">" +
         "BaseInterface</A></STRONG>"},

         // Test overriding/implementing methods with generic parameters.
                 {BUG_ID + FS + "pkg" + FS + "BaseClass.html",
         "<DT><STRONG>Specified by:</STRONG></DT><DD><CODE><A HREF=\"../pkg/BaseInterface.html#getAnnotation(java.lang.Class)\">getAnnotation</A></CODE> in interface <CODE><A HREF=\"../pkg/BaseInterface.html\" title=\"interface in pkg\">BaseInterface</A></CODE></DD>"+NL+"</DL>"},

         // Test diamond inheritence member summary (6256068)
                 {BUG_ID + FS + "diamond" + FS + "Z.html",
                 "<TD><CODE><A HREF=\"../diamond/A.html#aMethod()\">aMethod</A></CODE></TD>"},

         // Test that doc is inherited from closed parent (6270645)
                 {BUG_ID + FS + "inheritDist" + FS + "C.html",
                 "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;m1-B</TD>"},

    };

    private static final String[][] NEGATED_TEST = {
        {BUG_ID + FS + "pkg" + FS + "SubClass.html",
        "<A HREF=\"../pkg/BaseClass.html#staticMethod()\">staticMethod</A></CODE>"},
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
