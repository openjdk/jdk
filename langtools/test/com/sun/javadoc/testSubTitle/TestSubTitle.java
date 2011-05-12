/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7010342
 * @summary Test for correct sub title generation.
 * @author Bhavesh Patel
 * @library ../lib/
 * @build JavadocTester
 * @build TestSubTitle
 * @run main TestSubTitle
 */

public class TestSubTitle extends JavadocTester {

    private static final String BUG_ID = "7010342";
    private static final String[][] TEST = {
        {BUG_ID + FS + "pkg" + FS + "package-summary.html",
            "<div class=\"block\">This is the description of package pkg.</div>"
        },
        {BUG_ID + FS + "pkg" + FS + "C.html",
            "<div class=\"subTitle\">pkg</div>"
        }
    };
    private static final String[][] NEG_TEST = {
        {BUG_ID + FS + "pkg" + FS + "package-summary.html",
            "<p class=\"subTitle\">" + NL + "<div class=\"block\">This is the " +
            "description of package pkg.</div>" + NL + "</p>"
        },
        {BUG_ID + FS + "pkg" + FS + "C.html",
            "<p class=\"subTitle\">pkg</p>"
        }
    };
    private static final String[] ARGS = new String[]{
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg"
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestSubTitle tester = new TestSubTitle();
        run(tester, ARGS, TEST, NEG_TEST);
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
