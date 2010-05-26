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
 * @bug      4657239 4775743
 * @summary  Make sure a notification is printed when an output directory must
 *           be created.
 *           Make sure classname is not include in javadoc usage message.
 * @author   jamieh
 * @library  ../lib/
 * @build    JavadocTester
 * @build    TestNotifications
 * @run main TestNotifications
 */

public class TestNotifications extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "4657239";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg"
    };

    private static final String[] ARGS2 = new String[] {
        "-help"
    };

    //Input for string search tests.
    private static final String[][] TEST = {
        {NOTICE_OUTPUT, "Creating destination directory: \"4657239"}
    };
    private static final String[][] NEGATED_TEST = {
        {NOTICE_OUTPUT, "Creating destination directory: \"4657239"}
    };

    private static final String[][] NEGATED_TEST2 = {
        {NOTICE_OUTPUT, "[classnames]"}
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestNotifications tester = new TestNotifications();
        // Notify that the destination directory must be created.
        run(tester, ARGS, TEST, NO_TEST);
        // No need to notify that the destination must be created because
        // it already exists.
        run(tester, ARGS, NO_TEST, NEGATED_TEST);
        //Make sure classname is not include in javadoc usage message.
        run(tester, ARGS2, NO_TEST, NEGATED_TEST2);
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
