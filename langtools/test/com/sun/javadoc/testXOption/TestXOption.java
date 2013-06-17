/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8007687
 * @summary  Make sure that the -X option works properly.
 * @library  ../lib/
 * @build    JavadocTester TestXOption
 * @run main TestXOption
 */

public class TestXOption extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "8007687";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "-X",
            SRC_DIR + FS + "TestXOption.java"
    };

    private static final String[] ARGS2 = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR,
            SRC_DIR + FS + "TestXOption.java"
    };

    private static final String[][] TEST = {
        {NOTICE_OUTPUT, "-Xmaxerrs "},
        {NOTICE_OUTPUT, "-Xmaxwarns "},
        {STANDARD_OUTPUT, "-Xdocrootparent "},
        {STANDARD_OUTPUT, "-Xdoclint "},
        {STANDARD_OUTPUT, "-Xdoclint:"},
    };
    private static final String[][] NEGATED_TEST = NO_TEST;

    //The help option should not crash the doclet.
    private static final int EXPECTED_EXIT_CODE = 0;

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestXOption tester = new TestXOption();
        int actualExitCode = run(tester, ARGS, TEST, NEGATED_TEST);
        tester.checkExitCode(EXPECTED_EXIT_CODE, actualExitCode);
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
