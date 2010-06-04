/*
 * Copyright (c) 2004, 2005, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4732864 6280605
 * @summary  Make sure that you can link from one member to another using
 *           non-qualified name.
 * @author   jamieh
 * @library  ../lib/
 * @build    JavadocTester
 * @build    TestLinkTaglet
 * @run main TestLinkTaglet
 */

public class TestLinkTaglet extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "4732864-6280605";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg", SRC_DIR + FS + "checkPkg" + FS + "B.java"
    };

    //Input for string search tests.
    private static final String[][] TEST = {
        {BUG_ID + FS + "pkg" + FS + "C.html",
            "Qualified Link: <A HREF=\"../pkg/C.InnerC.html\" title=\"class in pkg\"><CODE>C.InnerC</CODE></A>.<br/>\n" +
            " Unqualified Link1: <A HREF=\"../pkg/C.InnerC.html\" title=\"class in pkg\"><CODE>C.InnerC</CODE></A>.<br/>\n" +
            " Unqualified Link2: <A HREF=\"../pkg/C.InnerC.html\" title=\"class in pkg\"><CODE>C.InnerC</CODE></A>.<br/>\n" +
            " Qualified Link: <A HREF=\"../pkg/C.html#method(pkg.C.InnerC, pkg.C.InnerC2)\"><CODE>method(pkg.C.InnerC, pkg.C.InnerC2)</CODE></A>.<br/>\n" +
            " Unqualified Link: <A HREF=\"../pkg/C.html#method(pkg.C.InnerC, pkg.C.InnerC2)\"><CODE>method(C.InnerC, C.InnerC2)</CODE></A>.<br/>\n" +
            " Unqualified Link: <A HREF=\"../pkg/C.html#method(pkg.C.InnerC, pkg.C.InnerC2)\"><CODE>method(InnerC, InnerC2)</CODE></A>.<br/>"
        },
        {BUG_ID + FS + "pkg" + FS + "C.InnerC.html",
            "Link to member in outer class: <A HREF=\"../pkg/C.html#MEMBER\"><CODE>C.MEMBER</CODE></A> <br/>\n" +
            " Link to member in inner class: <A HREF=\"../pkg/C.InnerC2.html#MEMBER2\"><CODE>C.InnerC2.MEMBER2</CODE></A> <br/>\n" +
            " Link to another inner class: <A HREF=\"../pkg/C.InnerC2.html\" title=\"class in pkg\"><CODE>C.InnerC2</CODE></A>"
        },
        {BUG_ID + FS + "pkg" + FS + "C.InnerC2.html",
            "Enclosing class:</STRONG></DT><DD><A HREF=\"../pkg/C.html\" title=\"class in pkg\">C</A>"
        },
    };
    private static final String[][] NEGATED_TEST = {
        {WARNING_OUTPUT, "Tag @see: reference not found: A"},
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestLinkTaglet tester = new TestLinkTaglet();
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
