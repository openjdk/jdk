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
 * @bug      4985072
 * @summary  Test to make sure that exceptions always show up in the
 *           correct order.
 * @author   jamieh
 * @library  ../lib/
 * @build    JavadocTester
 * @build    TestThrowsTag
 * @run main TestThrowsTag
 */

public class TestThrowsTag extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "4985072";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg"
    };

    //Input for string search tests.
    private static final String[][] TEST = {
        {BUG_ID + FS + "pkg" + FS + "C.html",
            "<DD><CODE><A HREF=\"../pkg/T1.html\" title=\"class in pkg\">T1</A></CODE> - the first throws tag.</DD>" + NL +
            "<DD><CODE><A HREF=\"../pkg/T2.html\" title=\"class in pkg\">T2</A></CODE> - the second throws tag.</DD>" + NL +
            "<DD><CODE><A HREF=\"../pkg/T3.html\" title=\"class in pkg\">T3</A></CODE> - the third throws tag.</DD>" + NL +
            "<DD><CODE><A HREF=\"../pkg/T4.html\" title=\"class in pkg\">T4</A></CODE> - the fourth throws tag.</DD>" + NL +
            "<DD><CODE><A HREF=\"../pkg/T5.html\" title=\"class in pkg\">T5</A></CODE> - the first inherited throws tag.</DD>" + NL +
            "<DD><CODE><A HREF=\"../pkg/T6.html\" title=\"class in pkg\">T6</A></CODE> - the second inherited throws tag.</DD>" + NL +
            "<DD><CODE><A HREF=\"../pkg/T7.html\" title=\"class in pkg\">T7</A></CODE> - the third inherited throws tag.</DD>" + NL +
            "<DD><CODE><A HREF=\"../pkg/T8.html\" title=\"class in pkg\">T8</A></CODE> - the fourth inherited throws tag.</DD>"
        },
    };
    private static final String[][] NEGATED_TEST = NO_TEST;

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestThrowsTag tester = new TestThrowsTag();
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
