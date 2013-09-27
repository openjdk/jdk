/*
 * Copyright (c) 2002, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;

/*
 * @test
 * @bug 4258405 4973606 8024096
 * @summary This test verifies that the doc-file directory does not
 *          get overwritten when the sourcepath is equal to the destination
 *          directory.
 *          Also test that -docfilessubdirs and -excludedocfilessubdir both work.
 * @author jamieh
 * @library ../lib/
 * @build JavadocTester
 * @build TestDocFileDir
 * @run main TestDocFileDir
 */

public class TestDocFileDir extends JavadocTester {

    private static final String BUG_ID = "4258405-4973606";

    private static final String[][] TEST1 = {
        {BUG_ID + "-1" + FS + "pkg" + FS + "doc-files" + FS + "testfile.txt",
            "This doc file did not get trashed."}
        };
    private static final String[][] NEGATED_TEST1 = NO_TEST;

    private static final String[] FILE_TEST2 = {
        BUG_ID + "-2" + FS + "pkg" + FS + "doc-files" + FS + "subdir-used1" +
            FS + "testfile.txt",
        BUG_ID + "-2" + FS + "pkg" + FS + "doc-files" + FS + "subdir-used2" +
            FS + "testfile.txt"
    };
    private static final String[] FILE_NEGATED_TEST2 = {
        BUG_ID + "-2" + FS + "pkg" + FS + "doc-files" + FS + "subdir-excluded1" +
            FS + "testfile.txt",
        BUG_ID + "-2" + FS + "pkg" + FS + "doc-files" + FS + "subdir-excluded2" +
            FS + "testfile.txt"
    };

    private static final String[][] TEST0 = {
        {"pkg" + FS + "doc-files" + FS + "testfile.txt",
            "This doc file did not get trashed."}
        };
    private static final String[][] NEGATED_TEST0 = {};

    //Output dir = Input Dir
    private static final String[] ARGS1 =
        new String[] {
            "-d", BUG_ID + "-1",
            "-sourcepath",
                "blah" + File.pathSeparator + BUG_ID + "-1" + File.pathSeparator + "blah",
            "pkg"};

    //Exercising -docfilessubdirs and -excludedocfilessubdir
    private static final String[] ARGS2 =
        new String[] {
            "-d", BUG_ID + "-2",
            "-sourcepath", SRC_DIR,
            "-docfilessubdirs",
            "-excludedocfilessubdir", "subdir-excluded1:subdir-excluded2",
            "pkg"};

    //Output dir = "", Input dir = ""
    private static final String[] ARGS0 =
        new String[] {"pkg" + FS + "C.java"};


    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestDocFileDir tester = new TestDocFileDir();
        copyDir(SRC_DIR + FS + "pkg", ".");
        run(tester, ARGS0, TEST0, NEGATED_TEST0);
        copyDir(SRC_DIR + FS + "pkg", BUG_ID + "-1");
        run(tester, ARGS1, TEST1, NEGATED_TEST1);
        run(tester, ARGS2, NO_TEST, NO_TEST, FILE_TEST2, FILE_NEGATED_TEST2);
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
