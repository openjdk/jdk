/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8016675
 * @summary Test for window title.
 * @author Bhavesh Patel
 * @library ../lib/
 * @build JavadocTester TestWindowTitle
 * @run main TestWindowTitle
 */

public class TestWindowTitle extends JavadocTester {

    private static final String BUG_ID = "8016675";
    private static final String WIN_TITLE =
            "Testing \"Window 'Title'\" with a \\ backslash and a / " +
            "forward slash and a \u00e8 unicode char also a    tab and also a " +
            "\t special character another \u0002 unicode)";
    private static final String[][] TEST = {
        {BUG_ID + FS  + "overview-summary.html",
            "parent.document.title=\"Overview (Testing \\\"Window \\\'Title\\\'\\\" " +
            "with a \\\\ backslash and a / forward slash and a \\u00E8 unicode char " +
            "also a    tab and also a \\t special character another \\u0002 unicode))\";"
        },
    };
    private static final String[][] NEG_TEST = {
        {BUG_ID + FS + "overview-summary.html",
            "parent.document.title=\"Overview (Testing \"Window \'Title\'\" " +
            "with a \\ backslash and a / forward slash and a \u00E8 unicode char " +
            "also a    tab and also a \t special character another \u0002 unicode))\";"
        },
    };
    private static final String[] ARGS = new String[]{
        "-d", BUG_ID, "-windowtitle", WIN_TITLE, "-sourcepath", SRC_DIR, "p1", "p2"
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestWindowTitle tester = new TestWindowTitle();
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
