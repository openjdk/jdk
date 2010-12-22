/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4515705 4804296 4702454 4697036
 * @summary  Make sure that first sentence warning only appears once.
 *           Make sure that only warnings/errors are printed when quiet is used.
 *           Make sure that links to private/unincluded methods do not cause
 *           a "link unresolved" warning.
 *           Make sure error message starts with "error -".
 * @author   jamieh
 * @library  ../lib/
 * @build    JavadocTester
 * @build    TestWarnings
 * @run main TestWarnings
 */

public class TestWarnings extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "4515705-4804296-4702454-4697036";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg"
    };

    private static final String[] ARGS2 = new String[] {
        "-d", BUG_ID, "-private", "-sourcepath", SRC_DIR, "pkg"
    };

    //Input for string search tests.
    private static final String[][] TEST = {
        {WARNING_OUTPUT,
            "X.java:11: warning - Missing closing '}' character for inline tag"},
        {ERROR_OUTPUT,
            "package.html: error - Body tag missing from HTML"},

    };
    private static final String[][] NEGATED_TEST = {
        {BUG_ID + FS + "pkg" + FS + "X.html", "can't find m()"},
        {BUG_ID + FS + "pkg" + FS + "X.html", "can't find X()"},
        {BUG_ID + FS + "pkg" + FS + "X.html", "can't find f"},
    };

    private static final String[][] TEST2 = {
        {BUG_ID + FS + "pkg" + FS + "X.html", "<a href=\"../pkg/X.html#m()\"><code>m()</code></a><br/>"},
        {BUG_ID + FS + "pkg" + FS + "X.html", "<a href=\"../pkg/X.html#X()\"><code>X()</code></a><br/>"},
        {BUG_ID + FS + "pkg" + FS + "X.html", "<a href=\"../pkg/X.html#f\"><code>f</code></a><br/>"},
    };

    private static final String[][] NEGATED_TEST2 = NO_TEST;


    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestWarnings tester = new TestWarnings();
        run(tester, ARGS, TEST, NEGATED_TEST);
        run(tester, ARGS2, TEST2, NEGATED_TEST2);
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
