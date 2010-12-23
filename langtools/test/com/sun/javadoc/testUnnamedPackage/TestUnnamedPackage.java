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
 * @bug      4904075 4774450 5015144
 * @summary  Reference unnamed package as "Unnamed", not empty string.
 *           Generate a package summary for the unnamed package.
 * @author   jamieh
 * @library  ../lib/
 * @build    JavadocTester
 * @build    TestUnnamedPackage
 * @run main TestUnnamedPackage
 */

public class TestUnnamedPackage extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "4904075-4774450-5015144";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, SRC_DIR + FS + "C.java"
    };

    //Input for string search tests.
    private static final String[][] TEST = {
        {BUG_ID + FS + "package-summary.html",
            "<h1 title=\"Package\" class=\"title\">Package&nbsp;&lt;Unnamed&gt;</h1>"
        },
        {BUG_ID + FS + "package-summary.html",
            "This is a package comment for the unnamed package."
        },
        {BUG_ID + FS + "package-summary.html",
            "This is a class in the unnamed package."
        },
        {BUG_ID + FS + "package-tree.html",
            "<h1 class=\"title\">Hierarchy For Package &lt;Unnamed&gt;</h1>"
        },
        {BUG_ID + FS + "index-all.html",
            "title=\"class in &lt;Unnamed&gt;\""
        },
        {BUG_ID + FS + "C.html", "<a href=\"package-summary.html\">"}
    };
    private static final String[][] NEGATED_TEST = {
        {ERROR_OUTPUT, "BadSource"},
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestUnnamedPackage tester = new TestUnnamedPackage();
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
