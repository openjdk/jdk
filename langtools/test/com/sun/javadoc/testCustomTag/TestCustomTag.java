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
 * @bug      8006248
 * @summary  Test custom tag. Verify that an unknown tag generates appropriate warnings.
 * @author   Bhavesh Patel
 * @library  ../lib/
 * @build    JavadocTester taglets.CustomTag TestCustomTag
 * @run main TestCustomTag
 */

public class TestCustomTag extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "8006248";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-Xdoclint:none", "-d", BUG_ID, "-tagletpath", SRC_DIR,
        "-taglet", "taglets.CustomTag", "-sourcepath",
        SRC_DIR, SRC_DIR + FS + "TagTestClass.java"
    };

    private static final String[] ARGS1 = new String[] {
        "-d", BUG_ID + "-1", "-tagletpath", SRC_DIR, "-taglet", "taglets.CustomTag",
        "-sourcepath", SRC_DIR, SRC_DIR + FS + "TagTestClass.java"
    };
    private static final String[] ARGS2 = new String[] {
        "-Xdoclint:none", "-d", BUG_ID + "-2", "-sourcepath",
        SRC_DIR, SRC_DIR + FS + "TagTestClass.java"
    };

    private static final String[] ARGS3 = new String[] {
        "-d", BUG_ID + "-3", "-sourcepath", SRC_DIR, SRC_DIR + FS + "TagTestClass.java"
    };

    //Input for string search tests.
    private static final String[][] TEST = new String[][] {
        {WARNING_OUTPUT, "warning - @unknownTag is an unknown tag."
        }
    };

    private static final String[][] TEST1 = new String[][] {
        {ERROR_OUTPUT, "error: unknown tag: unknownTag"
        }
    };
    private static final String[][] TEST2 = new String[][] {
        {WARNING_OUTPUT, "warning - @customTag is an unknown tag."
        },
        {WARNING_OUTPUT, "warning - @unknownTag is an unknown tag."
        }
    };

    private static final String[][] TEST3 = new String[][] {
        {ERROR_OUTPUT, "error: unknown tag: customTag"
        },
        {ERROR_OUTPUT, "error: unknown tag: unknownTag"
        }
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestCustomTag tester = new TestCustomTag();
        run(tester, ARGS, TEST, NO_TEST);
        run(tester, ARGS1, TEST1, NO_TEST);
        run(tester, ARGS2, TEST2, NO_TEST);
        run(tester, ARGS3, TEST3, NO_TEST);
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
