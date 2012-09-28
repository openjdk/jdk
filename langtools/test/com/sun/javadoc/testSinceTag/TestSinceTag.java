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
 * @bug      7180906
 * @summary  Test to make sure that the since tag works correctly
 * @author   Bhavesh Patel
 * @library  ../lib/
 * @build    JavadocTester TestSinceTag
 * @run main TestSinceTag
 */

public class TestSinceTag extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "7180906";

    //Javadoc arguments.
    private static final String[] ARGS1 = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg1"
    };

    private static final String[] ARGS2 = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "-nosince", "pkg1"
    };

    //Input for string search tests.
    private static final String[][] TEST = {
        {BUG_ID + FS + "pkg1" + FS + "C1.html",
            "<dl><dt><span class=\"strong\">Since:</span></dt>" + NL +
            "  <dd>JDK1.0</dd>"
        },
        {BUG_ID + FS + "serialized-form.html",
            "<dl><dt><span class=\"strong\">Since:</span></dt>" + NL +
            "  <dd>1.4</dd>"
        }
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestSinceTag tester = new TestSinceTag();
        run(tester, ARGS1, TEST, NO_TEST);
        run(tester, ARGS2, NO_TEST, TEST);
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
