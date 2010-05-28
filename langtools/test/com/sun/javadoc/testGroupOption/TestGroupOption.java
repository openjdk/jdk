/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4924383
 * @summary  Test to make sure the -group option does not cause a bad warning
 *           to be printed.
 * @author   jamieh
 * @library  ../lib/
 * @build    JavadocTester
 * @build    TestGroupOption
 * @run main TestGroupOption
 */

public class TestGroupOption extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "4924383";

    //Javadoc arguments.
    private static final String[] ARGS1 = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR,
        "-group", "Package One", "pkg1",
        "-group", "Package Two", "pkg2",
        "-group", "Package Three", "pkg3",
        "pkg1", "pkg2", "pkg3"
    };

    private static final String[] ARGS2 = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR,
        "-group", "Package One", "pkg1",
        "-group", "Package One", "pkg2",
        "-group", "Package One", "pkg3",
        "pkg1", "pkg2", "pkg3"
    };

    //Input for string search tests.
    private static final String[][] TEST1 = NO_TEST;
    private static final String[][] NEGATED_TEST1 = {{WARNING_OUTPUT, "-group"}};

    private static final String[][] TEST2 = {{WARNING_OUTPUT, "-group"}};
    private static final String[][] NEGATED_TEST2 = NO_TEST;

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        //Make sure the warning is not printed when -group is used correctly.
        TestGroupOption tester = new TestGroupOption();
        run(tester, ARGS1, TEST1, NEGATED_TEST1);
        tester.printSummary();

        //Make sure the warning is printed when -group is not used correctly.
        tester = new TestGroupOption();
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
