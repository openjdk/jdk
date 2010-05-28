/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4074234
 * @summary Make Javadoc capable of traversing/recursing all of given subpackages.
 * @author jamieh
 * @library ../lib/
 * @build JavadocTester
 * @build TestRecurseSubPackages
 * @run main TestRecurseSubPackages
 */

public class TestRecurseSubPackages extends JavadocTester {

    private static final String BUG_ID = "4074234";
    private static final String[] ARGS =
        new String[] {
            "-d", BUG_ID, "-sourcepath", SRC_DIR,
            "-subpackages", "pkg1", "-exclude", "pkg1.pkg2.packageToExclude"
        };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        String[][] tests = new String[6][2];
        for (int i = 0; i < tests.length; i++) {
            tests[i][0] = BUG_ID + FS + "allclasses-frame.html";
            tests[i][1] = "C" + (i+1) + ".html";
        }
        String[][] negatedTests = new String[][] {
            {BUG_ID + FS + "allclasses-frame.html", "DummyClass.html"}
        };
        TestRecurseSubPackages tester = new TestRecurseSubPackages();
        run(tester, ARGS, tests, negatedTests);
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
