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
 * @bug 4640745
 * @summary This test verifys that the -link option handles absolute paths.
 * @author jamieh
 * @library ../lib/
 * @build JavadocTester
 * @build TestAbsLinkPath
 * @run main TestAbsLinkPath
 */

public class TestAbsLinkPath extends JavadocTester {

    private static final String BUG_ID = "4640745";
    private static final String[][] TEST = {
        {"tmp" + FS + "pkg1" + FS + "C1.html", "C2.html"}};
    private static final String[][] NEGATED_TEST = NO_TEST;

    private static final String[] ARGS1 =
        new String[] {
            "-d", "tmp2", "-sourcepath", SRC_DIR, "pkg2"};
    private static final String[] ARGS2 =
        new String[] {
            "-d", "tmp", "-sourcepath", SRC_DIR,
            "-link", ".." + FS + "tmp2", "pkg1"};

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestAbsLinkPath tester = new TestAbsLinkPath();
        run(tester, ARGS1, NO_TEST, NO_TEST);
        run(tester, ARGS2,  TEST, NEGATED_TEST);
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
