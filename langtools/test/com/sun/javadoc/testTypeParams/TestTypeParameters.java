/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4927167 4974929
 * @summary  When the type parameters are more than 10 characters in length,
 *           make sure there is a line break between type params and return type
 *           in member summary.
 * @author   jamieh
 * @library  ../lib/
 * @build    JavadocTester
 * @build    TestTypeParameters
 * @run main TestTypeParameters
 */

public class TestTypeParameters extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "4927167-4974929";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-source", "1.5", "-sourcepath", SRC_DIR,
            "pkg"
    };

    //Input for string search tests.
    private static final String[][] TEST =
    {
        {BUG_ID + FS + "pkg" + FS + "C.html",
            "<td class=\"colFirst\"><code>&lt;W extends java.lang.String,V extends " +
            "java.util.List&gt;&nbsp;<br>java.lang.Object</code></td>"},
        {BUG_ID + FS + "pkg" + FS + "C.html",
            "<code>&lt;T&gt;&nbsp;java.lang.Object</code>"},
        {BUG_ID + FS + "pkg" + FS + "package-summary.html",
            "C&lt;E extends Parent&gt;"},
        //Nested type parameters
        {BUG_ID + FS + "pkg" + FS + "C.html",
            "<a name=\"formatDetails(java.util.Collection, java.util.Collection)\">" + NL +
            "<!--   -->" + NL +
            "</a>"},

    };
    private static final String[][] NEGATED_TEST = NO_TEST;


    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestTypeParameters tester = new TestTypeParameters();
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
