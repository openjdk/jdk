/*
 * Copyright (c) 2002, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4506980
 * @summary Test to make sure that there is no difference in the output
 * when specifying packages on the command line and specifying individual
 * classes.
 * @author jamieh
 * @library ../lib/
 * @build JavadocTester
 * @build TestCmndLineClass
 * @run main TestCmndLineClass
 */

public class TestCmndLineClass extends JavadocTester {

    private static final String OUTPUT_DIR1 = "4506980-tmp1";
    private static final String OUTPUT_DIR2 = "4506980-tmp2";
    private static final String[] ARGS1 =
        new String[] {
            "-d", OUTPUT_DIR1, "-sourcepath", SRC_DIR,
            "-notimestamp", SRC_DIR + "/C5.java", "pkg1", "pkg2"
        };
    private static final String[] ARGS2 =
        new String[] {
            "-d", OUTPUT_DIR2, "-sourcepath", SRC_DIR,
            "-notimestamp", SRC_DIR + "/C5.java",
            SRC_DIR + "/pkg1/C1.java",
            SRC_DIR + "/pkg1/C2.java",
            SRC_DIR + "/pkg2/C3.java",
            SRC_DIR + "/pkg2/C4.java"
        };
    private static final String[][] FILES_TO_DIFF = {
        {OUTPUT_DIR1 + "/C5.html", OUTPUT_DIR2 + "/C5.html"},
        {OUTPUT_DIR2 + "/pkg1/C1.html", OUTPUT_DIR2 + "/pkg1/C1.html"},
        {OUTPUT_DIR1 + "/pkg1/C2.html", OUTPUT_DIR2 + "/pkg1/C2.html"},
        {OUTPUT_DIR1 + "/pkg2/C3.html", OUTPUT_DIR2 + "/pkg2/C3.html"},
        {OUTPUT_DIR1 + "/pkg2/C4.html", OUTPUT_DIR2 + "/pkg2/C4.html"}
    };

    private static final String BUG_ID = "4506980";

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestCmndLineClass tester = new TestCmndLineClass();
        tester.run(ARGS1, NO_TEST, NO_TEST);
        tester.run(ARGS2, NO_TEST, NO_TEST);
        tester.runDiffs(FILES_TO_DIFF);
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
