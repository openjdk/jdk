/*
 * Copyright (c) 2002, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @ test
 * @ bug      <BUG-ID>
 * @ summary  <BUG-SYNOPSIS>
 * @ author   <AUTHOR> or delete
 * @ library  ../lib/
 * @ build    JavadocTester <CLASS NAME>
 * @ run main <CLASS NAME>
 */

import javadoc.tester.JavadocTester;

public class TemplateComplete extends JavadocTester {

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", OUTPUT_DIR, "-sourcepath", SRC_DIR
    };

    //Input for string search tests.
    private static final String[][] TEST = NO_TEST;
    private static final String[][] NEGATED_TEST = NO_TEST;

    //Input for Javadoc return code test.
    private static final int EXPECTED_EXIT_CODE = 0;


    //Input for file diff test.
    private static final String DIFFDIR1 = null;
    private static final String DIFFDIR2 = null;
    private static final String[][] FILES_TO_DIFF = {};

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TemplateComplete tester = new TemplateComplete();
        int actualExitCode = tester.run(ARGS, TEST, NEGATED_TEST);
        tester.checkExitCode(EXPECTED_EXIT_CODE, actualExitCode);
        tester.runDiffs(DIFFDIR1, DIFFDIR2, FILES_TO_DIFF, false);
        tester.printSummary();
    }
}
