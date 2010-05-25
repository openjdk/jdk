/*
 * Copyright (c) 2001, 2006, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4483401 4483407 4483409 4483413 4494343
 * @summary Test to make sure that Javadoc behaves properly when
 * run on a completely empty class (no comments or members).
 * @author jamieh
 * @library ../lib/
 * @build JavadocTester
 * @build TestEmptyClass
 * @run main TestEmptyClass
 */

public class TestEmptyClass extends JavadocTester {

    private static final String OUTPUT_DIR = "tmp";
    private static final String[][] TEST = NO_TEST;
    private static final String[][] NEGATED_TEST = {

        //The overview tree should not link to classes that were not documented
        {OUTPUT_DIR + FS + "overview-tree.html", "<A HREF=\"TestEmptyClass.html\">"},

        //The index page should not link to classes that were not documented
        {OUTPUT_DIR + FS + "index-all.html", "<A HREF=\"TestEmptyClass.html\">"},
    };
    private static final String[] ARGS =
        new String[] {
            "-classpath", SRC_DIR + FS + "src",
            "-d", OUTPUT_DIR, "-sourcepath", SRC_DIR + FS + "src",
            SRC_DIR + FS + "src" + FS + "Empty.java"
        };
    private static final String BUG_ID = "4483401 4483407 4483409 4483413 4494343";

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestEmptyClass tester = new TestEmptyClass();
        int exitCode = run(tester, ARGS, TEST, NEGATED_TEST);
        tester.printSummary();
        if (exitCode != 0) {
            throw new Error("Error found while executing Javadoc");
        }
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
