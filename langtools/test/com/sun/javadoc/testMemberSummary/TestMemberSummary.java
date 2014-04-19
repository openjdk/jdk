/*
 * Copyright (c) 2003, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4951228 6290760 8025633 8026567
 * @summary  Test the case where the overriden method returns a different
 *           type than the method in the child class.  Make sure the
 *           documentation is inherited but the return type isn't.
 * @author   jamieh
 * @library  ../lib/
 * @build    JavadocTester
 * @build    TestMemberSummary
 * @run main TestMemberSummary
 */

public class TestMemberSummary extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "4951228-6290760";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg","pkg2"
    };

    //Input for string search tests.
    private static final String[][] TEST = {
        // Check return type in member summary.
        {BUG_ID + "/pkg/PublicChild.html",
            "<code><a href=\"../pkg/PublicChild.html\" title=\"class in pkg\">PublicChild</a></code></td>\n" +
            "<td class=\"colLast\"><code><span class=\"memberNameLink\"><a href=\"../pkg/PublicChild.html#returnTypeTest--\">" +
            "returnTypeTest</a></span>()</code>"
        },
        // Check return type in member detail.
        {BUG_ID + "/pkg/PublicChild.html",
            "<pre>public&nbsp;<a href=\"../pkg/PublicChild.html\" title=\"class in pkg\">" +
            "PublicChild</a>&nbsp;returnTypeTest()</pre>"
        },

         // Legacy anchor dimensions (6290760)
        {BUG_ID + "/pkg2/A.html",
            "<a name=\"f-java.lang.Object:A-\">\n" +
            "<!--   -->\n" +
            "</a><a name=\"f-T:A-\">\n" +
            "<!--   -->\n" +
            "</a>"
        },
    };
    private static final String[][] NEGATED_TEST = NO_TEST;

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestMemberSummary tester = new TestMemberSummary();
        tester.run(ARGS, TEST, NEGATED_TEST);
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
