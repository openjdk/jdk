/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8002304
 * @summary  Test for various method types in the method summary table
 * @author   Bhavesh Patel
 * @library  ../lib/
 * @build    JavadocTester TestMethodTypes
 * @run main TestMethodTypes
 */

public class TestMethodTypes extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "8002304";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg1"
    };

    private static final String[][] TEST = {
        {BUG_ID + FS + "pkg1" + FS + "A.html",
            "var methods = {"
        },

        {BUG_ID + FS + "pkg1" + FS + "A.html",
            "<caption><span id=\"t0\" class=\"activeTableTab\"><span>All " +
            "Methods</span><span class=\"tabEnd\">&nbsp;</span></span>" +
            "<span id=\"t1\" class=\"tableTab\"><span><a href=\"javascript:show(1);\">" +
            "Static Methods</a></span><span class=\"tabEnd\">&nbsp;</span></span>" +
            "<span id=\"t2\" class=\"tableTab\"><span><a href=\"javascript:show(2);\">" +
            "Instance Methods</a></span><span class=\"tabEnd\">&nbsp;</span></span>" +
            "<span id=\"t4\" class=\"tableTab\"><span><a href=\"javascript:show(8);\">" +
            "Concrete Methods</a></span><span class=\"tabEnd\">&nbsp;</span></span>" +
            "<span id=\"t6\" class=\"tableTab\"><span><a href=\"javascript:show(32);\">" +
            "Deprecated Methods</a></span><span class=\"tabEnd\">&nbsp;</span></span>" +
            "</caption>"
        },

        {BUG_ID + FS + "pkg1" + FS + "A.html",
            "<tr id=\"i0\" class=\"altColor\">"
        },

        {BUG_ID + FS + "pkg1" + FS + "B.html",
            "<caption><span id=\"t0\" class=\"activeTableTab\"><span>All " +
            "Methods</span><span class=\"tabEnd\">&nbsp;</span></span>" +
            "<span id=\"t2\" class=\"tableTab\"><span><a href=\"javascript:show(2);\">" +
            "Instance Methods</a></span><span class=\"tabEnd\">&nbsp;</span></span>" +
            "<span id=\"t3\" class=\"tableTab\"><span><a href=\"javascript:show(4);\">" +
            "Abstract Methods</a></span><span class=\"tabEnd\">&nbsp;</span></span>" +
            "</caption>"
        },

        {BUG_ID + FS + "pkg1" + FS + "D.html",
            "var methods = {"
        },

        {BUG_ID + FS + "pkg1" + FS + "D.html",
            "<caption><span id=\"t0\" class=\"activeTableTab\"><span>All " +
            "Methods</span><span class=\"tabEnd\">&nbsp;</span></span>" +
            "<span id=\"t2\" class=\"tableTab\"><span><a href=\"javascript:show(2);\">" +
            "Instance Methods</a></span><span class=\"tabEnd\">&nbsp;</span></span>" +
            "<span id=\"t3\" class=\"tableTab\"><span><a href=\"javascript:show(4);\">" +
            "Abstract Methods</a></span><span class=\"tabEnd\">&nbsp;</span></span>" +
            "<span id=\"t4\" class=\"tableTab\"><span><a href=\"javascript:show(8);\">" +
            "Concrete Methods</a></span><span class=\"tabEnd\">&nbsp;</span></span>" +
            "<span id=\"t6\" class=\"tableTab\"><span><a href=\"javascript:show(32);\">" +
            "Deprecated Methods</a></span><span class=\"tabEnd\">&nbsp;</span></span>" +
            "</caption>"
        },

        {BUG_ID + FS + "pkg1" + FS + "D.html",
            "<tr id=\"i0\" class=\"altColor\">"
        },
    };
    private static final String[][] NEGATED_TEST = {
        {BUG_ID + FS + "pkg1" + FS + "A.html",
            "<caption><span>Methods</span><span class=\"tabEnd\">&nbsp;</span>" +
            "</caption>"
        },

        {BUG_ID + FS + "pkg1" + FS + "B.html",
            "<caption><span>Methods</span><span class=\"tabEnd\">&nbsp;</span>" +
            "</caption>"
        },

        {BUG_ID + FS + "pkg" + FS + "D.html",
            "<caption><span>Methods</span><span class=\"tabEnd\">&nbsp;</span>" +
            "</caption>"
        },
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestMethodTypes tester = new TestMethodTypes();
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
